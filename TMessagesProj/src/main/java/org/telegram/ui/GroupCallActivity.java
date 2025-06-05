package org.telegram.ui;

import static android.content.Context.AUDIO_SERVICE;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

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
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.CallLog;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.utils.PipUtils;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.messenger.voip.VoipAudioManager;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AccountSelectCell;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GroupCallInvitedCell;
import org.telegram.ui.Cells.GroupCallTextCell;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FillLastGridLayoutManager;
import org.telegram.ui.Components.GroupCallFullscreenAdapter;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.GroupCallRecordAlert;
import org.telegram.ui.Components.GroupVoipInviteAlert;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.ImageUpdater;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.JoinCallAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecordStatusDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.WaveDrawable;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Components.voip.GroupCallGridCell;
import org.telegram.ui.Components.voip.GroupCallMiniTextureView;
import org.telegram.ui.Components.voip.GroupCallRenderersContainer;
import org.telegram.ui.Components.voip.GroupCallStatusIcon;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.RTMPStreamPipOverlay;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.DominantColors;
import org.webrtc.voiceengine.WebRtcAudioTrack;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GroupCallActivity extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, VoIPService.StateListener {

    public final static int TABLET_LIST_SIZE = 320;
    public static final long TRANSITION_DURATION = 350;

    private static final int eveyone_can_speak_item = 1;
    private static final int admin_can_speak_item = 2;
    private static final int share_invite_link_item = 3;
    private static final int leave_item = 4;
    private static final int start_record_item = 5;
    private static final int edit_item = 6;
    private static final int permission_item = 7;
    private static final int user_item = 8;
    private static final int screen_capture_item = 9;
    private static final int sound_item = 10;
    private static final int noise_item = 11;
    private static final int user_item_gap = 0;

    private static final int MUTE_BUTTON_STATE_UNMUTE = 0;
    private static final int MUTE_BUTTON_STATE_MUTE = 1;
    private static final int MUTE_BUTTON_STATE_MUTED_BY_ADMIN = 2;
    private static final int MUTE_BUTTON_STATE_CONNECTING = 3;
    private static final int MUTE_BUTTON_STATE_RAISED_HAND = 4;
    private static final int MUTE_BUTTON_STATE_START_NOW = 5;
    private static final int MUTE_BUTTON_STATE_SET_REMINDER = 6;
    private static final int MUTE_BUTTON_STATE_CANCEL_REMINDER = 7;

    public static int currentScreenOrientation;

    public static GroupCallActivity groupCallInstance;
    public static boolean groupCallUiVisible;
    private final ProfileGalleryView avatarsViewPager;
    private final GridLayoutManager.SpanSizeLookup spanSizeLookup;

    private AccountInstance accountInstance;

    private View actionBarBackground;
    private ActionBar actionBar;
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private LinearLayout scheduleTimerContainer;
    private TextView scheduleInfoTextView;
    private TextView scheduleButtonTextView;
    private SimpleTextView scheduleStartInTextView;
    private SimpleTextView scheduleTimeTextView;
    private SimpleTextView scheduleStartAtTextView;
    private GroupCallItemAnimator itemAnimator;
    private FillLastGridLayoutManager layoutManager;
    private VoIPToggleButton flipButton;
    private FrameLayout speakerButtonContainer;
    private VoIPToggleButton speakerButton;
    private int speakerIcon;
    private ImageView speakerImageView;
    private VoIPToggleButton cameraButton;
    private VoIPToggleButton soundButton;
    private float soundButtonScale;
    private float cameraButtonScale;
    private VoIPToggleButton leaveButton;
    private RLottieImageView muteButton;
    private TextView[] muteLabel = new TextView[2];
    private FrameLayout buttonsContainer;
    private RadialProgressView radialProgressView;
    private Drawable shadowDrawable;
    private View actionBarShadow;
    private AnimatorSet actionBarAnimation;
    private LaunchActivity parentActivity;
    private UndoView[] undoView = new UndoView[2];
    private FrameLayout bulletinContainer;
    private AccountSelectCell accountSelectCell;
    private View accountGap;
    private boolean changingPermissions;
    private HintView recordHintView;
    private HintView reminderHintView;
    private int buttonsVisibility;
    private CallEncryptionCellDrawable encryptionDrawable;

    private ImageView expandButton;
    private ImageView minimizeButton;

    public final ArrayList<ChatObject.VideoParticipant> visibleVideoParticipants = new ArrayList<>();

    float progressToHideUi;

    private ShareAlert shareAlert;

    private boolean delayedGroupCallUpdated;

    private long creatingServiceTime;
    private boolean callInitied;

    private RectF rect = new RectF();

    private boolean enterEventSent;
    private boolean anyEnterEventSent;

    private float scrollOffsetY;

    PrivateVideoPreviewDialog previewDialog;

    private TLRPC.Peer selfPeer;
    private TLObject userSwitchObject;

    private Paint listViewBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<TLRPC.GroupCallParticipant> oldParticipants = new ArrayList<>();
    private ArrayList<ChatObject.VideoParticipant> oldVideoParticipants = new ArrayList<>();
    private ArrayList<Long> oldInvited = new ArrayList<>();
    private ArrayList<Long> oldShadyJoin = new ArrayList<>();
    private ArrayList<Long> oldShadyLeft = new ArrayList<>();
    private int oldCount;

    private RLottieDrawable bigMicDrawable;
    private RLottieDrawable handDrawables;
    private boolean playingHandAnimation;

    private final BlobDrawable tinyWaveDrawable;
    private final BlobDrawable bigWaveDrawable;

    private float amplitude;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;

    private RadialGradient radialGradient;
    private final Matrix radialMatrix;
    private final Paint radialPaint;

    private LightningView textureLightningView;

    private float switchToButtonProgress;
    private float switchToButtonInt2;
    private float scheduleButtonsScale;
    private ValueAnimator scheduleAnimator;

    private ValueAnimator muteButtonAnimator;
    private ValueAnimator expandAnimator;
    private ValueAnimator expandSizeAnimator;

    private TLRPC.InputPeer schedulePeer;
    @Nullable
    public TLRPC.Chat currentChat;
    public ChatObject.Call call;
    private boolean scheduleHasFewPeers;
    private String scheduledHash;

    private RecordCallDrawable recordCallDrawable;
    private AudioPlayerAlert.ClippingTextViewSwitcher titleTextView;
    private TextView liveLabelTextView;
    private Paint liveLabelPaint;
    private ValueAnimator liveLabelBgColorAnimator;
    private float textureLightningViewAnimatingAlpha;
    private LinearLayout titleLayout;
    private ActionBarMenuItem otherItem;
    private ActionBarMenuItem pipItem;
    private ActionBarMenuItem screenShareItem;
    private ActionBarMenuSubItem inviteItem;
    private ActionBarMenuSubItem editTitleItem;
    private ActionBarMenuSubItem soundItem;
    private ActionBarMenuSubItem noiseItem;
    private ActionBarMenuSubItem permissionItem;
    private ActionBarMenuSubItem recordItem;
    private ActionBarMenuSubItem screenItem;
    private ActionBarMenuSubItem everyoneItem;
    private ActionBarMenuSubItem adminItem;
    private ActionBarMenuSubItem leaveItem;
    private final LinearLayout menuItemsContainer;
    private View soundItemDivider;
    private WatchersView watchersView;

    private Runnable updateCallRecordRunnable;

    private GroupVoipInviteAlert groupVoipInviteAlert;

    private int muteButtonState = MUTE_BUTTON_STATE_UNMUTE;
    private boolean animatingToFullscreenExpand = false;

    private boolean startingGroupCall;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Paint paintTmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Paint leaveBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private WeavingState[] states = new WeavingState[8];
    private float switchProgress = 1.0f;
    private WeavingState prevState;
    private WeavingState currentState;
    private long lastUpdateTime;
    private int shaderBitmapSize = 200;
    private float showWavesProgress;
    private float showLightingProgress;

    private boolean scheduled;
    private boolean pressed;

    private int currentCallState;

    private float colorProgress;
    private int backgroundColor;
    private boolean invalidateColors = true;
    private final int[] colorsTmp = new int[4];

    // private boolean isVideoCall = true;
    private final ArrayList<GroupCallMiniTextureView> attachedRenderers = new ArrayList<>();
    private final ArrayList<GroupCallMiniTextureView> attachedRenderersTmp = new ArrayList<>();
    private GroupCallRenderersContainer renderersContainer;

    private View blurredView;
    PinchToZoomHelper pinchToZoomHelper;

    private float progressToAvatarPreview;

    private View scrimPopupLayout;
    private boolean avatarsPreviewShowed;
    private boolean useBlur;

    ImageUpdater currentAvatarUpdater;
    AvatarUpdaterDelegate avatarUpdaterDelegate;

    private Boolean wasNotInLayoutFullscreen;
    private Boolean wasExpandBigSize = true;

    private int scheduleStartAt;
    private boolean contentFullyOverlayed;
   // private float progressToFullscreenMode;
    ValueAnimator fullscreenModeAnimator;

    RecyclerListView fullscreenUsersListView;
    RecyclerListView tabletVideoGridView;
    GroupCallTabletGridAdapter tabletGridAdapter;
    GroupCallFullscreenAdapter fullscreenAdapter;
    ViewTreeObserver.OnPreDrawListener requestFullscreenListener;
    public CellFlickerDrawable cellFlickerDrawable = new CellFlickerDrawable();
    private PipSource pipSource;

    public static boolean isLandscapeMode;
    public static boolean isTabletMode;
    public final ArrayList<GroupCallStatusIcon> statusIconPool = new ArrayList<>();
    private boolean drawSpeakingSubtitle;

    private HashMap<View, Float> buttonsAnimationParamsX = new HashMap<>();
    private HashMap<View, Float> buttonsAnimationParamsY = new HashMap<>();
    boolean animateButtonsOnNextLayout;

    private Runnable onUserLeaveHintListener = this::onUserLeaveHint;

    private Runnable updateSchedeulRunnable = new Runnable() {
        @Override
        public void run() {
            if (scheduleTimeTextView == null || isDismissed()) {
                return;
            }
            int time;
            if (call != null) {
                time = call.call.schedule_date;
            } else {
                time = scheduleStartAt;
            }
            if (time == 0) {
                return;
            }
            int diff = time - accountInstance.getConnectionsManager().getCurrentTime();
            if (diff >= 24 * 60 * 60) {
                scheduleTimeTextView.setText(LocaleController.formatPluralString("Days", Math.round(diff / (24 * 60 * 60.0f))));
            } else {
                scheduleTimeTextView.setText(AndroidUtilities.formatFullDuration(Math.abs(diff)));
                if (diff < 0 && scheduleStartInTextView.getTag() == null) {
                    scheduleStartInTextView.setTag(1);
                    scheduleStartInTextView.setText(getString(R.string.VoipChatLateBy));
                }
            }
            scheduleStartAtTextView.setText(LocaleController.formatStartsTime(time, 3));
            AndroidUtilities.runOnUIThread(updateSchedeulRunnable, 1000);
        }
    };

    private Runnable unmuteRunnable = () -> {
        if (VoIPService.getSharedInstance() == null) {
            return;
        }
        VoIPService.getSharedInstance().setMicMute(false, true, false);
    };

    private Runnable pressRunnable = () -> {
        if (call == null || !scheduled || VoIPService.getSharedInstance() == null) {
            return;
        }
        try {
            muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignored) {}
        updateMuteButton(MUTE_BUTTON_STATE_MUTE, true);
        AndroidUtilities.runOnUIThread(unmuteRunnable, 80);
        scheduled = false;
        pressed = true;
    };

    public static volatile DispatchQueue updateTextureLightningQueue = new DispatchQueue("updateTextureLightningQueue");

    private boolean needTextureLightning = false;
    private final Runnable updateTextureLightningRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (renderersContainer == null || renderersContainer.inFullscreenMode) return;
                final GroupCallGridCell cell = findGroupCallGridCell();
                if (cell == null || !cell.isAttachedToWindow()) return;
                final GroupCallMiniTextureView rendered = cell.getRenderer();
                if (rendered == null) return;
                final VoIPTextureView textureView = rendered.textureView;
                if (textureView == null) return;
                updateTextureLightningQueue.postRunnable(() -> {
                    try {
                        Bitmap bitmap = textureView.blurRenderer.getBitmap(100, 100);
                        if (bitmap == null) return;
                        int[] bitmapColors = DominantColors.getColorsSync(false, bitmap, true);
                        AndroidUtilities.runOnUIThread(() -> textureLightningView.setNewColors(bitmapColors));
                    } catch (Exception e2) {
                        FileLog.e(e2);
                    }
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    };

    private GroupCallGridCell findGroupCallGridCell() {
        for (int i = 0; i < listView.getChildCount(); i++) {
            View view = listView.getChildAt(i);
            if (view.isAttachedToWindow() && view instanceof GroupCallGridCell && listView.getChildAdapterPosition(view) >= 0) {
                return (GroupCallGridCell) view;
            }
        }
        return null;
    }

    private void runUpdateTextureLightningRunnable() {
        AndroidUtilities.cancelRunOnUIThread(updateTextureLightningRunnable);
        if (!needTextureLightning || textureLightningView == null || VoIPService.getSharedInstance() == null || !isRtmpStream() || listView == null || !LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            return;
        }
        AndroidUtilities.runOnUIThread(updateTextureLightningRunnable, 30);
    }

    LongSparseIntArray visiblePeerIds = new LongSparseIntArray();

    public static final Property<GroupCallActivity, Float> COLOR_PROGRESS = new AnimationProperties.FloatProperty<GroupCallActivity>("colorProgress") {
        @Override
        public void setValue(GroupCallActivity object, float value) {
            object.setColorProgress(value);
        }

        @Override
        public Float get(GroupCallActivity object) {
            return object.getColorProgress();
        }
    };
    private final FrameLayout avatarPreviewContainer;
    private final AvatarPreviewPagerIndicator avatarPagerIndicator;
    private ViewGroup currentOptionsLayout;
    public boolean drawingForBlur;
    private final RLottieDrawable flipIcon;
    private int flipIconCurrentEndFrame;
    private boolean hasVideo;
    private final View buttonsBackgroundGradientView;
    private final View buttonsBackgroundGradientView2;
    private GradientDrawable buttonsBackgroundGradient;
    private int[] gradientColors = new int[2];
    private final DefaultItemAnimator fullscreenListItemAnimator;
    private boolean previewTextureTransitionEnabled;
    private boolean listViewVideoVisibility = true;

    public static boolean paused;

    private static class SmallRecordCallDrawable extends Drawable {

        private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long lastUpdateTime;
        private float alpha = 1.0f;
        private int state;

        private View parentView;

        public SmallRecordCallDrawable(View parent) {
            super();
            parentView = parent;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }

        @Override
        public void draw(Canvas canvas) {
            int cx = getBounds().centerX();
            int cy = getBounds().centerY();
            if (parentView instanceof SimpleTextView) {
                cy += dp(1);
                cx -= dp(3);
            } else {
                cy += dp(2);
            }

            paint2.setColor(0xffEE7D79);
            paint2.setAlpha((int) (255 * alpha));
            canvas.drawCircle(cx, cy, dp(4), paint2);

            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            if (dt > 17) {
                dt = 17;
            }
            lastUpdateTime = newTime;
            if (state == 0) {
                alpha += dt / 2000.0f;
                if (alpha >= 1.0f) {
                    alpha = 1.0f;
                    state = 1;
                }
            } else if (state == 1) {
                alpha -= dt / 2000.0f;
                if (alpha < 0.5f) {
                    alpha = 0.5f;
                    state = 0;
                }
            }
            parentView.invalidate();
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private static class RecordCallDrawable extends Drawable {

        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private long lastUpdateTime;
        private float alpha = 1.0f;
        private int state;

        private boolean recording;
        private View parentView;

        public RecordCallDrawable() {
            super();
            paint.setColor(0xffffffff);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.5f));
        }

        public void setParentView(View view) {
            parentView = view;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }

        public boolean isRecording() {
            return recording;
        }

        public void setRecording(boolean value) {
            recording = value;
            alpha = 1.0f;
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            int cx = getBounds().centerX();
            int cy = getBounds().centerY();
            canvas.drawCircle(cx, cy, dp(10), paint);

            paint2.setColor(recording ? 0xffEE7D79 : 0xffffffff);
            paint2.setAlpha((int) (255 * alpha));
            canvas.drawCircle(cx, cy, dp(5), paint2);
            if (recording) {
                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastUpdateTime;
                if (dt > 17) {
                    dt = 17;
                }
                lastUpdateTime = newTime;
                if (state == 0) {
                    alpha += dt / 2000.0f;
                    if (alpha >= 1.0f) {
                        alpha = 1.0f;
                        state = 1;
                    }
                } else if (state == 1) {
                    alpha -= dt / 2000.0f;
                    if (alpha < 0.5f) {
                        alpha = 0.5f;
                        state = 0;
                    }
                }
                parentView.invalidate();
            }
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private class VolumeSlider extends FrameLayout {

        private RLottieImageView imageView;
        private TextView textView;
        private TLRPC.GroupCallParticipant currentParticipant;
        private RLottieDrawable speakerDrawable;

        private boolean captured;
        private float sx, sy;
        private int thumbX;
        private double currentProgress;
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Path path = new Path();
        private float[] radii = new float[8];
        private RectF rect = new RectF();
        private int currentColor;
        private int oldColor;
        private float colorChangeProgress;
        private long lastUpdateTime;
        private float[] volumeAlphas = new float[3];
        private boolean dragging;

        public VolumeSlider(Context context, TLRPC.GroupCallParticipant participant) {
            super(context);
            setWillNotDraw(false);
            currentParticipant = participant;
            currentProgress = ChatObject.getParticipantVolume(participant) / 20000.0f;
            colorChangeProgress = 1.0f;

            setPadding(dp(12), 0, dp(12), 0);

            speakerDrawable = new RLottieDrawable(R.raw.speaker, "" + R.raw.speaker, dp(24), dp(24), true, null);

            imageView = new RLottieImageView(context);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            imageView.setAnimation(speakerDrawable);
            imageView.setTag(currentProgress == 0 ? 1 : null);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 0, 0, 0, 0));

            speakerDrawable.setCustomEndFrame(currentProgress == 0 ? 17 : 34);
            speakerDrawable.setCurrentFrame(speakerDrawable.getCustomEndFrame() - 1, false, true);

            textView = new TextView(context);
            textView.setLines(1);
            textView.setSingleLine(true);
            textView.setGravity(Gravity.LEFT);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            double vol = ChatObject.getParticipantVolume(currentParticipant) / 100.0;
            textView.setText(String.format(Locale.US, "%d%%", (int) (vol > 0 ? Math.max(vol, 1) : 0)));
            textView.setPadding(LocaleController.isRTL ? 0 : dp(43), 0, LocaleController.isRTL ? dp(43) : 0, 0);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

            paint2.setStyle(Paint.Style.STROKE);
            paint2.setStrokeWidth(dp(1.5f));
            paint2.setStrokeCap(Paint.Cap.ROUND);
            paint2.setColor(0xffffffff);

            int percent = (int) (ChatObject.getParticipantVolume(currentParticipant) / 100.0);
            for (int a = 0; a < volumeAlphas.length; a++) {
                int p;
                if (a == 0) {
                    p = 0;
                } else if (a == 1) {
                    p = 50;
                } else {
                    p = 150;
                }
                if (percent > p) {
                    volumeAlphas[a] = 1.0f;
                } else {
                    volumeAlphas[a] = 0.0f;
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(dp(48), View.MeasureSpec.EXACTLY));
            thumbX = (int) (MeasureSpec.getSize(widthMeasureSpec) * currentProgress);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            return onTouch(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return onTouch(event);
        }

        boolean onTouch(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                sx = ev.getX();
                sy = ev.getY();
                return true;
            } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                captured = false;
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    final ViewConfiguration vc = ViewConfiguration.get(getContext());
                    if (Math.abs(ev.getY() - sy) < vc.getScaledTouchSlop()) {
                        thumbX = (int) ev.getX();
                        if (thumbX < 0) {
                            thumbX = 0;
                        } else if (thumbX > getMeasuredWidth()) {
                            thumbX = getMeasuredWidth();
                        }
                        dragging = true;
                    }
                }
                if (dragging) {
                    if (ev.getAction() == MotionEvent.ACTION_UP) {
                        onSeekBarDrag(thumbX / (double) getMeasuredWidth(), true);
                    }
                    dragging = false;
                    invalidate();
                    return true;
                }
            } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                if (!captured) {
                    final ViewConfiguration vc = ViewConfiguration.get(getContext());
                    if (Math.abs(ev.getY() - sy) > vc.getScaledTouchSlop()) {
                        return false;
                    }
                    if (Math.abs(ev.getX() - sx) > vc.getScaledTouchSlop()) {
                        captured = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                        if (ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                            thumbX = (int) ev.getX();
                            if (thumbX < 0) {
                                thumbX = 0;
                            } else if (thumbX > getMeasuredWidth()) {
                                thumbX = getMeasuredWidth();
                            }
                            dragging = true;
                            invalidate();
                            return true;
                        }
                    }
                } else {
                    if (dragging) {
                        thumbX = (int) ev.getX();
                        if (thumbX < 0) {
                            thumbX = 0;
                        } else if (thumbX > getMeasuredWidth()) {
                            thumbX = getMeasuredWidth();
                        }
                        onSeekBarDrag(thumbX / (double) getMeasuredWidth(), false);
                        invalidate();
                        return true;
                    }
                }
            }
            return false;
        }

        private void onSeekBarDrag(double progress, boolean finalMove) {
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            currentProgress = progress;
            currentParticipant.volume = (int) (progress * 20000);
            currentParticipant.volume_by_admin = false;
            currentParticipant.flags |= 128;
            double vol = ChatObject.getParticipantVolume(currentParticipant) / 100.0;
            textView.setText(String.format(Locale.US, "%d%%", (int) (vol > 0 ? Math.max(vol, 1) : 0)));
            VoIPService.getSharedInstance().setParticipantVolume(currentParticipant, currentParticipant.volume);
            if (finalMove) {
                long id = MessageObject.getPeerId(currentParticipant.peer);
                TLObject object;
                if (id > 0) {
                    object = accountInstance.getMessagesController().getUser(id);
                } else {
                    object = accountInstance.getMessagesController().getChat(-id);
                }
                if (currentParticipant.volume == 0) {
                    if (scrimPopupWindow != null) {
                        scrimPopupWindow.dismiss();
                        scrimPopupWindow = null;
                    }
                    dismissAvatarPreview(true);
                    processSelectedOption(currentParticipant, id, canManageCall() ? 0 : 5);
                } else {
                    VoIPService.getSharedInstance().editCallMember(object, null, null, currentParticipant.volume, null, null);
                }
            }
            Integer newTag = currentProgress == 0 ? 1 : null;
            if (imageView.getTag() == null && newTag != null || imageView.getTag() != null && newTag == null) {
                speakerDrawable.setCustomEndFrame(currentProgress == 0 ? 17 : 34);
                speakerDrawable.setCurrentFrame(currentProgress == 0 ? 0 : 17);
                speakerDrawable.start();
                imageView.setTag(newTag);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int prevColor = currentColor;
            if (currentProgress < 0.25f) {
                currentColor = 0xffCC5757;
            } else if (currentProgress > 0.25f && currentProgress < 0.5f) {
                currentColor = 0xffC9A53B;
            } else if (currentProgress >= 0.5f && currentProgress <= 0.75f) {
                currentColor = 0xff57BC6B;
            } else {
                currentColor = 0xff4DA6DF;
            }
            int color;
            if (prevColor == 0) {
                color = currentColor;
                colorChangeProgress = 1f;
            } else {
                color = AndroidUtilities.getOffsetColor(oldColor, prevColor, colorChangeProgress, 1.0f);
                if (prevColor != currentColor) {
                    colorChangeProgress = 0.0f;
                    oldColor = color;
                }
            }
            paint.setColor(color);
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            if (dt > 17) {
                dt = 17;
            }
            lastUpdateTime = newTime;
            if (colorChangeProgress < 1.0f) {
                colorChangeProgress += dt / 200.0f;
                if (colorChangeProgress > 1.0f) {
                    colorChangeProgress = 1.0f;
                } else {
                    invalidate();
                }
            }
            path.reset();
            radii[0] = radii[1] = radii[6] = radii[7] = dp(6);
            float rad = thumbX < dp(12) ? Math.max(0, (thumbX - dp(6)) / (float) dp(6)) : 1.0f;
            radii[2] = radii[3] = radii[4] = radii[5] = dp(6) * rad;
            rect.set(0, 0, thumbX, getMeasuredHeight());
            path.addRoundRect(rect, radii, Path.Direction.CW);
            path.close();
            canvas.drawPath(path, paint);

            int percent = (int) (ChatObject.getParticipantVolume(currentParticipant) / 100.0);
            int cx = imageView.getLeft() + imageView.getMeasuredWidth() / 2 + dp(5);
            int cy = imageView.getTop() + imageView.getMeasuredHeight() / 2;
            for (int a = 0; a < volumeAlphas.length; a++) {
                int p;
                if (a == 0) {
                    p = 0;
                    rad = dp(6);
                } else if (a == 1) {
                    p = 50;
                    rad = dp(10);
                } else {
                    p = 150;
                    rad = dp(14);
                }
                float offset = (dp(2) * (1.0f - volumeAlphas[a]));
                paint2.setAlpha((int) (255 * volumeAlphas[a]));
                rect.set(cx - rad + offset, cy - rad + offset, cx + rad - offset, cy + rad - offset);
                canvas.drawArc(rect, -50, 100, false, paint2);
                if (percent > p) {
                    if (volumeAlphas[a] < 1.0f) {
                        volumeAlphas[a] += dt / 180.0f;
                        if (volumeAlphas[a] > 1.0f) {
                            volumeAlphas[a] = 1.0f;
                        }
                        invalidate();
                    }
                } else {
                    if (volumeAlphas[a] > 0.0f) {
                        volumeAlphas[a] -= dt / 180.0f;
                        if (volumeAlphas[a] < 0.0f) {
                            volumeAlphas[a] = 0.0f;
                        }
                        invalidate();
                    }
                }
            }
        }
    }

    public static class WeavingState {
        private float targetX = -1f;
        private float targetY = -1f;
        private float startX;
        private float startY;
        private float duration;
        private float time;
        public Shader shader;
        private Matrix matrix = new Matrix();
        public int currentState;

        public WeavingState(int state) {
            currentState = state;
        }

        public void update(int top, int left, int size, long dt, float amplitude) {
            if (shader == null) {
                return;
            }
            if (duration == 0 || time >= duration) {
                duration = Utilities.random.nextInt(200) + 1500;
                time = 0;
                if (targetX == -1f) {
                    setTarget();
                }
                startX = targetX;
                startY = targetY;
                setTarget();
            }
            time += dt * (0.5f + BlobDrawable.GRADIENT_SPEED_MIN) + dt * (BlobDrawable.GRADIENT_SPEED_MAX * 2) * amplitude;
            if (time > duration) {
                time = duration;
            }
            float interpolation = CubicBezierInterpolator.EASE_OUT.getInterpolation(time / duration);
            float x = left + size * (startX + (targetX - startX) * interpolation) - 200;
            float y = top + size * (startY + (targetY - startY) * interpolation) - 200;

            float s;
            if (isGradientState(currentState)) {
                s = 1f;
            } else {
                s = currentState == MUTE_BUTTON_STATE_MUTE ? 4 : 2.5f;
            }
            float scale = dp(122) / 400.0f * s;
            matrix.reset();
            matrix.postTranslate(x, y);
            matrix.postScale(scale, scale, x + 200, y + 200);

            shader.setLocalMatrix(matrix);
        }

        private void setTarget() {
            if (isGradientState(currentState)) {
                targetX = 0.85f + 0.20f * Utilities.random.nextInt(100) / 100f;
                targetY = 1f;
            } else if (currentState == MUTE_BUTTON_STATE_MUTE) {
                targetX = 0.2f + 0.3f * Utilities.random.nextInt(100) / 100f;
                targetY = 0.7f + 0.3f * Utilities.random.nextInt(100) / 100f;
            } else {
                targetX = 0.8f + 0.2f * (Utilities.random.nextInt(100) / 100f);
                targetY = Utilities.random.nextInt(100) / 100f;
            }
        }
    }

    public static boolean isGradientState(int state) {
        return VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().groupCall != null && VoIPService.getSharedInstance().groupCall.call.rtmp_stream || state == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || state == MUTE_BUTTON_STATE_RAISED_HAND || state == MUTE_BUTTON_STATE_START_NOW || state == MUTE_BUTTON_STATE_SET_REMINDER || state == MUTE_BUTTON_STATE_CANCEL_REMINDER;
    }

    @SuppressWarnings("FieldCanBeLocal")
    private static class LabeledButton extends FrameLayout {

        private ImageView imageView;
        private TextView textView;

        public LabeledButton(Context context, String text, int resId, int color) {
            super(context);

            imageView = new ImageView(context);
            if (Build.VERSION.SDK_INT >= 21) {
                imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(50), color, 0x1fffffff));
            } else {
                imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(50), color, color));
            }
            imageView.setImageResource(resId);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            addView(imageView, LayoutHelper.createFrame(50, 50, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setText(text);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 50 + 5, 0, 0));
        }

        public void setColor(int color) {
            Theme.setSelectorDrawableColor(imageView.getBackground(), color, false);
            if (Build.VERSION.SDK_INT < 21) {
                Theme.setSelectorDrawableColor(imageView.getBackground(), color, true);
            }
            imageView.invalidate();
        }
    }

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) ((containerView.getMeasuredWidth() - backgroundPaddingLeft * 2) / 6.0f);
        int h = (int) ((containerView.getMeasuredHeight() - AndroidUtilities.statusBarHeight) / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);

        canvas.save();
        canvas.translate(0, -AndroidUtilities.statusBarHeight);
        parentActivity.getActionBarLayout().getView().draw(canvas);
        canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f)));
        canvas.restore();
        canvas.save();
        canvas.translate(containerView.getX(), -AndroidUtilities.statusBarHeight);
        drawingForBlur = true;
        containerView.draw(canvas);
        drawingForBlur = false;

        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        groupCallUiVisible = true;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged);
        GroupCallPip.updateVisibility(getContext());
        return super.onCustomOpenAnimation();
    }

    @Override
    public void dismiss() {
        parentActivity.removeOnUserLeaveHintListener(onUserLeaveHintListener);
        parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        groupCallUiVisible = false;
        if (groupVoipInviteAlert != null) {
            groupVoipInviteAlert.dismiss();
        }
        delayedGroupCallUpdated = true;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.needShowAlert);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.groupCallUpdated);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.didLoadChatAdmins);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.applyGroupCallVisibleParticipants);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.userInfoDidLoad);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.mainUserInfoChanged);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.updateInterfaces);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.groupCallScreencastStateChanged);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.groupCallSpeakingUsersUpdated);
        accountInstance.getNotificationCenter().removeObserver(this, NotificationCenter.conferenceEmojiUpdated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
        super.dismiss();
    }

    private boolean isStillConnecting() {
        return currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING;
    }

    public long getChatId() {
        if (currentChat == null) return 0;
        return currentChat.id;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupCallUpdated) {
            Long callId = (Long) args[1];
            if (call != null && call.call.id == callId) {
                if (call.call instanceof TLRPC.TL_groupCallDiscarded) {
                    dismiss();
                } else {
                    if (creatingServiceTime == 0 && (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER || muteButtonState == MUTE_BUTTON_STATE_START_NOW || muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) && !call.isScheduled()) {
                        try {
                            Intent intent = new Intent(parentActivity, VoIPService.class);
                            intent.putExtra("chat_id", getChatId());
                            intent.putExtra("createGroupCall", false);
                            intent.putExtra("hasFewPeers", scheduleHasFewPeers);
                            intent.putExtra("peerChannelId", schedulePeer.channel_id);
                            intent.putExtra("peerChatId", schedulePeer.chat_id);
                            intent.putExtra("peerUserId", schedulePeer.user_id);
                            intent.putExtra("hash", scheduledHash);
                            intent.putExtra("peerAccessHash", schedulePeer.access_hash);
                            intent.putExtra("is_outgoing", true);
                            intent.putExtra("start_incall_activity", false);
                            intent.putExtra("account", accountInstance.getCurrentAccount());
                            intent.putExtra("scheduleDate", scheduleStartAt);
                            parentActivity.startService(intent);
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                        creatingServiceTime = SystemClock.elapsedRealtime();
                        AndroidUtilities.runOnUIThread(() -> {
                            if (!isStillConnecting()) {
                                return;
                            }
                            updateState(true, false);
                        }, 3000);
                    }
                    if (!callInitied && VoIPService.getSharedInstance() != null) {
                        call.addSelfDummyParticipant(false);
                        initCreatedGroupCall();
                        VoIPService.getSharedInstance().playConnectedSound();
                    }
                    updateItems();
                    for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                        View child = listView.getChildAt(a);
                        if (child instanceof GroupCallUserCell) {
                            ((GroupCallUserCell) child).applyParticipantChanges(true);
                        }
                    }
                    if (scrimView != null) {
                        delayedGroupCallUpdated = true;
                    } else {
                        applyCallParticipantUpdates(true);
                    }

                    updateSubtitle();
                    boolean selfUpdate = (Boolean) args[2];
                    boolean raisedHand = muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND;
                    updateState(true, selfUpdate);
                    updateTitle(true);
                    if (raisedHand && (muteButtonState == MUTE_BUTTON_STATE_MUTE || muteButtonState == MUTE_BUTTON_STATE_UNMUTE)) {
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_CAN_NOW_SPEAK, null);
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().playAllowTalkSound();
                        }
                    }

                    if (args.length >= 4) {
                        long justJoinedId = (Long) args[3];
                        if (justJoinedId != 0 && !isRtmpStream()) {
                            if (isConference()) {
                                VoIPService voip = VoIPService.getSharedInstance();
                                if (voip != null && justJoinedId == voip.convertingFromCallWithUserId) {
                                    return;
                                }
                            }
                            boolean hasInDialogs = false;
                            try {
                                ArrayList<TLRPC.Dialog> dialogs = accountInstance.getMessagesController().getAllDialogs();
                                if (dialogs != null) {
                                    for (TLRPC.Dialog dialog : dialogs) {
                                        if (dialog.id == justJoinedId) {
                                            hasInDialogs = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignore) {}
                            if (DialogObject.isUserDialog(justJoinedId)) {
                                TLRPC.User user = accountInstance.getMessagesController().getUser(justJoinedId);
                                if (user != null && (call.call.participants_count < 250 || UserObject.isContact(user) || user.verified || hasInDialogs)) {
                                    getUndoView().showWithAction(0, UndoView.ACTION_VOIP_USER_JOINED, user, currentChat, null, null);
                                }
                            } else {
                                TLRPC.Chat chat = accountInstance.getMessagesController().getChat(-justJoinedId);
                                if (chat != null && (call.call.participants_count < 250 || !ChatObject.isNotInChat(chat) || chat.verified || hasInDialogs)) {
                                    getUndoView().showWithAction(0, UndoView.ACTION_VOIP_USER_JOINED, chat, currentChat, null, null);
                                }
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.groupCallSpeakingUsersUpdated) {
            if (renderersContainer.inFullscreenMode && call != null) {
                boolean autoPinEnabled = renderersContainer.autoPinEnabled();
                if (call != null && renderersContainer.inFullscreenMode && renderersContainer.fullscreenParticipant != null && call.participants.get(MessageObject.getPeerId(renderersContainer.fullscreenParticipant.participant.peer)) == null) {
                    autoPinEnabled = true;
                }
                if (autoPinEnabled) {
                    ChatObject.VideoParticipant currentSpeaker = null;
                    for (int i = 0; i < visibleVideoParticipants.size(); i++) {
                        ChatObject.VideoParticipant participant = visibleVideoParticipants.get(i);
                        boolean newSpeaking = call.currentSpeakingPeers.get(MessageObject.getPeerId(participant.participant.peer), null) != null;
                        if (newSpeaking && !participant.participant.muted_by_you && renderersContainer.fullscreenPeerId != MessageObject.getPeerId(participant.participant.peer)) {
                            currentSpeaker = participant;
                        }
                    }
                    if (currentSpeaker != null) {
                        fullscreenFor(currentSpeaker);
                    }
                }
            }
            renderersContainer.setVisibleParticipant(true);
            updateSubtitle();
        } else if (id == NotificationCenter.webRtcMicAmplitudeEvent) {
            float amplitude = (float) args[0];
            setMicAmplitude(amplitude);
        } else if (id == NotificationCenter.needShowAlert) {
            int num = (Integer) args[0];
            if (num == 6) {
                String text = (String) args[1];
                String error;
                if ("GROUPCALL_PARTICIPANTS_TOO_MUCH".equals(text)) {
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        error = getString(R.string.VoipChannelTooMuch);
                    } else {
                        error = getString(R.string.VoipGroupTooMuch);
                    }
                } else if ("ANONYMOUS_CALLS_DISABLED".equals(text) || "GROUPCALL_ANONYMOUS_FORBIDDEN".equals(text)) {
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        error = getString(R.string.VoipChannelJoinAnonymousAdmin);
                    } else {
                        error = getString(R.string.VoipGroupJoinAnonymousAdmin);
                    }
                } else {
                    error = getString(R.string.ErrorOccurred) + "\n" + text;
                }

                AlertDialog.Builder builder = AlertsCreator.createSimpleAlert(getContext(), getString(R.string.VoipGroupVoiceChat), error);
                builder.setOnDismissListener(dialog -> dismiss());
                try {
                    builder.show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == NotificationCenter.didEndCall) {
            if (VoIPService.getSharedInstance() == null) {
                dismiss();
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == getChatId()) {
                updateItems();
                updateState(isShowing(), false);
            }
            long selfId = MessageObject.getPeerId(selfPeer);
            if (call != null && chatFull.id == -selfId) {
                TLRPC.GroupCallParticipant participant = call.participants.get(selfId);
                if (participant != null) {
                    participant.about = chatFull.about;
                    applyCallParticipantUpdates(true);
                    AndroidUtilities.updateVisibleRows(listView);

                    if (currentOptionsLayout != null) {
                        for (int i = 0; i < currentOptionsLayout.getChildCount(); i++) {
                            View child = currentOptionsLayout.getChildAt(i);
                            if (child instanceof ActionBarMenuSubItem && child.getTag() != null && (Integer) child.getTag() == 10) {
                                ((ActionBarMenuSubItem) child).setTextAndIcon(
                                        TextUtils.isEmpty(participant.about) ? getString(R.string.VoipAddDescription) : getString(R.string.VoipEditDescription),
                                        TextUtils.isEmpty(participant.about) ? R.drawable.msg_addbio : R.drawable.msg_info
                                );
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.didLoadChatAdmins) {
            long chatId = (Long) args[0];
            if (chatId == getChatId()) {
                updateItems();
                updateState(isShowing(), false);
            }
        } else if (id == NotificationCenter.applyGroupCallVisibleParticipants) {
            int count = listView.getChildCount();
            long time = (Long) args[0];
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                if (holder != null && holder.itemView instanceof GroupCallUserCell) {
                    GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                    if (cell.getParticipant() != null) {
                        cell.getParticipant().lastVisibleDate = time;
                    }
                }
            }
        } else if (id == NotificationCenter.userInfoDidLoad) {
            Long uid = (Long) args[0];
            long selfId = MessageObject.getPeerId(selfPeer);
            if (call != null && selfId == uid) {
                TLRPC.GroupCallParticipant participant = call.participants.get(selfId);
                if (participant != null) {
                    TLRPC.UserFull userInfo = (TLRPC.UserFull) args[1];
                    participant.about = userInfo.about;
                    applyCallParticipantUpdates(true);
                    AndroidUtilities.updateVisibleRows(listView);

                    if (currentOptionsLayout != null) {
                        for (int i = 0; i < currentOptionsLayout.getChildCount(); i++) {
                            View child = currentOptionsLayout.getChildAt(i);
                            if (child instanceof ActionBarMenuSubItem && child.getTag() != null && (Integer) child.getTag() == 10) {
                                ((ActionBarMenuSubItem) child).setTextAndIcon(
                                        TextUtils.isEmpty(participant.about) ? getString(R.string.VoipAddBio) : getString(R.string.VoipEditBio),
                                        TextUtils.isEmpty(participant.about) ? R.drawable.msg_addbio : R.drawable.msg_info
                                );
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            applyCallParticipantUpdates(true);
            AndroidUtilities.updateVisibleRows(listView);
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (int) args[0];
            if ((mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                applyCallParticipantUpdates(true);
            }
            if ((mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_EMOJI_STATUS) != 0) {
                AndroidUtilities.updateVisibleRows(listView);
            }
        } else if (id == NotificationCenter.groupCallScreencastStateChanged) {
            if (previewDialog != null) {
                previewDialog.dismiss(true, true);
            }
            updateItems();
        } else if (id == NotificationCenter.conferenceEmojiUpdated) {
            VoIPService voip = VoIPService.getSharedInstance();
            encryptionDrawable.setEmojis(voip != null && voip.conference != null ? voip.conference.getEmojis() : null);
        }
    }

    private void setMicAmplitude(float amplitude) {
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute()) {
            amplitude = 0f;
        }
        setAmplitude(amplitude * 4000.0f);
        if (call != null && listView != null) {
            TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
            if (participant != null) {
                if (!renderersContainer.inFullscreenMode) {
                    ArrayList<TLRPC.GroupCallParticipant> array = delayedGroupCallUpdated ? oldParticipants : call.visibleParticipants;
                    int idx = array.indexOf(participant);
                    if (idx >= 0) {

                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(idx + listAdapter.usersStartRow);
                        if (holder != null && holder.itemView instanceof GroupCallUserCell) {
                            GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                            cell.setAmplitude(amplitude * 15.0f);
                            if (holder.itemView == scrimView) {
                                if (!contentFullyOverlayed) {
                                    containerView.invalidate();
                                }
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < fullscreenUsersListView.getChildCount(); i++) {
                        GroupCallFullscreenAdapter.GroupCallUserCell cell = (GroupCallFullscreenAdapter.GroupCallUserCell) fullscreenUsersListView.getChildAt(i);
                        if (MessageObject.getPeerId(cell.getParticipant().peer) == MessageObject.getPeerId(participant.peer)) {
                            cell.setAmplitude(amplitude * 15.0f);
                        }
                    }
                }
                renderersContainer.setAmplitude(participant, amplitude * 15.0f);
            }
        }
    }

    private void applyCallParticipantUpdates(boolean animated) {
        if (renderersContainer == null || listView == null || call == null || delayedGroupCallUpdated) {
            return;
        }
        if (renderersContainer.inFullscreenMode) {
            renderersContainer.setVisibleParticipant(true);
        }
        long self = MessageObject.getPeerId(call.selfPeer);
        long dummyPeer = MessageObject.getPeerId(selfPeer);
        if (self != dummyPeer && call.participants.get(self) != null) {
            selfPeer = call.selfPeer;
        }
        int count = listView.getChildCount();
        View minChild = null;
        int minPosition = 0;
        int minTop = Integer.MAX_VALUE;
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
            if (holder != null && holder.getAdapterPosition() != RecyclerView.NO_POSITION && holder.getLayoutPosition() != RecyclerView.NO_POSITION) {
                if (minChild == null || child.getTop() < minTop) {
                    minChild = child;
                    minPosition = holder.getLayoutPosition();
                    minTop = child.getTop();
                }
            }
        }
        updateVideoParticipantList();
        if (listView.getItemAnimator() != null && !animated) {
            listView.setItemAnimator(null);
        } else if (listView.getItemAnimator() == null && animated) {
            listView.setItemAnimator(itemAnimator);
        }

        try {
            UpdateCallback updateCallback = new UpdateCallback(listAdapter);
            setOldRows(
                listAdapter.encryptionRow,
                listAdapter.addMemberRow,
                listAdapter.usersStartRow,
                listAdapter.usersEndRow,
                listAdapter.invitedStartRow,
                listAdapter.invitedEndRow,
                listAdapter.shadyJoinStartRow,
                listAdapter.shadyJoinEndRow,
                listAdapter.shadyLeftStartRow,
                listAdapter.shadyLeftEndRow,
                listAdapter.usersVideoGridStartRow,
                listAdapter.usersVideoGridEndRow,
                listAdapter.videoGridDividerRow,
                listAdapter.videoNotAvailableRow,
                listAdapter.conferenceAddPeopleRow,
                listAdapter.conferenceShareLinkRow
            );
            listAdapter.updateRows();
            DiffUtil.calculateDiff(diffUtilsCallback).dispatchUpdatesTo(updateCallback);
        } catch (Exception e) {
            FileLog.e(e);
            listAdapter.notifyDataSetChanged();
        }
        call.saveActiveDates();
        if (minChild != null) {
            layoutManager.scrollToPositionWithOffset(minPosition, minChild.getTop() - listView.getPaddingTop());
        }
        oldParticipants.clear();
        oldParticipants.addAll(call.visibleParticipants);

        oldVideoParticipants.clear();
        oldVideoParticipants.addAll(visibleVideoParticipants);
        oldInvited.clear();
        oldInvited.addAll(call.invitedUsers);
        oldShadyJoin.clear();
        oldShadyJoin.addAll(call.shadyJoinParticipants);
        oldShadyLeft.clear();
        oldShadyLeft.addAll(call.shadyLeftParticipants);
        oldCount = listAdapter.getItemCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallUserCell || child instanceof GroupCallInvitedCell) {
                RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                if (holder != null) {
                    if (child instanceof GroupCallUserCell) {
                        ((GroupCallUserCell) child).setDrawDivider(holder.getAdapterPosition() != listAdapter.getItemCount() - 2);
                    } else {
                        ((GroupCallInvitedCell) child).setDrawDivider(holder.getAdapterPosition() != listAdapter.getItemCount() - 2);
                    }
                }
            }
        }

        boolean autoPinEnabled = renderersContainer.autoPinEnabled();
        if (renderersContainer.inFullscreenMode && renderersContainer.fullscreenParticipant != null && !ChatObject.Call.videoIsActive(renderersContainer.fullscreenParticipant.participant, renderersContainer.fullscreenParticipant.presentation, call)) {
            boolean foundAnotherVideoParticipant = false;
            if (!visibleVideoParticipants.isEmpty()) {
                foundAnotherVideoParticipant = true;
                if (autoPinEnabled) {
                    fullscreenFor(visibleVideoParticipants.get(0));
                }
            }
            if (!foundAnotherVideoParticipant) {
                fullscreenFor(null);
            }
        }
        fullscreenAdapter.update(true, fullscreenUsersListView);
        if (fullscreenUsersListView.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
        }
        if (isTabletMode) {
            tabletGridAdapter.update(true, tabletVideoGridView);
        }
        if (listView.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateVisibleRows(listView);
        }
        attachedRenderersTmp.clear();
        attachedRenderersTmp.addAll(attachedRenderers);
        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
            attachedRenderersTmp.get(i).updateAttachState(true);
        }

        if (call != null && renderersContainer.inFullscreenMode && renderersContainer.fullscreenParticipant != null && call.participants.get(MessageObject.getPeerId(renderersContainer.fullscreenParticipant.participant.peer)) == null) {
            autoPinEnabled = true;
        }

        boolean hasVideoLocal = !call.visibleVideoParticipants.isEmpty();
        if (hasVideoLocal != hasVideo) {
            hasVideo = hasVideoLocal;
            if (isTabletMode) {
                containerView.requestLayout();
            }
        }
    }

    private void updateVideoParticipantList() {
        visibleVideoParticipants.clear();
        if (isTabletMode) {
            if (renderersContainer.inFullscreenMode) {
                visibleVideoParticipants.addAll(call.visibleVideoParticipants);
                if (renderersContainer.fullscreenParticipant != null) {
                    visibleVideoParticipants.remove(renderersContainer.fullscreenParticipant);
                }
            }
        } else {
            visibleVideoParticipants.addAll(call.visibleVideoParticipants);
        }
    }

    private void updateRecordCallText() {
        if (call == null) {
            return;
        }
        int time = accountInstance.getConnectionsManager().getCurrentTime() - call.call.record_start_date;
        if (call.recording) {
            recordItem.setSubtext(AndroidUtilities.formatDuration(time, false));
        } else {
            recordItem.setSubtext(null);
        }
    }

    private void updateItems() {
        if (call == null || call.isScheduled()) {
            pipItem.setVisibility(View.INVISIBLE);
            screenShareItem.setVisibility(View.GONE);
            if (call == null) {
                otherItem.setVisibility(View.GONE);
                return;
            }
        }
        if (changingPermissions) {
            return;
        }
        TLRPC.Chat newChat = accountInstance.getMessagesController().getChat(getChatId());
        if (newChat != null) {
            currentChat = newChat;
        }
        if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE) || (!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup) && (ChatObject.isPublic(currentChat) || ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) || ChatObject.isChannel(currentChat) && currentChat != null && !currentChat.megagroup && ChatObject.isPublic(currentChat)) {
            inviteItem.setVisibility(View.VISIBLE);
        } else {
            inviteItem.setVisibility(View.GONE);
        }

        TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));

        if (call == null || call.isScheduled() || participant != null && !participant.can_self_unmute && participant.muted) {
            noiseItem.setVisibility(View.GONE);
        } else {
            noiseItem.setVisibility(View.VISIBLE);
        }
        noiseItem.setIcon(SharedConfig.noiseSupression ? R.drawable.msg_noise_on : R.drawable.msg_noise_off);
        noiseItem.setSubtext(SharedConfig.noiseSupression ? getString(R.string.VoipNoiseCancellationEnabled) : getString(R.string.VoipNoiseCancellationDisabled));

        if (canManageCall()) {
            leaveItem.setVisibility(View.VISIBLE);
            editTitleItem.setVisibility(View.VISIBLE);
            if (isRtmpStream()) {
                recordItem.setVisibility(View.VISIBLE);
                screenItem.setVisibility(View.GONE);
            } else {
                if (call.isScheduled()) {
                    recordItem.setVisibility(View.GONE);
                    screenItem.setVisibility(View.GONE);
                } else {
                    recordItem.setVisibility(View.VISIBLE);
                }
            }
            if (isConference()) {
                recordItem.setVisibility(View.GONE);
                editTitleItem.setVisibility(View.GONE);
            }
            if (!call.canRecordVideo() || call.isScheduled() || Build.VERSION.SDK_INT < 21 || isRtmpStream()) {
                screenItem.setVisibility(View.GONE);
            } else {
                screenItem.setVisibility(View.VISIBLE);
            }
            screenShareItem.setVisibility(View.GONE);
            recordCallDrawable.setRecording(call.recording);
            if (call.recording) {
                if (updateCallRecordRunnable == null) {
                    AndroidUtilities.runOnUIThread(updateCallRecordRunnable = () -> {
                        updateRecordCallText();
                        AndroidUtilities.runOnUIThread(updateCallRecordRunnable, 1000);
                    }, 1000);
                }
                recordItem.setText(getString(R.string.VoipGroupStopRecordCall));
            } else {
                if (updateCallRecordRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(updateCallRecordRunnable);
                    updateCallRecordRunnable = null;
                }
                recordItem.setText(getString(R.string.VoipGroupRecordCall));
            }
            if (VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getVideoState(true) == Instance.VIDEO_STATE_ACTIVE) {
                screenItem.setTextAndIcon(getString(R.string.VoipChatStopScreenCapture), R.drawable.msg_screencast_off);
            } else {
                screenItem.setTextAndIcon(getString(R.string.VoipChatStartScreenCapture), R.drawable.msg_screencast);
            }
            updateRecordCallText();
        } else {
            boolean mutedByAdmin = participant != null && !participant.can_self_unmute && participant.muted && !canManageCall();
            boolean sharingScreen = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getVideoState(true) == Instance.VIDEO_STATE_ACTIVE;
            if (Build.VERSION.SDK_INT >= 21 && !mutedByAdmin && (call.canRecordVideo() || sharingScreen) && !call.isScheduled() && !isRtmpStream()) {
                if (sharingScreen) {
                    screenShareItem.setVisibility(View.GONE);
                    screenItem.setVisibility(View.VISIBLE);
                    screenItem.setTextAndIcon(getString(R.string.VoipChatStopScreenCapture), R.drawable.msg_screencast_off);
                    screenItem.setContentDescription(getString(R.string.VoipChatStopScreenCapture));
                } else {
                    screenItem.setTextAndIcon(getString(R.string.VoipChatStartScreenCapture), R.drawable.msg_screencast);
                    screenItem.setContentDescription(getString(R.string.VoipChatStartScreenCapture));
                    screenShareItem.setVisibility(View.GONE);
                    screenItem.setVisibility(View.VISIBLE);
                }
            } else {
                screenShareItem.setVisibility(View.GONE);
                screenItem.setVisibility(View.GONE);
            }
            leaveItem.setVisibility(View.GONE);
            editTitleItem.setVisibility(View.GONE);
            recordItem.setVisibility(View.GONE);
        }
        if (canManageCall() && call.call.can_change_join_muted && !isConference()) {
            permissionItem.setVisibility(View.VISIBLE);
        } else {
            permissionItem.setVisibility(View.GONE);
        }
        otherItem.setSubItemShown(leave_item, !isConference() || call != null && call.call != null && call.call.creator);
        soundItem.setVisibility(isRtmpStream() && !call.isScheduled() ? View.GONE : View.VISIBLE);
        if (editTitleItem.getVisibility() == View.VISIBLE || permissionItem.getVisibility() == View.VISIBLE || inviteItem.getVisibility() == View.VISIBLE ||
                screenItem.getVisibility() == View.VISIBLE || recordItem.getVisibility() == View.VISIBLE || leaveItem.getVisibility() == View.VISIBLE) {
            soundItemDivider.setVisibility(View.VISIBLE);
        } else {
            soundItemDivider.setVisibility(View.GONE);
        }

        int margin = 48;
        if ((VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().hasFewPeers || scheduleHasFewPeers) && !isRtmpStream() && selfPeer != null) {
            accountSelectCell.setVisibility(View.VISIBLE);
            accountGap.setVisibility(View.VISIBLE);
            long peerId = MessageObject.getPeerId(selfPeer);
            TLObject object;
            if (DialogObject.isUserDialog(peerId)) {
                object = accountInstance.getMessagesController().getUser(peerId);
            } else {
                object = accountInstance.getMessagesController().getChat(-peerId);
            }
            accountSelectCell.setObject(object);
            margin += 48;
        } else {
            margin += 48;
            accountSelectCell.setVisibility(View.GONE);
            accountGap.setVisibility(View.GONE);
        }

        if (currentChat != null && !ChatObject.isChannelOrGiga(currentChat) && isRtmpStream() && inviteItem.getVisibility() == View.GONE) {
            otherItem.setVisibility(View.GONE);
        } else {
            otherItem.setVisibility(View.VISIBLE);
        }

        FrameLayout.LayoutParams layoutParams = ((FrameLayout.LayoutParams) titleLayout.getLayoutParams());
        if (layoutParams.rightMargin != dp(margin)) {
            layoutParams.rightMargin = dp(margin);
            titleLayout.requestLayout();
        }

        ((FrameLayout.LayoutParams) menuItemsContainer.getLayoutParams()).rightMargin = 0;
        actionBar.setTitleRightMargin(dp(48) * 2);
    }

    protected void makeFocusable(BottomSheet bottomSheet, AlertDialog alertDialog, EditTextBoldCursor editText, boolean showKeyboard) {
        if (!enterEventSent) {
            BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
            if (fragment instanceof ChatActivity) {
                boolean keyboardVisible = ((ChatActivity) fragment).needEnterText();
                enterEventSent = true;
                anyEnterEventSent = true;
                AndroidUtilities.runOnUIThread(() -> {
                    if (bottomSheet != null && !bottomSheet.isDismissed()) {
                        bottomSheet.setFocusable(true);
                        editText.requestFocus();
                        if (showKeyboard) {
                            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
                        }
                    } else if (alertDialog != null && alertDialog.isShowing()) {
                        alertDialog.setFocusable(true);
                        editText.requestFocus();
                        if (showKeyboard) {
                            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
                        }
                    }
                }, keyboardVisible ? 200 : 0);
            } else {
                enterEventSent = true;
                anyEnterEventSent = true;
                if (bottomSheet != null) {
                    bottomSheet.setFocusable(true);
                } else if (alertDialog != null) {
                    alertDialog.setFocusable(true);
                }
                if (showKeyboard) {
                    AndroidUtilities.runOnUIThread(() -> {
                        editText.requestFocus();
                        AndroidUtilities.showKeyboard(editText);
                    }, 100);
                }
            }
        }
    }

    public static void create(LaunchActivity activity, AccountInstance account, TLRPC.Chat scheduleChat, TLRPC.InputPeer schedulePeer, boolean hasFewPeers, String scheduledHash) {
        if (groupCallInstance != null || schedulePeer == null && VoIPService.getSharedInstance() == null) {
            return;
        }
        if (schedulePeer != null) {
            ChatObject.Call call = account.getMessagesController().getGroupCall(scheduleChat.id, false);
            groupCallInstance = new GroupCallActivity(activity, account, call, scheduleChat, schedulePeer, hasFewPeers, scheduledHash);
        } else {
            ChatObject.Call call = VoIPService.getSharedInstance().groupCall;
            if (call == null) {
                return;
            }
            TLRPC.Chat chat = account.getMessagesController().getChat(call.chatId);
            call.addSelfDummyParticipant(true);
            groupCallInstance = new GroupCallActivity(activity, account, call, chat, null, hasFewPeers, scheduledHash);
        }
        groupCallInstance.parentActivity = activity;
        AndroidUtilities.runOnUIThread(() -> {
            if (groupCallInstance != null) {
                groupCallInstance.show();
            }
        });
    }

    private GroupCallActivity(Activity context, AccountInstance account, ChatObject.Call groupCall, TLRPC.Chat chat, TLRPC.InputPeer schedulePeer, boolean scheduleHasFewPeers, String scheduledHash) {
        super(context, false);
        setOpenNoDelay(true);
        this.accountInstance = account;
        this.call = groupCall;
        this.schedulePeer = schedulePeer;
        this.currentChat = chat;
        this.scheduledHash = scheduledHash;
        this.currentAccount = account.getCurrentAccount();
        this.scheduleHasFewPeers = scheduleHasFewPeers;
        fullWidth = true;
        isTabletMode = false;
        isLandscapeMode = false;
        paused = false;
        setDelegate(new BottomSheetDelegateInterface() {
            @Override
            public void onOpenAnimationStart() {

            }

            @Override
            public void onOpenAnimationEnd() {
                VoIPService voipService = VoIPService.getSharedInstance();
                if (voipService != null) {
                    CountDownLatch latch = voipService.getGroupCallBottomSheetLatch();
                    if (latch != null) {
                        latch.countDown();
                    }
                }

                if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                    showReminderHint();
                }
            }

            @Override
            public boolean canDismiss() {
                return true;
            }
        });
        drawDoubleNavigationBar = true;
        drawNavigationBar = true;
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setNavigationBarColor(0xff000000);
        }
        scrollNavBar = true;
        navBarColorKey = -1;

        scrimPaint = new Paint() {
            @Override
            public void setAlpha(int a) {
                super.setAlpha(a);
                if (containerView != null) {
                    containerView.invalidate();
                }
            }
        };
        setOnDismissListener(dialog -> {
            BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
            if (anyEnterEventSent) {
                if (fragment instanceof ChatActivity) {
                    ((ChatActivity) fragment).onEditTextDialogClose(true, true);
                }
            }
        });

        setDimBehindAlpha(75);

        listAdapter = new ListAdapter(context);

        RecordStatusDrawable recordStatusDrawable = new RecordStatusDrawable(true);
        recordStatusDrawable.setColor(Theme.getColor(Theme.key_voipgroup_speakingText));
        recordStatusDrawable.start();
        actionBar = new ActionBar(context) {

            @Override
            public void setAlpha(float alpha) {
                if (getAlpha() != alpha) {
                    super.setAlpha(alpha);
                    containerView.invalidate();
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (getAdditionalSubtitleTextView().getVisibility() == View.VISIBLE) {
                    canvas.save();
                    canvas.translate(getSubtitleTextView().getLeft(), getSubtitleTextView().getY() - dp(1));
                    recordStatusDrawable.setAlpha((int) (255 * getAdditionalSubtitleTextView().getAlpha()));
                    recordStatusDrawable.draw(canvas);
                    canvas.restore();
                    invalidate();
                }
            }
        };
        actionBar.setSubtitle("");
        actionBar.getSubtitleTextView().setVisibility(View.VISIBLE);
        actionBar.createAdditionalSubtitleTextView();
        actionBar.getAdditionalSubtitleTextView().setPadding(dp(24), 0, 0, 0);
        AndroidUtilities.updateViewVisibilityAnimated(actionBar.getAdditionalSubtitleTextView(), drawSpeakingSubtitle, 1f, false);
        actionBar.getAdditionalSubtitleTextView().setTextColor(Theme.getColor(Theme.key_voipgroup_speakingText));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setOccupyStatusBar(false);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    onBackPressed();
                } else if (id == eveyone_can_speak_item) {
                    call.call.join_muted = false;
                    toggleAdminSpeak();
                } else if (id == admin_can_speak_item) {
                    call.call.join_muted = true;
                    toggleAdminSpeak();
                } else if (id == share_invite_link_item) {
                    getLink(false);
                } else if (id == leave_item) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        builder.setTitle(getString(R.string.VoipChannelEndAlertTitle));
                        builder.setMessage(getString(R.string.VoipChannelEndAlertText));
                    } else {
                        builder.setTitle(getString(R.string.VoipGroupEndAlertTitle));
                        builder.setMessage(getString(R.string.VoipGroupEndAlertText));
                    }
                    builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);

                    builder.setPositiveButton(getString(R.string.VoipGroupEnd), (dialogInterface, i) -> {
                        if (call.isScheduled()) {
                            TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(getChatId());
                            if (chatFull != null) {
                                chatFull.flags &= ~2097152;
                                chatFull.call = null;
                                accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.groupCallUpdated, getChatId(), call.call.id, false);
                            }
                            TL_phone.discardGroupCall req = new TL_phone.discardGroupCall();
                            req.call = call.getInputGroupCall();
                            accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                                if (response instanceof TLRPC.TL_updates) {
                                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                                    accountInstance.getMessagesController().processUpdates(updates, false);
                                }
                            });
                        } else if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().hangUp(1);
                        }
                        dismiss();
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
                    });
                    builder.setNegativeButton(getString(R.string.Cancel), null);
                    AlertDialog dialog = builder.create();

                    dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
                    dialog.show();
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
                    }
                    dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
                } else if (id == screen_capture_item) {
                    screenShareItem.callOnClick();
                } else if (id == start_record_item) {
                    if (call.recording) {
                        boolean video = call.call.record_video_active;
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);
                        builder.setTitle(getString(R.string.VoipGroupStopRecordingTitle));
                        if (ChatObject.isChannelOrGiga(currentChat)) {
                            builder.setMessage(getString(R.string.VoipChannelStopRecordingText));
                        } else {
                            builder.setMessage(getString(R.string.VoipGroupStopRecordingText));
                        }
                        builder.setPositiveButton(getString(R.string.Stop), (dialogInterface, i) -> {
                            call.toggleRecord(null, ChatObject.Call.RECORD_TYPE_AUDIO);
                            getUndoView().showWithAction(0, video ? UndoView.ACTION_VOIP_VIDEO_RECORDING_FINISHED : UndoView.ACTION_VOIP_RECORDING_FINISHED, null);
                        });
                        builder.setNegativeButton(getString(R.string.Cancel), null);
                        AlertDialog dialog = builder.create();
                        dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
                        dialog.show();
                        dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    } else {
                        GroupCallRecordAlert alert = new GroupCallRecordAlert(getContext(), currentChat, hasVideo) {
                            @Override
                            public void onStartRecord(@ChatObject.Call.RecordType int type) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                                builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);

                                enterEventSent = false;
                                builder.setTitle(getString(R.string.VoipGroupStartRecordingTitle));
                                if (type == 0) {
                                    builder.setMessage(getString(call.call.rtmp_stream ? R.string.VoipGroupStartRecordingRtmpText : R.string.VoipGroupStartRecordingText));
                                } else {
                                    if (ChatObject.isChannelOrGiga(currentChat)) {
                                        builder.setMessage(getString(call.call.rtmp_stream ? R.string.VoipGroupStartRecordingRtmpVideoText : R.string.VoipChannelStartRecordingVideoText));
                                    } else {
                                        builder.setMessage(getString(call.call.rtmp_stream ? R.string.VoipGroupStartRecordingRtmpVideoText : R.string.VoipGroupStartRecordingVideoText));
                                    }
                                }
                                builder.setCheckFocusable(false);

                                EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
                                editText.setBackgroundDrawable(Theme.createEditTextDrawable(getContext(), Theme.getColor(Theme.key_voipgroup_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_voipgroup_windowBackgroundWhiteInputFieldActivated)));

                                LinearLayout linearLayout = new LinearLayout(getContext());
                                linearLayout.setOrientation(LinearLayout.VERTICAL);
                                builder.setView(linearLayout);

                                editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                                editText.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
                                editText.setMaxLines(1);
                                editText.setLines(1);
                                editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                                editText.setGravity(Gravity.LEFT | Gravity.TOP);
                                editText.setSingleLine(true);
                                editText.setHint(getString(R.string.VoipGroupSaveFileHint));
                                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                                editText.setHintTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
                                editText.setCursorColor(Theme.getColor(Theme.key_voipgroup_nameText));
                                editText.setCursorSize(dp(20));
                                editText.setCursorWidth(1.5f);
                                editText.setPadding(0, dp(4), 0, 0);
                                linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 0, 24, 12));
                                editText.setOnEditorActionListener((textView, i2, keyEvent) -> {
                                    AndroidUtilities.hideKeyboard(textView);
                                    builder.create().getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                                    return false;
                                });

                                final AlertDialog alertDialog = builder.create();
                                alertDialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground));
                                alertDialog.setOnShowListener(dialog -> makeFocusable(null, alertDialog, editText, true));
                                alertDialog.setOnDismissListener(dialog -> AndroidUtilities.hideKeyboard(editText));

                                builder.setPositiveButton(getString(R.string.Start), (dialogInterface, i) -> {
                                    call.toggleRecord(editText.getText().toString(), type);
                                    AndroidUtilities.hideKeyboard(editText);
                                    getUndoView().showWithAction(0, type == ChatObject.Call.RECORD_TYPE_AUDIO ? UndoView.ACTION_VOIP_RECORDING_STARTED : UndoView.ACTION_VOIP_VIDEO_RECORDING_STARTED, null);
                                    if (VoIPService.getSharedInstance() != null) {
                                        VoIPService.getSharedInstance().playStartRecordSound();
                                    }
                                });
                                builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));
                                AlertDialog dialog = builder.create();
                                dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
                                dialog.show();
                                dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
                                if (editText != null) {
                                    editText.requestFocus();
                                }
                            }
                        };
                        if (isRtmpStream()) {
                            alert.onStartRecord(ChatObject.Call.RECORD_TYPE_VIDEO_LANDSCAPE);
                        } else {
                            alert.show();
                        }
                    }
                } else if (id == permission_item) {
                    changingPermissions = true;
                    everyoneItem.setVisibility(View.VISIBLE);
                    adminItem.setVisibility(View.VISIBLE);

                    accountGap.setVisibility(View.GONE);
                    inviteItem.setVisibility(View.GONE);
                    leaveItem.setVisibility(View.GONE);
                    permissionItem.setVisibility(View.GONE);
                    editTitleItem.setVisibility(View.GONE);
                    recordItem.setVisibility(View.GONE);
                    screenItem.setVisibility(View.GONE);
                    accountSelectCell.setVisibility(View.GONE);
                    soundItem.setVisibility(View.GONE);
                    noiseItem.setVisibility(View.GONE);
                    otherItem.forceUpdatePopupPosition();
                } else if (id == edit_item) {
                    enterEventSent = false;
                    final EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
                    editText.setBackgroundDrawable(Theme.createEditTextDrawable(getContext(), true));

                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        builder.setTitle(getString(R.string.VoipChannelTitle));
                    } else {
                        builder.setTitle(getString(R.string.VoipGroupTitle));
                    }
                    builder.setCheckFocusable(false);
                    builder.setNegativeButton(getString(R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));

                    LinearLayout linearLayout = new LinearLayout(getContext());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    editText.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    editText.setMaxLines(1);
                    editText.setLines(1);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    editText.setGravity(Gravity.LEFT | Gravity.TOP);
                    editText.setSingleLine(true);
                    editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    editText.setHint(currentChat != null ? currentChat.title : "");
                    editText.setHintTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
                    editText.setCursorColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    editText.setCursorSize(dp(20));
                    editText.setCursorWidth(1.5f);
                    editText.setPadding(0, dp(4), 0, 0);
                    linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));
                    editText.setOnEditorActionListener((textView, i, keyEvent) -> {
                        AndroidUtilities.hideKeyboard(textView);
                        builder.create().getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                        return false;
                    });
                    editText.addTextChangedListener(new TextWatcher() {

                        boolean ignoreTextChange;

                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreTextChange) {
                                return;
                            }
                            if (s.length() > 40) {
                                ignoreTextChange = true;
                                s.delete(40, s.length());
                                AndroidUtilities.shakeView(editText);
                                try {
                                    editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                                } catch (Exception ignored) {}
                                ignoreTextChange = false;
                            }
                        }
                    });
                    if (!TextUtils.isEmpty(call.call.title)) {
                        editText.setText(call.call.title);
                        editText.setSelection(editText.length());
                    }
                    builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
                        AndroidUtilities.hideKeyboard(editText);
                        call.setTitle(editText.getText().toString());
                        builder.getDismissRunnable().run();
                    });

                    final AlertDialog alertDialog = builder.create();
                    alertDialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground));
                    alertDialog.setOnShowListener(dialog -> makeFocusable(null, alertDialog, editText, true));
                    alertDialog.setOnDismissListener(dialog -> AndroidUtilities.hideKeyboard(editText));
                    alertDialog.show();
                    alertDialog.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    editText.requestFocus();
                } else if (id == user_item) {
                    JoinCallAlert.open(getContext(), -getChatId(), accountInstance, null, JoinCallAlert.TYPE_DISPLAY, selfPeer, (peer1, hasFewPeers, schedule, isRtmpStream) -> {
                        if (call == null) {
                            return;
                        }
                        TLObject object;
                        if (peer1 instanceof TLRPC.TL_inputPeerUser) {
                            object = accountInstance.getMessagesController().getUser(peer1.user_id);
                        } else if (peer1 instanceof TLRPC.TL_inputPeerChat) {
                            object = accountInstance.getMessagesController().getChat(peer1.chat_id);
                        } else {
                            object = accountInstance.getMessagesController().getChat(peer1.channel_id);
                        }
                        if (call.isScheduled()) {
                            getUndoView().showWithAction(0, UndoView.ACTION_VOIP_USER_CHANGED, object, currentChat, null, null);
                            if (peer1 instanceof TLRPC.TL_inputPeerChannel) {
                                selfPeer = new TLRPC.TL_peerChannel();
                                selfPeer.channel_id = peer1.channel_id;
                            } else if (peer1 instanceof TLRPC.TL_inputPeerUser) {
                                selfPeer = new TLRPC.TL_peerUser();
                                selfPeer.user_id = peer1.user_id;
                            } else if (peer1 instanceof TLRPC.TL_inputPeerChat) {
                                selfPeer = new TLRPC.TL_peerChat();
                                selfPeer.chat_id = peer1.chat_id;
                            }
                            GroupCallActivity.this.schedulePeer = peer1;
                            TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(getChatId());
                            if (chatFull != null) {
                                chatFull.groupcall_default_join_as = selfPeer;
                                if (chatFull instanceof TLRPC.TL_chatFull) {
                                    chatFull.flags |= 32768;
                                } else {
                                    chatFull.flags |= 67108864;
                                }
                            }
                            TL_phone.saveDefaultGroupCallJoinAs req = new TL_phone.saveDefaultGroupCallJoinAs();
                            req.peer = MessagesController.getInputPeer(currentChat);
                            req.join_as = peer1;
                            accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {

                            });
                            updateItems();
                        } else {
                            if (VoIPService.getSharedInstance() == null || !hasFewPeers) {
                                return;
                            }
                            TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
                            VoIPService.getSharedInstance().setGroupCallPeer(peer1);
                            userSwitchObject = object;
                        }
                    });
                } else if (id == noise_item) {
                    SharedConfig.toggleNoiseSupression();
                    VoIPService service = VoIPService.getSharedInstance();
                    if (service == null) {
                        return;
                    }
                    service.setNoiseSupressionEnabled(SharedConfig.noiseSupression);
                } else if (id == sound_item) {
                    VoIPService service = VoIPService.getSharedInstance();
                    if (service == null) {
                        return;
                    }
                    ArrayList<CharSequence> names = new ArrayList<>();
                    ArrayList<Integer> icons = new ArrayList<>();
                    ArrayList<Integer> options = new ArrayList<>();

                    names.add(getString(R.string.VoipAudioRoutingSpeaker));
                    icons.add(R.drawable.msg_voice_speaker);
                    options.add(0);

                    if (service.hasEarpiece()) {
                        names.add(service.isHeadsetPlugged() ? getString(R.string.VoipAudioRoutingHeadset) : getString(R.string.VoipAudioRoutingPhone));
                        icons.add(service.isHeadsetPlugged() ? R.drawable.msg_voice_headphones : R.drawable.msg_voice_phone);
                        options.add(1);
                    }

                    if (service.isBluetoothHeadsetConnected()) {
                        names.add(service.currentBluetoothDeviceName != null ? service.currentBluetoothDeviceName : getString(R.string.VoipAudioRoutingBluetooth));
                        icons.add(R.drawable.msg_voice_bluetooth);
                        options.add(2);
                    }

                    int n = names.size();
                    CharSequence[] itemsArray = new CharSequence[n];
                    int[] iconsArray = new int[n];
                    for (int i = 0; i < n; i++) {
                        itemsArray[i] = names.get(i);
                        iconsArray[i] = icons.get(i);
                    }

                    BottomSheet.Builder builder = new BottomSheet.Builder(context)
                            .setTitle(getString(R.string.VoipSelectAudioOutput), true)
                            .setItems(itemsArray, iconsArray, (dialog, which) -> {
                                        if (VoIPService.getSharedInstance() == null) {
                                            return;
                                        }
                                        int newValue = options.get(which);
                                        cacheAudioOutputValue = newValue;
                                        updateState(true, true);
                                        cacheAudioOutputValue = null;
                                        AndroidUtilities.runOnUIThread(() -> {
                                            setAudioOutputValue(newValue);

                                            BulletinFactory.of(topBulletinContainer, new DarkBlueThemeResourcesProvider())
                                                .createSimpleBulletin(getContext().getResources().getDrawable(getAudioOutputToastIcon(newValue)).mutate(), getAudioOutputToastText(newValue))
                                                .show(isBulletinTop());
                                        });
                                    });
                    BottomSheet bottomSheet = builder.create();

                    bottomSheet.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled));
                    bottomSheet.fixNavigationBar(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled));
                    int selectedPosition;
                    if (service.getCurrentAudioRoute() == VoIPService.AUDIO_ROUTE_SPEAKER) {
                        selectedPosition = 0;
                    } else if (service.getCurrentAudioRoute() == VoIPService.AUDIO_ROUTE_EARPIECE) {
                        selectedPosition = 1;
                    } else {
                        selectedPosition = 2;
                    }
                    builder.show();
                    bottomSheet.setTitleColor(Theme.getColor(Theme.key_voipgroup_nameText));
                    for (int i = 0; i < bottomSheet.getItemViews().size(); i++) {
                        BottomSheetCell cell = bottomSheet.getItemViews().get(i);
                        int color;
                        if (i == selectedPosition) {
                            color = Theme.getColor(Theme.key_voipgroup_listeningText);
                            cell.isSelected = true;
                        } else {
                            color = Theme.getColor(Theme.key_voipgroup_nameText);
                        }
                        cell.setTextColor(color);
                        cell.setIconColor(color);
                        cell.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_actionBarItems), (int) (255 * 0.05f)), 2));
                    }
                }
            }
        });

        TLRPC.InputPeer peer;
        if (schedulePeer != null) {
            peer = schedulePeer;
        } else {
            peer = VoIPService.getSharedInstance().getGroupCallPeer();
        }
        if (peer == null) {
            selfPeer = new TLRPC.TL_peerUser();
            selfPeer.user_id = accountInstance.getUserConfig().getClientUserId();
        } else if (peer instanceof TLRPC.TL_inputPeerChannel) {
            selfPeer = new TLRPC.TL_peerChannel();
            selfPeer.channel_id = peer.channel_id;
        } else if (peer instanceof TLRPC.TL_inputPeerUser) {
            selfPeer = new TLRPC.TL_peerUser();
            selfPeer.user_id = peer.user_id;
        } else if (peer instanceof TLRPC.TL_inputPeerChat) {
            selfPeer = new TLRPC.TL_peerChat();
            selfPeer.chat_id = peer.chat_id;
        }

        VoIPService.audioLevelsCallback = (uids, levels, voice) -> {
            for (int a = 0; a < uids.length; a++) {
                TLRPC.GroupCallParticipant participant = call.participantsBySources.get(uids[a]);
                if (participant != null) {
                    if (!renderersContainer.inFullscreenMode) {
                        ArrayList<TLRPC.GroupCallParticipant> array = delayedGroupCallUpdated ? oldParticipants : call.visibleParticipants;
                        int idx = array.indexOf(participant);
                        if (idx >= 0) {
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(idx + listAdapter.usersStartRow);
                            if (holder != null && holder.itemView instanceof GroupCallUserCell) {
                                GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                                cell.setAmplitude(levels[a] * 15.0f);
                                if (holder.itemView == scrimView) {
                                    if (!contentFullyOverlayed) {
                                        containerView.invalidate();
                                    }
                                }
                            }
                        }
                    } else {
                        for (int i = 0; i < fullscreenUsersListView.getChildCount(); i++) {
                            GroupCallFullscreenAdapter.GroupCallUserCell cell = (GroupCallFullscreenAdapter.GroupCallUserCell) fullscreenUsersListView.getChildAt(i);
                            if (MessageObject.getPeerId(cell.getParticipant().peer) == MessageObject.getPeerId(participant.peer)) {
                                cell.setAmplitude(levels[a] * 15.0f);
                            }
                        }
                    }
                    renderersContainer.setAmplitude(participant, levels[a] * 15.0f);
                }
            }
        };

        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.groupCallUpdated);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.needShowAlert);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.didLoadChatAdmins);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.applyGroupCallVisibleParticipants);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.userInfoDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.mainUserInfoChanged);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.updateInterfaces);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.groupCallScreencastStateChanged);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.groupCallSpeakingUsersUpdated);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.conferenceEmojiUpdated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didEndCall);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

        bigMicDrawable = new RLottieDrawable(R.raw.voip_filled, "" + R.raw.voip_filled, dp(72), dp(72), true, null);
        handDrawables = new RLottieDrawable(R.raw.hand_2, "" + R.raw.hand_2, dp(72), dp(72), true, null);

        containerView = new FrameLayout(context) {

            private boolean ignoreLayout = false;
            private RectF rect = new RectF();
            private int lastSize;
            private boolean updateRenderers;

            boolean localHasVideo;
            boolean wasLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                ignoreLayout = true;
                boolean landscape = MeasureSpec.getSize(widthMeasureSpec) > totalHeight && !AndroidUtilities.isTablet();
                renderersContainer.listWidth = MeasureSpec.getSize(widthMeasureSpec);
                boolean tablet = AndroidUtilities.isTablet() && MeasureSpec.getSize(widthMeasureSpec) > totalHeight && !isRtmpStream();
                if (isLandscapeMode != landscape) {
                    isLandscapeMode = landscape;

                    int measuredWidth = muteButton.getMeasuredWidth();
                    if (measuredWidth == 0) {
                        measuredWidth = muteButton.getLayoutParams().width;
                    }
                    float muteButtonScale = dp(52) / (float) (measuredWidth - dp(8));
                    float multiplier = isLandscapeMode || isRtmpStream() ? muteButtonScale : renderersContainer.inFullscreenMode ? muteButtonScale : 1f;

                    boolean isExpanded = renderersContainer.inFullscreenMode && (AndroidUtilities.isTablet() || isLandscapeMode == isRtmpLandscapeMode());
                    View expandView = isExpanded ? minimizeButton : expandButton;
                    View expandOtherView = isExpanded ? expandButton : minimizeButton;
                    expandView.setAlpha(1f);
                    expandView.setScaleX(multiplier);
                    expandView.setScaleY(multiplier);
                    expandOtherView.setAlpha(0f);

                    muteLabel[0].setAlpha(1f);
                    muteLabel[1].setAlpha(1f);

                    if (!renderersContainer.inFullscreenMode && (!isLandscapeMode || AndroidUtilities.isTablet())) {
                        muteLabel[0].setScaleX(1f);
                        muteLabel[1].setScaleY(1f);
                    } else {
                        muteLabel[0].setScaleX(0.687f);
                        muteLabel[1].setScaleY(0.687f);
                    }

                    invalidateLayoutFullscreen();

                    layoutManager.setSpanCount(isLandscapeMode ? 6 : 2);
                    listView.invalidateItemDecorations();
                    fullscreenUsersListView.invalidateItemDecorations();
                    updateRenderers = true;
                    if (scheduleInfoTextView != null) {
                        scheduleInfoTextView.setVisibility(!isLandscapeMode ? View.VISIBLE : View.GONE);
                    }

                    boolean needFullscreen = isRtmpLandscapeMode() == landscape;
                    if (needFullscreen && isRtmpStream() && !renderersContainer.inFullscreenMode && !call.visibleVideoParticipants.isEmpty()) {
                        fullscreenFor(call.visibleVideoParticipants.get(0));
                        renderersContainer.delayHideUi();
                    }
                }
                if (isTabletMode != tablet) {
                    isTabletMode = tablet;
                    tabletVideoGridView.setVisibility(tablet ? View.VISIBLE : View.GONE);
                    listView.invalidateItemDecorations();
                    fullscreenUsersListView.invalidateItemDecorations();
                    updateRenderers = true;
                }
                if (updateRenderers) {
                    applyCallParticipantUpdates(true);
                    listAdapter.notifyDataSetChanged();
                    fullscreenAdapter.update(false, tabletVideoGridView);
                    if (isTabletMode) {
                        tabletGridAdapter.update(false, tabletVideoGridView);
                    }
                    tabletVideoGridView.setVisibility(isTabletMode ? View.VISIBLE : View.GONE);
                    tabletGridAdapter.setVisibility(tabletVideoGridView, isTabletMode && !renderersContainer.inFullscreenMode, true);

                    listViewVideoVisibility = !isTabletMode || renderersContainer.inFullscreenMode;

                    boolean fullscreenListVisibility = !isTabletMode && renderersContainer.inFullscreenMode;
                    fullscreenAdapter.setVisibility(fullscreenUsersListView, fullscreenListVisibility);
                    fullscreenUsersListView.setVisibility(fullscreenListVisibility ? View.VISIBLE : View.GONE);
                    listView.setVisibility((isTabletMode || !renderersContainer.inFullscreenMode) ? View.VISIBLE : View.GONE);
                    layoutManager.setSpanCount(isLandscapeMode ? 6 : 2);
                    updateState(false, false);
                    listView.invalidateItemDecorations();
                    fullscreenUsersListView.invalidateItemDecorations();

                    AndroidUtilities.updateVisibleRows(listView);
                    updateRenderers = false;
                    attachedRenderersTmp.clear();
                    attachedRenderersTmp.addAll(attachedRenderers);
                    renderersContainer.setIsTablet(isTabletMode);
                    for (int i = 0; i < attachedRenderersTmp.size(); i++) {
                        attachedRenderersTmp.get(i).updateAttachState(true);
                    }
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    setPadding(backgroundPaddingLeft, getStatusBarHeight(), backgroundPaddingLeft, 0);
                }
                int availableHeight = totalHeight - getPaddingTop();
                if (isRtmpStream()) {
                    availableHeight -= dp(72);
                } else {
                    availableHeight -= dp(14 + 231);
                }

                int listViewPaddingBottom;

                LayoutParams layoutParams = (LayoutParams) renderersContainer.getLayoutParams();
                if (isTabletMode) {
                    layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
                } else {
                    layoutParams.topMargin = 0;
                }

                for (int a = 0; a < 2; a++) {
                    layoutParams = (LayoutParams) undoView[a].getLayoutParams();
                    if (isTabletMode) {
                        layoutParams.rightMargin = dp(TABLET_LIST_SIZE + 8);
                    } else {
                        layoutParams.rightMargin = dp(8);
                    }
                }

                if (tabletVideoGridView != null) {
                    layoutParams = (LayoutParams) tabletVideoGridView.getLayoutParams();
                    layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
                }

                int buttonsGradientSize = dp(isRtmpStream() ? 100 : 150);
                //listView layoutParams
                layoutParams = (LayoutParams) listView.getLayoutParams();
                if (isTabletMode) {
                    layoutParams.gravity = hasVideo ? Gravity.RIGHT : Gravity.CENTER_HORIZONTAL;
                    layoutParams.width = dp(TABLET_LIST_SIZE);
                    layoutParams.rightMargin = layoutParams.leftMargin = dp(4);
                    layoutParams.bottomMargin = buttonsGradientSize;
                    layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
                    listViewPaddingBottom = dp(60);
                } else if (isLandscapeMode) {
                    layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                    layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
                    layoutParams.bottomMargin = dp(14);
                    layoutParams.rightMargin = dp(90);
                    layoutParams.leftMargin = dp(14);
                    listViewPaddingBottom = 0;
                } else {
                    layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                    listViewPaddingBottom = dp(60);
                    layoutParams.bottomMargin = buttonsGradientSize;
                    layoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + dp(14);
                    layoutParams.rightMargin = layoutParams.leftMargin = dp(14);
                }
                //
                if (isLandscapeMode && !isTabletMode) {
                    buttonsBackgroundGradientView.setVisibility(View.GONE);
                    buttonsBackgroundGradientView2.setVisibility(View.GONE);
                } else {
                    buttonsBackgroundGradientView.setVisibility(View.VISIBLE);
                    layoutParams = (LayoutParams) buttonsBackgroundGradientView.getLayoutParams();
                    layoutParams.bottomMargin = buttonsGradientSize;

                    if (isTabletMode) {
                        layoutParams.gravity = hasVideo ? Gravity.RIGHT | Gravity.BOTTOM : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        layoutParams.width = dp(TABLET_LIST_SIZE + 8);
                    } else {
                        layoutParams.width = LayoutHelper.MATCH_PARENT;
                    }

                    buttonsBackgroundGradientView2.setVisibility(View.VISIBLE);
                    layoutParams = (LayoutParams) buttonsBackgroundGradientView2.getLayoutParams();
                    layoutParams.height = buttonsGradientSize;

                    if (isTabletMode) {
                        layoutParams.gravity = hasVideo ? Gravity.RIGHT | Gravity.BOTTOM : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                        layoutParams.width = dp(TABLET_LIST_SIZE + 8);
                    } else {
                        layoutParams.width = LayoutHelper.MATCH_PARENT;
                    }
                }

                if (isLandscapeMode) {
                    fullscreenUsersListView.setPadding(0, dp(9), 0, dp(9));
                } else {
                    fullscreenUsersListView.setPadding(dp(9), 0, dp(9), 0);
                }

                // buttonsContainer
                layoutParams = (LayoutParams) buttonsContainer.getLayoutParams();
                if (isTabletMode) {
                    layoutParams.width = dp(TABLET_LIST_SIZE);
                    layoutParams.height = dp(200);
                    layoutParams.gravity = hasVideo ? Gravity.RIGHT | Gravity.BOTTOM : Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    layoutParams.rightMargin = 0;
                } else if (isLandscapeMode) {
                    layoutParams.width = dp(90);
                    layoutParams.height = LayoutHelper.MATCH_PARENT;
                    layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
                } else {
                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                    layoutParams.height = dp(200);
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    layoutParams.rightMargin = 0;
                }

                if (isLandscapeMode && !isTabletMode) {
                    layoutParams = (LayoutParams) actionBar.getLayoutParams();
                    layoutParams.rightMargin = dp(90);
                    layoutParams = (LayoutParams) menuItemsContainer.getLayoutParams();
                    layoutParams.rightMargin = dp(90);
                    layoutParams = (LayoutParams) actionBarBackground.getLayoutParams();
                    layoutParams.rightMargin = dp(90);
                    layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                    layoutParams.rightMargin = dp(90);
                } else {
                    layoutParams = (LayoutParams) actionBar.getLayoutParams();
                    layoutParams.rightMargin = 0;
                    layoutParams = (LayoutParams) menuItemsContainer.getLayoutParams();
                    layoutParams.rightMargin = 0;
                    layoutParams = (LayoutParams) actionBarBackground.getLayoutParams();
                    layoutParams.rightMargin = 0;
                    layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                    layoutParams.rightMargin = 0;
                }

                layoutParams = (LayoutParams) fullscreenUsersListView.getLayoutParams();
                if (isLandscapeMode) {
                    if (((LinearLayoutManager) fullscreenUsersListView.getLayoutManager()).getOrientation() != RecyclerView.VERTICAL) {
                        ((LinearLayoutManager) fullscreenUsersListView.getLayoutManager()).setOrientation(RecyclerView.VERTICAL);
                    }
                    layoutParams.height = LayoutHelper.MATCH_PARENT;
                    layoutParams.width = dp(80);
                    layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
                    layoutParams.rightMargin = dp(100);
                    layoutParams.bottomMargin = 0;
                } else {
                    if (((LinearLayoutManager) fullscreenUsersListView.getLayoutManager()).getOrientation() != RecyclerView.HORIZONTAL) {
                        ((LinearLayoutManager) fullscreenUsersListView.getLayoutManager()).setOrientation(RecyclerView.HORIZONTAL);
                    }
                    layoutParams.height = dp(80);
                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                    layoutParams.gravity = Gravity.BOTTOM;
                    layoutParams.rightMargin = 0;
                    layoutParams.bottomMargin = dp(100);
                }
                layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                int contentSize = Math.max(dp(64 + 50 + 58 * 2.5f), availableHeight / 5 * 3);
                int padding = isTabletMode ? 0 : Math.max(0, availableHeight - contentSize + dp(8));
                if (listView.getPaddingTop() != padding || listView.getPaddingBottom() != listViewPaddingBottom) {
                    listView.setPadding(0, padding, 0, listViewPaddingBottom);
                }

                if (watchersView != null) {
                    layoutParams = (LayoutParams) watchersView.getLayoutParams();
                    GroupCallGridCell cell = findGroupCallGridCell();
                    if (cell != null) {
                        int leaveButtonTop = buttonsContainer.getTop() + buttonsContainer.getMeasuredHeight() / 2 - leaveButton.getMeasuredHeight() / 2;
                        int top = padding + ActionBar.getCurrentActionBarHeight() + cell.getMeasuredHeight();
                        int diff = (leaveButtonTop - top) / 2;
                        layoutParams.topMargin = top + diff - dp(32);
                        layoutParams.height = dp(70);
                    }
                }

                if (textureLightningView != null) {
                    layoutParams = (LayoutParams) textureLightningView.getLayoutParams();
                    GroupCallGridCell cell = findGroupCallGridCell();
                    if (cell != null) {
                        layoutParams.height = cell.getMeasuredHeight() - dp(14);
                        layoutParams.width = cell.getMeasuredWidth() - dp(7);
                        layoutParams.rightMargin = layoutParams.leftMargin = dp(16);
                    }
                }
                if (scheduleStartAtTextView != null) {
                    int y = padding + (availableHeight - padding + dp(60)) / 2;
                    FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) scheduleStartInTextView.getLayoutParams();
                    layoutParams1.topMargin = y - dp(30);

                    FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) scheduleStartAtTextView.getLayoutParams();
                    layoutParams2.topMargin = y + dp(80);

                    FrameLayout.LayoutParams layoutParams3 = (FrameLayout.LayoutParams) scheduleTimeTextView.getLayoutParams();

                    if (layoutParams1.topMargin < ActionBar.getCurrentActionBarHeight() || layoutParams2.topMargin + dp(20) > totalHeight - dp(231)) {
                        scheduleStartInTextView.setVisibility(INVISIBLE);
                        scheduleStartAtTextView.setVisibility(INVISIBLE);
                        layoutParams3.topMargin = y - dp(20);
                    } else {
                        scheduleStartInTextView.setVisibility(VISIBLE);
                        scheduleStartAtTextView.setVisibility(VISIBLE);
                        layoutParams3.topMargin = y;
                    }
                }

                for (int i = 0; i < attachedRenderers.size(); i++) {
                    attachedRenderers.get(i).setFullscreenMode(renderersContainer.inFullscreenMode, true);
                }

                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
                int currentSize = getMeasuredHeight() + (getMeasuredWidth() << 16);
                 if (currentSize != lastSize) {
                    lastSize = currentSize;
                    dismissAvatarPreview(false);
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                boolean needAnimate = false;
                float fromX = 0;
                if (isTabletMode && localHasVideo != hasVideo && wasLayout) {
                    needAnimate = true;
                    fromX = listView.getX();
                }
                localHasVideo = hasVideo;
                renderersContainer.inLayout = true;
                super.onLayout(changed, l, t, r, b);
                renderersContainer.inLayout = false;
                updateLayout(false);
                wasLayout = true;

                if (needAnimate && listView.getLeft() != fromX) {
                    float dx = fromX - listView.getLeft();
                    listView.setTranslationX(dx);
                    buttonsContainer.setTranslationX(dx);
                    buttonsBackgroundGradientView.setTranslationX(dx);
                    buttonsBackgroundGradientView2.setTranslationX(dx);

                    listView.animate().translationX(0).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    buttonsBackgroundGradientView.animate().translationX(0).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    buttonsBackgroundGradientView2.animate().translationX(0).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    buttonsContainer.animate().translationX(0).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                }
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (scrimView != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                    float x = ev.getX();
                    float y = ev.getY();
                    boolean dismissScrim = true;
                    rect.set(scrimPopupLayout.getX(), scrimPopupLayout.getY(), scrimPopupLayout.getX() + scrimPopupLayout.getMeasuredWidth(), scrimPopupLayout.getY() + scrimPopupLayout.getMeasuredHeight());
                    if (rect.contains(x, y)) {
                        dismissScrim = false;
                    }
                    rect.set(avatarPreviewContainer.getX(), avatarPreviewContainer.getY(), avatarPreviewContainer.getX() + avatarPreviewContainer.getMeasuredWidth(), avatarPreviewContainer.getY() + avatarPreviewContainer.getMeasuredWidth() + scrimView.getMeasuredHeight());
                    if (rect.contains(x, y)) {
                        dismissScrim = false;
                    }
                    if (dismissScrim) {
                        dismissAvatarPreview(true);
                        return true;
                    }
                }
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - dp(37) && actionBar.getAlpha() == 0.0f && !avatarsPreviewShowed && previewDialog == null && !renderersContainer.inFullscreenMode) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }


            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                updateTopBulletinY();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int offset = dp(74);
                float top = scrollOffsetY - offset;

                int height = getMeasuredHeight() + dp(15) + backgroundPaddingTop;
                float rad = 1.0f;

                float moveProgress = 0;
                if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                    int willMoveUpTo = offset - backgroundPaddingTop - dp(14);
                    moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / willMoveUpTo);
                    int diff = (int) ((ActionBar.getCurrentActionBarHeight() - willMoveUpTo) * moveProgress);
                    top -= diff;
                    height += diff;
                    rad = 1.0f - moveProgress;
                }

                top += getPaddingTop();
                updateTopBulletinY();

                if (renderersContainer.progressToFullscreenMode != 1f) {
                    shadowDrawable.setBounds(0, (int) top, getMeasuredWidth(), height);
                    shadowDrawable.draw(canvas);

                    if (rad != 1.0f) {
                        Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                        rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + dp(24));
                        canvas.drawRoundRect(rect, dp(12) * rad, dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                    }

                    int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(backgroundColor) * 0.8f), (int) (Color.green(backgroundColor) * 0.8f), (int) (Color.blue(backgroundColor) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    float bottom = getStatusBarHeight();
                    canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, bottom, Theme.dialogs_onlineCirclePaint);

                    if (previewDialog != null) {
                        Theme.dialogs_onlineCirclePaint.setColor(previewDialog.getBackgroundColor());
                        canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, getStatusBarHeight(), Theme.dialogs_onlineCirclePaint);
                    }
                }

                if (renderersContainer.progressToFullscreenMode != 0) {
                    Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_actionBar), (int) (255 * renderersContainer.progressToFullscreenMode)));
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), Theme.dialogs_onlineCirclePaint);
                }
                if (isRtmpStream() && LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
                    if (renderersContainer.progressToFullscreenMode < 0.15) {
                        if (!needTextureLightning) {
                            needTextureLightning = true;
                            runUpdateTextureLightningRunnable();
                        }
                    } else {
                        if (needTextureLightning) {
                            needTextureLightning = false;
                            AndroidUtilities.cancelRunOnUIThread(updateTextureLightningRunnable);
                        }
                    }
                }

            }

            HashMap<Object, View> listCells = new HashMap<>();

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (isTabletMode) {
                    buttonsContainer.setTranslationY(0);
                    fullscreenUsersListView.setTranslationY(0);
                    buttonsContainer.setTranslationX(0);
                    fullscreenUsersListView.setTranslationY(0);
                } else if (isLandscapeMode) {
                    buttonsContainer.setTranslationY(0);
                    fullscreenUsersListView.setTranslationY(0);
                    buttonsContainer.setTranslationX(progressToHideUi * dp(94));
                    fullscreenUsersListView.setTranslationX(progressToHideUi * dp(94));
                } else {
                    buttonsContainer.setTranslationX(0);
                    fullscreenUsersListView.setTranslationX(0);
                    buttonsContainer.setTranslationY(progressToHideUi * dp(94));
                    fullscreenUsersListView.setTranslationY(progressToHideUi * dp(94));
                }

                for (int i = 0; i < listView.getChildCount(); i++) {
                    View view = listView.getChildAt(i);
                    if (view instanceof GroupCallUserCell) {
                        GroupCallUserCell cell = (GroupCallUserCell) view;
                        cell.setDrawAvatar(true);
                    }
                    if (!(view instanceof GroupCallGridCell)) {
                        if (view.getMeasuredWidth() != listView.getMeasuredWidth()) {
                            view.setTranslationX((listView.getMeasuredWidth() - view.getMeasuredWidth()) >> 1);
                        } else {
                            view.setTranslationX(0);
                        }
                    }
                }

                if (renderersContainer.isAnimating()) {
                    if (fullscreenUsersListView.getVisibility() == View.VISIBLE) {
                        listCells.clear();
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View view = listView.getChildAt(i);
                            if (!view.isAttachedToWindow()) {
                                continue;
                            }
                            if (view instanceof GroupCallGridCell && listView.getChildAdapterPosition(view) >= 0) {
                                GroupCallGridCell cell = (GroupCallGridCell) view;
                                if (cell.getRenderer() != renderersContainer.fullscreenTextureView) {
                                    listCells.put(cell.getParticipant(), view);
                                }
                            } else if (view instanceof GroupCallUserCell && listView.getChildAdapterPosition(view) >= 0) {
                                GroupCallUserCell cell = (GroupCallUserCell) view;
                                listCells.put(cell.getParticipant(), cell);
                            }
                        }
                        for (int i = 0; i < fullscreenUsersListView.getChildCount(); i++) {
                            GroupCallFullscreenAdapter.GroupCallUserCell cellTo = (GroupCallFullscreenAdapter.GroupCallUserCell) fullscreenUsersListView.getChildAt(i);

                            View cellFrom = listCells.get(cellTo.getVideoParticipant());
                            if (cellFrom == null) {
                                cellFrom = listCells.get(cellTo.getParticipant());
                            }
                            float progressToFullscreenMode = renderersContainer.progressToFullscreenMode;

                            if (!fullscreenListItemAnimator.isRunning()) {
                                cellTo.setAlpha(1f);
                            }
                            if (cellFrom != null) {
                                float toX, toY, fromX, fromY;
                                if (cellFrom instanceof GroupCallGridCell) {
                                    GroupCallGridCell gridCell = (GroupCallGridCell) cellFrom;
                                    fromX = gridCell.getLeft() + listView.getX() - renderersContainer.getLeft();
                                    fromY = gridCell.getTop() + listView.getY() - renderersContainer.getTop();

                                    toX = cellTo.getLeft() + fullscreenUsersListView.getX();
                                    toY = cellTo.getTop() + fullscreenUsersListView.getY();
                                } else {
                                    GroupCallUserCell userCell = (GroupCallUserCell) cellFrom;
                                    fromX = userCell.getLeft() + listView.getX() - renderersContainer.getLeft() + userCell.getAvatarImageView().getLeft() + (userCell.getAvatarImageView().getMeasuredWidth() >> 1);
                                    fromY = userCell.getTop() + listView.getY() - renderersContainer.getTop() + userCell.getAvatarImageView().getTop() + (userCell.getAvatarImageView().getMeasuredHeight() >> 1);

                                    toX = cellTo.getLeft() + fullscreenUsersListView.getX() + (cellTo.getMeasuredWidth() >> 1);
                                    toY = cellTo.getTop() + fullscreenUsersListView.getY() + (cellTo.getMeasuredHeight() >> 1);

                                    userCell.setDrawAvatar(false);
                                }

                                cellTo.setTranslationX((fromX - toX) * (1f - progressToFullscreenMode));
                                cellTo.setTranslationY((fromY - toY) * (1f - progressToFullscreenMode));
                                cellTo.setScaleX(1f);
                                cellTo.setScaleY(1f);
                                cellTo.setProgressToFullscreen(progressToFullscreenMode);
                            } else {
                                cellTo.setScaleX(1f);
                                cellTo.setScaleY(1f);
                                cellTo.setTranslationX(0);
                                cellTo.setTranslationY(0);
                                cellTo.setProgressToFullscreen(1f);
                                if (cellTo.getRenderer() == null) {
                                    cellTo.setAlpha(progressToFullscreenMode);
                                }
                            }
                        }
                    }
                } else {
                    for (int i = 0; i < fullscreenUsersListView.getChildCount(); i++) {
                        GroupCallFullscreenAdapter.GroupCallUserCell cellTo = (GroupCallFullscreenAdapter.GroupCallUserCell) fullscreenUsersListView.getChildAt(i);
                        cellTo.setProgressToFullscreen(1f);
                    }
                }
                for (int i = 0; i < attachedRenderers.size(); i++) {
                    GroupCallMiniTextureView child = attachedRenderers.get(i);
                    child.updatePosition(listView, tabletVideoGridView, fullscreenUsersListView, renderersContainer);
                }

                if (!isTabletMode) {
                    buttonsBackgroundGradientView.setAlpha(1f - renderersContainer.progressToFullscreenMode);
                    buttonsBackgroundGradientView2.setAlpha(1f - renderersContainer.progressToFullscreenMode);
                } else {
                    buttonsBackgroundGradientView.setAlpha(1f);
                    buttonsBackgroundGradientView2.setAlpha(1f);
                }

                if (renderersContainer.swipedBack) {
                    listView.setAlpha(1f - renderersContainer.progressToFullscreenMode);
                } else {
                    listView.setAlpha(1f);
                }

                if (watchersView != null) {
                    watchersView.setAlpha(1f - renderersContainer.progressToFullscreenMode);
                    watchersView.setTranslationY(renderersContainer.progressToFullscreenMode * dp(64));
                }
                super.dispatchDraw(canvas);
                if (drawingForBlur) {
                    return;
                }
                if (avatarsPreviewShowed) {
                    if (scrimView != null) {
                        if (!useBlur) {
                            canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
                        }
                        float listTop = listView.getY();

                        float[] radii = new float[8];
                        Path roundPath = new Path();

                        int count = listView.getChildCount();
                        float viewClipBottom = listView.getY() + listView.getMeasuredHeight();

                        GroupCallUserCell scrimViewLocal = null;
                        if (hasScrimAnchorView) {
                            for (int num = 0; num < count; num++) {
                                View child = listView.getChildAt(num);
                                if (child == scrimView) {
                                    scrimViewLocal = scrimView;
                                    break;
                                }
                            }
                        } else {
                            scrimViewLocal = scrimView;
                        }

                        if (scrimViewLocal != null && listTop < viewClipBottom) {
                            canvas.save();
                            if (scrimFullscreenView == null) {
                                canvas.clipRect(0, listTop * (1f - progressToAvatarPreview), getMeasuredWidth(), viewClipBottom * (1f - progressToAvatarPreview) + getMeasuredHeight() * progressToAvatarPreview);
                            }
                            float childY, childX;
                            if (!hasScrimAnchorView) {
                                childY = avatarPreviewContainer.getTop() + avatarPreviewContainer.getMeasuredWidth();
                                childX = avatarPreviewContainer.getLeft();
                            } else {
                                childY = (listView.getY() + scrimViewLocal.getY()) * (1f - progressToAvatarPreview) + (avatarPreviewContainer.getTop() + avatarPreviewContainer.getMeasuredWidth()) * progressToAvatarPreview;
                                childX = (listView.getLeft() + scrimViewLocal.getX()) * (1f - progressToAvatarPreview) + avatarPreviewContainer.getLeft() * progressToAvatarPreview;
                            }
                            canvas.translate(childX, childY);
                            if (!hasScrimAnchorView) {
                                canvas.saveLayerAlpha(0 ,0, scrimViewLocal.getMeasuredWidth(), scrimViewLocal.getClipHeight(), (int) (255 * progressToAvatarPreview), Canvas.ALL_SAVE_FLAG);
                            } else {
                                canvas.save();
                            }
                            float progress = progressToAvatarPreview;
                            float pr = 1.0f - CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - progress);
                            int h = (int) (scrimViewLocal.getMeasuredHeight() + (scrimViewLocal.getClipHeight() - scrimViewLocal.getMeasuredHeight()) * pr);
                            rect.set(0, 0, scrimViewLocal.getMeasuredWidth(), h);
                            scrimViewLocal.setProgressToAvatarPreview(hasScrimAnchorView ? progressToAvatarPreview : 1f);
                            for (int i = 0; i < 4; i++) {
                                radii[i] = dp(13) * (1f - progressToAvatarPreview);
                                radii[4 + i] = dp(13);
                            }

                            roundPath.reset();
                            roundPath.addRoundRect(rect, radii, Path.Direction.CW);
                            roundPath.close();
                            canvas.drawPath(roundPath, listViewBackgroundPaint);
                            scrimViewLocal.draw(canvas);
                            canvas.restore();
                            canvas.restore();

                            if (scrimPopupLayout != null) {
                                float y = childY + h;
                                float x = getMeasuredWidth() - scrimPopupLayout.getMeasuredWidth() - dp(14);
                                if (progressToAvatarPreview != 1f) {
                                    canvas.saveLayerAlpha(x, y, x + scrimPopupLayout.getMeasuredWidth(), y + scrimPopupLayout.getMeasuredHeight(), (int) (255 * progressToAvatarPreview), Canvas.ALL_SAVE_FLAG);
                                } else {
                                    canvas.save();
                                }
                                scrimPopupLayout.setTranslationX(x - scrimPopupLayout.getLeft());
                                scrimPopupLayout.setTranslationY(y - scrimPopupLayout.getTop());
                                float scale = 0.8f + 0.2f * progressToAvatarPreview;
                                canvas.scale(scale, scale, x + scrimPopupLayout.getMeasuredWidth() / 2f, y);

                                canvas.translate(x, y);
                                scrimPopupLayout.draw(canvas);
                                canvas.restore();
                            }
                        }

                        if (!pinchToZoomHelper.isInOverlayMode()) {
                            canvas.save();
                            if (hasScrimAnchorView && scrimFullscreenView == null) {
                                canvas.clipRect(0, listTop * (1f - progressToAvatarPreview), getMeasuredWidth(), viewClipBottom * (1f - progressToAvatarPreview) + getMeasuredHeight() * progressToAvatarPreview);
                            }
                            canvas.scale(avatarPreviewContainer.getScaleX(), avatarPreviewContainer.getScaleY(), avatarPreviewContainer.getX(), avatarPreviewContainer.getY());
                            canvas.translate(avatarPreviewContainer.getX(), avatarPreviewContainer.getY());
                            avatarPreviewContainer.draw(canvas);
                            canvas.restore();
                        }
                    }


                    if (progressToAvatarPreview != 1f && scrimFullscreenView == null) {
                        canvas.saveLayerAlpha((int) buttonsBackgroundGradientView2.getX(), (int) buttonsBackgroundGradientView.getY(), (int) (buttonsBackgroundGradientView2.getX() + buttonsBackgroundGradientView2.getMeasuredWidth()), getMeasuredHeight(), (int) (255 * (1f - progressToAvatarPreview)), Canvas.ALL_SAVE_FLAG);

                        canvas.save();
                        canvas.translate(buttonsBackgroundGradientView2.getX(), buttonsBackgroundGradientView2.getY());
                        buttonsBackgroundGradientView2.draw(canvas);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(buttonsBackgroundGradientView.getX(), buttonsBackgroundGradientView.getY());
                        buttonsBackgroundGradientView.draw(canvas);
                        canvas.restore();

                        canvas.save();
                        canvas.translate(buttonsContainer.getX(), buttonsContainer.getY());
                        buttonsContainer.draw(canvas);
                        canvas.restore();

                        for (int i = 0; i < 2; i++) {
                            if (undoView[i].getVisibility() == View.VISIBLE) {
                                canvas.save();
                                canvas.translate(undoView[1].getX(), undoView[1].getY());
                                undoView[1].draw(canvas);
                                canvas.restore();
                            }
                        }

                        canvas.restore();
                    }
                } else {
                    if (scrimView != null) {
                        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);

                        float listTop = listView.getY();
                        float listBottom = listView.getY() + listView.getMeasuredHeight();

                        if (hasScrimAnchorView) {
                            int count = listView.getChildCount();
                            for (int num = 0; num < count; num++) {
                                View child = listView.getChildAt(num);
                                if (child != scrimView) {
                                    continue;
                                }

                                float viewClipLeft = Math.max(listView.getLeft(), listView.getLeft() + child.getX());
                                float viewClipTop = Math.max(listTop, listView.getY() + child.getY());
                                float viewClipRight = Math.min(listView.getRight(), listView.getLeft() + child.getX() + child.getMeasuredWidth());
                                float viewClipBottom = Math.min(listView.getY() + listView.getMeasuredHeight(), listView.getY() + child.getY() + scrimView.getClipHeight());

                                if (viewClipTop < viewClipBottom) {
                                    if (child.getAlpha() != 1f) {
                                        canvas.saveLayerAlpha(viewClipLeft, viewClipTop, viewClipRight, viewClipBottom, (int) (255 * child.getAlpha()), Canvas.ALL_SAVE_FLAG);
                                    } else {
                                        canvas.save();
                                    }

                                    canvas.clipRect(viewClipLeft, viewClipTop, viewClipRight, getMeasuredHeight());
                                    canvas.translate(listView.getLeft() + child.getX(), listView.getY() + child.getY());
                                    float progress = scrimPaint.getAlpha() / 100.0f;
                                    float pr = 1.0f - CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - progress);
                                    int h = (int) (scrimView.getMeasuredHeight() + (scrimView.getClipHeight() - scrimView.getMeasuredHeight()) * pr);
                                    rect.set(0, 0, child.getMeasuredWidth(), h);
                                    scrimView.setAboutVisibleProgress(listViewBackgroundPaint.getColor(), progress);
                                    canvas.drawRoundRect(rect, dp(13), dp(13), listViewBackgroundPaint);
                                    child.draw(canvas);
                                    canvas.restore();
                                }
                            }
                        } else if (scrimFullscreenView != null) {
                            canvas.save();
                            float x = scrimFullscreenView.getX() + fullscreenUsersListView.getX() + renderersContainer.getX();
                            float y = scrimFullscreenView.getY() + fullscreenUsersListView.getY() + renderersContainer.getY();
                            canvas.translate(x, y);
                            if (scrimFullscreenView.getRenderer() != null && scrimFullscreenView.getRenderer().isAttached() && !scrimFullscreenView.getRenderer().showingInFullscreen) {
                                scrimFullscreenView.getRenderer().draw(canvas);
                            } else {
                                scrimFullscreenView.draw(canvas);
                            }
                            scrimFullscreenView.drawOverlays(canvas);
                            canvas.restore();
                        } else if (scrimRenderer != null && scrimRenderer.isAttached()) {
                            canvas.save();
                            float x = scrimRenderer.getX() + renderersContainer.getX();
                            float y = scrimRenderer.getY() + renderersContainer.getY();
                            canvas.translate(x, y);
                            scrimRenderer.draw(canvas);
                            canvas.restore();
                        }
                    }
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (!isTabletMode && renderersContainer.progressToFullscreenMode == 1f && (child == actionBar || child == actionBarShadow || child == actionBarBackground || child == titleTextView || child == menuItemsContainer || child == textureLightningView)) {
                    return true;
                }
                if (drawingForBlur && child == renderersContainer) {
                    canvas.save();
                    canvas.translate(renderersContainer.getX() + fullscreenUsersListView.getX(), renderersContainer.getY() + fullscreenUsersListView.getY());
                    fullscreenUsersListView.draw(canvas);
                    canvas.restore();
                    return true;
                }
                if (child == avatarPreviewContainer || child == scrimPopupLayout || child == scrimView) {
                    return true;
                }
                if (contentFullyOverlayed && useBlur) {
                    if (child == listView || child == buttonsContainer) {
                        return true;
                    }
                }
                if (scrimFullscreenView == null && !drawingForBlur && avatarsPreviewShowed && (child == buttonsBackgroundGradientView2 || child == buttonsBackgroundGradientView || child == buttonsContainer || child == undoView[0] || child == undoView[1])) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (scrimView != null && keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissAvatarPreview(true);
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        containerView.setFocusable(true);
        containerView.setFocusableInTouchMode(true);
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.setKeepScreenOn(true);
        containerView.setClipChildren(false);


        if (schedulePeer != null) {
            scheduleStartInTextView = new SimpleTextView(context);
            scheduleStartInTextView.setGravity(Gravity.CENTER);
            scheduleStartInTextView.setTextColor(0xffffffff);
            scheduleStartInTextView.setTypeface(AndroidUtilities.bold());
            scheduleStartInTextView.setTextSize(18);
            scheduleStartInTextView.setText(getString(R.string.VoipChatStartsIn));
            containerView.addView(scheduleStartInTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 21, 0, 21, 231 + 80)); //-80

            scheduleTimeTextView = new SimpleTextView(context) {

                private LinearGradient linearGradient;
                private int lastTextWidth;
                private Matrix matrix = new Matrix();
                private float targetX = -1f;
                private float startX;
                private float duration;
                private float time;
                private float gradientWidth;
                private long lastUpdateTime;

                private void setTarget() {
                    targetX = 0.20f * (Utilities.random.nextInt(100) - 50) / 50.0f;
                }

                @Override
                protected boolean createLayout(int width) {
                    boolean result = super.createLayout(width);
                    int w = getTextWidth();
                    if (w != lastTextWidth) {
                        gradientWidth = w * 1.3f;
                        linearGradient = new LinearGradient(0, getTextHeight(), w * 2.0f, 0, new int[]{Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2)}, new float[]{0.0f, 0.38f, 0.76f, 1.0f}, Shader.TileMode.CLAMP);
                        getPaint().setShader(linearGradient);
                        lastTextWidth = w;
                    }
                    return result;
                }

                @Override
                protected void onDraw(Canvas canvas) {
                    float moveProgress = 0.0f;
                    if (linearGradient != null) {
                        if (call != null && call.isScheduled()) {
                            long diff = ((long) call.call.schedule_date) * 1000 - accountInstance.getConnectionsManager().getCurrentTimeMillis();
                            if (diff < 0) {
                                moveProgress = 1.0f;
                            } else if (diff < 5000) {
                                moveProgress = 1.0f - diff / 5000.0f;
                            }
                        }
                        matrix.reset();
                        matrix.postTranslate(-lastTextWidth * 0.7f * moveProgress, 0);

                        long newTime = SystemClock.elapsedRealtime();
                        long dt = newTime - lastUpdateTime;
                        if (dt > 20) {
                            dt = 17;
                        }
                        lastUpdateTime = newTime;
                        if (duration == 0 || time >= duration) {
                            duration = Utilities.random.nextInt(200) + 1500;
                            time = 0;
                            if (targetX == -1f) {
                                setTarget();
                            }
                            startX = targetX;
                            setTarget();
                        }
                        time += dt * (0.5f + BlobDrawable.GRADIENT_SPEED_MIN) + dt * (BlobDrawable.GRADIENT_SPEED_MAX * 2) * amplitude;
                        if (time > duration) {
                            time = duration;
                        }
                        float interpolation = CubicBezierInterpolator.EASE_OUT.getInterpolation(time / duration);
                        float x = gradientWidth * (startX + (targetX - startX) * interpolation) - gradientWidth / 2;
                        matrix.postTranslate(x, 0);

                        linearGradient.setLocalMatrix(matrix);
                        invalidate();
                    }
                    super.onDraw(canvas);
                }
            };
            scheduleTimeTextView.setGravity(Gravity.CENTER);
            scheduleTimeTextView.setTextColor(0xffffffff);
            scheduleTimeTextView.setTypeface(AndroidUtilities.bold());
            scheduleTimeTextView.setTextSize(60);
            containerView.addView(scheduleTimeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 21, 0, 21, 231));

            scheduleStartAtTextView = new SimpleTextView(context);
            scheduleStartAtTextView.setGravity(Gravity.CENTER);
            scheduleStartAtTextView.setTextColor(0xffffffff);
            scheduleStartAtTextView.setTypeface(AndroidUtilities.bold());
            scheduleStartAtTextView.setTextSize(18);
            containerView.addView(scheduleStartAtTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 21, 0, 21, 231 - 30)); //+ 30
        }
        if (isRtmpStream()) {
            textureLightningView = new LightningView(context);
            containerView.addView(textureLightningView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.TOP | Gravity.LEFT, 0, 44, 0, 0));
        }

        listView = new RecyclerListView(context) {

            private final LongSparseIntArray visiblePeerTmp = new LongSparseIntArray();

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == scrimView) {
                    return false;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                float inMaxBottom = 0;
                float inMinTop = Float.MAX_VALUE;

                boolean animateBackground = itemAnimator.outMinTop != Float.MAX_VALUE;
                visiblePeerTmp.clear();
                for (int i = 0; i < visiblePeerIds.size(); i++){
                    visiblePeerTmp.put(visiblePeerIds.keyAt(i), 1);
                }
                visiblePeerIds.clear();
                boolean visiblePeersChanged = false;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    View child = getChildAt(a);
                    ViewHolder holder = findContainingViewHolder(child);
                    if (
                        holder == null ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_LAST_PADDING ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_GRID ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_VIDEO_GRID_DIVIDER ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_VIDEO_NOT_AVAILABLE ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_ENCRYPTION
                    ) {
                        continue;
                    }

                    if (holder.getItemViewType() == 1 && holder.itemView instanceof GroupCallUserCell) {
                        GroupCallUserCell userCell = (GroupCallUserCell) holder.itemView;
                        visiblePeerIds.append(userCell.getPeerId(), 1);
                        if (visiblePeerTmp.get(userCell.getPeerId(), 0) == 0) {
                            visiblePeersChanged = true;
                        } else {
                            visiblePeerTmp.delete(userCell.getPeerId());
                        }
                    }
                    if (animateBackground) {
                        if (!itemAnimator.removingHolders.contains(holder)) {
                            inMinTop = Math.min(inMinTop, Math.max(0, child.getTop()));
                            inMaxBottom = Math.max(inMaxBottom, child.getBottom());
                        }
                    } else {
                        inMaxBottom = Math.max(inMaxBottom, child.getY() + child.getMeasuredHeight());
                        inMinTop = Math.min(inMinTop, Math.max(0, child.getY()));
                    }
                }

                if (visiblePeerTmp.size() > 0) {
                    visiblePeersChanged = true;
                }

                if (visiblePeersChanged) {
                    updateSubtitle();
                }

                float minTop, maxBottom;
                if (animateBackground) {
                    minTop = itemAnimator.outMinTop * (1f - itemAnimator.animationProgress) + inMinTop * (itemAnimator.animationProgress);
                    maxBottom = itemAnimator.outMaxBottom * (1f - itemAnimator.animationProgress) + inMaxBottom * (itemAnimator.animationProgress);
                } else {
                    minTop = inMinTop;
                    maxBottom = inMaxBottom;
                }

                if (inMinTop != Float.MAX_VALUE) {
                    int itemsWidth = AndroidUtilities.isTablet() ? Math.min(dp(420), getMeasuredWidth()) : getMeasuredWidth();
                    int padding = (getMeasuredWidth() - itemsWidth) >> 1;
                    rect.set(padding, minTop, getMeasuredWidth() - padding, Math.min(getMeasuredHeight() - getTranslationY(), maxBottom));
                    canvas.drawRoundRect(rect, dp(13), dp(13), listViewBackgroundPaint);
                }

                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            public void setVisibility(int visibility) {
                if (getVisibility() != visibility) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        if (child instanceof GroupCallGridCell) {
                            attachRenderer((GroupCallGridCell) child, child.isAttachedToWindow() && visibility == View.VISIBLE);
                        }
                    }
                }
                super.setVisibility(visibility);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                itemAnimator.updateBackgroundBeforeAnimation();
            }
        };
        listView.setClipToPadding(false);
        listView.setClipChildren(false);
        itemAnimator = new GroupCallItemAnimator();
        itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        itemAnimator.setRemoveDuration(TRANSITION_DURATION);
        itemAnimator.setAddDuration(TRANSITION_DURATION);
        itemAnimator.setMoveDuration(TRANSITION_DURATION);
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() <= 0 || call == null) {
                    return;
                }
                if (!call.loadingMembers && !call.membersLoadEndReached && layoutManager.findLastVisibleItemPosition() > listAdapter.getItemCount() - 5) {
                    call.loadMembers(false);
                }
                updateLayout(true);
                if (textureLightningView != null) {
                    textureLightningView.invalidate();
                }
                containerView.invalidate();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = dp(74);
                    float top = scrollOffsetY - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() && listView.canScrollVertically(1)) {
                        View child = listView.getChildAt(0);
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > 0) {
                            listView.smoothScrollBy(0, holder.itemView.getTop());
                        }
                    }
                } else {
                    if (recordHintView != null) {
                        recordHintView.hide();
                    }
                    if (reminderHintView != null) {
                        reminderHintView.hide();
                    }
                }
            }
        });

        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new FillLastGridLayoutManager(getContext(), isLandscapeMode ? 6 : 2, LinearLayoutManager.VERTICAL, false, 0, listView));
        layoutManager.setSpanSizeLookup(spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int spanSize = isLandscapeMode ? 6 : 2;
                if (!isTabletMode && position >= listAdapter.usersVideoGridStartRow && position < listAdapter.usersVideoGridEndRow) {
                    int spanCount = 1;
                    int size = listAdapter.usersVideoGridEndRow - listAdapter.usersVideoGridStartRow;
                    if (position == listAdapter.usersVideoGridEndRow - 1) {
                        if (isLandscapeMode) {
                            spanCount = 2;
                        } else if (size % 2 == 0) {
                            spanCount = 1;
                        } else {
                            spanCount = 2;
                        }
                    }
                    if (isLandscapeMode) {
                        if (size == 1) {
                            return 6;
                        } else if (size == 2) {
                            return 3;
                        } else {
                            return 2;
                        }
                    } else {
                        return spanCount;
                    }
                }
                return spanSize;
            }
        });
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(view);
                if (position >= 0) {
                    outRect.setEmpty();
                    if (position >= listAdapter.usersVideoGridStartRow && position < listAdapter.usersVideoGridEndRow) {
                        int userPosition = position - listAdapter.usersVideoGridStartRow;
                        int cellCount = isLandscapeMode ? 6 : 2;
                        int index = userPosition % cellCount;
                        if (index == 0) {
                            outRect.right = dp(2);
                        } else if (index == cellCount - 1) {
                            outRect.left = dp(2);
                        } else {
                          //  outRect.right = AndroidUtilities.dp(0.5f);
                             outRect.left =  dp(1f);
                        }
                    }
                }
            }
        });
        layoutManager.setBind(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 14, 14, 14, 231));
        listView.setAdapter(listAdapter);
        listView.setTopBottomSelectorRadius(13);
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_voipgroup_listSelector));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof GroupCallGridCell) {
                fullscreenFor(((GroupCallGridCell) view).getParticipant());
            } else if (view instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) view;
                showMenuForCell(cell);
            } else if (view instanceof GroupCallInvitedCell) {
                GroupCallInvitedCell cell = (GroupCallInvitedCell) view;
                if (cell.getUser() == null) {
                    return;
                }

                if (isConference()) {
                    Long uid;
                    boolean isShady;
                    if (position - listAdapter.shadyJoinStartRow >= 0 && position - listAdapter.shadyJoinStartRow < call.shadyJoinParticipants.size()) {
                        uid = call.shadyJoinParticipants.get(position - listAdapter.shadyJoinStartRow);
                        isShady = true;
                    } else if (position - listAdapter.shadyLeftStartRow >= 0 && position - listAdapter.shadyLeftStartRow < call.shadyLeftParticipants.size()) {
                        uid = call.shadyLeftParticipants.get(position - listAdapter.shadyLeftStartRow);
                        isShady = true;
                    } else {
                        isShady = false;
                        int row = position - listAdapter.invitedStartRow;
                        if (delayedGroupCallUpdated) {
                            if (row >= 0 && row < oldInvited.size()) {
                                uid = oldInvited.get(row);
                            } else {
                                uid = null;
                            }
                        } else {
                            if (row >= 0 && row < call.invitedUsers.size()) {
                                uid = call.invitedUsers.get(row);
                            } else {
                                uid = null;
                            }
                        }
                    }
                    if (isShady) {
                        return;
                    }
                    final ChatObject.Call.InvitedUser invited = call.invitedUsersMessageIds.get(uid);
                    if (invited == null) {
                        return;
                    }

                    ItemOptions.makeOptions(container, new DarkBlueThemeResourcesProvider(), cell)
                        .addIf(invited.isCalling(), R.drawable.msg_endcall, getString(R.string.GroupCallStopCallingInvite), () -> {
                            final TL_phone.declineConferenceCallInvite req = new TL_phone.declineConferenceCallInvite();
                            req.msg_id = invited.msg_id;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                                if (res instanceof TLRPC.Updates) {
                                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                                }
                            });
                            if (call != null) {
                                invited.calling = false;
                                call.invitedUsersMessageIds.put(uid, invited);
                                applyCallParticipantUpdates(true);
                            }
                        })
                        .add(R.drawable.msg_remove, getString(R.string.GroupCallDiscardInvite), () -> {
                            final TL_phone.declineConferenceCallInvite req = new TL_phone.declineConferenceCallInvite();
                            req.msg_id = invited.msg_id;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                                if (res instanceof TLRPC.Updates) {
                                    MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) res, false);
                                }
                            });
                            final ArrayList<Integer> ids = new ArrayList<>();
                            ids.add(invited.msg_id);
                            MessagesController.getInstance(currentAccount).deleteMessages(ids, null, null, 0, 0, true, 0);
                            if (call != null) {
                                call.invitedUsers.remove(uid);
                                call.invitedUsersMap.remove(uid);
                                call.invitedUsersMessageIds.remove(uid);
                                applyCallParticipantUpdates(true);
                            }
                        })
                        .setScrimViewBackground(Theme.createRoundRectDrawable(dp(8), dp(8), listViewBackgroundPaint.getColor()))
                        .setDimAlpha(0x60)
                        .show();
                    return;
                }

                parentActivity.switchToAccount(currentAccount, true);

                Bundle args = new Bundle();
                args.putLong("user_id", cell.getUser().id);
                if (cell.hasAvatarSet()) {
                    args.putBoolean("expandPhoto", true);
                }
                parentActivity.presentFragment(new ProfileActivity(args));
                dismiss();
            } else if (position == listAdapter.addMemberRow) {
                if (ChatObject.isChannel(currentChat) && currentChat != null && !currentChat.megagroup && ChatObject.isPublic(currentChat)) {
                    getLink(false);
                    return;
                }
                TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(getChatId());
                if (chatFull == null) {
                    return;
                }
                enterEventSent = false;
                groupVoipInviteAlert = new GroupVoipInviteAlert(getContext(), accountInstance.getCurrentAccount(), currentChat, chatFull, call.participants, call.invitedUsersMap);
                groupVoipInviteAlert.setOnDismissListener(dialog -> groupVoipInviteAlert = null);
                groupVoipInviteAlert.setDelegate(new GroupVoipInviteAlert.GroupVoipInviteAlertDelegate() {
                    @Override
                    public void copyInviteLink() {
                        getLink(true);
                    }

                    @Override
                    public void inviteUser(long id) {
                        inviteUserToCall(id, true);
                    }

                    @Override
                    public void needOpenSearch(MotionEvent ev, EditTextBoldCursor editText) {
                        if (!enterEventSent) {
                            if (ev.getX() > editText.getLeft() && ev.getX() < editText.getRight()
                                    && ev.getY() > editText.getTop() && ev.getY() < editText.getBottom()) {
                                makeFocusable(groupVoipInviteAlert, null, editText, true);
                            } else {
                                makeFocusable(groupVoipInviteAlert, null, editText, false);
                            }
                        }
                    }
                });
                groupVoipInviteAlert.show();
            } else if (position == listAdapter.conferenceAddPeopleRow) {
                if (call == null || call.call == null) return;
                new UserSelectorBottomSheet(context, currentAccount, 0, null, UserSelectorBottomSheet.TYPE_CALL, true, new DarkBlueThemeResourcesProvider())
                    .exceptUsers(call != null ? call.sortedParticipants.stream().map(p -> DialogObject.getPeerDialogId(p.peer)).collect(Collectors.toSet()) : null)
                    .setOnShareCallLinkListener(this::openShareConferenceLink)
                    .setOnUsersSelector((video, users) -> {
                        if (call == null || call.call == null) return;
                        final String link = call.call.invite_link;
                        final int totalCount = users.size();
                        final AtomicInteger invitedCount = new AtomicInteger(0);
                        final HashSet<Long> restrictedUsers = new HashSet<>();
                        for (final long userId : users) {
                            call.addInvitedUser(userId);

                            final TL_phone.inviteConferenceCallParticipant req = new TL_phone.inviteConferenceCallParticipant();
                            req.call = new TLRPC.TL_inputGroupCall();
                            req.call.id = call.call.id;
                            req.call.access_hash = call.call.access_hash;
                            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(userId);
                            req.video = video;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                                if (res instanceof TLRPC.Updates) {
                                    final TLRPC.Updates updates = (TLRPC.Updates) res;
                                    MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                                    AndroidUtilities.runOnUIThread(() -> {
                                        int msg_id = 0;
                                        if (updates.update instanceof TLRPC.TL_updateNewMessage) {
                                            final TLRPC.TL_updateNewMessage updateNewMessage = (TLRPC.TL_updateNewMessage) updates.update;
                                            if (updateNewMessage.message != null && updateNewMessage.message.action instanceof TLRPC.TL_messageActionConferenceCall) {
                                                msg_id = updateNewMessage.message.id;
                                            }
                                        } else if (updates.update instanceof TLRPC.TL_updateMessageID) {
                                            final TLRPC.TL_updateMessageID u = (TLRPC.TL_updateMessageID) updates.update;
                                            msg_id = u.id;
                                        } else if (updates.updates != null) {
                                            for (int i = 0; i < updates.updates.size(); ++i) {
                                                final TLRPC.Update u = updates.updates.get(i);
                                                if (u instanceof TLRPC.TL_updateNewMessage) {
                                                    final TLRPC.TL_updateNewMessage updateNewMessage = (TLRPC.TL_updateNewMessage) u;
                                                    if (updateNewMessage.message != null && updateNewMessage.message.action instanceof TLRPC.TL_messageActionConferenceCall) {
                                                        msg_id = updateNewMessage.message.id;
                                                        break;
                                                    }
                                                } else if (u instanceof TLRPC.TL_updateMessageID) {
                                                    msg_id = ((TLRPC.TL_updateMessageID) u).id;
                                                    break;
                                                }
                                            }
                                        }
                                        if (call != null && msg_id != 0) {
                                            call.invitedUsersMessageIds.put(userId, ChatObject.Call.InvitedUser.make(msg_id));
                                            applyCallParticipantUpdates(true);
                                        }
                                    });
                                } else if (err != null && "USER_PRIVACY_RESTRICTED".equalsIgnoreCase(err.text)) {
                                    restrictedUsers.add(userId);
                                }
                                if (invitedCount.incrementAndGet() == totalCount && !restrictedUsers.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(() -> {
                                        final TL_account.getRequirementsToContact req3 = new TL_account.getRequirementsToContact();
                                        ArrayList<TLRPC.User> users2 = new ArrayList<>();
                                        ArrayList<Long> premiumBlockedUsers = new ArrayList<>();
                                        ArrayList<Long> premiumInviteBlockedUsers = new ArrayList<>();
                                        for (long userId1 : restrictedUsers) {
                                            if (groupCall != null) {
                                                groupCall.removeInvitedUser(userId1);
                                            }
                                            users2.add(MessagesController.getInstance(currentAccount).getUser(userId1));
                                            req3.id.add(MessagesController.getInstance(currentAccount).getInputUser(userId1));
                                        }
                                        final Runnable showLimitSheet = () -> {
                                            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
                                            if (fragment == null) return;
                                            final LimitReachedBottomSheet restricterdUsersBottomSheet = new LimitReachedBottomSheet(fragment, fragment.getContext(), LimitReachedBottomSheet.TYPE_CALL_RESTRICTED, currentAccount, new DarkBlueThemeResourcesProvider());
                                            restricterdUsersBottomSheet.setRestrictedUsers(null, users2, premiumBlockedUsers, premiumInviteBlockedUsers, link);
                                            restricterdUsersBottomSheet.show();
                                        };
                                        if (UserConfig.getInstance(currentAccount).isPremium()) {
                                            showLimitSheet.run();
                                        } else {
                                            ConnectionsManager.getInstance(currentAccount).sendRequest(req3, (res3, err3) -> AndroidUtilities.runOnUIThread(() -> {
                                                if (res3 instanceof Vector) {
                                                    Vector<TL_account.RequirementToContact> result = (Vector<TL_account.RequirementToContact>) res3;
                                                    for (int i = 0; i < Math.min(users2.size(), result.objects.size()); ++i) {
                                                        if (result.objects.get(i) instanceof TL_account.requirementToContactPremium) {
                                                            premiumBlockedUsers.add(users2.get(i).id);
                                                        }
                                                    }
                                                }
                                                showLimitSheet.run();
                                            }));
                                        }
                                    });
                                }
                            });
                        }
                        applyCallParticipantUpdates(true);

                        if (video) {
                            VoIPService voip = VoIPService.getSharedInstance();
                            if (voip == null) return;
                            final boolean isVideoActive = voip.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || voip.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;
                            if (isVideoActive) return;
                            // TODO: check permission
                            voip.createCaptureDevice(false);
                            if (!voip.isFrontFaceCamera()) {
                                voip.switchCamera();
                            }
                            voip.requestVideoCall(false);
                            voip.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                            voip.setMicMute(false, false, true);
                            voip.switchToSpeaker();
                            updateState(true, true);
                        }
                    })
                    .show();
            } else if (position == listAdapter.conferenceShareLinkRow) {
                openShareConferenceLink();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (isRtmpStream()) {
                return false;
            }
            if (view instanceof GroupCallGridCell)  {
                return showMenuForCell(view);
            }
            if (view instanceof GroupCallUserCell) {
                updateItems();
                GroupCallUserCell cell = (GroupCallUserCell) view;
                return cell.clickMuteButton();
            }
            return false;
        });

        if (isRtmpStream()) {
            watchersView = new WatchersView(getContext());
            containerView.addView(watchersView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 0, 0, 0));
        }

        tabletVideoGridView = new RecyclerListView(context);
        containerView.addView(tabletVideoGridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 14, 14, TABLET_LIST_SIZE + 4, 14));
        tabletVideoGridView.setAdapter(tabletGridAdapter = new GroupCallTabletGridAdapter(groupCall, currentAccount, this));
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 6, LinearLayoutManager.VERTICAL, false);
        tabletVideoGridView.setLayoutManager(gridLayoutManager);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return tabletGridAdapter.getSpanCount(position);
            }
        });

        tabletVideoGridView.setOnItemClickListener((view, position) -> {
            GroupCallGridCell cell = (GroupCallGridCell) view;
            if (cell.getParticipant() != null) {
                fullscreenFor(cell.getParticipant());
            }
        });
        DefaultItemAnimator tabletGridItemAnimator = new DefaultItemAnimator();
        tabletGridItemAnimator.setDelayAnimations(false);
        tabletGridItemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        tabletGridItemAnimator.setRemoveDuration(TRANSITION_DURATION);
        tabletGridItemAnimator.setAddDuration(TRANSITION_DURATION);
        tabletGridItemAnimator.setMoveDuration(TRANSITION_DURATION);
        tabletGridItemAnimator = new DefaultItemAnimator() {

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView.invalidate();
                renderersContainer.invalidate();
                containerView.invalidate();
                updateLayout(true);
            }
        };
        tabletVideoGridView.setItemAnimator(tabletGridItemAnimator);
        tabletVideoGridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                containerView.invalidate();
            }
        });
        tabletGridAdapter.setVisibility(tabletVideoGridView, false, false);
        tabletVideoGridView.setVisibility(View.GONE);

        buttonsContainer = new FrameLayout(context) {

            AnimatorSet currentButtonsAnimation;
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (!isLandscapeMode) {
                    widthMeasureSpec = MeasureSpec.makeMeasureSpec(Math.min(dp(460), MeasureSpec.getSize(widthMeasureSpec)), MeasureSpec.EXACTLY);
                }
                for (int i = 0; i < 2; i++) {
                    if (isLandscapeMode && !isTabletMode) {
                        muteLabel[i].getLayoutParams().width = (int) (MeasureSpec.getSize(widthMeasureSpec) / 0.68f);
                    } else {
                        muteLabel[i].getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int cw = dp(122);
                int w = (getMeasuredWidth() - cw) / 2;
                int h = getMeasuredHeight();

                int buttonsCount = 6;
                if (cameraButton.getVisibility() != View.VISIBLE) {
                    buttonsCount--;
                }
                if (soundButton.getVisibility() != View.VISIBLE) {
                    buttonsCount--;
                }
                if (flipButton.getVisibility() != View.VISIBLE) {
                    buttonsCount--;
                }
                if (speakerButtonContainer.getVisibility() != View.VISIBLE) {
                    buttonsCount--;
                }

                if (isLandscapeMode && !isTabletMode) {
                    int part = getMeasuredHeight() / buttonsCount;

                    int y, x;

                    if (soundButton.getVisibility() == View.VISIBLE) {
                        y = part / 2 - cameraButton.getMeasuredHeight() / 2;
                        x = (getMeasuredWidth() - cameraButton.getMeasuredWidth()) >> 1;
                        cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());

                        int partOffset = buttonsCount == 4 ? part : 0;
                        y = part / 2 + partOffset - soundButton.getMeasuredHeight() / 2;
                        x = (getMeasuredWidth() - soundButton.getMeasuredWidth()) >> 1;
                        soundButton.layout(x, y, x + soundButton.getMeasuredWidth(), y + soundButton.getMeasuredHeight());
                    } else {
                        y = part / 2 - flipButton.getMeasuredHeight() / 2;
                        x = (getMeasuredWidth() - flipButton.getMeasuredWidth()) >> 1;
                        flipButton.layout(x, y, x + flipButton.getMeasuredWidth(), y + flipButton.getMeasuredHeight());

                        y = part / 2 - speakerButtonContainer.getMeasuredHeight() / 2;
                        x = (getMeasuredWidth() - speakerButtonContainer.getMeasuredWidth()) >> 1;
                        speakerButtonContainer.layout(x, y, x + speakerButtonContainer.getMeasuredWidth(), y + speakerButtonContainer.getMeasuredHeight());

                        int partOffset = buttonsCount == 4 ? part : 0;
                        y = part / 2 + partOffset - cameraButton.getMeasuredHeight() / 2;
                        x = (getMeasuredWidth() - cameraButton.getMeasuredWidth()) >> 1;
                        cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());
                    }

                    int partOffset = buttonsCount == 4 ? part * 3 : part * 2;
                    y = part / 2 + partOffset - leaveButton.getMeasuredHeight() / 2;
                    x = (getMeasuredWidth() - leaveButton.getMeasuredWidth()) >> 1;
                    leaveButton.layout(x, y, x + leaveButton.getMeasuredWidth(), y + leaveButton.getMeasuredHeight());

                    partOffset = buttonsCount == 4 ? part * 2 : part;
                    y = part / 2 + partOffset - muteButton.getMeasuredWidth() / 2 - dp(4);
                    x = (getMeasuredWidth() - muteButton.getMeasuredWidth()) >> 1;
                    if (buttonsCount == 3) {
                        y -= dp(6);
                    }
                    muteButton.layout(x, y, x + muteButton.getMeasuredWidth(), y + muteButton.getMeasuredHeight());
                    minimizeButton.layout(x, y, x + minimizeButton.getMeasuredWidth(), y + minimizeButton.getMeasuredHeight());
                    expandButton.layout(x, y, x + expandButton.getMeasuredWidth(), y + expandButton.getMeasuredHeight());

                    float muteButtonScale = dp(52) / (float) (muteButton.getMeasuredWidth() - dp(8));
                    muteButton.animate().cancel();
                    muteButton.setScaleX(muteButtonScale);
                    muteButton.setScaleY(muteButtonScale);

                    for (int a = 0; a < 2; a++) {
                        x = (getMeasuredWidth() - muteLabel[a].getMeasuredWidth()) >> 1;
                        partOffset = buttonsCount == 4 ? part * 2 : part;

                        y = part / 2 + partOffset - muteButton.getMeasuredWidth() / 2 - dp(4);
                        if (buttonsCount == 3) {
                            y -= dp(6);
                        }
                        y += muteButton.getMeasuredWidth() * 0.687f + dp(4);
                        if (y + muteLabel[a].getMeasuredHeight() > partOffset + part) {
                            y -= dp(4);
                        }

                        muteLabel[a].layout(x, y, x + muteLabel[a].getMeasuredWidth(), y + muteLabel[a].getMeasuredHeight());
                        muteLabel[a].setScaleX(0.687f);
                        muteLabel[a].setScaleY(0.687f);
                    }
                } else if (renderersContainer.inFullscreenMode && !isTabletMode || isRtmpStream()) {
                    int part = getMeasuredWidth() / buttonsCount;
                    boolean isRtmpNotFullMode = !renderersContainer.inFullscreenMode && isRtmpStream();
                    int yOffset = (isRtmpNotFullMode) ? dp(27) : 0;
                    int x, y;
                    if (soundButton.getVisibility() == View.VISIBLE) {
                        x = part / 2 - cameraButton.getMeasuredWidth() / 2;
                        y = getMeasuredHeight() - cameraButton.getMeasuredHeight();
                        cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());

                        int partOffset = buttonsCount == 4 ? part : 0;
                        x = part / 2 + partOffset - leaveButton.getMeasuredWidth() / 2;
                        y = getMeasuredHeight() - soundButton.getMeasuredHeight() - yOffset;
                        soundButton.layout(x, y, x + soundButton.getMeasuredWidth(), y + soundButton.getMeasuredHeight());
                    } else {
                        int partOffset = buttonsCount == 4 ? part : 0;
                        x = part / 2 + partOffset - cameraButton.getMeasuredWidth() / 2;
                        y = getMeasuredHeight() - cameraButton.getMeasuredHeight();
                        cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());

                        x = part / 2 - flipButton.getMeasuredWidth() / 2;
                        y = getMeasuredHeight() - flipButton.getMeasuredHeight();
                        flipButton.layout(x, y, x + flipButton.getMeasuredWidth(), y + flipButton.getMeasuredHeight());

                        x = part / 2 - speakerButtonContainer.getMeasuredWidth() / 2;
                        y = getMeasuredHeight() - speakerButtonContainer.getMeasuredHeight();
                        speakerButtonContainer.layout(x, y, x + speakerButtonContainer.getMeasuredWidth(), y + speakerButtonContainer.getMeasuredHeight());
                    }

                    int partOffset = buttonsCount == 4 ? part * 3 : part * 2;
                    x = part / 2 + partOffset - leaveButton.getMeasuredWidth() / 2;
                    y = getMeasuredHeight() - leaveButton.getMeasuredHeight() - yOffset;
                    leaveButton.layout(x, y, x + leaveButton.getMeasuredWidth(), y + leaveButton.getMeasuredHeight());


                    partOffset = buttonsCount == 4 ? part * 2 : part;
                    x = part / 2 + partOffset - muteButton.getMeasuredWidth() / 2;
                    y = getMeasuredHeight() - leaveButton.getMeasuredHeight() - (muteButton.getMeasuredWidth() - dp(52)) / 2 - yOffset;
                    muteButton.layout(x, y, x + muteButton.getMeasuredWidth(), y + muteButton.getMeasuredHeight());
                    minimizeButton.layout(x, y, x + minimizeButton.getMeasuredWidth(), y + minimizeButton.getMeasuredHeight());
                    expandButton.layout(x, y, x + expandButton.getMeasuredWidth(), y + expandButton.getMeasuredHeight());

                    float muteButtonScale = dp(52) / (float) (muteButton.getMeasuredWidth() - dp(8));
                    muteButton.animate().scaleX(muteButtonScale).scaleY(muteButtonScale).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();

                    for (int a = 0; a < 2; a++) {
                        partOffset = buttonsCount == 4 ? part * 2 : part;
                        x = partOffset + (part - muteLabel[a].getMeasuredWidth()) / 2;
                        y = h - dp(27);
                        if (isRtmpNotFullMode) {
                            if (renderersContainer.inFullscreenMode) {
                                y -= dp(2);
                            } else {
                                y -= dp(25);
                            }
                        }
                        muteLabel[a].layout(x, y, x + muteLabel[a].getMeasuredWidth(), y + muteLabel[a].getMeasuredHeight());
                        muteLabel[a].animate().scaleX(0.687f).scaleY(0.687f).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                } else {
                    int x, y;
                    int buttonsYOffset = dp(0);
                    if (soundButton.getVisibility() == View.VISIBLE) {
                        if (cameraButton.getVisibility() == View.VISIBLE) {
                            x = (w - cameraButton.getMeasuredWidth()) / 2;
                            y = (h - cameraButton.getMeasuredHeight()) / 2;// - AndroidUtilities.dp(32);
                            cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());

                            x = (w - soundButton.getMeasuredWidth()) / 2;
                            y = (h - leaveButton.getMeasuredHeight()) / 2;// + AndroidUtilities.dp(32);
                            soundButton.layout(x, y, x + soundButton.getMeasuredWidth(), y + soundButton.getMeasuredHeight());
                        } else {
                            x = (w - soundButton.getMeasuredWidth()) / 2;
                            y = (h - soundButton.getMeasuredHeight()) / 2;
                            soundButton.layout(x, y, x + soundButton.getMeasuredWidth(), y + soundButton.getMeasuredHeight());
                        }
                    } else {
                        int offset = flipButton.getVisibility() == View.VISIBLE || speakerButtonContainer.getVisibility() == View.VISIBLE ? dp(28) : 0;

                        x = (w - flipButton.getMeasuredWidth()) / 2;
                        y = (h - flipButton.getMeasuredHeight()) / 2 + buttonsYOffset - offset;
                        flipButton.layout(x, y, x + flipButton.getMeasuredWidth(), y + flipButton.getMeasuredHeight());

                        x = (w - speakerButtonContainer.getMeasuredWidth()) / 2;
                        y = (h - speakerButtonContainer.getMeasuredHeight()) / 2 + buttonsYOffset - offset;
                        speakerButtonContainer.layout(x, y, x + speakerButtonContainer.getMeasuredWidth(), y + speakerButtonContainer.getMeasuredHeight());

                        x = (w - cameraButton.getMeasuredWidth()) / 2;
                        y = (h - cameraButton.getMeasuredHeight()) / 2 + buttonsYOffset + offset;
                        cameraButton.layout(x, y, x + cameraButton.getMeasuredWidth(), y + cameraButton.getMeasuredHeight());
                    }

                    y = (h - leaveButton.getMeasuredHeight()) / 2 + buttonsYOffset;
                    x = getMeasuredWidth() - w + (w - leaveButton.getMeasuredWidth()) / 2;
                    leaveButton.layout(x, y, x + leaveButton.getMeasuredWidth(), y + leaveButton.getMeasuredHeight());

                    x = (getMeasuredWidth() - muteButton.getMeasuredWidth()) / 2;
                    y = (h - muteButton.getMeasuredHeight()) / 2 - dp(9);
                    muteButton.layout(x, y, x + muteButton.getMeasuredWidth(), y + muteButton.getMeasuredHeight());
                    minimizeButton.layout(x, y, x + minimizeButton.getMeasuredWidth(), y + minimizeButton.getMeasuredHeight());
                    expandButton.layout(x, y, x + expandButton.getMeasuredWidth(), y + expandButton.getMeasuredHeight());

                    muteButton.animate().setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).scaleX(1f).scaleY(1f).start();

                    for (int a = 0; a < 2; a++) {
                        x = (getMeasuredWidth() - muteLabel[a].getMeasuredWidth()) / 2;
                        y = h - dp(12) - muteLabel[a].getMeasuredHeight();
                        muteLabel[a].layout(x, y, x + muteLabel[a].getMeasuredWidth(), y + muteLabel[a].getMeasuredHeight());
                        muteLabel[a].animate().scaleX(1f).scaleY(1f).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                    }
                }

                if (animateButtonsOnNextLayout) {
                    AnimatorSet animatorSet = new AnimatorSet();
                    boolean hasAnimation = false;
                    for (int i = 0; i < getChildCount(); i++) {
                        View child = getChildAt(i);
                        Float fromX = buttonsAnimationParamsX.get(child);
                        Float fromY = buttonsAnimationParamsY.get(child);
                        if (fromX != null && fromY != null) {
                            hasAnimation = true;
                            animatorSet.playTogether(ObjectAnimator.ofFloat(child, TRANSLATION_X, fromX - child.getLeft(), 0));
                            animatorSet.playTogether(ObjectAnimator.ofFloat(child, TRANSLATION_Y, fromY - child.getTop(), 0));
                        }
                    }
                    if (hasAnimation) {
                        if (currentButtonsAnimation != null) {
                            currentButtonsAnimation.removeAllListeners();
                            currentButtonsAnimation.cancel();
                        }
                        currentButtonsAnimation = animatorSet;
                        animatorSet.setDuration(TRANSITION_DURATION);
                        animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                currentButtonsAnimation = null;
                                for (int i = 0; i < getChildCount(); i++) {
                                    View child = getChildAt(i);
                                    child.setTranslationX(0);
                                    child.setTranslationY(0);
                                }
                            }
                        });
                        animatorSet.start();
                    }
                    buttonsAnimationParamsX.clear();
                    buttonsAnimationParamsY.clear();
                }
                animateButtonsOnNextLayout = false;
            }

            final OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.5f);
            int currentLightColor;

            @SuppressLint("DrawAllocation")
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (contentFullyOverlayed && useBlur) {
                    return;
                }
                int offset = (getMeasuredWidth() - getMeasuredHeight()) / 2;

                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastUpdateTime;
                lastUpdateTime = newTime;
                if (dt > 20) {
                    dt = 17;
                }

                if (currentState != null) {
                    currentState.update(0, offset, getMeasuredHeight(), dt, amplitude);
                }

                tinyWaveDrawable.minRadius = dp(62);
                tinyWaveDrawable.maxRadius = dp(62) + dp(20) * BlobDrawable.FORM_SMALL_MAX;

                bigWaveDrawable.minRadius = dp(65);
                bigWaveDrawable.maxRadius = dp(65) + dp(20) * BlobDrawable.FORM_BIG_MAX;

                if (animateToAmplitude != amplitude) {
                    amplitude += animateAmplitudeDiff * dt;
                    if (animateAmplitudeDiff > 0) {
                        if (amplitude > animateToAmplitude) {
                            amplitude = animateToAmplitude;
                        }
                    } else {
                        if (amplitude < animateToAmplitude) {
                            amplitude = animateToAmplitude;
                        }
                    }
                }

                boolean canSwitchProgress = true;
                if (prevState != null && prevState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                    radialProgressView.toCircle(true, true);
                    if (!radialProgressView.isCircle()) {
                        canSwitchProgress = false;
                    }
                } else if (prevState != null && currentState != null && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                    radialProgressView.toCircle(true, false);
                }
                if (canSwitchProgress) {
                    if (switchProgress != 1f) {
                        if (prevState != null && prevState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                            switchProgress += dt / 100f;
                        } else {
                            switchProgress += dt / 180f;
                        }

                        if (switchProgress >= 1.0f) {
                            switchProgress = 1f;
                            prevState = null;
                            if (currentState != null && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                                radialProgressView.toCircle(false, true);
                            }
                        }
                        invalidateColors = true;
                    }

                    if (invalidateColors && currentState != null) {
                        invalidateColors = false;
                        int lightingColor;
                        int soundButtonColor;
                        int soundButtonColorChecked;
                        int activeColor;
                        if (prevState != null) {
                            fillColors(prevState.currentState, colorsTmp);
                            int oldLight = colorsTmp[0];
                            int oldSound = colorsTmp[1];
                            int oldSound2 = colorsTmp[2];
                            int oldActive = colorsTmp[3];
                            fillColors(currentState.currentState, colorsTmp);
                            lightingColor = ColorUtils.blendARGB(oldLight, colorsTmp[0], switchProgress);
                            soundButtonColorChecked = ColorUtils.blendARGB(oldSound, colorsTmp[1], switchProgress);
                            soundButtonColor = ColorUtils.blendARGB(oldSound2, colorsTmp[2], switchProgress);
                            activeColor = ColorUtils.blendARGB(oldActive, colorsTmp[3], switchProgress);
                        } else {
                            fillColors(currentState.currentState, colorsTmp);
                            lightingColor = colorsTmp[0];
                            soundButtonColorChecked = colorsTmp[1];
                            soundButtonColor = colorsTmp[2];
                            activeColor = colorsTmp[3];
                        }
                        if (currentLightColor != lightingColor) {
                            radialGradient = new RadialGradient(0, 0, dp(100), new int[]{ColorUtils.setAlphaComponent(lightingColor, 60), ColorUtils.setAlphaComponent(lightingColor, 0)}, null, Shader.TileMode.CLAMP);
                            radialPaint.setShader(radialGradient);
                            currentLightColor = lightingColor;
                        }

                        soundButton.setBackgroundColor(soundButtonColor, soundButtonColorChecked);
                        cameraButton.setBackgroundColor(soundButtonColor, soundButtonColorChecked);
                        flipButton.setBackgroundColor(soundButtonColor, soundButtonColorChecked);
                        speakerButton.setBackgroundColor(soundButtonColorChecked, activeColor);
                    }

                    boolean showWaves = false;
                    boolean showLighting = false;
                    if (currentState != null) {
                        showWaves = currentState.currentState == MUTE_BUTTON_STATE_MUTE || currentState.currentState == MUTE_BUTTON_STATE_UNMUTE || isGradientState(currentState.currentState);
                        showLighting = currentState.currentState != MUTE_BUTTON_STATE_CONNECTING;
                    }

                    if (prevState != null && currentState != null && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                        showWavesProgress -= dt / 180f;
                        if (showWavesProgress < 0f) {
                            showWavesProgress = 0f;
                        }
                    } else {
                        if (showWaves && showWavesProgress != 1f) {
                            showWavesProgress += dt / 350f;
                            if (showWavesProgress > 1f) {
                                showWavesProgress = 1f;
                            }
                        } else if (!showWaves && showWavesProgress != 0) {
                            showWavesProgress -= dt / 350f;
                            if (showWavesProgress < 0f) {
                                showWavesProgress = 0f;
                            }
                        }
                    }

                    if (showLighting && showLightingProgress != 1f) {
                        showLightingProgress += dt / 350f;
                        if (showLightingProgress > 1f) {
                            showLightingProgress = 1f;
                        }
                    } else if (!showLighting && showLightingProgress != 0) {
                        showLightingProgress -= dt / 350f;
                        if (showLightingProgress < 0f) {
                            showLightingProgress = 0f;
                        }
                    }
                }

                float showWavesProgressInterpolated = overshootInterpolator.getInterpolation(GroupCallActivity.this.showWavesProgress);

                showWavesProgressInterpolated = 0.4f + 0.6f * showWavesProgressInterpolated;

                bigWaveDrawable.update(amplitude, 1f);
                tinyWaveDrawable.update(amplitude, 1f);

                if (prevState != null && currentState != null && !isRtmpStream() && (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING || prevState.currentState == MUTE_BUTTON_STATE_CONNECTING)) {
                    float progress;
                    if (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                        progress = switchProgress;
                        paint.setShader(prevState.shader);
                    } else {
                        progress = 1f - switchProgress;
                        paint.setShader(currentState.shader);
                    }

                    paintTmp.setColor(AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f));

                    int cx = (int) (muteButton.getX() + muteButton.getMeasuredWidth() / 2);
                    int cy = (int) (muteButton.getY() + muteButton.getMeasuredHeight() / 2);
                    radialMatrix.setTranslate(cx, cy);
                    radialGradient.setLocalMatrix(radialMatrix);

                    paint.setAlpha(76);

                    if (call != null) {
                        float radius = dp(52) / 2f;
                        canvas.drawCircle(leaveButton.getX() + leaveButton.getMeasuredWidth() / 2f, leaveButton.getY() + radius, radius, leaveBackgroundPaint);
                    }

                    canvas.save();
                    canvas.scale(BlobDrawable.GLOBAL_SCALE * muteButton.getScaleX(), BlobDrawable.GLOBAL_SCALE  * muteButton.getScaleY(), cx, cy);

                    canvas.save();
                    float scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * 0.5f;
                    canvas.scale(scale * showLightingProgress, scale * showLightingProgress, cx, cy);

                    float scaleLight = 0.7f + BlobDrawable.LIGHT_GRADIENT_SIZE;
                    canvas.save();
                    canvas.scale(scaleLight, scaleLight, cx, cy);
                    canvas.drawCircle(cx, cy, dp(160), radialPaint);
                    canvas.restore();
                    canvas.restore();

                    if (call != null) {
                        canvas.save();
                        scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * scheduleButtonsScale;
                        canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                        bigWaveDrawable.draw(cx, cy, canvas, paint);
                        canvas.restore();

                        canvas.save();
                        scale = BlobDrawable.SCALE_SMALL_MIN + BlobDrawable.SCALE_SMALL * amplitude * scheduleButtonsScale;
                        canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                        tinyWaveDrawable.draw(cx, cy, canvas, paint);
                        canvas.restore();
                    }

                    paint.setAlpha(255);

                    if (canSwitchProgress) {
                        canvas.drawCircle(cx, cy, dp(57), paint);
                        paint.setColor(Theme.getColor(Theme.key_voipgroup_connectingProgress));
                        if (progress != 0) {
                            paint.setAlpha((int) (255 * progress));
                            paint.setShader(null);
                            canvas.drawCircle(cx, cy, dp(57), paint);
                        }
                    }
                    canvas.drawCircle(cx, cy, dp(55) * progress, paintTmp);
                    if (!canSwitchProgress) {
                        radialProgressView.draw(canvas, cx, cy);
                    }
                    canvas.restore();
                } else {
                    for (int i = 0; i < 2; i++) {
                        float alpha;
                        float buttonRadius = dp(57);
                        if (i == 0 && prevState != null) {
                            if (!isRtmpStream()) {
                                paint.setShader(prevState.shader);
                            }
                            alpha = 1f - switchProgress;
                            if (prevState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                                buttonRadius -= alpha * dp(2);
                            }
                        } else if (i == 1 && currentState != null) {
                            if (!isRtmpStream()) {
                                paint.setShader(currentState.shader);
                            }
                            alpha = switchProgress;
                            if (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                                buttonRadius -= alpha * dp(2);
                            }
                        } else {
                            continue;
                        }
                        if (paint.getShader() == null && !isRtmpStream()) {
                            paint.setColor(AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f));
                        }

                        if (isRtmpStream()) {
                            int bgColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_rtmpButton), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f);
                            paint.setColor(bgColor);
                            paint.setShader(null);
                            soundButton.setBackgroundColor(AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f), bgColor);
                        }

                        int cx = (int) (muteButton.getX() + muteButton.getMeasuredWidth() / 2);
                        int cy = (int) (muteButton.getY() + muteButton.getMeasuredHeight() / 2);
                        radialMatrix.setTranslate(cx, cy);
                        radialGradient.setLocalMatrix(radialMatrix);

                        paint.setAlpha((int) (76 * alpha * switchToButtonProgress));
                        if (switchToButtonProgress > 0) {
                            if (i == 1) {
                                int a = leaveBackgroundPaint.getAlpha();
                                leaveBackgroundPaint.setAlpha((int) (a * switchToButtonProgress));
                                float radius = dp(52) / 2f;
                                canvas.drawCircle(leaveButton.getX() + leaveButton.getMeasuredWidth() / 2, leaveButton.getY() + radius, radius, leaveBackgroundPaint);
                                leaveBackgroundPaint.setAlpha(a);
                            }
                        }

                        canvas.save();
                        canvas.scale(BlobDrawable.GLOBAL_SCALE * muteButton.getScaleX(), BlobDrawable.GLOBAL_SCALE * muteButton.getScaleX(), cx, cy);

                        canvas.save();

                        float translation;
                        if (isLandscapeMode) {
                            translation = 0;
                        } else {
                            translation = dp(65) * (1.0f - switchToButtonInt2);
                        }
                       // cy += translation;
                        float scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * 0.5f;
                        canvas.scale(scale * showLightingProgress, scale * showLightingProgress, cx, cy);
                        if (i == 1 && !isRtmpStream() && LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
                            float scaleLight = 0.7f + BlobDrawable.LIGHT_GRADIENT_SIZE * scheduleButtonsScale;
                            canvas.save();
                            canvas.scale(scaleLight, scaleLight, cx, cy);
                            int a = radialPaint.getAlpha();
                            radialPaint.setAlpha((int) (a * switchToButtonProgress * (1f - progressToHideUi)));
                            canvas.drawCircle(cx, cy, dp(160), radialPaint);
                            radialPaint.setAlpha(a);
                            canvas.restore();
                        }
                        canvas.restore();
                        if (!isRtmpStream() && switchToButtonProgress > 0) {
                            canvas.save();
                            scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * showWavesProgressInterpolated * scheduleButtonsScale;
                            canvas.scale(scale,  scale, cx, cy);
                            bigWaveDrawable.draw(cx, cy, canvas, paint);
                            canvas.restore();

                            canvas.save();
                            scale = BlobDrawable.SCALE_SMALL_MIN + BlobDrawable.SCALE_SMALL * amplitude * showWavesProgressInterpolated * scheduleButtonsScale;
                            canvas.scale(scale, scale, cx, cy);
                            tinyWaveDrawable.draw(cx, cy, canvas, paint);
                            canvas.restore();
                        }

                        if (isLandscapeMode) {
                            if (i == 0) {
                                paint.setAlpha((int) (255 * switchToButtonInt2));
                            } else {
                                paint.setAlpha((int) (255 * alpha * switchToButtonInt2));
                            }
                        } else {
                            if (i == 0) {
                                paint.setAlpha(255);
                            } else {
                                paint.setAlpha((int) (255 * alpha));
                            }
                        }

                        if (currentButtonsAnimation == null) {
                            muteButton.setTranslationY(translation);
                        }
                        float switchButtonProgrss = isLandscapeMode ? 1f : switchToButtonInt2;
                        float startX = getMeasuredWidth() / 2 - dp(21);
                        float startY = dp(24);
                        float w = (startX + (buttonRadius - startX) * switchButtonProgrss) * scheduleButtonsScale;
                        float h = (startY + (buttonRadius  - startY) * switchButtonProgrss) * scheduleButtonsScale;

                        rect.set(cx - w, cy - h, cx + w, cy + h);
                        float rad = dp(4) + (buttonRadius - dp(4)) * switchButtonProgrss;
                        canvas.drawRoundRect(rect, rad, rad, paint);

                        if (i == 1 && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                            if (isRtmpStream()) {
                                radialProgressView.setSize((int) (rad * 2 - dp(4)));
                            }
                            radialProgressView.draw(canvas, cx, cy);
                        }

                        canvas.restore();

                        if (isLandscapeMode && switchToButtonInt2 == 0) {
                            paint.setAlpha(255);
                            float x = scheduleButtonTextView.getX() - getX();
                            float y = scheduleButtonTextView.getY() - getY();
                            rect.set(x, y, x + scheduleButtonTextView.getMeasuredWidth(),  y + scheduleButtonTextView.getMeasuredHeight());
                            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
                        }

                    }
                }
                super.dispatchDraw(canvas);
                if (!renderersContainer.isAnimating()) {
                    invalidate();
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == muteButton && child.getScaleX() != 1f) {
                    canvas.save();
                    float s = 1f / muteButton.getScaleX();
                    s = 1f + (s - 1f) * 0.2f;
                    canvas.scale(s, s, child.getX() + child.getMeasuredWidth() / 2f, child.getY() + child.getMeasuredHeight() / 2f);
                    boolean b = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return b;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };

        int color = Theme.getColor(Theme.key_voipgroup_unmuteButton2);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        radialMatrix = new Matrix();
        radialGradient = new RadialGradient(0, 0, dp(160), new int[]{Color.argb(50, r, g, b), Color.argb(0, r, g, b)}, null, Shader.TileMode.CLAMP);
        radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        radialPaint.setShader(radialGradient);

        tinyWaveDrawable = new BlobDrawable(9);
        bigWaveDrawable = new BlobDrawable(12);

        tinyWaveDrawable.minRadius = dp(62);
        tinyWaveDrawable.maxRadius = dp(72);
        tinyWaveDrawable.generateBlob();

        bigWaveDrawable.minRadius = dp(65);
        bigWaveDrawable.maxRadius = dp(75);
        bigWaveDrawable.generateBlob();

        tinyWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_unmuteButton), (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
        bigWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_unmuteButton), (int) (255 * WaveDrawable.CIRCLE_ALPHA_1)));

        soundButton = new VoIPToggleButton(context);
        soundButton.setCheckable(true);
        soundButton.setTextSize(12);
        buttonsContainer.addView(soundButton, LayoutHelper.createFrame(68, 80));
        soundButton.setOnClickListener(v -> {
            if (call == null || call.isScheduled() || isRtmpStream()) {
                getLink(false);
                return;
            }
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(getContext(), false);
        });

        cameraButton = new VoIPToggleButton(context);
        cameraButton.setCheckable(true);
        cameraButton.setTextSize(12);
        cameraButton.showText(false, false);
        cameraButton.setCrossOffset(-AndroidUtilities.dpf2(3.5f));
        cameraButton.setVisibility(View.GONE);
        buttonsContainer.addView(cameraButton, LayoutHelper.createFrame(68, 80));

        flipButton = new VoIPToggleButton(context);
        flipButton.setCheckable(true);
        flipButton.setTextSize(12);
        flipButton.showText(false, false);
        RLottieImageView flipIconView = new RLottieImageView(context);
        flipButton.addView(flipIconView, LayoutHelper.createFrame(32, 32, 0, 18, 10, 18, 0));
        flipIcon = new RLottieDrawable(R.raw.camera_flip, "" + R.raw.camera_flip, dp(24), dp(24), true, null);
        flipIconView.setAnimation(flipIcon);
        flipButton.setOnClickListener(view -> {
            renderersContainer.delayHideUi();
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
                service.switchCamera();
                if (flipIconCurrentEndFrame == 18) {
                    flipIcon.setCustomEndFrame(flipIconCurrentEndFrame = 39);
                    flipIcon.start();
                } else {
                    flipIcon.setCurrentFrame(0, false);
                    flipIcon.setCustomEndFrame(flipIconCurrentEndFrame = 18);
                    flipIcon.start();
                }

                for (int i = 0; i < attachedRenderers.size(); i++) {
                    GroupCallMiniTextureView renderer = attachedRenderers.get(i);
                    if (renderer.participant.participant.self && !renderer.participant.presentation) {
                        renderer.startFlipAnimation();
                    }
                }
            }
        });
        flipButton.setVisibility(View.GONE);
        buttonsContainer.addView(flipButton, LayoutHelper.createFrame(68, 80));

        speakerButton = new VoIPToggleButton(context);
        speakerButton.setCheckable(true);
        speakerButton.setTextSize(12);
        speakerButton.showText(false, false);
        speakerImageView = new ImageView(context);
        speakerImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        speakerImageView.setImageResource(speakerIcon = R.drawable.filled_sound_on);
        speakerImageView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        speakerImageView.setScaleX(1.11f);
        speakerImageView.setScaleY(1.11f);
        ScaleStateListAnimator.apply(speakerButton);
        speakerButton.addView(speakerImageView, LayoutHelper.createFrame(30, 30, 0, 19, 11, 18, 0));
        speakerButton.setOnClickListener(view -> {
            int newValue = getNextAudioOutputValue();
            cacheAudioOutputValue = newValue;
            updateState(true, true);
            cacheAudioOutputValue = null;
            AndroidUtilities.runOnUIThread(() -> {
                setAudioOutputValue(newValue);

                BulletinFactory.of(topBulletinContainer, new DarkBlueThemeResourcesProvider())
                    .createSimpleBulletin(getContext().getResources().getDrawable(getAudioOutputToastIcon(newValue)).mutate(), getAudioOutputToastText(newValue))
                    .show(isBulletinTop());
            });
        });
        speakerButtonContainer = new FrameLayout(context);
        speakerButtonContainer.setVisibility(View.GONE);
        speakerButtonContainer.addView(speakerButton, LayoutHelper.createFrame(68, 80));
        buttonsContainer.addView(speakerButtonContainer, LayoutHelper.createFrame(68, 80));

        leaveButton = new VoIPToggleButton(context);
        leaveButton.setDrawBackground(false);
        leaveButton.setTextSize(12);
        leaveButton.setData(R.drawable.msg_voiceclose, 0xffffffff, Theme.getColor(Theme.key_voipgroup_leaveButton), 0.3f, false, getString(R.string.VoipGroupLeave), false, false);
        buttonsContainer.addView(leaveButton, LayoutHelper.createFrame(68, 80));
        leaveButton.setOnClickListener(v -> {
            renderersContainer.delayHideUi();
            if (call == null || call.isScheduled()) {
                dismiss();
                return;
            }
            updateItems();
            onLeaveClick(context, this::dismiss, false);
        });

        muteButton = new RLottieImageView(context) {

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isRtmpStream()) {
                    return super.onTouchEvent(event);
                }
                //pinnedVideoView.delayHideUi();
                if (event.getAction() == MotionEvent.ACTION_DOWN && muteButtonState == MUTE_BUTTON_STATE_UNMUTE && call != null) {
                    AndroidUtilities.runOnUIThread(pressRunnable, 300);
                    scheduled = true;
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (scheduled) {
                        AndroidUtilities.cancelRunOnUIThread(pressRunnable);
                        scheduled = false;
                    } else if (pressed) {
                        AndroidUtilities.cancelRunOnUIThread(unmuteRunnable);
                        updateMuteButton(MUTE_BUTTON_STATE_UNMUTE, true);
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().setMicMute(true, true, false);
                            try {
                                muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            } catch (Exception ignored) {}
                        }
                        attachedRenderersTmp.clear();
                        attachedRenderersTmp.addAll(attachedRenderers);
                        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
                            attachedRenderersTmp.get(i).updateAttachState(true);
                        }
                        pressed = false;
                        MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                        super.onTouchEvent(cancel);
                        cancel.recycle();
                        return true;
                    }
                }
                return super.onTouchEvent(event);
            }

            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);

                info.setClassName(Button.class.getName());
                info.setEnabled(muteButtonState == MUTE_BUTTON_STATE_UNMUTE || muteButtonState == MUTE_BUTTON_STATE_MUTE);

                if (muteButtonState == MUTE_BUTTON_STATE_MUTE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, getString(R.string.VoipMute)));
                }
            }
        };
        muteButton.setAnimation(bigMicDrawable);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        buttonsContainer.addView(muteButton, LayoutHelper.createFrame(122, 122, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        muteButton.setOnClickListener(new View.OnClickListener() {

            Runnable finishRunnable = new Runnable() {
                @Override
                public void run() {
                    muteButton.setAnimation(bigMicDrawable);
                    playingHandAnimation = false;
                }
            };

            @SuppressLint("SourceLockedOrientationActivity")
            @Override
            public void onClick(View v) {
                if (call == null || muteButtonState == MUTE_BUTTON_STATE_CONNECTING) {
                    return;
                }
                if (isRtmpStream() && !call.isScheduled()) {
                    boolean fullscreen = renderersContainer != null && renderersContainer.inFullscreenMode && (AndroidUtilities.isTablet() || isLandscapeMode == isRtmpLandscapeMode());
                    if (fullscreen) {
                        fullscreenFor(null);
                        if (isLandscapeMode) {
                            AndroidUtilities.runOnUIThread(() -> {
                                wasNotInLayoutFullscreen = null;
                                wasExpandBigSize = null;
                                updateMuteButton(muteButtonState, true);
                            }, 200);
                        }
                        parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    } else if (!visibleVideoParticipants.isEmpty()) {
                        ChatObject.VideoParticipant participant = visibleVideoParticipants.get(0);
                        if (AndroidUtilities.isTablet()) {
                            fullscreenFor(participant);
                        } else {
                            if (isLandscapeMode == isRtmpLandscapeMode()) {
                                fullscreenFor(participant);
                            }
                            if (isRtmpLandscapeMode()) {
                                parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                            } else {
                                parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                            }
                        }
                    }
                    return;
                }
                if (muteButtonState == MUTE_BUTTON_STATE_START_NOW) {
                    if (startingGroupCall) {
                        return;
                    }
                    try {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
                    startingGroupCall = true;
                    TL_phone.startScheduledGroupCall req = new TL_phone.startScheduledGroupCall();
                    req.call = call.getInputGroupCall();
                    accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                        }
                    });
                } else if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER || muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                    if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                        if (reminderHintView != null) {
                            reminderHintView.hide();
                        }
                    }
                    TL_phone.toggleGroupCallStartSubscription req = new TL_phone.toggleGroupCallStartSubscription();
                    req.call = call.getInputGroupCall();
                    call.call.schedule_start_subscribed = !call.call.schedule_start_subscribed;
                    req.subscribed = call.call.schedule_start_subscribed;
                    accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
                        if (response != null) {
                            accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                        }
                    });
                    updateMuteButton(call.call.schedule_start_subscribed ? MUTE_BUTTON_STATE_CANCEL_REMINDER : MUTE_BUTTON_STATE_SET_REMINDER, true);
                } else {
                    if (VoIPService.getSharedInstance() == null || isStillConnecting()) {
                        return;
                    }
                    if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND) {
                        if (isConference()) {
                            return;
                        }
                        if (playingHandAnimation) {
                            return;
                        }
                        playingHandAnimation = true;
                        AndroidUtilities.shakeView(muteLabel[0]);
                        try {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                        int num = Utilities.random.nextInt(100);
                        int endFrame;
                        int startFrame;
                        if (num < 32) {
                            startFrame = 0;
                            endFrame = 120;
                        } else if (num < 64) {
                            startFrame = 120;
                            endFrame = 240;
                        } else if (num < 97) {
                            startFrame = 240;
                            endFrame = 420;
                        } else if (num == 98) {
                            startFrame = 420;
                            endFrame = 540;
                        } else {
                            startFrame = 540;
                            endFrame = 720;
                        }
                        handDrawables.setCustomEndFrame(endFrame);
                        handDrawables.setOnFinishCallback(finishRunnable, endFrame - 1);
                        muteButton.setAnimation(handDrawables);
                        handDrawables.setCurrentFrame(startFrame);
                        muteButton.playAnimation();
                        if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                            TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
                            TLObject object;
                            long peerId = MessageObject.getPeerId(participant.peer);
                            if (DialogObject.isUserDialog(peerId)) {
                                object = accountInstance.getMessagesController().getUser(peerId);
                            } else {
                                object = accountInstance.getMessagesController().getChat(-peerId);
                            }
                            VoIPService.getSharedInstance().editCallMember(object, null, null, null, true, null);
                            updateMuteButton(MUTE_BUTTON_STATE_RAISED_HAND, true);
                        }
                    } else if (muteButtonState == MUTE_BUTTON_STATE_UNMUTE) {
                        if (Build.VERSION.SDK_INT >= 23 && getParentActivity() != null && getParentActivity().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            PermissionRequest.ensurePermission(R.raw.permission_request_microphone, R.string.VoipNeedMicPermissionWithHint, Manifest.permission.RECORD_AUDIO, success -> {
                                if (success) {
                                    // reload mic
                                }
                            });
                            return;
                        }
                        updateMuteButton(MUTE_BUTTON_STATE_MUTE, true);
                        VoIPService.getSharedInstance().setMicMute(false, false, true);
                        try {
                            muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                    } else {
                        updateMuteButton(MUTE_BUTTON_STATE_UNMUTE, true);
                        VoIPService.getSharedInstance().setMicMute(true, false, true);
                        try {
                            muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });

        int padding = dp(38);
        expandButton = new ImageView(context);
        expandButton.setScaleX(0.1f);
        expandButton.setScaleY(0.1f);
        expandButton.setAlpha(0f);
        expandButton.setImageResource(R.drawable.voice_expand);
        expandButton.setPadding(padding, padding, padding, padding);
        buttonsContainer.addView(expandButton, LayoutHelper.createFrame(122, 122, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        minimizeButton = new ImageView(context);
        minimizeButton.setScaleX(0.1f);
        minimizeButton.setScaleY(0.1f);
        minimizeButton.setAlpha(0f);
        minimizeButton.setImageResource(R.drawable.voice_minimize);
        minimizeButton.setPadding(padding, padding, padding, padding);
        buttonsContainer.addView(minimizeButton, LayoutHelper.createFrame(122, 122, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        if (call != null && isRtmpStream() && !call.isScheduled()) {
            int measuredWidth = expandButton.getMeasuredWidth();
            if (measuredWidth == 0) {
                measuredWidth = expandButton.getLayoutParams().width;
            }
            float expandButtonScale = dp(52) / (float) (measuredWidth - dp(8));
            expandButton.setAlpha(1f);
            expandButton.setScaleX(expandButtonScale);
            expandButton.setScaleY(expandButtonScale);
            muteButton.setAlpha(0f);
        }

        radialProgressView = new RadialProgressView(context);
        radialProgressView.setSize(dp(110));
        radialProgressView.setStrokeWidth(4);
        radialProgressView.setProgressColor(Theme.getColor(Theme.key_voipgroup_connectingProgress));

        for (int a = 0; a < 2; a++) {
            muteLabel[a] = new TextView(context);
            muteLabel[a].setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            muteLabel[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            muteLabel[a].setGravity(Gravity.CENTER_HORIZONTAL);
            buttonsContainer.addView(muteLabel[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 26));
        }

       // buttonsContainer.addView(muteLabelContainer, LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);

        actionBar.setAlpha(0.0f);
        actionBar.getBackButton().setScaleX(0.9f);
        actionBar.getBackButton().setScaleY(0.9f);
        actionBar.getBackButton().setTranslationX(-dp(14));

        actionBar.getTitleTextView().setTranslationY(dp(23));
        actionBar.getSubtitleTextView().setTranslationY(dp(20));
        actionBar.getAdditionalSubtitleTextView().setTranslationY(dp(20));

        otherItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_voipgroup_actionBarItems));
        otherItem.setLongClickEnabled(false);
        otherItem.setIcon(R.drawable.ic_ab_other);
        otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        otherItem.setSubMenuOpenSide(2);
        otherItem.setDelegate(id -> actionBar.getActionBarMenuOnItemClick().onItemClick(id));
        otherItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_voipgroup_actionBarItemsSelector), 6));
        otherItem.setOnClickListener(v -> {
            if (call == null || renderersContainer.inFullscreenMode) {
                return;
            }
            if (call.call.join_muted) {
                everyoneItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
                everyoneItem.setChecked(false);
                adminItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));
                adminItem.setChecked(true);
            } else {
                everyoneItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));
                everyoneItem.setChecked(true);
                adminItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
                adminItem.setChecked(false);
            }
            changingPermissions = false;
            otherItem.hideSubItem(eveyone_can_speak_item);
            otherItem.hideSubItem(admin_can_speak_item);

            if (VoIPService.getSharedInstance() != null && (VoIPService.getSharedInstance().hasEarpiece() || VoIPService.getSharedInstance().isBluetoothHeadsetConnected())) {
                int rout = VoIPService.getSharedInstance().getCurrentAudioRoute();
                if (rout == VoIPService.AUDIO_ROUTE_BLUETOOTH) {
                    soundItem.setIcon(R.drawable.msg_voice_bluetooth);
                    soundItem.setSubtext(VoIPService.getSharedInstance().currentBluetoothDeviceName != null ? VoIPService.getSharedInstance().currentBluetoothDeviceName : getString(R.string.VoipAudioRoutingBluetooth));
                } else if (rout == VoIPService.AUDIO_ROUTE_EARPIECE) {
                    soundItem.setIcon(VoIPService.getSharedInstance().isHeadsetPlugged() ? R.drawable.msg_voice_headphones : R.drawable.msg_voice_phone);
                    soundItem.setSubtext(VoIPService.getSharedInstance().isHeadsetPlugged() ? getString(R.string.VoipAudioRoutingHeadset) : getString(R.string.VoipAudioRoutingPhone));
                } else if (rout == VoIPService.AUDIO_ROUTE_SPEAKER) {
                    VoipAudioManager vam = VoipAudioManager.get();
                    if (vam.isSpeakerphoneOn()) {
                        soundItem.setIcon(R.drawable.msg_voice_speaker);
                        soundItem.setSubtext(getString(R.string.VoipAudioRoutingSpeaker));
                    } else {
                        soundItem.setIcon(R.drawable.msg_voice_phone);
                        soundItem.setSubtext(getString(R.string.VoipAudioRoutingPhone));
                    }
                }
            }
            updateItems();
            otherItem.toggleSubMenu();
        });
        otherItem.setPopupItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), false);
        otherItem.setPopupItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), true);

        pipItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_voipgroup_actionBarItems));
        pipItem.setLongClickEnabled(false);
        pipItem.setIcon(R.drawable.msg_voice_pip);
        pipItem.setContentDescription(getString(R.string.AccDescrPipMode));
        pipItem.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_voipgroup_actionBarItemsSelector), 6));
        pipItem.setOnClickListener(v -> {
            if (isRtmpStream()) {
                if (PipUtils.checkAnyPipPermissions(parentActivity)) {
                    RTMPStreamPipOverlay.show(parentActivity);
                    dismiss();
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AlertsCreator.createDrawOverlayPermissionDialog(parentActivity, null, true).show();
                }
                return;
            }
            if (AndroidUtilities.checkInlinePermissions(parentActivity)) {
                GroupCallPip.clearForce();
                dismiss();
            } else {
                AlertsCreator.createDrawOverlayGroupCallPermissionDialog(getContext()).show();
            }
        });

        screenShareItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_voipgroup_actionBarItems));
        screenShareItem.setLongClickEnabled(false);
        screenShareItem.setIcon(R.drawable.msg_screencast);
        screenShareItem.setContentDescription(getString(R.string.AccDescrPipMode));
        screenShareItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_voipgroup_actionBarItemsSelector), 6));
        screenShareItem.setOnClickListener(v -> {
            VoIPService voIPService = VoIPService.getSharedInstance();
            if (voIPService == null) {
                return;
            }
            if (voIPService.getVideoState(true) == Instance.VIDEO_STATE_ACTIVE) {
                voIPService.stopScreenCapture();
            } else {
                startScreenCapture();
            }
        });

        titleTextView = new AudioPlayerAlert.ClippingTextViewSwitcher(context) {
            @Override
            protected TextView createTextView() {
                TextView textView = new TextView(context);
                textView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                textView.setTypeface(AndroidUtilities.bold());
                textView.setGravity(Gravity.LEFT | Gravity.TOP);
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setOnClickListener(v -> {
                    if (call != null && call.recording) {
                        showRecordHint(textView);
                    }
                });
                return textView;
            }
        };

        actionBarBackground = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ActionBar.getCurrentActionBarHeight());
            }
        };
        actionBarBackground.setAlpha(0.0f);

        liveLabelPaint =  new Paint(Paint.ANTI_ALIAS_FLAG);
        liveLabelPaint.setColor(0xff3d4657);
        liveLabelTextView = new TextView(getContext()) {
            private RectF rect = new RectF();

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(rect, dp(12), dp(12), liveLabelPaint);
                super.onDraw(canvas);
            }
        };
        liveLabelTextView.setTextColor(getThemedColor(Theme.key_voipgroup_actionBarItems));
        liveLabelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        liveLabelTextView.setText(getString(R.string.VoipChannelLabelLive));
        liveLabelTextView.setMaxLines(1);
        liveLabelTextView.setGravity(Gravity.CENTER);
        liveLabelTextView.setTypeface(AndroidUtilities.bold());
        liveLabelTextView.setPadding(dp(8), dp(0), dp(8), dp(0));
        liveLabelTextView.setTag(-1);
        if (!isRtmpStream()) {
            liveLabelTextView.setVisibility(View.GONE);
        }

        titleLayout = new LinearLayout(getContext());
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.addView(titleTextView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        titleLayout.addView(liveLabelTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 18, 6, 4, 0,0));

        containerView.addView(actionBarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        containerView.addView(titleLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 48, 0));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        menuItemsContainer = new LinearLayout(context);
        menuItemsContainer.setOrientation(LinearLayout.HORIZONTAL);
        menuItemsContainer.addView(screenShareItem, LayoutHelper.createLinear(48, 48));
        menuItemsContainer.addView(pipItem, LayoutHelper.createLinear(48, 48));
        menuItemsContainer.addView(otherItem, LayoutHelper.createLinear(48, 48));
        containerView.addView(menuItemsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 48, Gravity.TOP | Gravity.RIGHT));

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context) {
                @Override
                public void showWithAction(long did, int action, Object infoObject, Object infoObject2, Runnable actionRunnable, Runnable cancelRunnable) {
                    if (previewDialog != null) {
                        return;
                    }
                    super.showWithAction(did, action, infoObject, infoObject2, actionRunnable, cancelRunnable);
                }
            };
            undoView[a].setAdditionalTranslationY(dp(10));
            if (Build.VERSION.SDK_INT >= 21) {
                undoView[a].setTranslationZ(dp(5));
            }
            containerView.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        accountSelectCell = new AccountSelectCell(context, true);
        accountSelectCell.setTag(R.id.width_tag, 240);
        otherItem.addSubItem(user_item, accountSelectCell, LayoutHelper.WRAP_CONTENT, dp(48));
        otherItem.setShowSubmenuByMove(false);
        accountSelectCell.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_voipgroup_listSelector), 6, 6));
        accountGap = otherItem.addGap(user_item_gap);
        everyoneItem = otherItem.addSubItem(eveyone_can_speak_item, 0, getString(R.string.VoipGroupAllCanSpeak), true);
        everyoneItem.updateSelectorBackground(true, false);
        adminItem = otherItem.addSubItem(admin_can_speak_item, 0, getString(R.string.VoipGroupOnlyAdminsCanSpeak), true);
        adminItem.updateSelectorBackground(false, true);

        everyoneItem.setCheckColor(Theme.key_voipgroup_checkMenu);
        everyoneItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));
        adminItem.setCheckColor(Theme.key_voipgroup_checkMenu);
        adminItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));

        Paint soundDrawablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        soundDrawablePaint.setColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        soundDrawablePaint.setStyle(Paint.Style.STROKE);
        soundDrawablePaint.setStrokeWidth(dp(1.5f));
        soundDrawablePaint.setStrokeCap(Paint.Cap.ROUND);
        soundItem = otherItem.addSubItem(sound_item, R.drawable.msg_voice_speaker, null, getString(R.string.VoipGroupAudio), true, false);
        soundItem.setItemHeight(56);

        noiseItem = otherItem.addSubItem(noise_item, R.drawable.msg_noise_on, null, getString(R.string.VoipNoiseCancellation), true, false);
        noiseItem.setItemHeight(56);

        soundItemDivider = otherItem.addDivider(ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_actionBar), Color.BLACK, 0.3f));
        ((ViewGroup.MarginLayoutParams) soundItemDivider.getLayoutParams()).topMargin = 0;
        ((ViewGroup.MarginLayoutParams) soundItemDivider.getLayoutParams()).bottomMargin = 0;
        editTitleItem = otherItem.addSubItem(edit_item, R.drawable.msg_edit, recordCallDrawable, ChatObject.isChannelOrGiga(currentChat) ? getString(R.string.VoipChannelEditTitle) : getString(R.string.VoipGroupEditTitle), true, false);
        permissionItem = otherItem.addSubItem(permission_item, R.drawable.msg_permissions, recordCallDrawable, getString(R.string.VoipGroupEditPermissions), false, false);
        inviteItem = otherItem.addSubItem(share_invite_link_item, R.drawable.msg_link, getString(R.string.VoipGroupShareInviteLink));
        recordCallDrawable = new RecordCallDrawable();
        screenItem = otherItem.addSubItem(screen_capture_item, R.drawable.msg_screencast, getString(R.string.VoipChatStartScreenCapture));
        recordItem = otherItem.addSubItem(start_record_item, 0, recordCallDrawable, getString(R.string.VoipGroupRecordCall), true, false);
        recordCallDrawable.setParentView(recordItem.getImageView());
        leaveItem = otherItem.addSubItem(leave_item, R.drawable.msg_cancel, isConference() ? getString(R.string.VoipGroupEndConference) : ChatObject.isChannelOrGiga(currentChat) ? getString(R.string.VoipChannelEndChat) : getString(R.string.VoipGroupEndChat));
        otherItem.setPopupItemsSelectorColor(Theme.getColor(Theme.key_voipgroup_listSelector));
        otherItem.getPopupLayout().setFitItems(true);

        soundItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        noiseItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        leaveItem.setColors(Theme.getColor(Theme.key_voipgroup_leaveCallMenu), Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
        inviteItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        editTitleItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        permissionItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        recordItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
        screenItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));

        if (call != null) {
            initCreatedGroupCall();
        }
        if (isConference()) {
            encryptionDrawable = new CallEncryptionCellDrawable(context);
            VoIPService voip = VoIPService.getSharedInstance();
            encryptionDrawable.setEmojis(voip != null && voip.conference != null ? voip.conference.getEmojis() : null);
        }


        leaveBackgroundPaint.setColor(Theme.getColor(Theme.key_voipgroup_leaveButton));

        updateTitle(false);
        actionBar.getTitleTextView().setOnClickListener(v -> {
            if (call != null && call.recording) {
                showRecordHint(actionBar.getTitleTextView());
            }
        });

        fullscreenUsersListView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                GroupCallFullscreenAdapter.GroupCallUserCell cell = (GroupCallFullscreenAdapter.GroupCallUserCell) child;
                if (!renderersContainer.isAnimating() && !fullscreenListItemAnimator.isRunning()) {
                    cell.setAlpha(1f);
                    cell.setTranslationX(0);
                    cell.setTranslationY(0);
                }
                if (cell.isRemoving(fullscreenUsersListView) && cell.getRenderer() != null) {
                    return true;
                } else if (cell.getTranslationY() != 0 && cell.getRenderer() != null && cell.getRenderer().primaryView != null) {
                    float listTop = listView.getTop() - getTop();
                    float listBottom = listTop + listView.getMeasuredHeight();
                    float progress = renderersContainer.progressToFullscreenMode;
                    canvas.save();
                    canvas.clipRect(0, listTop * (1f - progress), getMeasuredWidth(), listBottom * (1f - progress) + getMeasuredHeight() * progress);
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }
        };
        fullscreenListItemAnimator = new DefaultItemAnimator() {

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView.invalidate();
                renderersContainer.invalidate();
                containerView.invalidate();
                updateLayout(true);
            }
        };
        fullscreenUsersListView.setClipToPadding(false);
        fullscreenListItemAnimator.setDelayAnimations(false);
        fullscreenListItemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        fullscreenListItemAnimator.setRemoveDuration(TRANSITION_DURATION);
        fullscreenListItemAnimator.setAddDuration(TRANSITION_DURATION);
        fullscreenListItemAnimator.setMoveDuration(TRANSITION_DURATION);

        fullscreenUsersListView.setItemAnimator(fullscreenListItemAnimator);
        fullscreenUsersListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                containerView.invalidate();
                renderersContainer.invalidate();
            }
        });
        fullscreenUsersListView.setClipChildren(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        fullscreenUsersListView.setLayoutManager(layoutManager);
        fullscreenUsersListView.setAdapter(fullscreenAdapter = new GroupCallFullscreenAdapter(groupCall, currentAccount, this));
        fullscreenAdapter.setVisibility(fullscreenUsersListView, false);
        fullscreenUsersListView.setOnItemClickListener((view, position) -> {
           // pinnedVideoView.delayHideUi();
            GroupCallFullscreenAdapter.GroupCallUserCell userCell = (GroupCallFullscreenAdapter.GroupCallUserCell) view;
            if (userCell.getVideoParticipant() == null) {
                fullscreenFor(new ChatObject.VideoParticipant(userCell.getParticipant(), false, false));
            } else {
                fullscreenFor(userCell.getVideoParticipant());
            }
        });
        fullscreenUsersListView.setOnItemLongClickListener((view, position) -> {
            if (showMenuForCell(view)) {
                try {
                    listView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } catch (Exception ignored) {}
            }
            return false;
        });
        fullscreenUsersListView.setVisibility(View.GONE);
        fullscreenUsersListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (!isLandscapeMode) {
                    outRect.set(dp(4), 0, dp(4), 0);
                } else {
                    outRect.set(0, dp(4), 0, dp(4));
                }
            }
        });

        renderersContainer = new GroupCallRenderersContainer(context, listView, fullscreenUsersListView, attachedRenderers, call, this) {
            @Override
            protected void update() {
                super.update();

                float finalColorProgress2 = Math.max(colorProgress, renderersContainer == null ? 0 : renderersContainer.progressToFullscreenMode);
                navBarColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled), Theme.getColor(Theme.key_voipgroup_actionBar), finalColorProgress2, 1.0f);
                containerView.invalidate();
                setColorProgress(colorProgress);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == scrimRenderer) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void onFullScreenModeChanged(boolean startAnimation) {
                delayedGroupCallUpdated = startAnimation;
                if (isTabletMode) {
                    if (!startAnimation && renderersContainer.inFullscreenMode) {
                        tabletGridAdapter.setVisibility(tabletVideoGridView, false, true);
                    }
                } else {
                    if (startAnimation) {
                        GroupCallActivity.this.undoView[0].hide(false, 1);
                        renderersContainer.undoView[0].hide(false, 2);
                        if (!renderersContainer.inFullscreenMode) {
                            listView.setVisibility(View.VISIBLE);
                            actionBar.setVisibility(View.VISIBLE);
                            if (watchersView != null) {
                                watchersView.setVisibility(View.VISIBLE);
                            }
                        }
                        updateState(true, false);
                        buttonsContainer.requestLayout();
                        if (fullscreenUsersListView.getVisibility() != View.VISIBLE) {
                            fullscreenUsersListView.setVisibility(View.VISIBLE);
                            fullscreenAdapter.setVisibility(fullscreenUsersListView, true);
                            fullscreenAdapter.update(false, fullscreenUsersListView);
                        } else {
                            fullscreenAdapter.setVisibility(fullscreenUsersListView, true);
                            applyCallParticipantUpdates(true);
                        }
                    } else {
                        if (!renderersContainer.inFullscreenMode) {
                            fullscreenUsersListView.setVisibility(View.GONE);
                            fullscreenAdapter.setVisibility(fullscreenUsersListView, false);
                        } else {
                            actionBar.setVisibility(View.GONE);
                            listView.setVisibility(View.GONE);
                            if (watchersView != null) {
                                watchersView.setVisibility(View.GONE);
                            }
                        }

                        if (fullscreenUsersListView.getVisibility() == View.VISIBLE) {
                            for (int i = 0; i < fullscreenUsersListView.getChildCount(); i++) {
                                View child = fullscreenUsersListView.getChildAt(i);
                                child.setAlpha(1f);
                                child.setScaleX(1f);
                                child.setScaleY(1f);
                                child.setTranslationX(0);
                                child.setTranslationY(0);
                                ((GroupCallFullscreenAdapter.GroupCallUserCell) child).setProgressToFullscreen(renderersContainer.progressToFullscreenMode);
                            }
                        }
                    }
                    buttonsBackgroundGradientView2.setVisibility(startAnimation ? View.VISIBLE : View.GONE);
                    if (!delayedGroupCallUpdated) {
                        applyCallParticipantUpdates(true);
                    }
                }
            }

            ValueAnimator uiVisibilityAnimator;

            public void onUiVisibilityChanged() {
                if (renderersContainer == null) {
                    return;
                }
                boolean uiVisible = renderersContainer.isUiVisible();
                if (uiVisibilityAnimator != null) {
                    uiVisibilityAnimator.removeAllListeners();
                    uiVisibilityAnimator.cancel();
                }
                uiVisibilityAnimator = ValueAnimator.ofFloat(progressToHideUi, uiVisible ? 0 : 1f);
                uiVisibilityAnimator.addUpdateListener(valueAnimator -> {
                    progressToHideUi = (float) valueAnimator.getAnimatedValue();
                    renderersContainer.setProgressToHideUi(progressToHideUi);
                    fullscreenUsersListView.invalidate();
                    containerView.invalidate();
                    buttonsContainer.invalidate();
                });
                uiVisibilityAnimator.setDuration(TRANSITION_DURATION);
                uiVisibilityAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                uiVisibilityAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        invalidateLayoutFullscreen();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        uiVisibilityAnimator = null;
                        progressToHideUi = uiVisible ? 0 : 1f;
                        renderersContainer.setProgressToHideUi(progressToHideUi);
                        fullscreenUsersListView.invalidate();
                        containerView.invalidate();
                        buttonsContainer.invalidate();
                    }
                });
                uiVisibilityAnimator.start();

            }

            @Override
            protected boolean canHideUI() {
                return super.canHideUI() && previewDialog == null;
            }

            @Override
            protected void onBackPressed() {
                GroupCallActivity.this.onBackPressed();
            }
        };
        renderersContainer.setClipChildren(false);
        fullscreenAdapter.setRenderersPool(attachedRenderers, renderersContainer);
        if (tabletVideoGridView != null) {
            tabletGridAdapter.setRenderersPool(attachedRenderers, renderersContainer);
        }
        avatarPagerIndicator = new AvatarPreviewPagerIndicator(context);
        avatarsViewPager = new ProfileGalleryView(context, actionBar, listView, avatarPagerIndicator) {
            @Override
            public void invalidate() {
                super.invalidate();
                containerView.invalidate();
            }
        };
        avatarsViewPager.setImagesLayerNum(Integer.MAX_VALUE);
        avatarsViewPager.setInvalidateWithParent(true);
        avatarPagerIndicator.setProfileGalleryView(avatarsViewPager);
        avatarPreviewContainer = new FrameLayout(context) {

            Rect rect = new Rect();
            RectF rectF = new RectF();
            Path path = new Path();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
                super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(size + getPaddingBottom(), MeasureSpec.EXACTLY));
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (progressToAvatarPreview != 1) {
                    if (scrimView != null && hasScrimAnchorView) {
                        canvas.save();
                        float s = getMeasuredHeight() / (float) scrimView.getAvatarImageView().getMeasuredHeight();

                        float fromRadius = scrimView.getAvatarImageView().getMeasuredHeight() / 2f * s;
                        int topRad = (int) ((fromRadius * (1f - progressToAvatarPreview) + (dp(13) * progressToAvatarPreview)));
                        int bottomRad = (int) (fromRadius * (1f - progressToAvatarPreview));
                        scrimView.getAvatarWavesDrawable().draw(canvas, scrimView.getAvatarImageView().getMeasuredHeight() / 2, scrimView.getAvatarImageView().getMeasuredHeight() / 2, this);
                        scrimView.getAvatarImageView().getImageReceiver().setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        scrimView.getAvatarImageView().setRoundRadius(topRad, topRad, bottomRad, bottomRad);
                        scrimView.getAvatarImageView().getImageReceiver().draw(canvas);
                        scrimView.getAvatarImageView().setRoundRadius(scrimView.getAvatarImageView().getMeasuredHeight() / 2);
                        canvas.restore();
                    } else if (scrimFullscreenView != null && scrimRenderer == null && previewTextureTransitionEnabled) {
                        canvas.save();
                        float s = getMeasuredHeight() / (float) scrimFullscreenView.getAvatarImageView().getMeasuredHeight();

                        float fromRadius = scrimFullscreenView.getAvatarImageView().getMeasuredHeight() / 2f * s;
                        int topRad = (int) ((fromRadius * (1f - progressToAvatarPreview) + (dp(13) * progressToAvatarPreview)));
                        int bottomRad = (int) (fromRadius * (1f - progressToAvatarPreview));
                       // scrimFullscreenView.getAvatarWavesDrawable().draw(canvas, scrimFullscreenView.getAvatarImageView().getMeasuredHeight() / 2, scrimFullscreenView.getAvatarImageView().getMeasuredHeight() / 2, this);
                        scrimFullscreenView.getAvatarImageView().getImageReceiver().setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        scrimFullscreenView.getAvatarImageView().setRoundRadius(topRad, topRad, bottomRad, bottomRad);
                        scrimFullscreenView.getAvatarImageView().getImageReceiver().draw(canvas);
                        scrimFullscreenView.getAvatarImageView().setRoundRadius(scrimFullscreenView.getAvatarImageView().getMeasuredHeight() / 2);
                        canvas.restore();
                    }
                }
                avatarsViewPager.setAlpha(progressToAvatarPreview);

                path.reset();
                rectF.set(0, 0, getMeasuredHeight(), getMeasuredWidth());
                path.addRoundRect(rectF, new float[]{dp(13), dp(13), dp(13), dp(13), 0, 0, 0, 0}, Path.Direction.CCW);
                canvas.save();
                canvas.clipPath(path);
                View textureView = avatarsViewPager.findVideoActiveView();
                if (textureView != null && scrimRenderer != null && scrimRenderer.isAttached() && !drawingForBlur) {
                    canvas.save();
                    rect.setEmpty();
                    avatarsViewPager.getChildVisibleRect(textureView, rect, null);
                    int left = rect.left;
                    if (left < -avatarsViewPager.getMeasuredWidth()) {
                        left += avatarsViewPager.getMeasuredWidth() * 2;
                    } else if (left > avatarsViewPager.getMeasuredWidth()) {
                        left -= avatarsViewPager.getMeasuredWidth() * 2;
                    }
                    canvas.translate(left, 0);
                    scrimRenderer.draw(canvas);
                    canvas.restore();
                }
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            public void invalidate() {
                super.invalidate();
                containerView.invalidate();
            }
        };
        avatarPreviewContainer.setVisibility(View.GONE);

        avatarsViewPager.setVisibility(View.VISIBLE);
        avatarsViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                int realPosition = avatarsViewPager.getRealPosition(position);
                avatarPagerIndicator.saveCurrentPageProgress();
                avatarPagerIndicator.invalidate();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                if (getAlpha() != alpha) {
                    super.setAlpha(alpha);
                    checkContentOverlayed();
                }
            }
        };
        containerView.addView(renderersContainer);
        renderersContainer.addView(fullscreenUsersListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.BOTTOM, 0, 0, 0, 100));

        buttonsContainer.setWillNotDraw(false);
        buttonsBackgroundGradientView = new View(context);
        gradientColors[0] = backgroundColor;
        gradientColors[1] = Color.TRANSPARENT;
        buttonsBackgroundGradientView.setBackground(buttonsBackgroundGradient = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, gradientColors));
        containerView.addView(buttonsBackgroundGradientView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT | Gravity.BOTTOM));
        buttonsBackgroundGradientView2 = new View(context);
        buttonsBackgroundGradientView2.setBackgroundColor(gradientColors[0]);
        containerView.addView(buttonsBackgroundGradientView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.LEFT | Gravity.BOTTOM));
        containerView.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        containerView.addView(blurredView);

        avatarPreviewContainer.addView(avatarsViewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        avatarPreviewContainer.addView(avatarPagerIndicator, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, 0));
        containerView.addView(avatarPreviewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 14, 14, 14, 14));

        applyCallParticipantUpdates(false);
        listAdapter.notifyDataSetChanged();

        if (isTabletMode) {
            tabletGridAdapter.update(false, tabletVideoGridView);
        }
        oldCount = listAdapter.getItemCount();

        if (schedulePeer != null) {
            scheduleInfoTextView = new TextView(context);
            scheduleInfoTextView.setGravity(Gravity.CENTER);
            scheduleInfoTextView.setTextColor(0xff7B8389);
            scheduleInfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            if (ChatObject.isChannel(currentChat) && currentChat != null && !currentChat.megagroup) {
                scheduleInfoTextView.setTag(1);
            }
            containerView.addView(scheduleInfoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 21, 0, 21, 100));

            final NumberPicker dayPicker = new NumberPicker(context);
            dayPicker.setTextColor(0xffffffff);
            dayPicker.setSelectorColor(0xff6D89ED);
            dayPicker.setTextOffset(dp(10));
            dayPicker.setItemCount(5);
            final NumberPicker hourPicker = new NumberPicker(context) {
                @Override
                protected CharSequence getContentDescription(int value) {
                    return LocaleController.formatPluralString("Hours", value);
                }
            };
            hourPicker.setItemCount(5);
            hourPicker.setTextColor(0xffffffff);
            hourPicker.setSelectorColor(0xff6D89ED);
            hourPicker.setTextOffset(-dp(10));
            final NumberPicker minutePicker = new NumberPicker(context) {
                @Override
                protected CharSequence getContentDescription(int value) {
                    return LocaleController.formatPluralString("Minutes", value);
                }
            };
            minutePicker.setItemCount(5);
            minutePicker.setTextColor(0xffffffff);
            minutePicker.setSelectorColor(0xff6D89ED);
            minutePicker.setTextOffset(-dp(34));

            scheduleButtonTextView = new TextView(context);
            scheduleButtonTextView.setLines(1);
            scheduleButtonTextView.setSingleLine(true);
            scheduleButtonTextView.setEllipsize(TextUtils.TruncateAt.END);
            scheduleButtonTextView.setGravity(Gravity.CENTER);
            scheduleButtonTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(8), 0, 0x3f000000));
            scheduleButtonTextView.setTextColor(0xffffffff);
            scheduleButtonTextView.setTypeface(AndroidUtilities.bold());
            scheduleButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            containerView.addView(scheduleButtonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 21, 0, 21, 20.5f));
            scheduleButtonTextView.setOnClickListener(v -> {
                scheduleAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
                scheduleAnimator.setDuration(600);
                scheduleAnimator.addUpdateListener(a -> {
                    switchToButtonProgress = (float) a.getAnimatedValue();
                    updateScheduleUI(true);

                    buttonsContainer.invalidate();
                    listView.invalidate();
                });
                scheduleAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        scheduleAnimator = null;
                    }
                });
                scheduleAnimator.start();
                if (ChatObject.isChannelOrGiga(currentChat)) {
                    titleTextView.setText(getString(R.string.VoipChannelVoiceChat), true);
                } else {
                    titleTextView.setText(getString(R.string.VoipGroupVoiceChat), true);
                }
                Calendar calendar = Calendar.getInstance();
                boolean setSeconds = AlertsCreator.checkScheduleDate(null, null, 7 * 24 * 60 * 60, 3, dayPicker, hourPicker, minutePicker);
                calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
                calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
                calendar.set(Calendar.MINUTE, minutePicker.getValue());
                if (setSeconds) {
                    calendar.set(Calendar.SECOND, 0);
                }
                scheduleStartAt = (int) (calendar.getTimeInMillis() / 1000);
                updateScheduleUI(false);

                TL_phone.createGroupCall req = new TL_phone.createGroupCall();
                req.peer = MessagesController.getInputPeer(chat);
                req.random_id = Utilities.random.nextInt();
                req.schedule_date = scheduleStartAt;
                req.flags |= 2;
                account.getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response != null) {
                        TLRPC.Updates updates = (TLRPC.Updates) response;
                        for (int a = 0; a < updates.updates.size(); a++) {
                            TLRPC.Update update = updates.updates.get(a);
                            if (update instanceof TLRPC.TL_updateGroupCall) {
                                TLRPC.TL_updateGroupCall updateGroupCall = (TLRPC.TL_updateGroupCall) update;
                                AndroidUtilities.runOnUIThread(() -> {
                                    call = new ChatObject.Call();
                                    call.call = new TLRPC.TL_groupCall();
                                    call.call.participants_count = 0;
                                    call.call.version = 1;
                                    call.call.can_start_video = true;
                                    call.call.can_change_join_muted = true;
                                    call.chatId = chat == null ? 0 : chat.id;
                                    call.call.schedule_date = scheduleStartAt;
                                    call.call.flags |= 128;
                                    call.currentAccount = accountInstance;
                                    call.setSelfPeer(peer);
                                    call.call.access_hash = updateGroupCall.call.access_hash;
                                    call.call.id = updateGroupCall.call.id;
                                    call.createNoVideoParticipant();
                                    fullscreenAdapter.setGroupCall(call);
                                    renderersContainer.setGroupCall(call);
                                    tabletGridAdapter.setGroupCall(call);
                                    accountInstance.getMessagesController().putGroupCall(call.chatId, call);
                                });
                                break;
                            }
                        }
                        accountInstance.getMessagesController().processUpdates(updates, false);
                    } else {
                        AndroidUtilities.runOnUIThread(() -> {
                            accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.needShowAlert, 6, error.text);
                            dismiss();
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
            });

            scheduleTimerContainer = new LinearLayout(context) {

                boolean ignoreLayout = false;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    ignoreLayout = true;
                    int count = 5;
                    dayPicker.setItemCount(count);
                    hourPicker.setItemCount(count);
                    minutePicker.setItemCount(count);
                    dayPicker.getLayoutParams().height = dp(54) * count;
                    hourPicker.getLayoutParams().height = dp(54) * count;
                    minutePicker.getLayoutParams().height = dp(54) * count;
                    ignoreLayout = false;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                public void requestLayout() {
                    if (ignoreLayout) {
                        return;
                    }
                    super.requestLayout();
                }
            };
            scheduleTimerContainer.setWeightSum(1.0f);
            scheduleTimerContainer.setOrientation(LinearLayout.HORIZONTAL);
            containerView.addView(scheduleTimerContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 54 * 5, Gravity.TOP | Gravity.LEFT, 0, 50, 0, 0));

            long currentTime = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(currentTime);
            int currentYear = calendar.get(Calendar.YEAR);
            int currentDay = calendar.get(Calendar.DAY_OF_YEAR);

            scheduleTimerContainer.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
            dayPicker.setMinValue(0);
            dayPicker.setMaxValue(365);
            dayPicker.setWrapSelectorWheel(false);
            dayPicker.setFormatter(value -> {
                if (value == 0) {
                    return getString(R.string.MessageScheduleToday);
                } else {
                    long date = currentTime + (long) value * 86400000L;
                    calendar.setTimeInMillis(date);
                    int year = calendar.get(Calendar.YEAR);
                    if (year == currentYear) {
                        return (
                            LocaleController.getInstance().getFormatterWeek().format(date) +
                            " " +
                            LocaleController.getInstance().getFormatterScheduleDay().format(date)
                        );
                    } else {
                        return LocaleController.getInstance().getFormatterScheduleYear().format(date);
                    }
                }
            });
            final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
                try {
                    container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                } catch (Exception ignore) {}
                AlertsCreator.checkScheduleDate(scheduleButtonTextView, scheduleInfoTextView, 7 * 24 * 60 * 60, 2, dayPicker, hourPicker, minutePicker);
            };
            dayPicker.setOnValueChangedListener(onValueChangeListener);

            hourPicker.setMinValue(0);
            hourPicker.setMaxValue(23);
            scheduleTimerContainer.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f));
            hourPicker.setFormatter(value -> String.format("%02d", value));
            hourPicker.setOnValueChangedListener(onValueChangeListener);

            minutePicker.setMinValue(0);
            minutePicker.setMaxValue(59);
            minutePicker.setValue(0);
            minutePicker.setFormatter(value -> String.format("%02d", value));
            scheduleTimerContainer.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f));
            minutePicker.setOnValueChangedListener(onValueChangeListener);

            calendar.setTimeInMillis(currentTime + 3 * 60 * 60 * 1000);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            int nextDay = calendar.get(Calendar.DAY_OF_YEAR);
            int minute = calendar.get(Calendar.MINUTE);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            dayPicker.setValue(currentDay != nextDay ? 1 : 0);
            minutePicker.setValue(minute);
            hourPicker.setValue(hour);

            AlertsCreator.checkScheduleDate(scheduleButtonTextView, scheduleInfoTextView, 7 * 24 * 60 * 60, 2, dayPicker, hourPicker, minutePicker);
        }

        ViewGroup decorView;
        if (Build.VERSION.SDK_INT >= 21) {
            decorView = (ViewGroup) getWindow().getDecorView();
        } else {
            decorView = containerView;
        }
        pinchToZoomHelper = new PinchToZoomHelper(decorView, containerView) {
            @Override
            protected void invalidateViews() {
                super.invalidateViews();
                for (int i = 0; i < avatarsViewPager.getChildCount(); i++) {
                    avatarsViewPager.getChildAt(i).invalidate();
                }
            }

            @Override
            protected void drawOverlays(Canvas canvas, float alpha, float parentOffsetX, float parentOffsetY, float clipTop, float clipBottom) {
                if (alpha > 0) {
                    float x = avatarPreviewContainer.getX() + containerView.getX();
                    float y = avatarPreviewContainer.getY() + containerView.getY();
                    AndroidUtilities.rectTmp.set(x, y, x + avatarsViewPager.getMeasuredWidth(), y + avatarsViewPager.getMeasuredHeight());
                    canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);

                    canvas.translate(x, y);
                    avatarPreviewContainer.draw(canvas);

                    canvas.restore();
                }
            }
        };
        pinchToZoomHelper.setCallback(new PinchToZoomHelper.Callback() {

            @Override
            public void onZoomStarted(MessageObject messageObject) {
                listView.cancelClickRunnables(true);
                pinchToZoomHelper.getPhotoImage().setRoundRadius(dp(13), dp(13), 0, 0);
                containerView.invalidate();
            }

            @Override
            public void onZoomFinished(MessageObject messageObject) {
                containerView.invalidate();
            }
        });
        avatarsViewPager.setPinchToZoomHelper(pinchToZoomHelper);

        cameraButton.setOnClickListener((View) -> {
            if (Build.VERSION.SDK_INT >= 23 && parentActivity != null && parentActivity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                parentActivity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 104);
                return;
            }
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            if (VoIPService.getSharedInstance().getVideoState(false) != Instance.VIDEO_STATE_ACTIVE) {
                undoView[0].hide(false, 1);
                if (previewDialog == null) {
                    VoIPService voIPService = VoIPService.getSharedInstance();
                    if (voIPService != null) {
                        voIPService.createCaptureDevice(false);
                    }
                    previewDialog = new PrivateVideoPreviewDialog(context, true, VoIPService.getSharedInstance().getVideoState(true) != Instance.VIDEO_STATE_ACTIVE) {
                        @Override
                        public void onDismiss(boolean screencast, boolean apply) {
                            boolean showMicIcon = previewDialog.micEnabled;
                            previewDialog = null;
                            VoIPService service = VoIPService.getSharedInstance();
                            if (apply) {
                                if (service != null) {
                                    service.setupCaptureDevice(screencast, showMicIcon);
                                }
                                if (screencast) {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                }
                                updateState(true, false);
                                call.sortParticipants();
                                applyCallParticipantUpdates(true);
                                buttonsContainer.requestLayout();
                            } else {
                                if (service != null) {
                                    service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                }
                            }
                        }
                    };
                    container.addView(previewDialog);
                    if (voIPService != null && !voIPService.isFrontFaceCamera()) {
                        voIPService.switchCamera();
                    }
                }
            } else {
                VoIPService.getSharedInstance().setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                updateState(true, false);
                updateSpeakerPhoneIcon(false);
                call.sortParticipants();
                applyCallParticipantUpdates(true);
                buttonsContainer.requestLayout();
            }
        });
        updateScheduleUI(false);
        updateItems();
        updateSpeakerPhoneIcon(false);
        updateState(false, false);
        setColorProgress(0.0f);
        updateSubtitle();
        /*if (isRtmpStream()) {
            todo
            if (PipNativeApiController.checkPermissions(context) == PipNativeApiController.PIP_GRANTED_PIP) {
                pipSource = new PipSource.Builder(context, this)
                    .setTagPrefix("group-call-activity")
                    .build();
            }
        }*/

        bulletinContainer = new FrameLayout(context);
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
    }

    public LaunchActivity getParentActivity() {
        return parentActivity;
    }

    private void invalidateLayoutFullscreen() {
        if (isRtmpStream()) {
            boolean notFullscreen = renderersContainer.isUiVisible() || !renderersContainer.inFullscreenMode || isLandscapeMode != isRtmpLandscapeMode() && !AndroidUtilities.isTablet();
            if (wasNotInLayoutFullscreen != null && notFullscreen == wasNotInLayoutFullscreen) {
                return;
            }

            int systemUiVisibility = containerView.getSystemUiVisibility();
            if (notFullscreen) {
                systemUiVisibility &= ~View.SYSTEM_UI_FLAG_FULLSCREEN;
                systemUiVisibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                setHideSystemVerticalInsets(false);
            } else {
                setHideSystemVerticalInsets(true);
                systemUiVisibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
                systemUiVisibility |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            containerView.setSystemUiVisibility(systemUiVisibility);
            wasNotInLayoutFullscreen = notFullscreen;
        }
    }

    public LinearLayout getMenuItemsContainer() {
        return menuItemsContainer;
    }

    public void fullscreenFor(ChatObject.VideoParticipant videoParticipant) {
        if (videoParticipant == null) {
            parentActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService == null || renderersContainer.isAnimating()) {
            return;
        }
        if (isTabletMode) {
            if (requestFullscreenListener != null) {
                listView.getViewTreeObserver().removeOnPreDrawListener(requestFullscreenListener);
                requestFullscreenListener = null;
            }
            ArrayList<ChatObject.VideoParticipant> activeSinks = new ArrayList<>();
            if (videoParticipant == null) {
                attachedRenderersTmp.clear();
                attachedRenderersTmp.addAll(attachedRenderers);
                for (int i = 0; i < attachedRenderersTmp.size(); i++) {
                    final GroupCallMiniTextureView miniTextureView = attachedRenderersTmp.get(i);
                    if (miniTextureView.primaryView != null) {
                        miniTextureView.primaryView.setRenderer(null);
                        if (miniTextureView.secondaryView != null) {
                            miniTextureView.secondaryView.setRenderer(null);
                        }
                        if (miniTextureView.tabletGridView != null) {
                            miniTextureView.tabletGridView.setRenderer(null);
                        }
                        activeSinks.add(miniTextureView.participant);
                        miniTextureView.forceDetach(false);
                        miniTextureView.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (miniTextureView.getParent() != null) {
                                    containerView.removeView(miniTextureView);
                                }
                            }
                        });
                    }
                }
                listViewVideoVisibility = false;
                tabletGridAdapter.setVisibility(tabletVideoGridView, true, true);
            } else {
                attachedRenderersTmp.clear();
                attachedRenderersTmp.addAll(attachedRenderers);
                for (int i = 0; i < attachedRenderersTmp.size(); i++) {
                    final GroupCallMiniTextureView miniTextureView = attachedRenderersTmp.get(i);
                    if (miniTextureView.tabletGridView != null && (miniTextureView.participant == null || !miniTextureView.participant.equals(videoParticipant))) {
                        activeSinks.add(miniTextureView.participant);
                        miniTextureView.forceDetach(false);
                        if (miniTextureView.secondaryView != null) {
                            miniTextureView.secondaryView.setRenderer(null);
                        }
                        if (miniTextureView.primaryView != null) {
                            miniTextureView.primaryView.setRenderer(null);
                        }
                        miniTextureView.animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (miniTextureView.getParent() != null) {
                                    containerView.removeView(miniTextureView);
                                }
                            }
                        });
                    }
                }
                listViewVideoVisibility = true;
                tabletGridAdapter.setVisibility(tabletVideoGridView, false, false);

                if (!activeSinks.isEmpty()) {
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int i = 0; i < attachedRenderers.size(); i++) {
                            if (attachedRenderers.get(i).participant != null) {
                                activeSinks.remove(attachedRenderers.get(i).participant);
                            }
                        }
                        for (int i = 0; i < activeSinks.size(); i++) {
                            ChatObject.VideoParticipant participant = activeSinks.get(i);
                            if (participant.participant.self) {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().setLocalSink(null, participant.presentation);
                                }
                            } else {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().removeRemoteSink(participant.participant, participant.presentation);
                                }
                            }
                        }
                    });
                }
            }
            boolean updateScroll = !renderersContainer.inFullscreenMode;
            listView.getViewTreeObserver().addOnPreDrawListener(requestFullscreenListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    requestFullscreenListener = null;
                    renderersContainer.requestFullscreen(videoParticipant);
                    if (delayedGroupCallUpdated) {
                        delayedGroupCallUpdated = false;
                        applyCallParticipantUpdates(true);
                        if (updateScroll && videoParticipant != null) {
                            listView.scrollToPosition(0);
                        }
                        delayedGroupCallUpdated = true;
                    } else {
                        applyCallParticipantUpdates(true);
                    }
                    return false;
                }
            });
        } else {
            if (requestFullscreenListener != null) {
                listView.getViewTreeObserver().removeOnPreDrawListener(requestFullscreenListener);
                requestFullscreenListener = null;
            }

            if (videoParticipant != null) {
                if (fullscreenUsersListView.getVisibility() != View.VISIBLE) {
                    fullscreenUsersListView.setVisibility(View.VISIBLE);
                    fullscreenAdapter.update(false, fullscreenUsersListView);
                    delayedGroupCallUpdated = true;
                    if (!renderersContainer.inFullscreenMode) {
                        fullscreenAdapter.scrollTo(videoParticipant, fullscreenUsersListView);
                    }
                    listView.getViewTreeObserver().addOnPreDrawListener(requestFullscreenListener = new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            listView.getViewTreeObserver().removeOnPreDrawListener(this);
                            requestFullscreenListener = null;
                            renderersContainer.requestFullscreen(videoParticipant);
                            AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
                            return false;
                        }
                    });
                } else {
                    renderersContainer.requestFullscreen(videoParticipant);
                    AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
                }
            } else {
                if (listView.getVisibility() != View.VISIBLE) {
                    listView.setVisibility(View.VISIBLE);
                    applyCallParticipantUpdates(false);
                    delayedGroupCallUpdated = true;
                    listView.getViewTreeObserver().addOnPreDrawListener(requestFullscreenListener = new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            listView.getViewTreeObserver().removeOnPreDrawListener(this);
                            renderersContainer.requestFullscreen(null);
                            AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
                            return false;
                        }
                    });
                } else {
                    listView.getViewTreeObserver().addOnPreDrawListener(requestFullscreenListener = new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            listView.getViewTreeObserver().removeOnPreDrawListener(this);
                            renderersContainer.requestFullscreen(null);
                            AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
                            return false;
                        }
                    });
                }
            }
        }
    }

    public void enableCamera() {
        cameraButton.callOnClick();
    }

    private void checkContentOverlayed() {
        boolean overlayed = !avatarPriviewTransitionInProgress && blurredView.getVisibility() == View.VISIBLE && blurredView.getAlpha() == 1f;

        if (contentFullyOverlayed != overlayed) {
            contentFullyOverlayed = overlayed;
            buttonsContainer.invalidate();
            containerView.invalidate();
            listView.invalidate();
        }
    }

    private void updateScheduleUI(boolean animation) {
        if ((scheduleTimerContainer == null || call != null) && scheduleAnimator == null) {
            scheduleButtonsScale = 1.0f;
            switchToButtonInt2 = 1.0f;
            switchToButtonProgress = 1.0f;
            if (scheduleTimerContainer == null) {
                return;
            }
        }
        if (!animation) {
            AndroidUtilities.cancelRunOnUIThread(updateSchedeulRunnable);
            updateSchedeulRunnable.run();
            if (call == null || call.isScheduled()) {
                listView.setVisibility(View.INVISIBLE);
            } else {
                listView.setVisibility(View.VISIBLE);
            }
            if (ChatObject.isChannelOrGiga(currentChat)) {
                leaveItem.setText(getString(R.string.VoipChannelCancelChat));
            } else {
                leaveItem.setText(getString(R.string.VoipGroupCancelChat));
            }
        }
        float scheduleButtonsScale2;
        float alpha;
        if (switchToButtonProgress > 0.6f) {
            scheduleButtonsScale = scheduleButtonsScale2 = 1.05f - CubicBezierInterpolator.DEFAULT.getInterpolation((switchToButtonProgress - 0.6f) / 0.4f) * 0.05f;
            switchToButtonInt2 = 1.0f;
            alpha = 1.0f;
        } else {
            scheduleButtonsScale = 1.0f + CubicBezierInterpolator.DEFAULT.getInterpolation(switchToButtonProgress / 0.6f) * 0.05f;
            switchToButtonInt2 = CubicBezierInterpolator.DEFAULT.getInterpolation(switchToButtonProgress / 0.6f);
            scheduleButtonsScale2 = 1.05f * CubicBezierInterpolator.DEFAULT.getInterpolation(switchToButtonProgress / 0.6f);
            alpha = switchToButtonProgress / 0.6f;
        }

        float muteButtonScale = isLandscapeMode ? scheduleButtonsScale2 * dp(52) / (float) (muteButton.getMeasuredWidth() - dp(8)) : scheduleButtonsScale2;

        float reversedAlpha = 1.0f - alpha;
        leaveButton.setAlpha(alpha);
        soundButton.setAlpha(alpha * (soundButton.isEnabled() ? 1.0f : 0.5f));
        muteButton.setAlpha(alpha);
        scheduleTimerContainer.setAlpha(reversedAlpha);
        scheduleStartInTextView.setAlpha(alpha);
        scheduleStartAtTextView.setAlpha(alpha);
        scheduleTimeTextView.setAlpha(alpha);
        muteLabel[0].setAlpha(alpha);
        scheduleTimeTextView.setScaleX(scheduleButtonsScale2);
        scheduleTimeTextView.setScaleY(scheduleButtonsScale2);
        leaveButton.setScaleX(scheduleButtonsScale2);
        leaveButton.setScaleY(scheduleButtonsScale2);
        soundButton.setScaleX(scheduleButtonsScale2);
        soundButton.setScaleY(scheduleButtonsScale2);
        muteButton.setScaleX(muteButtonScale);
        muteButton.setScaleY(muteButtonScale);
        scheduleButtonTextView.setScaleX(reversedAlpha);
        scheduleButtonTextView.setScaleY(reversedAlpha);
        scheduleButtonTextView.setAlpha(reversedAlpha);
        scheduleInfoTextView.setAlpha(reversedAlpha);
        cameraButton.setAlpha(alpha);
        cameraButton.setScaleY(scheduleButtonsScale2);
        cameraButton.setScaleX(scheduleButtonsScale2);
        flipButton.setAlpha(alpha);
        flipButton.setScaleY(scheduleButtonsScale2);
        flipButton.setScaleX(scheduleButtonsScale2);
        speakerButton.setAlpha(alpha);
        speakerButtonContainer.setScaleY(scheduleButtonsScale2);
        speakerButtonContainer.setScaleX(scheduleButtonsScale2);
        otherItem.setAlpha(alpha);
        int newVisibility = reversedAlpha == 0.0f ? View.INVISIBLE : View.VISIBLE;
        if (newVisibility != scheduleTimerContainer.getVisibility()) {
            scheduleTimerContainer.setVisibility(newVisibility);
            scheduleButtonTextView.setVisibility(newVisibility);
        }
    }

    private void initCreatedGroupCall() {
        if (callInitied) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        callInitied = true;
        oldParticipants.addAll(call.visibleParticipants);
        oldVideoParticipants.addAll(visibleVideoParticipants);
        oldInvited.addAll(call.invitedUsers);
        oldShadyJoin.addAll(call.shadyJoinParticipants);
        oldShadyLeft.addAll(call.shadyLeftParticipants);
        currentCallState = service.getCallState();
        if (call == null) {
            call = service.groupCall;
            fullscreenAdapter.setGroupCall(call);
            renderersContainer.setGroupCall(call);
            tabletGridAdapter.setGroupCall(call);
        }
        actionBar.setTitleRightMargin(dp(48) * 2);
        call.saveActiveDates();
        VoIPService.getSharedInstance().registerStateListener(this);
        if (scheduleTimeTextView != null && scheduleTimeTextView.getVisibility() == View.VISIBLE) {
            leaveButton.setData(R.drawable.msg_voiceclose, 0xffffffff, Theme.getColor(Theme.key_voipgroup_leaveButton), 0.3f, false, getString(R.string.VoipGroupLeave), false, true);
            updateSpeakerPhoneIcon(true);
            leaveItem.setText(ChatObject.isChannelOrGiga(currentChat) ? getString(R.string.VoipChannelEndChat) : getString(R.string.VoipGroupEndChat));
            listView.setVisibility(View.VISIBLE);
            pipItem.setVisibility(View.VISIBLE);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(listView, View.ALPHA, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(listView, View.TRANSLATION_Y, dp(200), 0.0f),
                    ObjectAnimator.ofFloat(scheduleTimeTextView, View.SCALE_X, 0.0f),
                    ObjectAnimator.ofFloat(scheduleTimeTextView, View.SCALE_Y, 0.0f),
                    ObjectAnimator.ofFloat(scheduleTimeTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartInTextView, View.SCALE_X, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartInTextView, View.SCALE_Y, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartInTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartAtTextView, View.SCALE_X, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartAtTextView, View.SCALE_Y, 0.0f),
                    ObjectAnimator.ofFloat(scheduleStartAtTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(pipItem, View.SCALE_X, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(pipItem, View.SCALE_Y, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(pipItem, View.ALPHA, 0.0f, 1.0f));
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    scheduleTimeTextView.setVisibility(View.INVISIBLE);
                    scheduleStartAtTextView.setVisibility(View.INVISIBLE);
                    scheduleStartInTextView.setVisibility(View.INVISIBLE);
                }
            });
            animatorSet.setDuration(300);
            animatorSet.start();
        }
    }

    private void updateSubtitle() {
        if (actionBar == null || call == null) {
            return;
        }
        SpannableStringBuilder spannableStringBuilder = null;
        int speakingIndex = 0;
        for (int i = 0; i < call.currentSpeakingPeers.size(); i++) {
            long key = call.currentSpeakingPeers.keyAt(i);
            TLRPC.GroupCallParticipant participant = call.currentSpeakingPeers.get(key);
            if (participant.self || renderersContainer.isVisible(participant) || visiblePeerIds.get(key, 0) == 1) {
                continue;
            }
            long peerId = MessageObject.getPeerId(participant.peer);
            if (spannableStringBuilder == null) {
                spannableStringBuilder = new SpannableStringBuilder();
            }
            if (speakingIndex < 2) {
                TLRPC.User user = peerId > 0 ? MessagesController.getInstance(currentAccount).getUser(peerId) : null;
                TLRPC.Chat chat = peerId <= 0 ? MessagesController.getInstance(currentAccount).getChat(peerId) : null;
                if (user == null && chat == null) {
                    continue;
                }
                if (speakingIndex != 0) {
                    spannableStringBuilder.append(", ");
                }
                if (user != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        spannableStringBuilder.append(UserObject.getFirstName(user), new TypefaceSpan(AndroidUtilities.bold()), 0);
                    } else {
                        spannableStringBuilder.append(UserObject.getFirstName(user));
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        spannableStringBuilder.append(chat.title, new TypefaceSpan(AndroidUtilities.bold()), 0);
                    } else {
                        spannableStringBuilder.append(chat.title);
                    }
                }
            }
            speakingIndex++;
            if (speakingIndex == 2) {
                break;
            }
        }
        boolean drawStatus;
        if (speakingIndex > 0) {
            String s = LocaleController.getPluralString("MembersAreSpeakingToast", speakingIndex);
            int replaceIndex = s.indexOf("un1");
            SpannableStringBuilder spannableStringBuilder1 = new SpannableStringBuilder(s);
            spannableStringBuilder1.replace(replaceIndex, replaceIndex + 3, spannableStringBuilder);
            actionBar.getAdditionalSubtitleTextView().setText(spannableStringBuilder1);
            drawStatus = true;
        } else {
            drawStatus = false;
        }
        actionBar.getSubtitleTextView().setText(LocaleController.formatPluralString(isRtmpStream() ? "ViewersWatching" : "Participants", call.call.participants_count + (listAdapter.addSelfToCounter() ? 1 : 0)));
        if (isRtmpStream()) {
            if (watchersView != null) {
                watchersView.setWatchersCount(call.call.participants_count);
            }
        }
        if (drawStatus != drawSpeakingSubtitle) {
            drawSpeakingSubtitle = drawStatus;
            actionBar.invalidate();
            actionBar.getSubtitleTextView().setPivotX(0);
            actionBar.getSubtitleTextView().setPivotY(actionBar.getMeasuredHeight() >> 1);
            actionBar.getSubtitleTextView().animate().scaleX(drawSpeakingSubtitle ? 0.98f : 1f).scaleY(drawSpeakingSubtitle ? 0.9f : 1f).alpha(drawSpeakingSubtitle ? 0f : 1f).setDuration(150);
            AndroidUtilities.updateViewVisibilityAnimated(actionBar.getAdditionalSubtitleTextView(), drawSpeakingSubtitle);
        }
    }

    @Override
    public void show() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 2048);
        super.show();

        if (RTMPStreamPipOverlay.isVisible()) {
            RTMPStreamPipOverlay.dismiss();
        }
    }

    @Override
    public void dismissInternal() {
        if (renderersContainer != null) {
            if (requestFullscreenListener != null) {
                listView.getViewTreeObserver().removeOnPreDrawListener(requestFullscreenListener);
                requestFullscreenListener = null;
            }
            attachedRenderersTmp.clear();
            attachedRenderersTmp.addAll(attachedRenderers);
            for (int i = 0; i < attachedRenderersTmp.size(); i++) {
                attachedRenderersTmp.get(i).saveThumb();
                renderersContainer.removeView(attachedRenderersTmp.get(i));
                attachedRenderersTmp.get(i).release();
                attachedRenderersTmp.get(i).forceDetach(true);
            }
            attachedRenderers.clear();

            if (renderersContainer.getParent() != null) {
                attachedRenderers.clear();
                containerView.removeView(renderersContainer);
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 2048);
        super.dismissInternal();
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().unregisterStateListener(this);
            VoIPService.getSharedInstance().setSinks(null, null);
        }
        if (groupCallInstance == this) {
            groupCallInstance = null;
        }

        groupCallUiVisible = false;

        VoIPService.audioLevelsCallback = null;
        GroupCallPip.updateVisibility(getContext());
        if (call != null) {
            call.clearVideFramesInfo();
        }
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().clearRemoteSinks();
        }
        if (pipSource != null) {
            pipSource.destroy();
            pipSource = null;
        }
    }

    public final static float MAX_AMPLITUDE = 8_500f;

    private void setAmplitude(double value) {
        animateToAmplitude = (float) (Math.min(MAX_AMPLITUDE, value) / MAX_AMPLITUDE);
        animateAmplitudeDiff = (animateToAmplitude - amplitude) / (100 + 500.0f * BlobDrawable.AMPLITUDE_SPEED);
    }

    @Override
    public void onStateChanged(int state) {
        currentCallState = state;
        updateState(isShowing(), false);
    }

    public UndoView getUndoView() {
        if (!isTabletMode && renderersContainer.inFullscreenMode) {
            return renderersContainer.getUndoView();
        }
        if (undoView[0].getVisibility() == View.VISIBLE) {
            UndoView old = undoView[0];
            undoView[0] = undoView[1];
            undoView[1] = old;
            old.hide(true, 2);
            containerView.removeView(undoView[0]);
            containerView.addView(undoView[0]);
        }
        return undoView[0];
    }

    private float getColorProgress() {
        return colorProgress;
    }

    private void updateTitle(boolean animated) {
        if (call == null) {
            if (ChatObject.isChannelOrGiga(currentChat)) {
                titleTextView.setText(getString(R.string.VoipChannelScheduleVoiceChat), animated);
            } else {
                titleTextView.setText(getString(R.string.VoipGroupScheduleVoiceChat), animated);
            }
            return;
        }
        if (!TextUtils.isEmpty(call.call.title)) {
            if (!call.call.title.equals(actionBar.getTitle())) {
                if (animated) {
                    actionBar.setTitleAnimated(call.call.title, true, 180);
                    actionBar.getTitleTextView().setOnClickListener(v -> {
                        if (call != null && call.recording) {
                            showRecordHint(actionBar.getTitleTextView());
                        }
                    });
                } else {
                    actionBar.setTitle(call.call.title);
                }
                titleTextView.setText(call.call.title, animated);
            }
        } else {
            if (currentChat != null && !currentChat.title.equals(actionBar.getTitle())) {
                if (animated) {
                    actionBar.setTitleAnimated(currentChat.title, true, 180);
                    actionBar.getTitleTextView().setOnClickListener(v -> {
                        if (call != null && call.recording) {
                            showRecordHint(actionBar.getTitleTextView());
                        }
                    });
                } else {
                    actionBar.setTitle(currentChat.title);
                }
                if (ChatObject.isChannelOrGiga(currentChat)) {
                    if (isRtmpStream()) {
                        titleTextView.setText(currentChat.title, animated);
                    } else {
                        titleTextView.setText(getString(R.string.VoipChannelVoiceChat), animated);
                    }
                } else {
                    titleTextView.setText(getString(R.string.VoipGroupVoiceChat), animated);
                }
            } else if (currentChat == null) {
                actionBar.setTitle(getString(R.string.ConferenceChat));
                titleTextView.setText(getString(R.string.ConferenceChat), animated);
            }
        }
        SimpleTextView textView = actionBar.getTitleTextView();
        if (call.recording) {
            if (textView.getRightDrawable() == null) {
                textView.setRightDrawable(new SmallRecordCallDrawable(textView));
                TextView tv = titleTextView.getTextView();
                tv.setCompoundDrawablesWithIntrinsicBounds(null, null, new SmallRecordCallDrawable(tv), null);
                tv = titleTextView.getNextTextView();
                tv.setCompoundDrawablesWithIntrinsicBounds(null, null, new SmallRecordCallDrawable(tv), null);
            }
        } else {
            if (textView.getRightDrawable() != null) {
                textView.setRightDrawable(null);
                titleTextView.getTextView().setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                titleTextView.getNextTextView().setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }
    }

    private void setColorProgress(float progress) {
        colorProgress = progress;
        float finalColorProgress = colorProgress;
        float finalColorProgress2 = Math.max(colorProgress, renderersContainer == null ? 0 : renderersContainer.progressToFullscreenMode);
        backgroundColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled), Theme.getColor(Theme.key_voipgroup_actionBar), finalColorProgress, 1.0f);
        actionBarBackground.setBackgroundColor(backgroundColor);
        otherItem.redrawPopup(0xff232A33);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));

        navBarColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled), Theme.getColor(Theme.key_voipgroup_actionBar), finalColorProgress2, 1.0f);

        int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_listViewBackground), finalColorProgress, 1.0f);
        if (encryptionDrawable != null) {
            encryptionDrawable.setPaintBackgroundColor(color);
        }
        listViewBackgroundPaint.setColor(color);
        listView.setGlowColor(color);

        if (muteButtonState == MUTE_BUTTON_STATE_CONNECTING || isGradientState(muteButtonState)) {
            muteButton.invalidate();
        }

        if (buttonsBackgroundGradientView != null) {
            gradientColors[0] = backgroundColor;
            gradientColors[1] = Color.TRANSPARENT;
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                buttonsBackgroundGradient.setColors(gradientColors);
            } else {
                buttonsBackgroundGradientView.setBackground(buttonsBackgroundGradient = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, gradientColors));
            }
            buttonsBackgroundGradientView2.setBackgroundColor(gradientColors[0]);
        }

        color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_leaveButton), Theme.getColor(Theme.key_voipgroup_leaveButtonScrolled), finalColorProgress, 1.0f);
        leaveButton.setBackgroundColor(color, color);

        color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_lastSeenText), finalColorProgress, 1.0f);
        int color2 = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_mutedIconUnscrolled), Theme.getColor(Theme.key_voipgroup_mutedIcon), finalColorProgress, 1.0f);
        int featuredColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallTextCell) {
                final GroupCallTextCell cell = (GroupCallTextCell) child;
                if (isConference()) {
                    cell.setColors(featuredColor, featuredColor);
                } else {
                    cell.setColors(color2, color);
                }
            } else if (child instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) child;
                cell.setGrayIconColor(actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled, color2);
            } else if (child instanceof GroupCallInvitedCell) {
                GroupCallInvitedCell cell = (GroupCallInvitedCell) child;
                cell.setGrayIconColor(actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled, color2);
            }
        }
        containerView.invalidate();
        listView.invalidate();
        container.invalidate();
    }

    private String[] invites = new String[2];

    private void getLink(boolean copy) {
        TLRPC.Chat newChat = accountInstance.getMessagesController().getChat(getChatId());
        if (newChat != null && !ChatObject.isPublic(newChat)) {
            TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(getChatId());
            String url, username;
            if (!TextUtils.isEmpty(username = ChatObject.getPublicUsername(currentChat))) {
                url = accountInstance.getMessagesController().linkPrefix + "/" + username;
            } else {
                url = chatFull != null && chatFull.exported_invite != null ? chatFull.exported_invite.link : null;
            }
            if (TextUtils.isEmpty(url)) {
                TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
                req.peer = MessagesController.getInputPeer(currentChat);
                accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_chatInviteExported) {
                        TLRPC.TL_chatInviteExported invite = (TLRPC.TL_chatInviteExported) response;
                        if (chatFull != null) {
                            chatFull.exported_invite = invite;
                        } else {
                            openShareAlert(true, null, invite.link, copy);
                        }
                    }
                }));
            } else {
                openShareAlert(true, null, url, copy);
            }
        } else {
            if (call == null) {
                return;
            }
            for (int a = 0; a < 2; a++) {
                int num = a;
                TL_phone.exportGroupCallInvite req = new TL_phone.exportGroupCallInvite();
                req.call = call.getInputGroupCall();
                req.can_self_unmute = a == 1;
                accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TL_phone.exportedGroupCallInvite) {
                        TL_phone.exportedGroupCallInvite invite = (TL_phone.exportedGroupCallInvite) response;
                        invites[num] = invite.link;
                    } else {
                        invites[num] = "";
                    }
                    for (int b = 0; b < 2; b++) {
                        if (invites[b] == null) {
                            return;
                        } else if (invites[b].length() == 0) {
                            invites[b] = null;
                        }
                    }
                    if (!copy && canManageCall() && !call.call.join_muted) {
                        invites[0] = null;
                    }
                    if (invites[0] == null && invites[1] == null && ChatObject.isPublic(currentChat)) {
                        openShareAlert(true, null, accountInstance.getMessagesController().linkPrefix + "/" + ChatObject.getPublicUsername(currentChat), copy);
                    } else {
                        openShareAlert(false, invites[0], invites[1], copy);
                    }
                }));
            }
        }
    }

    private void openShareAlert(boolean withMessage, String urlMuted, String urlUnmuted, boolean copy) {
        if (isRtmpStream() && urlMuted != null) {
            urlUnmuted = null;
        }
        if (copy) {
            AndroidUtilities.addToClipboard(urlMuted != null ? urlMuted : urlUnmuted);
            if (AndroidUtilities.shouldShowClipboardToast()) {
                getUndoView().showWithAction(0, UndoView.ACTION_VOIP_LINK_COPIED, null, null, null, null);
            }
        } else {
            boolean keyboardIsOpen = false;
            if (parentActivity != null) {
                BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
                if (fragment instanceof ChatActivity) {
                    keyboardIsOpen = ((ChatActivity) fragment).needEnterText();
                    anyEnterEventSent = true;
                    enterEventSent = true;
                }
            }
            if (urlMuted != null && urlUnmuted == null) {
                urlUnmuted = urlMuted;
                urlMuted = null;
            }

            String message;
            if (urlMuted == null && withMessage) {
                if (ChatObject.isChannelOrGiga(currentChat)) {
                    message = formatString("VoipChannelInviteText", R.string.VoipChannelInviteText, urlUnmuted);
                } else {
                    message = formatString("VoipGroupInviteText", R.string.VoipGroupInviteText, urlUnmuted);
                }
            } else {
                message = urlUnmuted;
            }
            shareAlert = new ShareAlert(getContext(), null, null, message, urlMuted, false, urlUnmuted, urlMuted, false, true) {
                @Override
                protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                    if (!showToast) return;
                    if (dids.size() == 1) {
                        getUndoView().showWithAction(dids.valueAt(0).id, UndoView.ACTION_VOIP_INVITE_LINK_SENT, count);
                    } else {
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_INVITE_LINK_SENT, count, dids.size(), null, null);
                    }
                }
            };
            shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
                @Override
                public boolean didCopy() {
                    if (AndroidUtilities.shouldShowClipboardToast()) {
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_LINK_COPIED, null, null, null, null);
                    }
                    return true;
                }
            });
            shareAlert.setOnDismissListener(dialog -> shareAlert = null);
            AndroidUtilities.runOnUIThread(() -> {
                if (shareAlert != null) {
                    shareAlert.show();
                }
            }, keyboardIsOpen ? 200 : 0);
        }
    }

    private void inviteUserToCall(long id, boolean shouldAdd) {
        if (call == null) {
            return;
        }
        TLRPC.User user = accountInstance.getMessagesController().getUser(id);
        if (user == null) {
            return;
        }
        final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER)};
        TL_phone.inviteToGroupCall req = new TL_phone.inviteToGroupCall();
        req.call = call.getInputGroupCall();
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = user.id;
        inputUser.access_hash = user.access_hash;
        req.users.add(inputUser);
        int requestId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (call != null && !delayedGroupCallUpdated) {
                        call.addInvitedUser(id);
                        applyCallParticipantUpdates(true);
                        if (groupVoipInviteAlert != null) {
                            groupVoipInviteAlert.dismiss();
                        }
                        try {
                            progressDialog[0].dismiss();
                        } catch (Throwable ignore) {

                        }
                        progressDialog[0] = null;
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_INVITED, user, currentChat, null, null);
                    }
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog[0].dismiss();
                    } catch (Throwable ignore) {

                    }
                    progressDialog[0] = null;
                    if (shouldAdd && "USER_NOT_PARTICIPANT".equals(error.text)) {
                        processSelectedOption(null, id, 3);
                    } else {
                        BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                    }
                });
            }
        });
        if (requestId != 0) {
            AndroidUtilities.runOnUIThread(() -> {
                if (progressDialog[0] == null) {
                    return;
                }
                progressDialog[0].setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(requestId, true));
                progressDialog[0].show();
            }, 500);
        }
    }

    ObjectAnimator subtitleYAnimator;
    ObjectAnimator additionalSubtitleYAnimator;

    public void invalidateActionBarAlpha() {
        actionBar.setAlpha((actionBar.getTag() != null ? 1f : 0f) * (1f - renderersContainer.progressToFullscreenMode));
    }

    private void updateLayout(boolean animated) {
        float minY = Integer.MAX_VALUE;
        int N = listView.getChildCount();
        for (int a = 0; a < N; a++) {
            View child = listView.getChildAt(a);
            if (listView.getChildAdapterPosition(child) >= 0) {
                minY = Math.min(minY, child.getTop());
            }
        }
        if (minY < 0 || minY == Integer.MAX_VALUE) {
            minY = N != 0 ? 0 : listView.getPaddingTop();
        }
        boolean show = minY <= ActionBar.getCurrentActionBarHeight() - dp(14);
        minY += ActionBar.getCurrentActionBarHeight() + dp(14);
        if (show && actionBar.getTag() == null || !show && actionBar.getTag() != null) {
            actionBar.setTag(show ? 1 : null);
            if (actionBarAnimation != null) {
                actionBarAnimation.cancel();
                actionBarAnimation = null;
            }
            setUseLightStatusBar(actionBar.getTag() == null);

            actionBar.getBackButton().animate()
                    .scaleX(show ? 1.0f : 0.9f)
                    .scaleY(show ? 1.0f : 0.9f)
                    .translationX(show ? 0.0f : -dp(14))
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();

            actionBar.getTitleTextView().animate()
                    .translationY(show ? 0.0f : dp(23))
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();

            if (subtitleYAnimator != null) {
                subtitleYAnimator.removeAllListeners();
                subtitleYAnimator.cancel();
            }
            subtitleYAnimator = ObjectAnimator.ofFloat(actionBar.getSubtitleTextView(), View.TRANSLATION_Y, actionBar.getSubtitleTextView().getTranslationY(), show ? 0.0f : dp(20));
            subtitleYAnimator.setDuration(300);
            subtitleYAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            subtitleYAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    subtitleYAnimator = null;
                    actionBar.getSubtitleTextView().setTranslationY(show ? 0.0f : dp(20));
                }
            });
            subtitleYAnimator.start();

            if (additionalSubtitleYAnimator != null) {
                additionalSubtitleYAnimator.cancel();
            }
            additionalSubtitleYAnimator = ObjectAnimator.ofFloat(actionBar.getAdditionalSubtitleTextView(), View.TRANSLATION_Y, show ? 0.0f : dp(20));
            additionalSubtitleYAnimator.setDuration(300);
            additionalSubtitleYAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            additionalSubtitleYAnimator.start();

            actionBarAnimation = new AnimatorSet();
            actionBarAnimation.setDuration(140);
            actionBarAnimation.playTogether(
                    ObjectAnimator.ofFloat(actionBar, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarBackground, View.ALPHA, show ? 1.0f : 0.0f),
                    ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, show ? 1.0f : 0.0f));
            actionBarAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    actionBarAnimation = null;
                }
            });
            actionBarAnimation.start();

            renderersContainer.pipView.setClickable(!show || isLandscapeMode);
        }
        if (scrollOffsetY != minY) {
            setScrollOffsetY(minY);
        }
    }

    public void invalidateScrollOffsetY() {
        setScrollOffsetY(scrollOffsetY);
    }

    private void setScrollOffsetY(float scrollOffsetY) {
        this.scrollOffsetY = scrollOffsetY;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        listView.setTopGlowOffset((int) ((scrollOffsetY) - layoutParams.topMargin));

        int offset = dp(74);
        float t = scrollOffsetY - offset;
        int diff;
        if (t + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() * 2) {
            int willMoveUpTo = offset - backgroundPaddingTop - dp(14) + ActionBar.getCurrentActionBarHeight();
            float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() * 2 - t - backgroundPaddingTop) / willMoveUpTo);
            diff = (int) (dp(AndroidUtilities.isTablet() ? 17 : 13) * moveProgress);
            if (textureLightningView != null) {
                textureLightningView.setShadowOffset((int) (dp(8) * moveProgress));
            }
            float newProgress = Math.min(1.0f, moveProgress);
            if (Math.abs(newProgress - colorProgress) > 0.0001f) {
                setColorProgress(Math.min(1.0f, moveProgress));
            }
            titleTextView.setScaleX(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
            titleTextView.setScaleY(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
            titleTextView.setAlpha(Math.max(0.0f, 1.0f - moveProgress * 1.2f) * (1f - renderersContainer.progressToFullscreenMode));
            liveLabelTextView.setScaleX(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
            liveLabelTextView.setScaleY(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
            liveLabelTextView.setAlpha(Math.max(0.0f, 1.0f - moveProgress * 1.2f) * (1f - renderersContainer.progressToFullscreenMode));
        } else {
            diff = 0;
            titleTextView.setScaleX(1.0f);
            titleTextView.setScaleY(1.0f);
            titleTextView.setAlpha(1f - renderersContainer.progressToFullscreenMode);
            liveLabelTextView.setScaleX(1.0f);
            liveLabelTextView.setScaleY(1.0f);
            liveLabelTextView.setAlpha(1f - renderersContainer.progressToFullscreenMode);
            if (colorProgress > 0.0001f) {
                setColorProgress(0.0f);
            }
        }

        menuItemsContainer.setTranslationY(Math.max(dp(4), scrollOffsetY - dp(53) - diff));
        titleLayout.setTranslationY(Math.max(dp(4), scrollOffsetY - dp(44) - diff));
        if (textureLightningView != null) {
            textureLightningView.setTranslationY(Math.max(dp(4), scrollOffsetY - dp(37)));
        }
        if (scheduleTimerContainer != null) {
            scheduleTimerContainer.setTranslationY(Math.max(dp(4), scrollOffsetY - dp(44) - diff));
        }
        containerView.invalidate();
        updateTopBulletinY();
    }

    private void cancelMutePress() {
        if (scheduled) {
            scheduled = false;
            AndroidUtilities.cancelRunOnUIThread(pressRunnable);
        }
        if (pressed) {
            pressed = false;
            MotionEvent cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
            muteButton.onTouchEvent(cancel);
            cancel.recycle();
        }
    }

    private void updateState(boolean animated, boolean selfUpdated) {
        if (call == null || call.isScheduled()) {
            int state;
            if (canManageCall()) {
                state = MUTE_BUTTON_STATE_START_NOW;
            } else {
                state = call.call.schedule_start_subscribed ? MUTE_BUTTON_STATE_CANCEL_REMINDER : MUTE_BUTTON_STATE_SET_REMINDER;
            }
            updateMuteButton(state, animated);
            leaveButton.setData(R.drawable.msg_voiceclose, 0xffffffff, Theme.getColor(Theme.key_voipgroup_leaveButton), 0.3f, false, getString(R.string.Close), false, false);
            updateScheduleUI(false);
            return;
        }
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService == null) {
            return;
        }

        if (!voIPService.isConverting() && !voIPService.isSwitchingStream() && (creatingServiceTime == 0 || Math.abs(SystemClock.elapsedRealtime() - creatingServiceTime) > 3000) && (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING)) {
            cancelMutePress();
            updateMuteButton(MUTE_BUTTON_STATE_CONNECTING, animated);
        } else {
            if (userSwitchObject != null) {
                getUndoView().showWithAction(0, UndoView.ACTION_VOIP_USER_CHANGED, userSwitchObject, currentChat, null, null);
                userSwitchObject = null;
            }
            TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
            if (!voIPService.micSwitching && participant != null && !participant.can_self_unmute && participant.muted && !canManageCall()) {
                cancelMutePress();
                if (participant.raise_hand_rating != 0) {
                    updateMuteButton(MUTE_BUTTON_STATE_RAISED_HAND, animated);
                } else {
                    updateMuteButton(MUTE_BUTTON_STATE_MUTED_BY_ADMIN, animated);
                }
                voIPService.setMicMute(true, false, false);
            } else {
                boolean micMuted = voIPService.isMicMute();
                if (!voIPService.micSwitching && selfUpdated && participant != null && participant.muted && !micMuted) {
                    cancelMutePress();
                    voIPService.setMicMute(true, false, false);
                    micMuted = true;
                }
                if (micMuted) {
                    updateMuteButton(MUTE_BUTTON_STATE_UNMUTE, animated);
                } else {
                    updateMuteButton(MUTE_BUTTON_STATE_MUTE, animated);
                }
            }
        }

        boolean outgoingVideoIsActive = VoIPService.getSharedInstance() != null && VoIPService.getSharedInstance().getVideoState(false) == Instance.VIDEO_STATE_ACTIVE;


        TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
        boolean mutedByAdmin = participant != null && !participant.can_self_unmute && participant.muted && !canManageCall();
        boolean cameraButtonVisible;
        boolean flipButtonVisible;
        boolean soundButtonVisible;
        boolean speakerButtonVisible;
        if ((!mutedByAdmin && call.canRecordVideo() || outgoingVideoIsActive) && !isRtmpStream()) {
            cameraButtonVisible = true;
            soundButtonVisible = false;
        } else {
            cameraButtonVisible = false;
            soundButtonVisible = true;
        }

        if (outgoingVideoIsActive) {
            if (animated && flipButton.getVisibility() != View.VISIBLE) {
                flipButton.setScaleX(0.3f);
                flipButton.setScaleY(0.3f);
            }
            flipButtonVisible = true;
            speakerButtonVisible = false;
        } else {
            if (animated && speakerButtonContainer.getVisibility() != View.VISIBLE) {
                speakerButtonContainer.setScaleX(0.3f);
                speakerButtonContainer.setScaleY(0.3f);
            }
            flipButtonVisible = false;
            speakerButtonVisible = !mutedByAdmin;
        }

        int newButtonsVisibility = (flipButtonVisible ? 1 : 0) + (soundButtonVisible ? 2 : 0) + (cameraButtonVisible ? 4 : 0) + (renderersContainer != null && renderersContainer.inFullscreenMode ? 8 : 0) + (speakerButtonVisible ? 16 : 0);

        if (buttonsVisibility != 0 && buttonsVisibility != newButtonsVisibility && animated) {
            for (int i = 0; i < buttonsContainer.getChildCount(); i++) {
                View child = buttonsContainer.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE) {
                    buttonsAnimationParamsX.put(child, child.getX());
                    buttonsAnimationParamsY.put(child, child.getY());
                }
            }
            animateButtonsOnNextLayout = true;
        }

        boolean soundButtonChanged = (buttonsVisibility | 2) != (newButtonsVisibility | 2);
        buttonsVisibility = newButtonsVisibility;

        if (cameraButtonVisible) {
            cameraButton.setData(R.drawable.calls_video, Color.WHITE, 0, 1f, true, getString(R.string.VoipCamera), !outgoingVideoIsActive, animated);
            cameraButton.setChecked(true, false);
        } else {
            cameraButton.setVisibility(View.GONE);
        }

        if (flipButtonVisible) {
            flipButton.setData(0, Color.WHITE, 0, 1f, true, getString(R.string.VoipFlip), false, false);
            flipButton.setChecked(true, false);
        } else {
            flipButton.setVisibility(View.GONE);
        }

        if (speakerButtonVisible) {
            final int icon = getAudioOutputIcon(getAudioOutputValue());
            if (speakerIcon != icon) {
                AndroidUtilities.updateImageViewImageAnimated(speakerImageView, speakerIcon = icon);
            }
            speakerButtonContainer.setVisibility(View.VISIBLE);
            speakerButton.setData(0, Color.WHITE, 0, 1f, true, getAudioOutputName(getAudioOutputValue()), false, false);
            speakerButton.setChecked(getAudioOutputActive(getAudioOutputValue()), animated);
        } else {
            speakerButtonContainer.setVisibility(View.GONE);
        }

        boolean soundButtonWasVisible = soundButton.getVisibility() == View.VISIBLE;
        soundButton.setVisibility(soundButtonVisible ? View.VISIBLE : View.GONE);
        if (soundButtonChanged && soundButtonVisible) {
            updateSpeakerPhoneIcon(false);
        }

        if (soundButtonChanged) {
            float s = soundButtonVisible ? 1f : 0.3f;
            if (!animated) {
                soundButton.animate().cancel();
                soundButton.setScaleX(s);
                soundButton.setScaleY(s);
            } else {
                if (soundButtonVisible && !soundButtonWasVisible) {
                    soundButton.setScaleX(0.3f);
                    soundButton.setScaleY(0.3f);
                }
                soundButton.animate().scaleX(s).scaleY(s).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }

        float cameraScale;
        if (cameraButton.getVisibility() == View.VISIBLE) {
            cameraScale = 1f;
            cameraButton.showText(cameraScale == 1f, animated);
        } else {
            cameraScale = 0.3f;
        }

        if (this.cameraButtonScale != cameraScale) {
            cameraButtonScale = cameraScale;
            if (!animated) {
                cameraButton.animate().cancel();
                cameraButton.setScaleX(cameraScale);
                cameraButton.setScaleY(cameraScale);
            } else {
                cameraButton.animate().scaleX(cameraScale).scaleY(cameraScale).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }

        float flipButtonScale;
        if (isTabletMode) {
            flipButtonScale = 0.8f;
        } else {
            flipButtonScale = isLandscapeMode || (renderersContainer != null && renderersContainer.inFullscreenMode) ? 1f : 0.8f;
        }

        if (!outgoingVideoIsActive) {
            flipButtonScale = 0.3f;
        }
        if (!animated) {
            flipButton.animate().cancel();
            flipButton.setScaleX(flipButtonScale);
            flipButton.setScaleY(flipButtonScale);
        } else {
            flipButton.animate().scaleX(flipButtonScale).scaleY(flipButtonScale).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        flipButton.showText(flipButtonScale == 1f, animated);

        float speakerButtonScale;
        if (isTabletMode) {
            speakerButtonScale = 0.8f;
        } else {
            speakerButtonScale = isLandscapeMode || (renderersContainer != null && renderersContainer.inFullscreenMode) ? 1f : 0.8f;
        }

        if (outgoingVideoIsActive) {
            speakerButtonScale = 0.3f;
        }
        if (!animated) {
            speakerButtonContainer.animate().cancel();
            speakerButtonContainer.setScaleX(speakerButtonScale);
            speakerButtonContainer.setScaleY(speakerButtonScale);
        } else {
            speakerButtonContainer.animate().scaleX(speakerButtonScale).scaleY(speakerButtonScale).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        }
        speakerButton.showText(speakerButtonScale == 1f, animated);

        float soundButtonScale = outgoingVideoIsActive ? 0.3f : 1f;
        if (this.soundButtonScale != soundButtonScale) {
            this.soundButtonScale = soundButtonScale;
            if (!animated) {
                soundButton.animate().cancel();
                soundButton.setScaleX(soundButtonScale);
                soundButton.setScaleY(soundButtonScale);
            } else {
                soundButton.animate().scaleX(soundButtonScale).scaleY(soundButtonScale).setDuration(TRANSITION_DURATION).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        }

        updateLiveLabel(animated);
        if (isRtmpStream() && LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            if (currentCallState == VoIPService.STATE_ESTABLISHED) {
                needTextureLightning = true;
                runUpdateTextureLightningRunnable();
            } else {
                needTextureLightning = false;
                AndroidUtilities.cancelRunOnUIThread(updateTextureLightningRunnable);
            }
        }
    }

    private void updateLiveLabel(boolean animated) {
        if (isRtmpStream()) {
            liveLabelTextView.setVisibility(View.VISIBLE);
            boolean wasLive = ((int) liveLabelTextView.getTag()) == VoIPService.STATE_ESTABLISHED;
            boolean isLive = currentCallState == VoIPService.STATE_ESTABLISHED;
            liveLabelTextView.setTag(currentCallState);
            if (wasLive != isLive) {
                if (liveLabelBgColorAnimator != null) {
                    liveLabelBgColorAnimator.cancel();
                }
                if (animated) {
                    int fromColor = liveLabelPaint.getColor();
                    int toColor = isLive ? 0xffee3e4c : 0xff3d4657;
                    liveLabelBgColorAnimator = ValueAnimator.ofFloat(0f, 1f);
                    liveLabelBgColorAnimator.addUpdateListener(valueAnimator -> {
                        float progress = (float) valueAnimator.getAnimatedValue();
                        int color = AndroidUtilities.getOffsetColor(fromColor, toColor, progress, 1f);
                        liveLabelPaint.setColor(color);
                        liveLabelTextView.invalidate();

                        textureLightningViewAnimatingAlpha = isLive ? progress : (1 - progress);
                        if (textureLightningView != null && needTextureLightning) {
                            textureLightningView.invalidate();
                        }
                    });
                    liveLabelBgColorAnimator.setDuration(300);
                    liveLabelBgColorAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    liveLabelBgColorAnimator.addListener(new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            liveLabelBgColorAnimator = null;
                            liveLabelPaint.setColor(currentCallState == VoIPService.STATE_ESTABLISHED ? 0xffee3e4c : 0xff3d4657);
                            liveLabelTextView.invalidate();
                        }
                    });
                    liveLabelBgColorAnimator.start();
                } else {
                    liveLabelPaint.setColor(currentCallState == VoIPService.STATE_ESTABLISHED ? 0xffee3e4c : 0xff3d4657);
                    liveLabelTextView.invalidate();
                }
            }
        } else {
            liveLabelTextView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        updateSpeakerPhoneIcon(true);
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute()) {
            setMicAmplitude(0f);
        }
        if (listView.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateVisibleRows(listView);
        }
        if (fullscreenUsersListView.getVisibility() == View.VISIBLE) {
            AndroidUtilities.updateVisibleRows(fullscreenUsersListView);
        }
        attachedRenderersTmp.clear();
        attachedRenderersTmp.addAll(attachedRenderers);
        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
            attachedRenderersTmp.get(i).updateAttachState(true);
        }
    }

    private void updateSpeakerPhoneIcon(boolean animated) {
        if (soundButton == null || soundButton.getVisibility() != View.VISIBLE) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null || isRtmpStream()) {
            soundButton.setData(R.drawable.msg_voiceshare, Color.WHITE, 0, 0.3f, true, getString(R.string.VoipChatShare), false, animated);
            soundButton.setEnabled(ChatObject.isPublic(currentChat) || ChatObject.hasAdminRights(currentChat) && ChatObject.canAddUsers(currentChat), false);
            soundButton.setChecked(true, false);
            return;
        } else {
            soundButton.setEnabled(true, animated);
        }

        boolean bluetooth = service.isBluetoothOn() || service.isBluetoothWillOn();
        boolean checked = !bluetooth && service.isSpeakerphoneOn();

        if (bluetooth) {
            soundButton.setData(R.drawable.calls_bluetooth, Color.WHITE, 0, 0.1f, true, getString(R.string.VoipAudioRoutingBluetooth), false, animated);
        } else if (checked) {
            soundButton.setData(R.drawable.calls_speaker, Color.WHITE, 0, 0.3f, true, getString(R.string.VoipSpeaker), false, animated);
        } else {
            if (service.isHeadsetPlugged()) {
                soundButton.setData(R.drawable.calls_headphones, Color.WHITE, 0, 0.1f, true, getString(R.string.VoipAudioRoutingHeadset), false, animated);
            } else {
                soundButton.setData(R.drawable.calls_speaker, Color.WHITE, 0, 0.1f, true, getString(R.string.VoipSpeaker), false, animated);
            }
        }
        soundButton.setChecked(checked, animated);

        final int icon = getAudioOutputIcon(getAudioOutputValue());
        if (speakerIcon != icon) {
            AndroidUtilities.updateImageViewImageAnimated(speakerImageView, speakerIcon = icon);
        }
        if (speakerButtonContainer.getVisibility() == View.VISIBLE) {
            speakerButton.setData(0, Color.WHITE, 0, 1f, true, getAudioOutputName(getAudioOutputValue()), false, false);
            speakerButton.setChecked(getAudioOutputActive(getAudioOutputValue()), animated);
        }
    }

    private void updateMuteButton(int state, boolean animated) {
        boolean fullscreen = renderersContainer != null && renderersContainer.inFullscreenMode && (AndroidUtilities.isTablet() || isLandscapeMode == isRtmpLandscapeMode());
        if (!isRtmpStream() && muteButtonState == state && animated) {
            return;
        }
        if (muteButtonAnimator != null) {
            muteButtonAnimator.cancel();
            muteButtonAnimator = null;
        }
        if (expandAnimator != null) {
            expandAnimator.cancel();
            expandAnimator = null;
        }

        String newText;
        String newSubtext;

        boolean changed;
        boolean mutedByAdmin = false;
        if (state == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
            newText = getString(R.string.VoipGroupCancelReminder);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(202);
        } else if (state == MUTE_BUTTON_STATE_SET_REMINDER) {
            newText = getString(R.string.VoipGroupSetReminder);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(344);
        } else if (state == MUTE_BUTTON_STATE_START_NOW) {
            newText = getString(R.string.VoipGroupStartNow);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(377);
        } else if (state == MUTE_BUTTON_STATE_UNMUTE) {
            newText = getString(R.string.VoipGroupUnmute);
            newSubtext = getString(R.string.VoipHoldAndTalk);
            if (muteButtonState == MUTE_BUTTON_STATE_CONNECTING) {
                int endFrame = bigMicDrawable.getCustomEndFrame();
                if (endFrame == 136 || endFrame == 173 || endFrame == 274 || endFrame == 311) {
                    changed = bigMicDrawable.setCustomEndFrame(99);
                } else {
                    changed = false;
                }
            } else if (muteButtonState == MUTE_BUTTON_STATE_START_NOW) {
                changed = bigMicDrawable.setCustomEndFrame(404);
            } else if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                changed = bigMicDrawable.setCustomEndFrame(376);
            } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                changed = bigMicDrawable.setCustomEndFrame(237);
            } else if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                changed = bigMicDrawable.setCustomEndFrame(36);
            } else {
                changed = bigMicDrawable.setCustomEndFrame(99);
            }
        } else if (state == MUTE_BUTTON_STATE_MUTE) {
            newText = getString(isConference() ? R.string.VoipTapToMuteConference : R.string.VoipTapToMute);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND ? 99 : 69);
        } else if (isConference() && state == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
            newText = getString(R.string.VoipMutedByAdmin);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(99);
        } else if (state == MUTE_BUTTON_STATE_RAISED_HAND) {
            newText = getString(R.string.VoipMutedTapedForSpeak);
            newSubtext = getString(R.string.VoipMutedTapedForSpeakInfo);
            changed = bigMicDrawable.setCustomEndFrame(136);
        } else {
            TLRPC.GroupCallParticipant participant = call.participants.get(MessageObject.getPeerId(selfPeer));
            if (mutedByAdmin = participant != null && !participant.can_self_unmute && participant.muted && !canManageCall()) {
                if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                    changed = bigMicDrawable.setCustomEndFrame(311);
                } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                    changed = bigMicDrawable.setCustomEndFrame(274);
                } else if (muteButtonState == MUTE_BUTTON_STATE_MUTE) {
                    changed = bigMicDrawable.setCustomEndFrame(173);
                } else {
                    changed = bigMicDrawable.setCustomEndFrame(136);
                }
            } else {
                if (muteButtonState == MUTE_BUTTON_STATE_START_NOW) {
                    changed = bigMicDrawable.setCustomEndFrame(404);
                } else if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                    changed = bigMicDrawable.setCustomEndFrame(376);
                } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                    changed = bigMicDrawable.setCustomEndFrame(237);
                } else if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND) {
                    changed = bigMicDrawable.setCustomEndFrame(36);
                } else {
                    changed = bigMicDrawable.setCustomEndFrame(99);
                }
            }
            if (state == MUTE_BUTTON_STATE_CONNECTING) {
                newText = getString(R.string.Connecting);
                newSubtext = "";
            } else {
                newText = getString(R.string.VoipMutedByAdmin);
                newSubtext = getString(R.string.VoipMutedTapForSpeak);
            }
        }
        if (isRtmpStream() && state != MUTE_BUTTON_STATE_CONNECTING && !call.isScheduled()) {
            newText = getString(fullscreen ? R.string.VoipGroupMinimizeStream : R.string.VoipGroupExpandStream);
            newSubtext = "";
            changed = animatingToFullscreenExpand != fullscreen;
            animatingToFullscreenExpand = fullscreen;
        }

        final String contentDescription;
        if (!TextUtils.isEmpty(newSubtext)) {
            contentDescription = newText + " " + newSubtext;
        } else {
            contentDescription = newText;
        }
        muteButton.setContentDescription(contentDescription);

        if (animated) {
            if (changed) {
                if (state == MUTE_BUTTON_STATE_START_NOW) {
                    bigMicDrawable.setCurrentFrame(376);
                } else if (state == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                    bigMicDrawable.setCurrentFrame(173);
                } else if (state == MUTE_BUTTON_STATE_SET_REMINDER) {
                    bigMicDrawable.setCurrentFrame(311);
                } else if (state == MUTE_BUTTON_STATE_UNMUTE) {
                    if (muteButtonState == MUTE_BUTTON_STATE_START_NOW) {
                        bigMicDrawable.setCurrentFrame(376);
                    } else if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                        bigMicDrawable.setCurrentFrame(344);
                    } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                        bigMicDrawable.setCurrentFrame(202);
                    } else if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                        bigMicDrawable.setCurrentFrame(0);
                    } else {
                        bigMicDrawable.setCurrentFrame(69);
                    }
                } else if (state == MUTE_BUTTON_STATE_MUTE || muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN && isConference()) {
                    bigMicDrawable.setCurrentFrame(muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND ? 69 : 36);
                } else if (state == MUTE_BUTTON_STATE_RAISED_HAND) {
                    bigMicDrawable.setCurrentFrame(99);
                } else {
                    if (mutedByAdmin) {
                        if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                            bigMicDrawable.setCurrentFrame(274);
                        } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                            bigMicDrawable.setCurrentFrame(237);
                        } else if (muteButtonState == MUTE_BUTTON_STATE_MUTE) {
                            bigMicDrawable.setCurrentFrame(136);
                        } else {
                            bigMicDrawable.setCurrentFrame(99);
                        }
                    } else {
                        if (muteButtonState == MUTE_BUTTON_STATE_START_NOW) {
                            bigMicDrawable.setCurrentFrame(376);
                        } else if (muteButtonState == MUTE_BUTTON_STATE_CANCEL_REMINDER) {
                            bigMicDrawable.setCurrentFrame(344);
                        } else if (muteButtonState == MUTE_BUTTON_STATE_SET_REMINDER) {
                            bigMicDrawable.setCurrentFrame(202);
                        } else if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN || muteButtonState == MUTE_BUTTON_STATE_RAISED_HAND) {
                            bigMicDrawable.setCurrentFrame(0);
                        } else {
                            bigMicDrawable.setCurrentFrame(69);
                        }
                    }
                }
            }
            muteButton.playAnimation();
            muteLabel[1].setVisibility(View.VISIBLE);
            muteLabel[1].setAlpha(0.0f);
            muteLabel[1].setTranslationY(-dp(5));
            muteLabel[1].setText(newText);

            if (isRtmpStream() && !call.isScheduled()) {
                muteButton.setAlpha(0f);
                boolean isExpanded = renderersContainer.inFullscreenMode && (AndroidUtilities.isTablet() || isLandscapeMode == isRtmpLandscapeMode());
                View hideView = isExpanded ? expandButton : minimizeButton;
                View showView = isExpanded ? minimizeButton : expandButton;
                float muteButtonScale = dp(52) / (float) (muteButton.getMeasuredWidth() - dp(8));
                boolean bigSize = AndroidUtilities.isTablet() ? !renderersContainer.inFullscreenMode : !renderersContainer.inFullscreenMode && !isLandscapeMode;
                boolean changedSize = wasExpandBigSize == null || bigSize != wasExpandBigSize;
                wasExpandBigSize = bigSize;

                if (expandSizeAnimator != null) {
                    expandSizeAnimator.cancel();
                    expandSizeAnimator = null;
                }

                if (changedSize) {
                    expandSizeAnimator = ValueAnimator.ofFloat(0, 1);
                    expandSizeAnimator.addUpdateListener(animation -> {
                        float multiplier = isLandscapeMode || isRtmpStream() ? muteButtonScale : lerp(1f, muteButtonScale, renderersContainer.progressToFullscreenMode);
                        showView.setScaleY(multiplier);
                    });
                    expandSizeAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            expandSizeAnimator = null;
                        }
                    });
                    expandSizeAnimator.start();
                } else {
                    float multiplier = isLandscapeMode || isRtmpStream() ? muteButtonScale : lerp(1f, muteButtonScale, renderersContainer.progressToFullscreenMode);
                    showView.setAlpha(1f);
                    showView.setScaleX(multiplier);
                    showView.setScaleY(multiplier);
                    hideView.setAlpha(0f);
                }

                if (changed) {
                    expandAnimator = ValueAnimator.ofFloat(0, 1);
                    expandAnimator.addUpdateListener(animation -> {
                        float val = (float) animation.getAnimatedValue();

                        float multiplier = isLandscapeMode || isRtmpStream() ? muteButtonScale : lerp(1f, muteButtonScale, renderersContainer.progressToFullscreenMode);

                        hideView.setAlpha(1f - val);
                        float scale = (0.1f + (1f - val) * 0.9f) * multiplier;
                        hideView.setScaleX(scale);
                        hideView.setScaleY(scale);

                        showView.setAlpha(val);
                        scale = (0.1f + val * 0.9f) * multiplier;
                        showView.setScaleX(scale);
                        showView.setScaleY(scale);
                    });
                    expandAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            expandAnimator = null;
                        }
                    });
                    expandAnimator.start();
                } else {
                    float multiplier = isLandscapeMode || isRtmpStream() ? muteButtonScale : lerp(1f, muteButtonScale, renderersContainer.progressToFullscreenMode);

                    showView.setAlpha(1f);
                    showView.setScaleX(multiplier);
                    showView.setScaleY(multiplier);
                    hideView.setAlpha(0f);
                }
            } else {
                muteButton.setAlpha(1f);
                expandButton.setAlpha(0f);
                minimizeButton.setAlpha(0f);
            }

            if (changed) {
                muteButtonAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
                muteButtonAnimator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    muteLabel[0].setAlpha(1.0f - v);
                    muteLabel[0].setTranslationY(dp(5) * v);
                    muteLabel[1].setAlpha(v);
                    muteLabel[1].setTranslationY(dp(-5 + 5 * v));
                });
                muteButtonAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (muteButtonAnimator != null) {
                            muteButtonAnimator = null;
                            TextView temp = muteLabel[0];
                            muteLabel[0] = muteLabel[1];
                            muteLabel[1] = temp;
                            temp.setVisibility(View.INVISIBLE);
                            for (int a = 0; a < 2; a++) {
                                muteLabel[a].setTranslationY(0);
                            }
                        }
                    }
                });
                muteButtonAnimator.setDuration(180);
                muteButtonAnimator.start();
            } else {
                muteLabel[0].setAlpha(0f);
                muteLabel[1].setAlpha(1f);

                TextView temp = muteLabel[0];
                muteLabel[0] = muteLabel[1];
                muteLabel[1] = temp;
                temp.setVisibility(View.INVISIBLE);
                for (int a = 0; a < 2; a++) {
                    muteLabel[a].setTranslationY(0);
                }
            }
            muteButtonState = state;
        } else {
            muteButtonState = state;
            bigMicDrawable.setCurrentFrame(bigMicDrawable.getCustomEndFrame() - 1, false, true);
            muteLabel[0].setText(newText);

            if (isRtmpStream() && !call.isScheduled()) {
                muteButton.setAlpha(0f);
                boolean isExpanded = renderersContainer != null && renderersContainer.inFullscreenMode && (AndroidUtilities.isTablet() || isLandscapeMode == isRtmpLandscapeMode());
                View hideView = isExpanded ? expandButton : minimizeButton;
                View showView = isExpanded ? minimizeButton : expandButton;

                showView.setAlpha(1f);
                hideView.setAlpha(0f);
            } else {
                muteButton.setAlpha(1f);
                expandButton.setAlpha(0f);
                minimizeButton.setAlpha(0f);
            }
        }
        updateMuteButtonState(animated);
    }

    private void fillColors(int state, int[] colorsToSet) {
        if (isRtmpStream()) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_disabledButton);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_rtmpButton), Theme.getColor(Theme.key_voipgroup_soundButtonActiveScrolled), colorProgress, 1.0f);
            colorsToSet[2] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f);
        } else if (state == MUTE_BUTTON_STATE_UNMUTE) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_unmuteButton2);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_soundButtonActive), Theme.getColor(Theme.key_voipgroup_soundButtonActiveScrolled), colorProgress, 1.0f);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_soundButton);
        } else if (state == MUTE_BUTTON_STATE_MUTE) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_muteButton2);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_soundButtonActive2), Theme.getColor(Theme.key_voipgroup_soundButtonActive2Scrolled), colorProgress, 1.0f);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_soundButton2);
        } else if (isGradientState(state)) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3);
            colorsToSet[1] = Theme.getColor(Theme.key_voipgroup_mutedByAdminMuteButton);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_mutedByAdminMuteButtonDisabled);
        } else {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_disabledButton);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_disabledButtonActive), Theme.getColor(Theme.key_voipgroup_disabledButtonActiveScrolled), colorProgress, 1.0f);
            colorsToSet[2] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f);
        }
        if (isGradientState(state)) {
            colorsToSet[3] = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), .50f);
        } else if (state == MUTE_BUTTON_STATE_MUTE) {
            colorsToSet[3] = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_soundButtonActive2), ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_muteButton), Theme.getColor(Theme.key_voipgroup_muteButton3), .50f), .75f);
        } else {
            colorsToSet[3] = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_unmuteButton2), Theme.getColor(Theme.key_voipgroup_unmuteButton), .50f);
        }
    }

    private void showRecordHint(View view) {
        if (recordHintView == null) {
            recordHintView = new HintView(getContext(), 8, true);
            recordHintView.setAlpha(0.0f);
            recordHintView.setVisibility(View.INVISIBLE);
            recordHintView.setShowingDuration(3000);
            containerView.addView(recordHintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
            if (ChatObject.isChannelOrGiga(currentChat)) {
                recordHintView.setText(getString(R.string.VoipChannelRecording));
            } else {
                recordHintView.setText(getString(R.string.VoipGroupRecording));
            }
            recordHintView.setBackgroundColor(0xea272f38, 0xffffffff);
        }
        recordHintView.setExtraTranslationY(-AndroidUtilities.statusBarHeight);
        recordHintView.showForView(view, true);
    }

    private void showReminderHint() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        if (preferences.getBoolean("reminderhint", false)) {
            return;
        }
        preferences.edit().putBoolean("reminderhint", true).commit();
        if (reminderHintView == null) {
            reminderHintView = new HintView(getContext(), 8);
            reminderHintView.setAlpha(0.0f);
            reminderHintView.setVisibility(View.INVISIBLE);
            reminderHintView.setShowingDuration(4000);
            containerView.addView(reminderHintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 0));
            reminderHintView.setText(getString(R.string.VoipChatReminderHint));
            reminderHintView.setBackgroundColor(0xea272f38, 0xffffffff);
        }
        reminderHintView.setExtraTranslationY(-AndroidUtilities.statusBarHeight);
        reminderHintView.showForView(muteButton, true);
    }

    private void updateMuteButtonState(boolean animated) {
        muteButton.invalidate();

        if (states[muteButtonState] == null) {
            states[muteButtonState] = new WeavingState(muteButtonState);
            if (muteButtonState == MUTE_BUTTON_STATE_CONNECTING) {
                states[muteButtonState].shader = null;
            } else {
                if (isGradientState(muteButtonState)) {
                    states[muteButtonState].shader = new LinearGradient(0, 400, 400, 0, new int[]{Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2)}, null, Shader.TileMode.CLAMP);
                } else if (muteButtonState == MUTE_BUTTON_STATE_MUTE) {
                    states[muteButtonState].shader = new RadialGradient(200, 200, 200, new int[]{Theme.getColor(Theme.key_voipgroup_muteButton), Theme.getColor(Theme.key_voipgroup_muteButton3)}, null, Shader.TileMode.CLAMP);
                } else {
                    states[muteButtonState].shader = new RadialGradient(200, 200, 200, new int[]{Theme.getColor(Theme.key_voipgroup_unmuteButton2), Theme.getColor(Theme.key_voipgroup_unmuteButton)}, null, Shader.TileMode.CLAMP);
                }
            }
        }
        if (states[muteButtonState] != currentState) {
            prevState = currentState;
            currentState = states[muteButtonState];
            if (prevState == null || !animated) {
                switchProgress = 1;
                prevState = null;
            } else {
                switchProgress = 0;
            }
        }

        if (!animated) {
            boolean showWaves = false;
            boolean showLighting = false;
            if (currentState != null) {
                showWaves = currentState.currentState == MUTE_BUTTON_STATE_MUTE || currentState.currentState == MUTE_BUTTON_STATE_UNMUTE;
                showLighting = currentState.currentState != MUTE_BUTTON_STATE_CONNECTING;
            }
            showWavesProgress = showWaves ? 1f : 0f;
            showLightingProgress = showLighting ? 1f : 0f;
        }

        buttonsContainer.invalidate();
    }

    private static void processOnLeave(ChatObject.Call call, boolean discard, long selfId, Runnable onLeave) {
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().hangUp(discard ? 1 : 0);
        }
        if (call != null) {
            TLRPC.GroupCallParticipant participant = call.participants.get(selfId);
            if (participant != null) {
                call.participants.delete(selfId);
                call.sortedParticipants.remove(participant);
                call.visibleParticipants.remove(participant);
                for (int i = 0; i < call.visibleVideoParticipants.size(); i++) {
                    ChatObject.VideoParticipant videoParticipant = call.visibleVideoParticipants.get(i);
                    if (MessageObject.getPeerId(videoParticipant.participant.peer) == MessageObject.getPeerId(participant.peer)) {
                        call.visibleVideoParticipants.remove(i);
                        i--;
                    }
                }
                call.call.participants_count--;
            }

            for (int i = 0; i < call.sortedParticipants.size(); i++) {
                TLRPC.GroupCallParticipant participant1 = call.sortedParticipants.get(i);
                participant1.lastActiveDate = participant1.lastSpeakTime;
            }
        }
        if (onLeave != null) {
            onLeave.run();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
    }

    public static void onLeaveClick(Context context, Runnable onLeave, boolean fromOverlayWindow) {
        onLeaveClick(context, onLeave, fromOverlayWindow, false);
    }

    public static void onLeaveClick(Context context, Runnable onLeave, boolean fromOverlayWindow, boolean doNotAsk) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        TLRPC.Chat currentChat = service.getChat();
        ChatObject.Call call = service.groupCall;

        long selfId = service.getSelfId();
        if (doNotAsk || !ChatObject.canManageCalls(currentChat)) {
            processOnLeave(call, false, selfId, onLeave);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (service.isConference()) {
            builder.setTitle(getString(R.string.VoipChannelLeaveConferenceAlertTitle));
            builder.setMessage(getString(R.string.VoipChannelLeaveConferenceAlertText));
        } else if (ChatObject.isChannelOrGiga(currentChat)) {
            builder.setTitle(getString(R.string.VoipChannelLeaveAlertTitle));
            builder.setMessage(getString(R.string.VoipChannelLeaveAlertText));
        } else {
            builder.setTitle(getString(R.string.VoipGroupLeaveAlertTitle));
            builder.setMessage(getString(R.string.VoipGroupLeaveAlertText));
        }

        int currentAccount = service.getAccount();

        CheckBoxCell[] cells = new CheckBoxCell[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        if (!service.isConference() || call != null && call.call != null && call.call.creator) {
            cells[0] = new CheckBoxCell(context, 1);
            cells[0].setBackground(Theme.getSelectorDrawable(false));
            if (fromOverlayWindow) {
                cells[0].setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            } else {
                cells[0].setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
                CheckBoxSquare checkBox = (CheckBoxSquare) cells[0].getCheckBoxView();
                checkBox.setColors(Theme.key_voipgroup_mutedIcon, Theme.key_voipgroup_listeningText, Theme.key_voipgroup_nameText);
            }
            cells[0].setTag(0);
            if (service.isConference()) {
                cells[0].setText(getString(R.string.VoipChannelLeaveConferenceAlertEndChat), "", false, false);
            } else if (ChatObject.isChannelOrGiga(currentChat)) {
                cells[0].setText(getString(R.string.VoipChannelLeaveAlertEndChat), "", false, false);
            } else {
                cells[0].setText(getString(R.string.VoipGroupLeaveAlertEndChat), "", false, false);
            }

            cells[0].setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            cells[0].setOnClickListener(v -> {
                Integer num = (Integer) v.getTag();
                cells[num].setChecked(!cells[num].isChecked(), true);
            });
        }

        builder.setView(linearLayout);
        builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);
        builder.setPositiveButton(getString(R.string.VoipGroupLeave), (dialogInterface, position) -> processOnLeave(call, cells[0] != null && cells[0].isChecked(), selfId, onLeave));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        if (fromOverlayWindow) {
            builder.setDimEnabled(false);
        }
        AlertDialog dialog = builder.create();
        if (fromOverlayWindow) {
            if (Build.VERSION.SDK_INT >= 26) {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            } else {
                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            }
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!fromOverlayWindow) {
            dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
        }
        dialog.show();
        if (!fromOverlayWindow) {
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
            }
            dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        }
    }

    private Paint scrimPaint;
    private GroupCallUserCell scrimView;
    private GroupCallGridCell scrimGridView;
    private GroupCallMiniTextureView scrimRenderer;
    private GroupCallFullscreenAdapter.GroupCallUserCell scrimFullscreenView;
    private boolean hasScrimAnchorView;
    private boolean avatarPriviewTransitionInProgress;
    private boolean scrimViewAttached;
    private int popupAnimationIndex = -1;
    private AnimatorSet scrimAnimatorSet;
    private ActionBarPopupWindow scrimPopupWindow;

    private void processSelectedOption(TLRPC.GroupCallParticipant participant, long peerId, int option) {
        VoIPService voIPService = VoIPService.getSharedInstance();
        if (voIPService == null) {
            return;
        }
        TLObject object;
        if (peerId > 0) {
            object = accountInstance.getMessagesController().getUser(peerId);
        } else {
            object = accountInstance.getMessagesController().getChat(-peerId);
        }
        if (option == 0 || option == 2 || option == 3) {
            if (option == 0) {
                if (VoIPService.getSharedInstance() == null) {
                    return;
                }
                VoIPService.getSharedInstance().editCallMember(object, true, null, null, null, null);
                getUndoView().showWithAction(0, UndoView.ACTION_VOIP_MUTED, object, null, null, null);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setDialogButtonColorKey(Theme.key_voipgroup_listeningText);

            TextView messageTextView = new TextView(getContext());
            messageTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

            FrameLayout frameLayout = new FrameLayout(getContext());
            builder.setView(frameLayout);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(dp(12));

            BackupImageView imageView = new BackupImageView(getContext());
            imageView.setRoundRadius(dp(20));
            frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

            avatarDrawable.setInfo(currentAccount, object);
            String name;
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                imageView.setForUserOrChat(user, avatarDrawable);
                name = UserObject.getFirstName(user);
            } else {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                imageView.setForUserOrChat(chat, avatarDrawable);
                name = chat.title;
            }

            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            final String title = currentChat != null ? currentChat.title : "";
            if (option == 2) {
                if (isConference()) {
                    textView.setText(getString(R.string.VoipConferenceRemoveMemberAlertTitle2));
                    messageTextView.setText(AndroidUtilities.replaceTags(formatString(R.string.VoipConferenceRemoveMemberAlertText2, name)));
                } else {
                    textView.setText(getString(R.string.VoipGroupRemoveMemberAlertTitle2));
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        messageTextView.setText(AndroidUtilities.replaceTags(formatString(R.string.VoipChannelRemoveMemberAlertText2, name, title)));
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(formatString(R.string.VoipGroupRemoveMemberAlertText2, name, title)));
                    }
                }
            } else {
                textView.setText(getString(R.string.VoipGroupAddMemberTitle));
                messageTextView.setText(AndroidUtilities.replaceTags(formatString(R.string.VoipGroupAddMemberText, name, title)));
            }

            frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

            if (option == 2) {
                builder.setPositiveButton(getString(R.string.VoipGroupUserRemove), (dialogInterface, i) -> {
                    if (isConference()) {
                        final VoIPService voip = VoIPService.getSharedInstance();
                        if (voip != null && voip.conference != null && object instanceof TLRPC.User) {
                            TLRPC.User user = (TLRPC.User) object;
                            voip.conference.kick(user.id);
                            call.addKickedUser(user.id);
                            getUndoView().showWithAction(0, UndoView.ACTION_VOIP_KICKED, user, null, null, null);
                        }
                    } else if (object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) object;
                        accountInstance.getMessagesController().deleteParticipantFromChat(getChatId(), user);
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_REMOVED, user, null, null, null);
                    } else {
                        TLRPC.Chat chat = (TLRPC.Chat) object;
                        accountInstance.getMessagesController().deleteParticipantFromChat(getChatId(), null, chat, false, false);
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_REMOVED, chat, null, null, null);
                    }
                });
            } else if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                builder.setPositiveButton(getString(R.string.VoipGroupAdd), (dialogInterface, i) -> {
                    BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
                    accountInstance.getMessagesController().addUserToChat(getChatId(), user, 0, null, fragment, () -> inviteUserToCall(peerId, false));
                });
            }
            builder.setNegativeButton(getString(R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
            dialog.show();
            if (option == 2) {
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
                }
            }
        } else if (option == 6) {
            parentActivity.switchToAccount(currentAccount, true);
            Bundle args = new Bundle();
            if (peerId > 0) {
                args.putLong("user_id", peerId);
            } else {
                args.putLong("chat_id", -peerId);
            }
            parentActivity.presentFragment(new ChatActivity(args));
            dismiss();
        } else if (option == 8) {
            parentActivity.switchToAccount(currentAccount, true);
            BaseFragment fragment = parentActivity.getActionBarLayout().getFragmentStack().get(parentActivity.getActionBarLayout().getFragmentStack().size() - 1);
            if (fragment instanceof ChatActivity) {
                if (((ChatActivity) fragment).getDialogId() == peerId) {
                    dismiss();
                    return;
                }
            }
            Bundle args = new Bundle();
            if (peerId > 0) {
                args.putLong("user_id", peerId);
            } else {
                args.putLong("chat_id", -peerId);
            }
            parentActivity.presentFragment(new ChatActivity(args));
            dismiss();
        } else if (option == 7) {
            voIPService.editCallMember(object, true, null, null, false, null);
            updateMuteButton(MUTE_BUTTON_STATE_MUTED_BY_ADMIN, true);
        } else if (option == 9) {
            if (currentAvatarUpdater != null && currentAvatarUpdater.isUploadingImage()) {
                return;
            }
            TLRPC.User user = accountInstance.getUserConfig().getCurrentUser();

            currentAvatarUpdater = new ImageUpdater(true, ImageUpdater.FOR_TYPE_USER, true);
            currentAvatarUpdater.setOpenWithFrontfaceCamera(true);
            currentAvatarUpdater.setForceDarkTheme(true);
            currentAvatarUpdater.setSearchAvailable(true, true);
            currentAvatarUpdater.setShowingFromDialog(true);
            currentAvatarUpdater.parentFragment = parentActivity.getActionBarLayout().getLastFragment();
            currentAvatarUpdater.setDelegate(avatarUpdaterDelegate = new AvatarUpdaterDelegate(peerId));


            currentAvatarUpdater.openMenu(user.photo != null && user.photo.photo_big != null && !(user.photo instanceof TLRPC.TL_userProfilePhotoEmpty), () -> accountInstance.getMessagesController().deleteUserPhoto(null), dialog -> {

            }, 0);
        } else if (option == 10) {
            AlertsCreator.createChangeBioAlert(participant.about, peerId, getContext(), currentAccount);
        } else if (option == 11) {
            AlertsCreator.createChangeNameAlert(peerId, getContext(), currentAccount);
        } else {
            if (option == 5) {
                voIPService.editCallMember(object, true, null, null, null, null);
                getUndoView().showWithAction(0, UndoView.ACTION_VOIP_MUTED_FOR_YOU, object);
                voIPService.setParticipantVolume(participant, 0);
            } else {
                if ((participant.flags & 128) != 0 && participant.volume == 0) {
                    participant.volume = 10000;
                    participant.volume_by_admin = false;
                    voIPService.editCallMember(object, false, null, participant.volume, null, null);
                } else {
                    voIPService.editCallMember(object, false, null, null, null, null);
                }
                voIPService.setParticipantVolume(participant, ChatObject.getParticipantVolume(participant));
                getUndoView().showWithAction(0, option == 1 ? UndoView.ACTION_VOIP_UNMUTED : UndoView.ACTION_VOIP_UNMUTED_FOR_YOU, object, null, null, null);
            }
        }
    }

    private boolean showMenuForCell(View rendererCell) {
        if (itemAnimator.isRunning() || getContext() == null) {
            return false;
        }
        if (avatarPriviewTransitionInProgress || avatarsPreviewShowed) {
            dismissAvatarPreview(true);
            return false;
        }
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
            return false;
        }

        clearScrimView();

        GroupCallUserCell view;
        if (rendererCell instanceof GroupCallGridCell) {
            GroupCallGridCell groupCallGridCell = ((GroupCallGridCell) rendererCell);
            if (groupCallGridCell.getParticipant() == call.videoNotAvailableParticipant) {
                return false;
            }
            view = new GroupCallUserCell(groupCallGridCell.getContext());
            long selfPeerId = MessageObject.getPeerId(selfPeer);
            view.setData(accountInstance, groupCallGridCell.getParticipant().participant, call, selfPeerId, null, false);
            if (view.rightDrawable != null) {
                view.rightDrawable.play();
            }
            hasScrimAnchorView = false;
            scrimGridView = groupCallGridCell;
            scrimRenderer = groupCallGridCell.getRenderer();
            if (!isTabletMode && !isLandscapeMode) {
                scrimViewAttached = true;
                containerView.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 14, 0));
            } else {
                scrimViewAttached = false;
            }
        } else if (rendererCell instanceof GroupCallFullscreenAdapter.GroupCallUserCell) {
            GroupCallFullscreenAdapter.GroupCallUserCell groupCallFullscreenCell = ((GroupCallFullscreenAdapter.GroupCallUserCell) rendererCell);
            if (groupCallFullscreenCell.getParticipant() == call.videoNotAvailableParticipant.participant) {
                return false;
            }
            view = new GroupCallUserCell(groupCallFullscreenCell.getContext());
            long selfPeerId = MessageObject.getPeerId(selfPeer);
            view.setData(accountInstance, groupCallFullscreenCell.getParticipant(), call, selfPeerId, null, false);
            if (view.rightDrawable != null) {
                view.rightDrawable.play();
            }
            hasScrimAnchorView = false;
            scrimFullscreenView = groupCallFullscreenCell;
            scrimRenderer = groupCallFullscreenCell.getRenderer();
            if (scrimRenderer != null && scrimRenderer.showingInFullscreen) {
                scrimRenderer = null;
            }
            containerView.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 14, 0));
            scrimViewAttached = true;
        } else {
            view = (GroupCallUserCell) rendererCell;
            hasScrimAnchorView = true;
            scrimViewAttached = true;
        }
        if (view == null) {
            return false;
        }

        boolean showWithAvatarPreview = !isLandscapeMode && !isTabletMode && !AndroidUtilities.isInMultiwindow;
        TLRPC.GroupCallParticipant participant = view.getParticipant();
        if (participant == null) {
            return false;
        }

        Rect rect = new Rect();

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
        popupLayout.setBackgroundDrawable(null);
        popupLayout.setPadding(0, 0, 0, 0);
        popupLayout.setOnTouchListener(new View.OnTouchListener() {

            private int[] pos = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        View contentView = scrimPopupWindow.getContentView();
                        contentView.getLocationInWindow(pos);
                        rect.set(pos[0], pos[1], pos[0] + contentView.getMeasuredWidth(), pos[1] + contentView.getMeasuredHeight());
                        if (!rect.contains((int) event.getX(), (int) event.getY())) {
                            scrimPopupWindow.dismiss();
                        }
                    }
                } else if (event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
                    if (scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                        scrimPopupWindow.dismiss();
                    }
                }
                return false;
            }
        });
        popupLayout.setDispatchKeyEventListener(keyEvent -> {
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_BACK && keyEvent.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                scrimPopupWindow.dismiss();
            }
        });

        LinearLayout buttonsLayout = new LinearLayout(getContext());
        LinearLayout volumeLayout = !participant.muted_by_you ? new LinearLayout(getContext()) : null;
        currentOptionsLayout = buttonsLayout;
        LinearLayout linearLayout = new LinearLayout(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                buttonsLayout.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (volumeLayout != null) {
                    volumeLayout.measure(MeasureSpec.makeMeasureSpec(buttonsLayout.getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    setMeasuredDimension(buttonsLayout.getMeasuredWidth(), buttonsLayout.getMeasuredHeight() + volumeLayout.getMeasuredHeight());
                } else {
                    setMeasuredDimension(buttonsLayout.getMeasuredWidth(), buttonsLayout.getMeasuredHeight());
                }
            }
        };
        linearLayout.setMinimumWidth(dp(240));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        VolumeSlider volumeSlider = null;

        int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_listViewBackground), colorProgress, 1.0f);
        if (volumeLayout != null && !view.isSelfUser() && !participant.muted_by_you && (!participant.muted || participant.can_self_unmute)) {
            Drawable shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            volumeLayout.setBackgroundDrawable(shadowDrawable);
            linearLayout.addView(volumeLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

            volumeSlider = new VolumeSlider(getContext(), participant);
            volumeLayout.addView(volumeSlider, LayoutHelper.MATCH_PARENT, 48);
        }

        buttonsLayout.setMinimumWidth(dp(240));
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        Drawable shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        buttonsLayout.setBackgroundDrawable(shadowDrawable);
        linearLayout.addView(buttonsLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, volumeSlider != null ? -8 : 0, 0, 0));

        ScrollView scrollView;
        if (Build.VERSION.SDK_INT >= 21) {
            scrollView = new ScrollView(getContext(), null, 0, R.style.scrollbarShapeStyle) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                }
            };
        } else {
            scrollView = new ScrollView(getContext());
        }
        scrollView.setClipToPadding(false);
        popupLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        long peerId = MessageObject.getPeerId(participant.peer);

        ArrayList<String> items = new ArrayList<>(2);
        ArrayList<Integer> icons = new ArrayList<>(2);
        ArrayList<Integer> options = new ArrayList<>(2);
        boolean isAdmin = false;
        if (participant.peer instanceof TLRPC.TL_peerUser) {
            if (ChatObject.isChannel(currentChat)) {
                TLRPC.ChannelParticipant p = accountInstance.getMessagesController().getAdminInChannel(participant.peer.user_id, getChatId());
                isAdmin = p != null && (p instanceof TLRPC.TL_channelParticipantCreator || p.admin_rights.manage_call);
            } else {
                TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(getChatId());
                if (chatFull != null && chatFull.participants != null) {
                    for (int a = 0, N = chatFull.participants.participants.size(); a < N; a++) {
                        TLRPC.ChatParticipant chatParticipant = chatFull.participants.participants.get(a);
                        if (chatParticipant.user_id == participant.peer.user_id) {
                            isAdmin = chatParticipant instanceof TLRPC.TL_chatParticipantAdmin || chatParticipant instanceof TLRPC.TL_chatParticipantCreator;
                            break;
                        }
                    }
                }
            }
        } else {
            isAdmin = peerId == -getChatId();
        }
        if (view.isSelfUser()) {
            if (isConference() && view.isHandRaised()) {
                items.add(getString(R.string.VoipGroupCancelRaiseHand));
                icons.add(R.drawable.msg_handdown);
                options.add(7);
            }

            items.add(view.hasAvatarSet() ? getString(R.string.VoipAddPhoto) : getString(R.string.VoipSetNewPhoto));
            icons.add(R.drawable.msg_addphoto);
            options.add(9);

            if (peerId > 0) {
                items.add(TextUtils.isEmpty(participant.about) ? getString(R.string.VoipAddBio) : getString(R.string.VoipEditBio));
            } else {
                items.add(TextUtils.isEmpty(participant.about) ? getString(R.string.VoipAddDescription) : getString(R.string.VoipEditDescription));
            }
            icons.add(TextUtils.isEmpty(participant.about) ? R.drawable.msg_addbio : R.drawable.msg_info);
            options.add(10);

            items.add(peerId > 0 ? getString(R.string.VoipEditName) : getString(R.string.VoipEditTitle));
            icons.add(R.drawable.msg_edit);
            options.add(11);
        } else if (canManageCall()) {
            if (isConference() || !isAdmin || !participant.muted) {
                if (!participant.muted || participant.can_self_unmute) {
                    items.add(getString(R.string.VoipGroupMute));
                    icons.add(R.drawable.msg_voice_muted);
                    options.add(0);
                } else {
                    items.add(getString(R.string.VoipGroupAllowToSpeak));
                    if (participant.raise_hand_rating != 0) {
                        icons.add(R.drawable.msg_allowspeak);
                    } else {
                        icons.add(R.drawable.msg_voice_unmuted);
                    }
                    options.add(1);
                }
            } else if (isConference() && participant.muted_by_you) {
                items.add(getString(R.string.VoipGroupUnmuteForMe));
                icons.add(R.drawable.msg_voice_unmuted);
                options.add(4);
            }
            if (participant.peer != null && participant.peer.channel_id != 0 && !ChatObject.isMegagroup(currentAccount, participant.peer.channel_id)) {
                items.add(getString(R.string.VoipGroupOpenChannel));
                icons.add(R.drawable.msg_channel);
                options.add(8);
            } else {
                items.add(getString(R.string.VoipGroupOpenProfile));
                icons.add(R.drawable.msg_openprofile);
                options.add(6);
            }
            if (isConference() ? call != null && call.call != null && call.call.creator : !isAdmin && ChatObject.canBlockUsers(currentChat)) {
                items.add(getString(R.string.VoipGroupUserRemove));
                icons.add(R.drawable.msg_block2);
                options.add(2);
            }
        } else {
            if (participant.muted_by_you) {
                items.add(getString(R.string.VoipGroupUnmuteForMe));
                icons.add(R.drawable.msg_voice_unmuted);
                options.add(4);
            } else {
                items.add(getString(R.string.VoipGroupMuteForMe));
                icons.add(R.drawable.msg_voice_muted);
                options.add(5);
            }

            if (participant.peer != null && participant.peer.channel_id != 0 && !ChatObject.isMegagroup(currentAccount, participant.peer.channel_id)) {
                items.add(getString(R.string.VoipGroupOpenChannel));
                icons.add(R.drawable.msg_msgbubble3);
                options.add(8);
            } else {
                items.add(getString(R.string.VoipGroupOpenChat));
                icons.add(R.drawable.msg_msgbubble3);
                options.add(6);
            }
        }

        for (int a = 0, N = items.size(); a < N; a++) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext(), a == 0, a == N - 1);
            if (options.get(a) != 2) {
                cell.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
            } else {
                cell.setColors(Theme.getColor(Theme.key_voipgroup_leaveCallMenu), Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
            }
            cell.setSelectorColor(Theme.getColor(Theme.key_voipgroup_listSelector));
            cell.setTextAndIcon(items.get(a), icons.get(a));
            buttonsLayout.addView(cell);
            final int i = a;
            cell.setTag(options.get(a));
            cell.setOnClickListener(v1 -> {
                if (i >= options.size()) {
                    return;
                }
                TLRPC.GroupCallParticipant participant1 = call.participants.get(MessageObject.getPeerId(participant.peer));
                if (participant1 == null) {
                    participant1 = participant;
                }
                processSelectedOption(participant1, MessageObject.getPeerId(participant1.peer), options.get(i));
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                } else {
                    if (options.get(i) != 9 && options.get(i) != 10 && options.get(i) != 11) {
                        dismissAvatarPreview(true);
                    }
                }
            });
        }
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        listView.stopScroll();
        layoutManager.setCanScrollVertically(false);
        scrimView = view;
        scrimView.setAboutVisible(true);
        containerView.invalidate();
        listView.invalidate();
        if (scrimAnimatorSet != null) {
            scrimAnimatorSet.cancel();
        }

        scrimPopupLayout = popupLayout;

        final ImageLocation imageLocation;
        final ImageLocation thumbLocation;
        if (peerId > 0) {
            TLRPC.User currentUser = accountInstance.getMessagesController().getUser(peerId);
            imageLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_BIG);
            thumbLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_SMALL);
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(peerId);
            if (userFull == null) {
                MessagesController.getInstance(currentAccount).loadUserInfo(currentUser, false, 0);
            }
        } else {
            TLRPC.Chat currentChat = accountInstance.getMessagesController().getChat(-peerId);
            imageLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_BIG);
            thumbLocation = ImageLocation.getForUserOrChat(currentChat, ImageLocation.TYPE_SMALL);
        }

        boolean hasAttachedRenderer = scrimRenderer != null && scrimRenderer.isAttached();
        if (imageLocation == null && !hasAttachedRenderer) {
            showWithAvatarPreview = false;
        } else if (showWithAvatarPreview) {
            avatarsViewPager.setParentAvatarImage(scrimView.getAvatarImageView());
            avatarsViewPager.setHasActiveVideo(hasAttachedRenderer);
            avatarsViewPager.setData(peerId, true);
            avatarsViewPager.setCreateThumbFromParent(true);
            avatarsViewPager.initIfEmpty(null, imageLocation, thumbLocation, true);
            if (scrimRenderer != null) {
                scrimRenderer.setShowingAsScrimView(true, true);
            }
            if (MessageObject.getPeerId(selfPeer) == peerId && currentAvatarUpdater != null && avatarUpdaterDelegate != null && avatarUpdaterDelegate.avatar != null) {
                avatarsViewPager.addUploadingImage(avatarUpdaterDelegate.uploadingImageLocation, ImageLocation.getForLocal(avatarUpdaterDelegate.avatar));
            }
        }

        if (showWithAvatarPreview) {
            avatarsPreviewShowed = true;
            popupLayout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
            containerView.addView(scrimPopupLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            useBlur = true;//scrimFullscreenView == null;
            if (useBlur) {
                prepareBlurBitmap();
            }
            avatarPriviewTransitionInProgress = true;
            avatarPreviewContainer.setVisibility(View.VISIBLE);
            if (volumeSlider != null) {
                volumeSlider.invalidate();
            }
            runAvatarPreviewTransition(true, view);

            if (scrimFullscreenView != null) {
                scrimFullscreenView.getAvatarImageView().setAlpha(0f);
            }
        } else {
            avatarsPreviewShowed = false;
            scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    if (scrimPopupWindow != this) {
                        return;
                    }
                    scrimPopupWindow = null;
                    if (scrimAnimatorSet != null) {
                        scrimAnimatorSet.cancel();
                        scrimAnimatorSet = null;
                    }
                    layoutManager.setCanScrollVertically(true);
                    scrimAnimatorSet = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0));
                    scrimAnimatorSet.playTogether(animators);
                    scrimAnimatorSet.setDuration(220);
                    scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            clearScrimView();
                            containerView.invalidate();
                            listView.invalidate();
                            if (delayedGroupCallUpdated) {
                                delayedGroupCallUpdated = false;
                                applyCallParticipantUpdates(true);
                            }
                        }
                    });
                    scrimAnimatorSet.start();
                }
            };

            scrimPopupWindow.setPauseNotifications(true);
            scrimPopupWindow.setDismissAnimationDuration(220);
            scrimPopupWindow.setOutsideTouchable(true);
            scrimPopupWindow.setClippingEnabled(true);
            scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
            scrimPopupWindow.setFocusable(true);
            popupLayout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
            scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
            scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
            scrimPopupWindow.getContentView().setFocusableInTouchMode(true);

            int popupX, popupY;
            if (scrimFullscreenView != null) {
                if (isLandscapeMode) {
                    popupX = (int) (scrimFullscreenView.getX() + fullscreenUsersListView.getX() + renderersContainer.getX()) - popupLayout.getMeasuredWidth() + dp(32);
                    popupY = (int) (scrimFullscreenView.getY() + fullscreenUsersListView.getY() + renderersContainer.getY()) - dp(6);
                } else {
                    popupX = (int) (scrimFullscreenView.getX() + fullscreenUsersListView.getX() + renderersContainer.getX()) - dp(14);
                    popupY = (int) (scrimFullscreenView.getY() + fullscreenUsersListView.getY() + renderersContainer.getY() - popupLayout.getMeasuredHeight());
                }
            } else {
                popupX = (int) (listView.getX() + listView.getMeasuredWidth() + dp(8) - popupLayout.getMeasuredWidth());
                if (hasScrimAnchorView) {
                    popupY = (int) (listView.getY() + view.getY() + view.getClipHeight());
                } else if (scrimGridView != null) {
                    popupY = (int) (listView.getY() + scrimGridView.getY() + scrimGridView.getMeasuredHeight());
                } else {
                    popupY = (int) listView.getY();
                }
            }

            scrimPopupWindow.showAtLocation(listView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
            scrimAnimatorSet = new AnimatorSet();
            ArrayList<Animator> animators = new ArrayList<>();
            animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 100));
            scrimAnimatorSet.playTogether(animators);
            scrimAnimatorSet.setDuration(150);
            scrimAnimatorSet.start();
        }

        return true;
    }

    private void clearScrimView() {
        if (scrimRenderer != null) {
            scrimRenderer.textureView.setRoundCorners(dp(8));
            scrimRenderer.setShowingAsScrimView(false, false);
            scrimRenderer.invalidate();
            renderersContainer.invalidate();
        }
        if (scrimView != null && !hasScrimAnchorView) {
            if (scrimView.getParent() != null) {
                containerView.removeView(scrimView);
            }
        }
        if (scrimView != null) {
            scrimView.setProgressToAvatarPreview(0f);
            scrimView.setAboutVisible(false);
            scrimView.getAvatarImageView().setAlpha(1f);
        }
        if (scrimFullscreenView != null) {
            scrimFullscreenView.getAvatarImageView().setAlpha(1f);
        }
        scrimView = null;
        scrimGridView = null;
        scrimFullscreenView = null;
        scrimRenderer = null;

    }

    private void startScreenCapture() {
        if (parentActivity == null || Build.VERSION.SDK_INT < 21) {
            return;
        }
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) parentActivity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        parentActivity.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), LaunchActivity.SCREEN_CAPTURE_REQUEST_CODE);
    }

    private void runAvatarPreviewTransition(boolean enter, GroupCallUserCell view) {
        float fromX, fromY, fromScale;
        int fromRadius;

        float left = dp(14) + containerView.getPaddingLeft();
        float top = dp(14) + containerView.getPaddingTop();
        if (hasScrimAnchorView) {
            fromX = view.getAvatarImageView().getX() + view.getX() + listView.getX() - left;
            fromY = view.getAvatarImageView().getY() + view.getY() + listView.getY() - top;
            fromScale = view.getAvatarImageView().getMeasuredHeight() / (float) listView.getMeasuredWidth();
            fromRadius = (int) ((view.getAvatarImageView().getMeasuredHeight() >> 1) / fromScale);
        } else {
            if (scrimRenderer == null) {
                previewTextureTransitionEnabled = true;
            } else {
                previewTextureTransitionEnabled = enter || avatarsViewPager.getRealPosition(avatarsViewPager.getCurrentItem()) == 0;
            }
            if (scrimGridView != null && previewTextureTransitionEnabled) {
                fromX = scrimGridView.getX() + listView.getX() - left;
                fromY = scrimGridView.getY() + listView.getY() + dp(2) - top;
                fromScale = 1f;
                fromRadius = 0;
            } else if (scrimFullscreenView != null) {
                if (scrimRenderer == null) {
                    fromX = scrimFullscreenView.getAvatarImageView().getX() + scrimFullscreenView.getX() + fullscreenUsersListView.getX() + renderersContainer.getX() - left;
                    fromY = scrimFullscreenView.getAvatarImageView().getY() + scrimFullscreenView.getY() + fullscreenUsersListView.getY() + renderersContainer.getY() - top;
                    fromScale = scrimFullscreenView.getAvatarImageView().getMeasuredHeight() / (float) listView.getMeasuredWidth();
                    fromRadius = (int) ((scrimFullscreenView.getAvatarImageView().getMeasuredHeight() >> 1) / fromScale);
                } else if (previewTextureTransitionEnabled) {
                    fromX = scrimFullscreenView.getX() + fullscreenUsersListView.getX() + renderersContainer.getX() - left;
                    fromY = scrimFullscreenView.getY() + fullscreenUsersListView.getY() + renderersContainer.getY() - top;
                    fromScale = 1f;
                    fromRadius = 0;
                } else {
                    fromX = 0;
                    fromY = 0;
                    fromScale = 0.96f;
                    fromRadius = 0;
                }
            } else {
                fromX = 0;
                fromY = 0;
                fromScale = 0.96f;
                fromRadius = 0;
            }

            if (!previewTextureTransitionEnabled && scrimRenderer != null) {
                scrimRenderer.invalidate();
                renderersContainer.invalidate();
                scrimRenderer.setShowingAsScrimView(false, false);
                scrimRenderer = null;
            }

        }

        if (enter) {
            avatarPreviewContainer.setScaleX(fromScale);
            avatarPreviewContainer.setScaleY(fromScale);
            avatarPreviewContainer.setTranslationX(fromX);
            avatarPreviewContainer.setTranslationY(fromY);
            avatarPagerIndicator.setAlpha(0);
        }

        avatarsViewPager.setRoundRadius(fromRadius, fromRadius);

        if (useBlur) {
            if (enter) {
                blurredView.setAlpha(0f);
            }
            blurredView.animate().alpha(enter ? 1f : 0).setDuration(220).start();
        }
        avatarPagerIndicator.animate().alpha(enter ? 1f : 0).setDuration(220).start();

        if (!enter && scrimRenderer != null) {
            scrimRenderer.setShowingAsScrimView(false, true);
            if (avatarsViewPager.getRealPosition(avatarsViewPager.getCurrentItem()) != 0) {
                scrimRenderer.textureView.cancelAnimation();
                scrimGridView = null;
            }
        }

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(enter ? 0 : 1f, enter ? 1f : 0);

        valueAnimator.addUpdateListener((valueAnimator1) -> {
            progressToAvatarPreview = (float) valueAnimator1.getAnimatedValue();
            renderersContainer.progressToScrimView = progressToAvatarPreview;
            float s = fromScale * (1f - progressToAvatarPreview) + 1f * progressToAvatarPreview;
            avatarPreviewContainer.setScaleX(s);
            avatarPreviewContainer.setScaleY(s);
            avatarPreviewContainer.setTranslationX(fromX * (1f - progressToAvatarPreview));
            avatarPreviewContainer.setTranslationY(fromY * (1f - progressToAvatarPreview));

            if (!useBlur) {
                scrimPaint.setAlpha((int) (100 * progressToAvatarPreview));
            }

            if (scrimRenderer != null) {
                scrimRenderer.textureView.setRoundCorners(dp(8) * (1f - progressToAvatarPreview));
            }

            avatarPreviewContainer.invalidate();
            containerView.invalidate();
            avatarsViewPager.setRoundRadius((int) (fromRadius * (1f - progressToAvatarPreview)), (int) (fromRadius * (1f - progressToAvatarPreview)));
        });
        popupAnimationIndex = accountInstance.getNotificationCenter().setAnimationInProgress(popupAnimationIndex, new int[]{NotificationCenter.dialogPhotosLoaded, NotificationCenter.fileLoaded, NotificationCenter.messagesDidLoad});

        GroupCallMiniTextureView videoRenderer = scrimGridView == null ? null : scrimRenderer;
        if (videoRenderer != null) {
            videoRenderer.animateToScrimView = true;
        }
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (videoRenderer != null) {
                    videoRenderer.animateToScrimView = false;
                }
                accountInstance.getNotificationCenter().onAnimationFinish(popupAnimationIndex);
                avatarPriviewTransitionInProgress = false;
                progressToAvatarPreview = enter ? 1f : 0f;
                renderersContainer.progressToScrimView = progressToAvatarPreview;
                if (!enter) {
                    scrimPaint.setAlpha(0);
                    clearScrimView();
                    if (scrimPopupLayout.getParent() != null) {
                        containerView.removeView(scrimPopupLayout);
                    }
                    scrimPopupLayout = null;
                    avatarPreviewContainer.setVisibility(View.GONE);
                    avatarsPreviewShowed = false;
                    layoutManager.setCanScrollVertically(true);

                    blurredView.setVisibility(View.GONE);

                    if (delayedGroupCallUpdated) {
                        delayedGroupCallUpdated = false;
                        applyCallParticipantUpdates(true);
                    }

                    if (scrimRenderer != null) {
                        scrimRenderer.textureView.setRoundCorners(0);
                    }
                } else {
                    avatarPreviewContainer.setAlpha(1f);
                    avatarPreviewContainer.setScaleX(1f);
                    avatarPreviewContainer.setScaleY(1f);
                    avatarPreviewContainer.setTranslationX(0);
                    avatarPreviewContainer.setTranslationY(0);
                }
                checkContentOverlayed();
                containerView.invalidate();
                avatarsViewPager.invalidate();
                listView.invalidate();
            }
        });
        if (!hasScrimAnchorView && scrimRenderer != null) {
            valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            valueAnimator.setDuration(220);
            scrimRenderer.textureView.setAnimateNextDuration(220);
            scrimRenderer.textureView.synchOrRunAnimation(valueAnimator);
        } else {
            valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            valueAnimator.setDuration(220);
            valueAnimator.start();
        }

        checkContentOverlayed();
    }

    private void dismissAvatarPreview(boolean animated) {
        if (avatarPriviewTransitionInProgress || !avatarsPreviewShowed) {
            return;
        }
        if (animated) {
            avatarPriviewTransitionInProgress = true;
            runAvatarPreviewTransition(false, scrimView);
        } else {
            clearScrimView();
            containerView.removeView(scrimPopupLayout);
            scrimPopupLayout = null;
            avatarPreviewContainer.setVisibility(View.GONE);
            containerView.invalidate();
            avatarsPreviewShowed = false;
            layoutManager.setCanScrollVertically(true);
            listView.invalidate();
            blurredView.setVisibility(View.GONE);

            if (delayedGroupCallUpdated) {
                delayedGroupCallUpdated = false;
                applyCallParticipantUpdates(true);
            }
            checkContentOverlayed();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        public final static int VIEW_TYPE_INVITE_MEMBERS = 0,
                VIEW_TYPE_PARTICIPANT = 1,
                VIEW_TYPE_CALL_INVITED = 2,
                VIEW_TYPE_LAST_PADDING = 3,
                VIEW_TYPE_GRID = 4,
                VIEW_TYPE_VIDEO_GRID_DIVIDER = 5,
                VIEW_TYPE_VIDEO_NOT_AVAILABLE = 6,
                VIEW_TYPE_ENCRYPTION = 7;

        private Context mContext;
        private int usersStartRow;
        private int usersEndRow;
        private int invitedStartRow;
        private int invitedEndRow;
        private int shadyJoinStartRow;
        private int shadyJoinEndRow;
        private int shadyLeftStartRow;
        private int shadyLeftEndRow;
        private int addMemberRow;
        private int conferenceAddPeopleRow;
        private int conferenceShareLinkRow;
        private int lastRow;
        private int rowsCount;
        private int usersVideoGridStartRow;
        private int usersVideoGridEndRow;
        private int videoGridDividerRow;
        private int videoNotAvailableRow;
        private int encryptionRow;

        private boolean hasSelfUser;

        public ListAdapter(Context context) {
            mContext = context;
        }

        public boolean addSelfToCounter() {
            if (isRtmpStream()) {
                return false;
            }
            if (hasSelfUser || VoIPService.getSharedInstance() == null) {
                return false;
            }
            return !VoIPService.getSharedInstance().isJoined();
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        private void updateRows() {
            if (call == null || call.isScheduled() || delayedGroupCallUpdated) {
                return;
            }
            addMemberRow = -1;
            conferenceAddPeopleRow = -1;
            conferenceShareLinkRow = -1;
            videoGridDividerRow = -1;
            videoNotAvailableRow = -1;
            encryptionRow = -1;
            rowsCount = 0;
            hasSelfUser = call.participants.indexOfKey(MessageObject.getPeerId(selfPeer)) >= 0;

            if (isConference()/* && call.participants.size() > 1*/) {
                encryptionRow = rowsCount++;
            }

            usersVideoGridStartRow = rowsCount;
            rowsCount += visibleVideoParticipants.size();
            usersVideoGridEndRow = rowsCount;

            int videoCount = visibleVideoParticipants.size();

            if (videoCount > 0) {
                videoGridDividerRow = rowsCount++;
            }
            if (!visibleVideoParticipants.isEmpty() && canManageCall() && call.call.participants_count > accountInstance.getMessagesController().groupCallVideoMaxParticipants) {
                videoNotAvailableRow = rowsCount++;
            }
            usersStartRow = rowsCount;
            if (!isRtmpStream()) {
                rowsCount += call.visibleParticipants.size();
            }
            usersEndRow = rowsCount;

            if (call.invitedUsers.isEmpty() || isRtmpStream()) {
                invitedStartRow = -1;
                invitedEndRow = -1;
            } else {
                invitedStartRow = rowsCount;
                rowsCount += call.invitedUsers.size();
                invitedEndRow = rowsCount;
            }

            if (call.shadyJoinParticipants.isEmpty() || isRtmpStream()) {
                shadyJoinStartRow = -1;
                shadyJoinEndRow = -1;
            } else {
                shadyJoinStartRow = rowsCount;
                rowsCount += call.shadyJoinParticipants.size();
                shadyJoinEndRow = rowsCount;
            }

            if (call.shadyLeftParticipants.isEmpty() || isRtmpStream()) {
                shadyLeftStartRow = -1;
                shadyLeftEndRow = -1;
            } else {
                shadyLeftStartRow = rowsCount;
                rowsCount += call.shadyLeftParticipants.size();
                shadyLeftEndRow = rowsCount;
            }

            if (isConference()) {
                conferenceAddPeopleRow = rowsCount++;
                conferenceShareLinkRow = rowsCount++;
            } else if (!isRtmpStream() && ((!ChatObject.isChannel(currentChat) || currentChat != null && currentChat.megagroup) && ChatObject.canWriteToChat(currentChat) ||
                    ChatObject.isChannel(currentChat) && currentChat != null && !currentChat.megagroup && ChatObject.isPublic(currentChat))) {
                addMemberRow = rowsCount++;
            }

            lastRow = rowsCount++;
        }

        @Override
        public void notifyDataSetChanged() {
            updateRows();
            super.notifyDataSetChanged();
        }

        @Override
        public void notifyItemChanged(int position) {
            updateRows();
            super.notifyItemChanged(position);
        }

        @Override
        public void notifyItemChanged(int position, @Nullable Object payload) {
            updateRows();
            super.notifyItemChanged(position, payload);
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeChanged(positionStart, itemCount);
        }

        @Override
        public void notifyItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
            updateRows();
            super.notifyItemRangeChanged(positionStart, itemCount, payload);
        }

        @Override
        public void notifyItemInserted(int position) {
            updateRows();
            super.notifyItemInserted(position);
        }

        @Override
        public void notifyItemMoved(int fromPosition, int toPosition) {
            updateRows();
            super.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeInserted(positionStart, itemCount);
        }

        @Override
        public void notifyItemRemoved(int position) {
            updateRows();
            super.notifyItemRemoved(position);
        }

        @Override
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            updateRows();
            super.notifyItemRangeRemoved(positionStart, itemCount);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == VIEW_TYPE_PARTICIPANT) {
                GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                int key = actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled;
                cell.setGrayIconColor(key, Theme.getColor(key));
                cell.setDrawDivider(holder.getAdapterPosition() != getItemCount() - 2);
            } else if (type == VIEW_TYPE_CALL_INVITED) {
                GroupCallInvitedCell cell = (GroupCallInvitedCell) holder.itemView;
                int key = actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled;
                cell.setGrayIconColor(key, Theme.getColor(key));
                cell.setDrawDivider(holder.getAdapterPosition() != getItemCount() - 2);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_INVITE_MEMBERS:
                    final GroupCallTextCell textCell = (GroupCallTextCell) holder.itemView;
                    if (position == conferenceAddPeopleRow) {
                        final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
                        textCell.setColors(color, color);
                        textCell.setTextAndIcon(getString(R.string.VoipConferenceAddPeople), R.drawable.msg_contact_add, true);
                    } else if (position == conferenceShareLinkRow) {
                        final int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
                        textCell.setColors(color, color);
                        textCell.setTextAndIcon(getString(R.string.VoipConferenceShareLink), R.drawable.msg_link2, false);
                    } else {
                        final int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_lastSeenText), actionBar.getTag() != null ? 1.0f : 0.0f, 1.0f);
                        textCell.setColors(color, color);
                        if (ChatObject.isChannel(currentChat) && currentChat != null && !currentChat.megagroup && ChatObject.isPublic(currentChat)) {
                            textCell.setTextAndIcon(getString(R.string.VoipGroupShareLink), R.drawable.msg_link, false);
                        } else {
                            textCell.setTextAndIcon(getString(R.string.VoipGroupInviteMember), R.drawable.msg_contact_add, false);
                        }
                    }
                    break;
                case VIEW_TYPE_PARTICIPANT: {
                    GroupCallUserCell userCell = (GroupCallUserCell) holder.itemView;
                    TLRPC.GroupCallParticipant participant;
                    int row = position - usersStartRow;
                    if (delayedGroupCallUpdated) {
                        if (row >= 0 && row < oldParticipants.size()) {
                            participant = oldParticipants.get(row);
                        } else {
                            participant = null;
                        }
                    } else {
                        if (row >= 0 && row < call.visibleParticipants.size()) {
                            participant = call.visibleParticipants.get(row);
                        } else {
                            participant = null;
                        }
                    }
                    if (participant != null) {
                        long peerId = MessageObject.getPeerId(participant.peer);
                        long selfPeerId = MessageObject.getPeerId(selfPeer);
                        TLRPC.FileLocation uploadingAvatar = (peerId == selfPeerId && avatarUpdaterDelegate != null) ? avatarUpdaterDelegate.avatar : null;
                        float uploadingProgress = (uploadingAvatar != null) ? avatarUpdaterDelegate.uploadingProgress : 1f;
                        boolean animated = userCell.getParticipant() != null && MessageObject.getPeerId(userCell.getParticipant().peer) == peerId;
                        userCell.setData(accountInstance, participant, call, selfPeerId, uploadingAvatar, animated);
                        userCell.setUploadProgress(uploadingProgress, animated);
                    }
                    break;
                }
                case VIEW_TYPE_CALL_INVITED: {
                    GroupCallInvitedCell invitedCell = (GroupCallInvitedCell) holder.itemView;
                    Long uid;
                    boolean isShadyJoin = false, isShadyLeft = false;
                    boolean calling = false;
                    int row = position - invitedStartRow;
                    if (position - shadyJoinStartRow >= 0 && position - shadyJoinStartRow < call.shadyJoinParticipants.size()) {
                        uid = call.shadyJoinParticipants.get(position - shadyJoinStartRow);
                        isShadyJoin = true;
                    } else if (position - shadyLeftStartRow >= 0 && position - shadyLeftStartRow < call.shadyLeftParticipants.size()) {
                        uid = call.shadyLeftParticipants.get(position - shadyLeftStartRow);
                        isShadyLeft = true;
                    } else if (delayedGroupCallUpdated) {
                        if (row >= 0 && row < oldInvited.size()) {
                            uid = oldInvited.get(row);
                        } else {
                            uid = null;
                        }
                    } else {
                        if (row >= 0 && row < call.invitedUsers.size()) {
                            uid = call.invitedUsers.get(row);
                            final ChatObject.Call.InvitedUser invited = call.invitedUsersMessageIds.get(uid);
                            calling = invited != null && invited.isCalling();
                        } else {
                            uid = null;
                        }
                    }
                    if (uid != null) {
                        invitedCell.setData(currentAccount, uid, calling, isShadyJoin, isShadyLeft);
                    }
                    break;
                }
                case VIEW_TYPE_GRID: {
                    GroupCallGridCell userCell = (GroupCallGridCell) holder.itemView;
                    ChatObject.VideoParticipant oldParticipant = userCell.getParticipant();
                    int row = position - usersVideoGridStartRow;
                    userCell.spanCount = spanSizeLookup.getSpanSize(position);

                    ChatObject.VideoParticipant participant;

                    if (delayedGroupCallUpdated) {
                        if (row >= 0 && row < oldVideoParticipants.size()) {
                            participant = oldVideoParticipants.get(row);
                        } else {
                            participant = null;
                        }
                    } else {
                        if (row >= 0 && row < visibleVideoParticipants.size()) {
                            participant = visibleVideoParticipants.get(row);
                        } else {
                            participant = null;
                        }
                    }
                    if (participant != null) {
                        long peerId = MessageObject.getPeerId(participant.participant.peer);
                        long selfPeerId = MessageObject.getPeerId(selfPeer);
                        TLRPC.FileLocation uploadingAvatar = (peerId == selfPeerId && avatarUpdaterDelegate != null) ? avatarUpdaterDelegate.avatar : null;
                        float uploadingProgress = (uploadingAvatar != null) ? avatarUpdaterDelegate.uploadingProgress : 1f;
                        boolean animated = userCell.getParticipant() != null && userCell.getParticipant().equals(participant);
                        userCell.setData(accountInstance, participant, call, selfPeerId);
                    }
                    if (oldParticipant != null && !oldParticipant.equals(participant) && userCell.attached && userCell.getRenderer() != null) {
                        attachRenderer(userCell, false);
                        attachRenderer(userCell, true);
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return !(type == VIEW_TYPE_LAST_PADDING || type == VIEW_TYPE_GRID || type == VIEW_TYPE_VIDEO_GRID_DIVIDER || type == VIEW_TYPE_VIDEO_NOT_AVAILABLE/* || type == VIEW_TYPE_ENCRYPTION*/);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_INVITE_MEMBERS:
                    view = new GroupCallTextCell(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (AndroidUtilities.isTablet()) {
                                int w = Math.min(dp(420), MeasureSpec.getSize(widthMeasureSpec));
                                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec);
                            } else {
                                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            }
                        }
                    };
                    break;
                case VIEW_TYPE_PARTICIPANT:
                    view = new GroupCallUserCell(mContext) {
                        @Override
                        protected void onMuteClick(GroupCallUserCell cell) {
                            showMenuForCell(cell);
                        }

                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (AndroidUtilities.isTablet()) {
                                int w = Math.min(dp(420), MeasureSpec.getSize(widthMeasureSpec));
                                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec);
                            } else {
                                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            }
                        }
                    };
                    break;
                case VIEW_TYPE_CALL_INVITED:
                    view = new GroupCallInvitedCell(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            if (AndroidUtilities.isTablet()) {
                                int w = Math.min(dp(420), MeasureSpec.getSize(widthMeasureSpec));
                                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec);
                            } else {
                                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            }
                        }
                    };
                    break;
                case VIEW_TYPE_GRID:
                    view = new GroupCallGridCell(mContext, false) {
                        @Override
                        protected void onAttachedToWindow() {
                            super.onAttachedToWindow();
                            if (listView.getVisibility() == View.VISIBLE && listViewVideoVisibility) {
                                attachRenderer(this, true);
                            }
                        }

                        @Override
                        protected void onDetachedFromWindow() {
                            super.onDetachedFromWindow();
                            attachRenderer(this, false);
                        }
                    };
                    break;
                case VIEW_TYPE_VIDEO_GRID_DIVIDER:
                    view = new View(mContext) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(isLandscapeMode ? 0 : 8), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case VIEW_TYPE_VIDEO_NOT_AVAILABLE:
                    TextView textView = new TextView(mContext);
                    textView.setTextColor(0xff7B8389);
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    textView.setGravity(Gravity.CENTER_HORIZONTAL);
                    textView.setPadding(0, 0, 0, dp(10));
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        textView.setText(formatString(R.string.VoipChannelVideoNotAvailableAdmin, LocaleController.formatPluralString("Participants", accountInstance.getMessagesController().groupCallVideoMaxParticipants)));
                    } else {
                        textView.setText(formatString(R.string.VoipVideoNotAvailableAdmin, LocaleController.formatPluralString("Members", accountInstance.getMessagesController().groupCallVideoMaxParticipants)));
                    }
                    view = textView;
                    break;
                case VIEW_TYPE_ENCRYPTION:
                    if (encryptionDrawable == null) {
                        encryptionDrawable = new CallEncryptionCellDrawable(mContext);
                    }
                    view = new CallEncryptionCell(mContext, encryptionDrawable);
                    break;
                case VIEW_TYPE_LAST_PADDING:
                default:
                    view = new View(mContext);
                    break;
            }
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT);
            view.setLayoutParams(params);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == lastRow) {
                return VIEW_TYPE_LAST_PADDING;
            } else if (position == addMemberRow || position == conferenceAddPeopleRow || position == conferenceShareLinkRow) {
                return VIEW_TYPE_INVITE_MEMBERS;
            } else if (position == videoGridDividerRow) {
                return VIEW_TYPE_VIDEO_GRID_DIVIDER;
            } else if (position >= usersStartRow && position < usersEndRow) {
                return VIEW_TYPE_PARTICIPANT;
            } else if (position >= usersVideoGridStartRow && position < usersVideoGridEndRow) {
                return VIEW_TYPE_GRID;
            } else if (position == videoNotAvailableRow) {
                return VIEW_TYPE_VIDEO_NOT_AVAILABLE;
            } else if (position == encryptionRow) {
                return VIEW_TYPE_ENCRYPTION;
            }
            return VIEW_TYPE_CALL_INVITED;
        }
    }

    private void attachRenderer(GroupCallGridCell cell, boolean attach) {
        if (isDismissed()) {
            return;
        }
        if (attach && cell.getRenderer() == null) {
            cell.setRenderer(GroupCallMiniTextureView.getOrCreate(attachedRenderers, renderersContainer, cell, null, null, cell.getParticipant(), call, this));
        } else if (!attach) {
            if (cell.getRenderer() != null) {
                cell.getRenderer().setPrimaryView(null);
                cell.setRenderer(null);
            }
        }
    }

    private int oldEncryptionRow;
    private int oldAddMemberRow;
    private int oldUsersStartRow;
    private int oldUsersEndRow;
    private int oldInvitedStartRow;
    private int oldInvitedEndRow;
    private int oldShadyJoinStartRow;
    private int oldShadyJoinEndRow;
    private int oldShadyLeftStartRow;
    private int oldShadyLeftEndRow;
    private int oldUsersVideoStartRow;
    private int oldUsersVideoEndRow;
    private int oldVideoDividerRow;
    private int oldVideoNotAvailableRow;
    private int oldConferenceAddPeopleRow;
    private int oldConferenceShareLinkRow;

    public void setOldRows(
        int encryptionRow,
        int addMemberRow,
        int usersStartRow,
        int usersEndRow,
        int invitedStartRow,
        int invitedEndRow,
        int shadyJoinStartRow,
        int shadyJoinEndRow,
        int shadyLeftStartRow,
        int shadyLeftEndRow,
        int usersVideoStartRow,
        int usersVideoEndRow,
        int videoDividerRow,
        int videoNotAvailableRow,
        int conferenceAddPeopleRow,
        int conferenceShareLinkRow
    ) {
        oldEncryptionRow = encryptionRow;
        oldAddMemberRow = addMemberRow;
        oldUsersStartRow = usersStartRow;
        oldUsersEndRow = usersEndRow;
        oldInvitedStartRow = invitedStartRow;
        oldInvitedEndRow = invitedEndRow;
        oldShadyJoinStartRow = shadyJoinStartRow;
        oldShadyJoinEndRow = shadyJoinEndRow;
        oldShadyLeftStartRow = shadyLeftStartRow;
        oldShadyLeftEndRow = shadyLeftEndRow;
        oldUsersVideoStartRow = usersVideoStartRow;
        oldUsersVideoEndRow = usersVideoEndRow;
        oldVideoDividerRow = videoDividerRow;
        oldVideoNotAvailableRow = videoNotAvailableRow;
        oldConferenceAddPeopleRow = conferenceAddPeopleRow;
        oldConferenceShareLinkRow = conferenceShareLinkRow;
    }

    private DiffUtil.Callback diffUtilsCallback = new DiffUtil.Callback() {

        @Override
        public int getOldListSize() {
            return oldCount;
        }

        @Override
        public int getNewListSize() {
            return listAdapter.rowsCount;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            if (listAdapter.addMemberRow >= 0) {
                if (oldItemPosition == oldAddMemberRow && newItemPosition == listAdapter.addMemberRow) {
                    return true;
                } else if (oldItemPosition == oldAddMemberRow && newItemPosition != listAdapter.addMemberRow ||
                        oldItemPosition != oldAddMemberRow && newItemPosition == listAdapter.addMemberRow) {
                    return false;
                }
            }
            if (listAdapter.conferenceAddPeopleRow >= 0) {
                if (oldItemPosition == oldConferenceAddPeopleRow && newItemPosition == listAdapter.conferenceAddPeopleRow) {
                    return true;
                } else if (oldItemPosition == oldConferenceAddPeopleRow && newItemPosition != listAdapter.conferenceAddPeopleRow ||
                    oldItemPosition != oldConferenceAddPeopleRow && newItemPosition == listAdapter.conferenceAddPeopleRow) {
                    return false;
                }
            }
            if (listAdapter.conferenceShareLinkRow >= 0) {
                if (oldItemPosition == oldConferenceShareLinkRow && newItemPosition == listAdapter.conferenceShareLinkRow) {
                    return true;
                } else if (oldItemPosition == oldConferenceShareLinkRow && newItemPosition != listAdapter.conferenceShareLinkRow ||
                    oldItemPosition != oldConferenceShareLinkRow && newItemPosition == listAdapter.conferenceShareLinkRow) {
                    return false;
                }
            }
            if (listAdapter.encryptionRow >= 0) {
                if (oldItemPosition == oldEncryptionRow && newItemPosition == listAdapter.encryptionRow) {
                    return true;
                } else if (oldItemPosition == oldEncryptionRow && newItemPosition != listAdapter.encryptionRow ||
                    oldItemPosition != oldEncryptionRow && newItemPosition == listAdapter.encryptionRow) {
                    return false;
                }
            }
            if (listAdapter.videoNotAvailableRow >= 0) {
                if (oldItemPosition == oldVideoNotAvailableRow && newItemPosition == listAdapter.videoNotAvailableRow) {
                    return true;
                } else if (oldItemPosition == oldVideoNotAvailableRow && newItemPosition != listAdapter.videoNotAvailableRow ||
                        oldItemPosition != oldVideoNotAvailableRow && newItemPosition == listAdapter.videoNotAvailableRow) {
                    return false;
                }
            }
            if (listAdapter.videoGridDividerRow >= 0 && listAdapter.videoGridDividerRow == newItemPosition && oldItemPosition == oldVideoDividerRow) {
                return true;
            }
            if (oldItemPosition == oldCount - 1 && newItemPosition == listAdapter.rowsCount - 1) {
                return true;
            } else if (oldItemPosition == oldCount - 1 || newItemPosition == listAdapter.rowsCount - 1) {
                return false;
            }
            if ((newItemPosition >= listAdapter.usersVideoGridStartRow && newItemPosition < listAdapter.usersVideoGridEndRow) &&
                    (oldItemPosition >= oldUsersVideoStartRow && oldItemPosition < oldUsersVideoEndRow)) {
                ChatObject.VideoParticipant oldItem = oldVideoParticipants.get(oldItemPosition - oldUsersVideoStartRow);
                ChatObject.VideoParticipant newItem = visibleVideoParticipants.get(newItemPosition - listAdapter.usersVideoGridStartRow);
                return oldItem.equals(newItem);
            } else if ((newItemPosition >= listAdapter.usersStartRow && newItemPosition < listAdapter.usersEndRow) &&
                    (oldItemPosition >= oldUsersStartRow && oldItemPosition < oldUsersEndRow)) {
                TLRPC.GroupCallParticipant oldItem = oldParticipants.get(oldItemPosition - oldUsersStartRow);
                TLRPC.GroupCallParticipant newItem = call.visibleParticipants.get(newItemPosition - listAdapter.usersStartRow);
                return MessageObject.getPeerId(oldItem.peer) == MessageObject.getPeerId(newItem.peer) && (oldItemPosition == newItemPosition || oldItem.lastActiveDate == oldItem.active_date);
            } else if (newItemPosition >= listAdapter.invitedStartRow && newItemPosition < listAdapter.invitedEndRow &&
                    oldItemPosition >= oldInvitedStartRow && oldItemPosition < oldInvitedEndRow) {
                Long oldItem = oldInvited.get(oldItemPosition - oldInvitedStartRow);
                Long newItem = call.invitedUsers.get(newItemPosition - listAdapter.invitedStartRow);
                return oldItem.equals(newItem);
            } else if (newItemPosition >= listAdapter.shadyJoinStartRow && newItemPosition < listAdapter.shadyJoinEndRow &&
                oldItemPosition >= oldShadyJoinStartRow && oldItemPosition < oldShadyJoinEndRow) {
                Long oldItem = oldShadyJoin.get(oldItemPosition - oldShadyJoinStartRow);
                Long newItem = call.shadyJoinParticipants.get(newItemPosition - listAdapter.shadyJoinStartRow);
                return oldItem.equals(newItem);
            } else if (newItemPosition >= listAdapter.shadyLeftStartRow && newItemPosition < listAdapter.shadyLeftEndRow &&
                oldItemPosition >= oldShadyLeftStartRow && oldItemPosition < oldShadyLeftEndRow) {
                Long oldItem = oldShadyLeft.get(oldItemPosition - oldShadyLeftStartRow);
                Long newItem = call.shadyLeftParticipants.get(newItemPosition - listAdapter.shadyLeftStartRow);
                return oldItem.equals(newItem);
            }
            return false;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return true;
        }
    };

    private static class UpdateCallback implements ListUpdateCallback {

        final RecyclerView.Adapter adapter;

        private UpdateCallback(RecyclerView.Adapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onInserted(int position, int count) {
            adapter.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            adapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            adapter.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void onChanged(int position, int count, @Nullable Object payload) {
            adapter.notifyItemRangeChanged(position, count, payload);
        }
    }

    private void toggleAdminSpeak() {
        final TL_phone.toggleGroupCallSettings req = new TL_phone.toggleGroupCallSettings();
        req.call = call.getInputGroupCall();
        req.join_muted = call.call.join_muted;
        req.flags |= 1;
        accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                accountInstance.getMessagesController().processUpdates((TLRPC.Updates) response, false);
            }
        });
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

    @Override
    public void onBackPressed() {
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (avatarsPreviewShowed) {
            dismissAvatarPreview(true);
            return;
        }
        if (renderersContainer.inFullscreenMode) {
            fullscreenFor(null);
            return;
        }
        super.onBackPressed();
    }

    private class AvatarUpdaterDelegate implements ImageUpdater.ImageUpdaterDelegate {

        public float uploadingProgress;
        private TLRPC.FileLocation avatarBig;
        private TLRPC.FileLocation avatar;
        private ImageLocation uploadingImageLocation;

        private final long peerId;

        private AvatarUpdaterDelegate(long peerId) {
            this.peerId = peerId;
        }

        @Override
        public void didUploadPhoto(TLRPC.InputFile photo, TLRPC.InputFile video, double videoStartTimestamp, String videoPath, TLRPC.PhotoSize bigSize, TLRPC.PhotoSize smallSize, boolean isVideo, TLRPC.VideoSize emojiMarkup) {
            AndroidUtilities.runOnUIThread(() -> {
                if (photo != null || video != null || emojiMarkup != null) {
                    if (peerId > 0) {
                        TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                        if (photo != null) {
                            req.file = photo;
                            req.flags |= 1;
                        }
                        if (video != null) {
                            req.video = video;
                            req.flags |= 2;
                            req.video_start_ts = videoStartTimestamp;
                            req.flags |= 4;
                        }
                        if (emojiMarkup != null) {
                            req.video_emoji_markup = emojiMarkup;
                            req.flags |= 16;
                        }

                        accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (uploadingImageLocation != null) {
                                avatarsViewPager.removeUploadingImage(uploadingImageLocation);
                                uploadingImageLocation = null;
                            }
                            if (error == null) {
                                TLRPC.User user = accountInstance.getMessagesController().getUser(accountInstance.getUserConfig().getClientUserId());
                                if (user == null) {
                                    user = accountInstance.getUserConfig().getCurrentUser();
                                    if (user == null) {
                                        return;
                                    }
                                    accountInstance.getMessagesController().putUser(user, false);
                                } else {
                                    accountInstance.getUserConfig().setCurrentUser(user);
                                }
                                TLRPC.TL_photos_photo photos_photo = (TLRPC.TL_photos_photo) response;
                                ArrayList<TLRPC.PhotoSize> sizes = photos_photo.photo.sizes;
                                TLRPC.PhotoSize small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150);
                                TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800);
                                TLRPC.VideoSize videoSize = photos_photo.photo.video_sizes.isEmpty() ? null : photos_photo.photo.video_sizes.get(0);

                                user.photo = new TLRPC.TL_userProfilePhoto();
                                user.photo.photo_id = photos_photo.photo.id;
                                if (small != null) {
                                    user.photo.photo_small = small.location;
                                }
                                if (big != null) {
                                    user.photo.photo_big = big.location;
                                }

                                if (small != null && avatar != null) {
                                    File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(small, true);
                                    File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatar, true);
                                    src.renameTo(destFile);
                                    String oldKey = avatar.volume_id + "_" + avatar.local_id + "@50_50";
                                    String newKey = small.location.volume_id + "_" + small.location.local_id + "@50_50";
                                    ImageLoader.getInstance().replaceImageInCache(oldKey, newKey, ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), false);
                                }

                                if (big != null && avatarBig != null) {
                                    File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(big, true);
                                    File src = FileLoader.getInstance(currentAccount).getPathToAttach(avatarBig, true);
                                    src.renameTo(destFile);
                                }
                                if (videoSize != null && videoPath != null) {
                                    File destFile = FileLoader.getInstance(currentAccount).getPathToAttach(videoSize, "mp4", true);
                                    File src = new File(videoPath);
                                    src.renameTo(destFile);
                                }

                                accountInstance.getMessagesController().getDialogPhotos(user.id).reset();
                                ArrayList<TLRPC.User> users = new ArrayList<>();
                                users.add(user);
                                accountInstance.getMessagesStorage().putUsersAndChats(users, null, false, true);

                                final ImageLocation imageLocation;
                                final ImageLocation thumbLocation;
                                TLRPC.User currentUser = accountInstance.getMessagesController().getUser(peerId);
                                imageLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_BIG);
                                thumbLocation = ImageLocation.getForUser(currentUser, ImageLocation.TYPE_SMALL);
                                ImageLocation thumb = ImageLocation.getForLocal(avatarBig);
                                if (thumb == null) {
                                    thumb = ImageLocation.getForLocal(avatar);
                                } else {
                                    thumb = thumbLocation;
                                }
                                avatarsViewPager.setCreateThumbFromParent(false);
                                avatarsViewPager.initIfEmpty(null, imageLocation, thumb, true);
                                avatar = null;
                                avatarBig = null;
                                AndroidUtilities.updateVisibleRows(listView);
                                updateAvatarUploadingProgress(1f);
                            }

                            accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                            accountInstance.getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
                            accountInstance.getUserConfig().saveConfig(true);
                        }));
                    } else {
                        accountInstance.getMessagesController().changeChatAvatar(-peerId, null, photo, video, emojiMarkup, videoStartTimestamp, videoPath, smallSize.location, bigSize.location, () -> {
                            if (uploadingImageLocation != null) {
                                avatarsViewPager.removeUploadingImage(uploadingImageLocation);
                                uploadingImageLocation = null;
                            }

                            TLRPC.Chat currentChat = accountInstance.getMessagesController().getChat(-peerId);
                            ImageLocation imageLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_BIG);
                            ImageLocation thumbLocation = ImageLocation.getForChat(currentChat, ImageLocation.TYPE_SMALL);
                            ImageLocation thumb = ImageLocation.getForLocal(avatarBig);
                            if (thumb == null) {
                                thumb = ImageLocation.getForLocal(avatar);
                            } else {
                                thumb = thumbLocation;
                            }
                            avatarsViewPager.setCreateThumbFromParent(false);
                            avatarsViewPager.initIfEmpty(null, imageLocation, thumb, true);
                            avatar = null;
                            avatarBig = null;
                            AndroidUtilities.updateVisibleRows(listView);
                            updateAvatarUploadingProgress(1f);
                        });
                    }
                } else {
                    avatar = smallSize.location;
                    avatarBig = bigSize.location;
                    uploadingImageLocation = ImageLocation.getForLocal(avatarBig);
                    avatarsViewPager.addUploadingImage(uploadingImageLocation, ImageLocation.getForLocal(avatar));
                    AndroidUtilities.updateVisibleRows(listView);
                }
            });
        }

        @Override
        public void didStartUpload(boolean fromAvatarConstructor, boolean isVideo) {

        }

        @Override
        public void onUploadProgressChanged(float progress) {
            avatarsViewPager.setUploadProgress(uploadingImageLocation, progress);
            updateAvatarUploadingProgress(progress);
        }

        public void updateAvatarUploadingProgress(float progress) {
            uploadingProgress = progress;
            if (listView == null) {
                return;
            }
            for (int i = 0; i < listView.getChildCount(); i++) {
                View child = listView.getChildAt(i);
                if (child instanceof GroupCallUserCell) {
                    GroupCallUserCell cell = ((GroupCallUserCell) child);
                    if (cell.isSelfUser()) {
                        cell.setUploadProgress(progress, true);
                    }
                }
            }
        }
    }

    public View getScrimView() {
        return scrimView;
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        attachedRenderersTmp.clear();
        attachedRenderersTmp.addAll(attachedRenderers);
        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
            attachedRenderersTmp.get(i).updateAttachState(true);
        }
        if (previewDialog != null) {
            previewDialog.update();
        }
    }

    private class GroupCallItemAnimator extends DefaultItemAnimator {

        public float animationProgress;
        public ValueAnimator animator;

        HashSet<RecyclerView.ViewHolder> addingHolders = new HashSet<>();
        HashSet<RecyclerView.ViewHolder> removingHolders = new HashSet<>();

        float outMaxBottom;
        float outMinTop;

        @Override
        public void endAnimations() {
            super.endAnimations();
            removingHolders.clear();
            addingHolders.clear();
            outMinTop = Float.MAX_VALUE;
            listView.invalidate();
        }

        public void updateBackgroundBeforeAnimation() {
            if (animator != null) {
                return;
            }
            addingHolders.clear();
            addingHolders.addAll(mPendingAdditions);

            removingHolders.clear();
            removingHolders.addAll(mPendingRemovals);

            outMaxBottom = 0;
            outMinTop = Float.MAX_VALUE;

            if (!addingHolders.isEmpty() || !removingHolders.isEmpty()) {
                for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                    View child = listView.getChildAt(a);
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                    if (
                        holder == null ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_LAST_PADDING ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_GRID ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_VIDEO_GRID_DIVIDER ||
                        holder.getItemViewType() == ListAdapter.VIEW_TYPE_ENCRYPTION
                    ) {
                        continue;
                    }
                    if (!addingHolders.contains(holder)) {
                        outMaxBottom = Math.max(outMaxBottom, child.getY() + child.getMeasuredHeight());
                        outMinTop = Math.min(outMinTop, Math.max(0, child.getY()));
                    }
                }
                animationProgress = 0f;
                listView.invalidate();
            }
        }


        @Override
        public void runPendingAnimations() {
            boolean removalsPending = !mPendingRemovals.isEmpty();
            boolean movesPending = !mPendingMoves.isEmpty();
            boolean additionsPending = !mPendingAdditions.isEmpty();
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (removalsPending || movesPending || additionsPending) {
                animationProgress = 0f;
                animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(valueAnimator -> {
                    animationProgress = (float) valueAnimator.getAnimatedValue();
                    listView.invalidate();
                    renderersContainer.invalidate();
                    containerView.invalidate();
                    updateLayout(true);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        animator = null;
                        listView.invalidate();
                        renderersContainer.invalidate();
                        containerView.invalidate();
                        updateLayout(true);

                        addingHolders.clear();
                        removingHolders.clear();
                    }
                });
                animator.setDuration(TRANSITION_DURATION);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();

                listView.invalidate();
                renderersContainer.invalidate();
            }
            super.runPendingAnimations();
        }
    }

    @Override
    protected boolean canDismissWithTouchOutside() {
        return !renderersContainer.inFullscreenMode;
    }

    private void onUserLeaveHint() {
        if (isRtmpStream() && AndroidUtilities.checkInlinePermissions(parentActivity) && !RTMPStreamPipOverlay.isVisible()) {
            dismiss();
            AndroidUtilities.runOnUIThread(() -> RTMPStreamPipOverlay.show(parentActivity), 100);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        parentActivity.addOnUserLeaveHintListener(onUserLeaveHintListener);
    }

    public void onResume() {
        paused = false;
        listAdapter.notifyDataSetChanged();
        if (fullscreenUsersListView.getVisibility() == View.VISIBLE) {
            fullscreenAdapter.update(false, fullscreenUsersListView);
        }
        if (isTabletMode) {
            tabletGridAdapter.update(false, tabletVideoGridView);
        }
        attachedRenderersTmp.clear();
        attachedRenderersTmp.addAll(attachedRenderers);
        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
            attachedRenderersTmp.get(i).updateAttachState(true);
        }
    }

    public void onPause() {
        paused = true;
        attachedRenderersTmp.clear();
        attachedRenderersTmp.addAll(attachedRenderers);
        for (int i = 0; i < attachedRenderersTmp.size(); i++) {
            attachedRenderersTmp.get(i).updateAttachState(false);
        }
    }

    public boolean isRtmpLandscapeMode() {
        return isRtmpStream() && !call.visibleVideoParticipants.isEmpty() && (call.visibleVideoParticipants.get(0).aspectRatio == 0 || call.visibleVideoParticipants.get(0).aspectRatio >= 1);
    }

    public boolean isRtmpStream() {
        return call != null && call.call.rtmp_stream;
    }

    public boolean isConference() {
        final VoIPService voip = VoIPService.getSharedInstance();
        return voip != null && voip.isConference();
    }

    public boolean canManageCall() {
        final VoIPService voip = VoIPService.getSharedInstance();
        if (voip != null && voip.isConference()) {
            return call != null && call.call != null && call.call.creator;
        } else {
            return ChatObject.canManageCalls(currentChat);
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (parentActivity == null) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (VoIPService.getSharedInstance() != null) {
                if (Build.VERSION.SDK_INT >= 32) {
                    boolean oldValue = WebRtcAudioTrack.isSpeakerMuted();
                    AudioManager am = (AudioManager) parentActivity.getSystemService(AUDIO_SERVICE);
                    int minVolume = am.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL);
                    boolean mute = am.getStreamVolume(AudioManager.STREAM_VOICE_CALL) == minVolume && event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN;
                    WebRtcAudioTrack.setSpeakerMute(mute);
                    if (oldValue != WebRtcAudioTrack.isSpeakerMuted()) {
                        getUndoView().showWithAction(0, mute ? UndoView.ACTION_VOIP_SOUND_MUTED : UndoView.ACTION_VOIP_SOUND_UNMUTED, null);
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    public class WatchersView extends LinearLayout {

        private final AnimatedTextView watchersCountTextView;
        private float lastWidth = 0;

        public WatchersView(Context context) {
            super(context);
            setOrientation(LinearLayout.VERTICAL);
            setGravity(Gravity.CENTER);
            watchersCountTextView = new AnimatedTextView(context, true, false, false);
            watchersCountTextView.setTextColor(Color.WHITE);
            watchersCountTextView.setTextSize(dp(46));
            watchersCountTextView.setTypeface(AndroidUtilities.bold());
            watchersCountTextView.setGravity(Gravity.CENTER_HORIZONTAL);

            TextView labelTextView = new TextView(context);
            labelTextView.setTextColor(Color.WHITE);
            labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            labelTextView.setTypeface(AndroidUtilities.bold());
            labelTextView.setText(getString(R.string.VoipChannelWatching));

            addView(watchersCountTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46));
            addView(labelTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        }

        void setWatchersCount(int count) {
            CharSequence watchersCountText = LocaleController.formatNumber(count, ',');
            float newWidth = watchersCountTextView.getPaint().measureText(watchersCountText, 0, watchersCountText.length());
            if (lastWidth != newWidth) {
                Shader watchersCountTextShader = new LinearGradient(0, 0, newWidth, 0, new int[]{getThemedColor(Theme.key_premiumGradient1), getThemedColor(Theme.key_premiumGradient3)}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
                watchersCountTextView.getPaint().setShader(watchersCountTextShader);
                lastWidth = newWidth;
            }
            watchersCountTextView.setText(watchersCountText);
        }
    }

    public class LightningView extends View {
        private int[] currentColors;
        private int parentBackgroundColor;
        private final Paint paint;
        private float shadowOffset;

        public LightningView(Context context) {
            super(context);

            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(0);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (needTextureLightning && renderersContainer.progressToFullscreenMode < 0.1) {
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
            }
        }

        public void setNewColors(int[] colors) {
            boolean needInvalidate = false;
            if (currentColors == null || colors[0] != currentColors[0] || colors[1] != currentColors[1]) {
                if (currentColors == null) {
                    paint.setAlpha(255);
                }
                currentColors = colors;
                if (liveLabelBgColorAnimator != null && textureLightningViewAnimatingAlpha != 1) {
                    currentColors[0] = ColorUtils.setAlphaComponent(currentColors[0], (int) (255 * textureLightningViewAnimatingAlpha));
                    currentColors[1] = ColorUtils.setAlphaComponent(currentColors[1], (int) (255 * textureLightningViewAnimatingAlpha));
                }

                LinearGradient gradient = new LinearGradient(0, 0, 0, textureLightningView.getMeasuredHeight(), currentColors, null, Shader.TileMode.CLAMP);
                paint.setShader(gradient);
                needInvalidate = true;
            }
            if (parentBackgroundColor != backgroundColor) {
                paint.setShadowLayer(dp(36), 0, shadowOffset, backgroundColor);
                parentBackgroundColor = backgroundColor;
                needInvalidate = true;
            }
            if (needInvalidate) {
                invalidate();
            }
            runUpdateTextureLightningRunnable();
        }

        public void setShadowOffset(int offset) {
            if (shadowOffset != offset) {
                paint.setShadowLayer(dp(36), 0, shadowOffset, backgroundColor);
                shadowOffset = offset;
                invalidate();
            }
        }
    }

    private static String getRandomEmoji() {
        String[] set = EmojiData.data[(int) Math.floor(Math.random() * EmojiData.data.length)];
        return set[(int) Math.floor(Math.random() * set.length)];
    }

    public static final class EmojiSlot {

        private final int offset;

        private final long INTERVAL = 180;
        private final Drawable[] pool = new Drawable[6];
        private Drawable realThumb;
        private AnimatedEmojiDrawable real;
        private boolean realAllowed;
        private boolean loaded;
        private long startTime;
        private GradientClip clip = new GradientClip();
        private final HashSet<View> parents = new HashSet<>();
        private boolean attached = false;
        private final AnimatedEmojiSpan.InvalidateHolder invalidate = () -> {
            for (View parent : parents) {
                parent.invalidate();
            }
        };

        public EmojiSlot(int offset) {
            this.offset = offset;
            for (int i = 0; i < pool.length; ++i) {
                pool[i] = Emoji.getEmojiDrawable(getRandomEmoji());
            }
            startTime = System.currentTimeMillis();
        }

        private String lastEmoji;
        public void set(String emoji) {
            final boolean wasLoaded = loaded;
            loaded = emoji != null;
            if (emoji != null && (real == null || !TextUtils.equals(lastEmoji, emoji))) {
                if (real != null) {
                    real.removeView(invalidate);
                }
                realThumb = Emoji.getEmojiDrawable(emoji);
                final int currentAccount = UserConfig.getProductionAccount();
                real = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_STANDARD_EMOJI, currentAccount);
                real.setupEmojiThumb(lastEmoji = emoji);
                updateEmoji();
                if (attached) {
                    real.addView(invalidate);
                }
            }
            if (loaded && !wasLoaded)
                realAllowed = false;
        }

        private void updateEmoji() {
            if (real == null || lastEmoji == null) return;
            final int currentAccount = UserConfig.getProductionAccount();
            final TLRPC.InputStickerSet inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
            inputStickerSet.short_name = "StaticEmoji"; // "RestrictedEmoji";
            final TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(currentAccount).getStickerSet(inputStickerSet, 0, false, true, loaded -> updateEmoji());
            if (set == null) return;

            final String thisEmoji = lastEmoji.replace("\uFE0F", "");
            TLRPC.Document document = null;
            for (TLRPC.Document d : set.documents) {
                if (TextUtils.equals(MessageObject.findAnimatedEmojiEmoticon(d, null).replace("\uFE0F", ""), thisEmoji)) {
                    document = d;
                    break;
                }
            }
            if (document != null) {
                real.setupDocument(document);
            } else {
                FileLog.e("emoji \"" + lastEmoji + "\" not found in addemoji/" + inputStickerSet.short_name);
            }
        }

        public void attach(View parent) {
            parents.add(parent);
            checkAttach();
        }

        public void detach(View parent) {
            parents.remove(parent);
            checkAttach();
        }

        private void checkAttach() {
            final boolean attached = !parents.isEmpty();
            if (this.attached != attached) {
                if (this.attached = attached) {
                    onAttached();
                } else {
                    onDetached();
                }
            }
        }

        private void onAttached() {
            if (real != null) {
                real.addView(invalidate);
            }
        }

        private void onDetached() {
            if (real != null) {
                real.removeView(invalidate);
            }
        }

        private final RectF rectF = new RectF();
        public boolean draw(Canvas canvas, RectF bounds, float alertProgress) {
            boolean stopped = false;
            final float pad = dp(6);
            rectF.set(bounds);
            rectF.inset(-pad, -pad);
            canvas.saveLayerAlpha(rectF.left, rectF.top, rectF.right, rectF.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);

            final long now = System.currentTimeMillis() + (offset * (INTERVAL / 4L));
            final long elapsed = now - startTime;

            final float y = elapsed / (float) INTERVAL;
            final float my = Math.min(1.0f, y);

            if (loaded && real != null && realThumb != null && realAllowed) {
                rectF.set(bounds);
                rectF.offset(0, (my - 1) * (bounds.height() + pad));
                if (alertProgress < 1) {
                    canvas.save();
                    realThumb.setBounds(0, 0, (int) rectF.width(), (int) rectF.height());
                    canvas.translate(rectF.left, rectF.top);
                    realThumb.setAlpha((int) (0xFF * (1.0f - alertProgress)));
                    realThumb.draw(canvas);
                    realThumb.setAlpha(0xFF);
                    canvas.restore();
                }
                if (alertProgress > 0) {
                    canvas.save();
                    rectF.inset(dp(-4), dp(-4));
                    real.setBounds(0, 0, (int) rectF.width(), (int) rectF.height());
                    canvas.translate(rectF.left, rectF.top);
                    real.setAlpha((int) (0xFF * alertProgress));
                    real.draw(canvas);
                    real.setAlpha(0xFF);
                    canvas.restore();
                }
            } else {
                canvas.save();
                rectF.set(bounds);
                rectF.offset(0, (my - 1) * (bounds.height() + pad));
                canvas.translate(rectF.left, rectF.top);
                pool[1].setBounds(0, 0, (int) rectF.width(), (int) rectF.height());
                pool[1].setAlpha((int) (0xFF * 0.5f));
                pool[1].draw(canvas);
                pool[1].setAlpha(0xFF);
                canvas.restore();
            }

            rectF.set(bounds);
            rectF.offset(0, my * (bounds.height() + pad));
            canvas.save();
            canvas.translate(rectF.left, rectF.top);
            pool[0].setBounds(0, 0, (int) rectF.width(), (int) rectF.height());
            pool[0].setAlpha((int) (0xFF * 0.5f));
            pool[0].draw(canvas);
            pool[0].setAlpha(0xFF);
            canvas.restore();

            if (y >= 1) {
                if (!loaded || !realAllowed) {
                    startTime = now - (elapsed % INTERVAL);
                    shiftPool();
                    if (loaded) realAllowed = true;
                } else {
                    stopped = true;
                }
            }

            rectF.set(bounds);
            rectF.inset((int) -pad, (int) -pad);
            rectF.set(rectF.left, rectF.top, rectF.right, rectF.top + pad);
            clip.draw(canvas, rectF, GradientClip.TOP, 1.0f);

            rectF.set(bounds);
            rectF.inset((int) -pad, (int) -pad);
            rectF.set(rectF.left, rectF.bottom - pad, rectF.right, rectF.bottom);
            clip.draw(canvas, rectF, GradientClip.BOTTOM, 1.0f);

            canvas.restore();

            return !stopped;
        }

        private void shiftPool() {
            for (int i = 0; i < pool.length - 1; ++i) {
                pool[i] = pool[i + 1];
            }
            pool[pool.length - 1] = Emoji.getEmojiDrawable(getRandomEmoji());
        }
    }

    public static final class CallEncryptionCellDrawable {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
//        private final Drawable[] emojis = new Drawable[4];
        private final EmojiSlot[] slots = new EmojiSlot[4];
        private final Text text1, text2, closeText;

        public CallEncryptionCellDrawable(Context context) {
            for (int i = 0; i < slots.length; ++i) {
                slots[i] = new EmojiSlot(i);
            }

            backgroundPaint.setColor(Theme.getColor(Theme.key_voipgroup_listViewBackground));
            text1 = new Text(getString(R.string.ConferenceEncrypted), 12, AndroidUtilities.bold());
            text2 = new Text(getString(R.string.ConferenceEncryptedInfo), 11)
                .multiline(99).setMaxWidth(dp(200)).lineSpacing(dp(2.66f));
            closeText = new Text(getString(R.string.ConferenceEncryptedClose), 14, AndroidUtilities.bold());

            setEmojis(null);
        }

        private void invalidate() {
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        private View parentView;
        public void setParentView(View parentView) {
            this.parentView = parentView;
            for (int i = 0; i < slots.length; ++i) {
                slots[i].attach(parentView);
            }
        }
        public void resetParentView(View parentView) {
            if (this.parentView != parentView) {
                for (int i = 0; i < slots.length; ++i) {
                    slots[i].detach(parentView);
                }
                this.parentView = null;
            }
        }

        private boolean loading = true;
        private final AnimatedFloat loadingAlpha = new AnimatedFloat(this::invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        public void setEmojis(String[] emojis) {
            loading = emojis == null;
            for (int i = 0; i < 4; ++i) {
                this.slots[i].set(emojis == null ? null : emojis[i]);
            }
            invalidate();
        }

        private int listBackgroundColor;
        private final RectF fromRect = new RectF();
        private final RectF toRect = new RectF();
        private void setPaintBackgroundColor(int color) {
            listBackgroundColor = color;
            invalidate();
        }

        private final Path clipPath = new Path();
        public boolean draw(Canvas canvas, final float totalWidth, float alertProgress) {
            boolean invalidate = false;
            canvas.save();
            text1.ellipsize(totalWidth - dp(80 + 6 + 14 + 32));
            backgroundPaint.setColor(ColorUtils.blendARGB(listBackgroundColor, Theme.getColor(Theme.key_voipgroup_listViewBackground), alertProgress));

            final float loading = this.loadingAlpha.set(this.loading);
            final float width1 = dp(14) + dp(80 + 6) + text1.getWidth();//lerp(, loading1.getWidth(), loading);
            final float height1 = dp(28);

            final float width2 = dp(200 + 32);
            final float height2 = dp(54) + text2.getHeight() + dp(50);

            final float width = lerp(width1, width2, alertProgress);
            final float height = lerp(height1, height2, alertProgress);

            final float r = lerp(dp(14), dp(16), alertProgress);

            AndroidUtilities.rectTmp.set(
                (totalWidth - width) / 2.0f,
                0,
                (totalWidth + width) / 2.0f,
                height
            );
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
//            if (alertProgress > 0) {
                clipPath.rewind();
                clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
                canvas.clipPath(clipPath);
//            }

            final int es = dp(18), es2p = (int) ((width2 - dp(32)) / 4), es2 = dp(30);
            final int et = (int) AndroidUtilities.rectTmp.centerY() - es / 2;
            final int eb = (int) AndroidUtilities.rectTmp.centerY() + es / 2;
            {
                fromRect.set(
                    (int) AndroidUtilities.rectTmp.left + dp(7) + dp(10) - es / 2,
                    et,
                    (int) AndroidUtilities.rectTmp.left + dp(7) + dp(10) + es / 2,
                    eb
                );
                final int toCX = (int) (totalWidth / 2.0f - 2 * es2p + es2p * .5f);
                toRect.set(
                    toCX - es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) - es2 / 2f),
                    toCX + es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) + es2 / 2f)
                );
                AndroidUtilities.lerpCentered(fromRect, toRect, alertProgress, toRect);
                if (slots[0].draw(canvas, toRect, alertProgress)) {
                    invalidate = true;
                }
            }
            {
                fromRect.set(
                    (int) AndroidUtilities.rectTmp.left + dp(7 + 20) + dp(10) - es / 2,
                    et,
                    (int) AndroidUtilities.rectTmp.left + dp(7 + 20) + dp(10) + es / 2,
                    eb
                );
                final int toCX = (int) (totalWidth / 2.0f - 2 * es2p + es2p * 1.5f);
                toRect.set(
                    toCX - es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) - es2 / 2f),
                    toCX + es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) + es2 / 2f)
                );
                AndroidUtilities.lerpCentered(fromRect, toRect, alertProgress, toRect);
                if (slots[1].draw(canvas, toRect, alertProgress)) {
                    invalidate = true;
                }
            }
//            if (loading > 0) {
//                loading1.draw(canvas, totalWidth / 2.0f - loading1.getWidth() / 2.0f, height1 / 2.0f, 0xFFFFFFFF, .75f * (1.0f - alertProgress) * loading);
//            }
            text1.draw(canvas, totalWidth / 2.0f - text1.getWidth() / 2.0f, height1 / 2.0f, 0xFFFFFFFF, (1.0f - alertProgress) * lerp(1.0f, .75f, loading));
            {
                fromRect.set(
                    (int) AndroidUtilities.rectTmp.right - dp(7 + 40) + dp(10) - es / 2,
                    et,
                    (int) AndroidUtilities.rectTmp.right - dp(7 + 40) + dp(10) + es / 2,
                    eb
                );
                final int toCX = (int) (totalWidth / 2.0f - 2 * es2p + es2p * 2.5f);
                toRect.set(
                    toCX - es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) - es2 / 2f),
                    toCX + es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) + es2 / 2f)
                );
                AndroidUtilities.lerpCentered(fromRect, toRect, alertProgress, toRect);
                if (slots[2].draw(canvas, toRect, alertProgress)) {
                    invalidate = true;
                }
            }
            {
                fromRect.set(
                    (int) AndroidUtilities.rectTmp.right - dp(7 + 20) + dp(10) - es / 2,
                    et,
                    (int) AndroidUtilities.rectTmp.right - dp(7 + 20) + dp(10) + es / 2,
                    eb
                );
                final int toCX = (int) (totalWidth / 2.0f - 2 * es2p + es2p * 3.5f);
                toRect.set(
                    toCX - es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) - es2 / 2f),
                    toCX + es2 / 2,
                    (int) (AndroidUtilities.rectTmp.top + dp(27.33f) + es2 / 2f)
                );
                AndroidUtilities.lerpCentered(fromRect, toRect, alertProgress, toRect);
                if (slots[3].draw(canvas, toRect, alertProgress)) {
                    invalidate = true;
                }
            }

            if (alertProgress > 0) {
                text2.draw(canvas, AndroidUtilities.rectTmp.centerX() - width2 / 2.0f + dp(16), dp(54), 0xFFFFFFFF, alertProgress);
                dividerPaint.setColor(0xFF000000);
                dividerPaint.setAlpha((int) (0xFF * alertProgress));
                canvas.drawRect(AndroidUtilities.rectTmp.left, height2 - dp(40), AndroidUtilities.rectTmp.right, height2 - dp(40) + dp(.66f), dividerPaint);
                closeText.draw(canvas, AndroidUtilities.rectTmp.centerX() - closeText.getWidth() / 2.0f, height2 - dp(20), 0xFFFFFFFF, alertProgress);
            }

            canvas.restore();
            return invalidate;
        }
    }

    public static final class CallEncryptionCell extends View {

        private final CallEncryptionCellDrawable drawable;
        public CallEncryptionCell(Context context, CallEncryptionCellDrawable drawable) {
            super(context);
            this.drawable = drawable;
            NotificationCenter.listenEmojiLoading(this);

//            boolean[] b = new boolean[1];
            setOnClickListener(v -> {
//                b[0] = !b[0];
//                drawable.setEmojis(b[0] ? new String[]{ getRandomEmoji(), getRandomEmoji(), getRandomEmoji(), getRandomEmoji() } : null);
                new EncryptionCallDialog(context, this).show();
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (drawable != null) {
                drawable.setParentView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (drawable != null) {
                drawable.resetParentView(this);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = dp(38);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            if (drawable != null && drawable.draw(canvas, getMeasuredWidth(), 0.0f)) {
                invalidate();
            }
        }

        private static final class EncryptionCallDialog extends Dialog {

            private final View windowView;
            private final CallEncryptionCell cell;

            private Bitmap blurBitmap;
            private Paint blurBitmapPaint;
            private BitmapShader blurBitmapShader;
            private final Matrix blurMatrix = new Matrix();

            private float progress;
            private ValueAnimator progressAnimator;

            public EncryptionCallDialog(Context context, CallEncryptionCell cell) {
                super(context, R.style.TransparentDialog);
                this.cell = cell;

                if (cell != null) {
                    cell.setVisibility(View.INVISIBLE);
                }
                AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
                    if (cell != null) {
                        cell.setVisibility(View.VISIBLE);
                    }
                    blurBitmap = bitmap;

                    blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                    ColorMatrix colorMatrix = new ColorMatrix();
                    AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .05f : +.25f);
                    AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? -.02f : -.04f);
                    blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
                }, 14);

                windowView = new View(context) {
                    private final int[] p = new int[2];
                    @Override
                    public void dispatchDraw(@NonNull Canvas canvas) {
                        if (progress > 0 && blurBitmapPaint != null) {
                            blurMatrix.reset();
                            final float s = (float) getWidth() / blurBitmap.getWidth();
                            blurMatrix.postScale(s, s);
                            blurBitmapShader.setLocalMatrix(blurMatrix);

                            blurBitmapPaint.setAlpha((int) (0xFF * progress));
                            canvas.drawRect(0, 0, getWidth(), getHeight(), blurBitmapPaint);
                        }
                        if (cell != null) {
                            if (cell.isAttachedToWindow() && cell.getAlpha() > 0.5f) {
                                cell.getLocationInWindow(p);
                            } else {
                                dismiss();
                            }
                            canvas.save();
                            canvas.translate(
                                p[0] - cell.getMeasuredWidth() * (1.0f - cell.getScaleX()),
                                p[1] - cell.getMeasuredHeight() * (1.0f - cell.getScaleY())
                            );
                            if (cell.drawable.draw(canvas, cell.getMeasuredWidth(), progress)) {
                                invalidate();
                            }
                            canvas.restore();
                        }
                    }
                };
                windowView.setOnClickListener(v -> dismiss());
            }

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);

                Window window = getWindow();
                window.setWindowAnimations(R.style.DialogNoAnimation);
                setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.FILL;
                params.dimAmount = 0;
                params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
                params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
                if (Build.VERSION.SDK_INT >= 21) {
                    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                        WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
                }
                if (!BuildVars.DEBUG_PRIVATE_VERSION) {
                    params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
                }
                params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                if (Build.VERSION.SDK_INT >= 28) {
                    params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                window.setAttributes(params);
            }

            @Override
            public void show() {
                super.show();
                animate(1.0f, null);
                AndroidUtilities.runOnUIThread(() -> {
                    if (cell != null) {
                        cell.setVisibility(View.INVISIBLE);
                    }
                }, 16);
            }

            private boolean dismissing;
            @Override
            public void dismiss() {
                if (dismissing) return;
                dismissing = true;
                animate(0.0f, () -> {
                    if (cell != null) {
                        cell.setVisibility(View.VISIBLE);
                    }
                    AndroidUtilities.runOnUIThread(super::dismiss, 16);
                });
                try {
                    WindowManager.LayoutParams params = getWindow().getAttributes();
                    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    getWindow().setAttributes(params);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            private void animate(float to, Runnable done) {
                if (progressAnimator != null) {
                    progressAnimator.cancel();
                    progressAnimator = null;
                }
                progressAnimator = ValueAnimator.ofFloat(progress, to);
                progressAnimator.addUpdateListener(anm -> {
                    progress = (float) anm.getAnimatedValue();
                    windowView.invalidate();
                });
                progressAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        progress = to;
                        windowView.invalidate();
                        if (done != null) {
                            done.run();
                        }
                    }
                });
                progressAnimator.setDuration(420);
                progressAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                progressAnimator.start();
            }
        }
    }

    public void openShareConferenceLink() {
        if (call == null || call.call == null) return;

        final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.showDelayed(300);

        final TL_phone.exportGroupCallInvite req = new TL_phone.exportGroupCallInvite();
        req.call = new TLRPC.TL_inputGroupCall();
        req.call.id = call.call.id;
        req.call.access_hash = call.call.access_hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            progressDialog.dismiss();
            if (res instanceof TL_phone.exportedGroupCallInvite) {
                final TL_phone.exportedGroupCallInvite r = (TL_phone.exportedGroupCallInvite) res;
                CallLogActivity.showCallLinkSheet(getContext(), currentAccount, req.call, r.link, new DarkBlueThemeResourcesProvider(), false, call != null && call.call != null && call.call.creator);
            } else if (err != null) {
                BulletinFactory.of(topBulletinContainer, new DarkBlueThemeResourcesProvider())
                    .showForError(err);
            }
        }));
    }

    private Integer cacheAudioOutputValue;
    public int getAudioOutputValue() {
        if (cacheAudioOutputValue != null) return cacheAudioOutputValue;
        final VoIPService voip = VoIPService.getSharedInstance();
        if (voip == null) return 0;
        final int currentAudioRoute = voip.getCurrentAudioRoute();
        switch (currentAudioRoute) {
            case VoIPService.AUDIO_ROUTE_SPEAKER: return 0;
            case VoIPService.AUDIO_ROUTE_EARPIECE: return 1;
            default: return 2;
        }
    }

    public void setAudioOutputValue(int value) {
        final VoIPService voip = VoIPService.getSharedInstance();
        if (voip == null) return;
        voip.setAudioOutput(value);
        cacheAudioOutputValue = value;
    }

    public int getNextAudioOutputValue() {
        final int currentValue = getAudioOutputValue();
        final boolean active = getAudioOutputActive(currentValue);
        if (active) {
            return 1;
        } else {
            final VoIPService voip = VoIPService.getSharedInstance();
            if (voip != null && voip.isBluetoothHeadsetConnected()) {
                return 2;
            }
            return 0;
        }
    }

    public String getAudioOutputName(int value) {
        final VoIPService voip = VoIPService.getSharedInstance();
        switch (value) {
            case 0: return getString(R.string.VoipAudioRoutingSpeaker);
            case 1: return voip != null && voip.isHeadsetPlugged() ? getString(R.string.VoipAudioRoutingHeadset) : getString(R.string.VoipAudioRoutingPhone);
            case 2: return voip != null && voip.currentBluetoothDeviceName != null ? voip.currentBluetoothDeviceName : getString(R.string.VoipAudioRoutingBluetooth);
            default: return null;
        }
    }

    public int getAudioOutputIcon(int value) {
        final VoIPService voip = VoIPService.getSharedInstance();
        if (voip != null && voip.isBluetoothHeadsetConnected()) { // if (value == 2) {
            return R.drawable.filled_calls_bluetooth_s;
        } else {
            return R.drawable.filled_sound_on;
        }
    }

    public boolean getAudioOutputActive(int value) {
        switch (value) {
            case 0: return true;
            case 1: return false;
            case 2: return true;
            default: return true;
        }
    }

    public int getAudioOutputToastIcon(int value) {
        if (value == 2) {
            return R.drawable.msg_voice_bluetooth;
        } else if (value == 0) {
            return R.drawable.msg_voice_speaker;
        } else {
            final VoIPService voip = VoIPService.getSharedInstance();
            return voip != null && voip.isHeadsetPlugged() ? R.drawable.msg_voice_headphones : R.drawable.msg_voice_phone;
        }
    }

    public String getAudioOutputToastText(int value) {
        final VoIPService voip = VoIPService.getSharedInstance();
        if (value == 2) {
            return voip != null && voip.currentBluetoothDeviceName != null ? formatString(R.string.VoipAudioSwitchedToBluetoothDevice, voip.currentBluetoothDeviceName) : getString(R.string.VoipAudioSwitchedToBluetooth);
        } else if (value == 1) {
            return voip != null && voip.isHeadsetPlugged() ? getString(R.string.VoipAudioSwitchedToHeadset) : getString(R.string.VoipAudioSwitchedToPhone);
        } else {
            return getString(R.string.VoipAudioSwitchedToSpeaker);
        }
    }

    private boolean isBulletinTop() {
        final int offset = dp(74);
        float top = scrollOffsetY - offset;
        float moveProgress = 0;
        if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
            int willMoveUpTo = offset - backgroundPaddingTop - dp(14);
            moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / willMoveUpTo);
        }
        return moveProgress > 0.5f;
    }

    private void updateTopBulletinY() {
        if (topBulletinContainer == null) return;
        int offset = dp(74);
        float top = scrollOffsetY - offset;
        float moveProgress = 0;
        if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
            int willMoveUpTo = offset - backgroundPaddingTop - dp(14);
            moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / willMoveUpTo);
            int diff = (int) ((ActionBar.getCurrentActionBarHeight() - willMoveUpTo) * moveProgress);
            top -= diff;
        }
        top += containerView.getPaddingTop();
        topBulletinContainer.setTranslationY(
            lerp(
                -topBulletinContainer.getTop() - topBulletinContainer.getHeight() + top + dp(10),
                -topBulletinContainer.getTop() + actionBar.getY() + actionBar.getHeight(),
                moveProgress
            )
        );
        final Bulletin bulletin = Bulletin.getVisibleBulletin();
        if (bulletin != null && bulletin.getLayout() != null && bulletin.getLayout().getParent() != null && bulletin.getLayout().getParent().getParent() == topBulletinContainer) {
            bulletin.getLayout().setTop(moveProgress > 0.5f);
        }
    }
}
