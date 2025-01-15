package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.ChatListItemAnimator;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChannelBoostsController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarSpan;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChooseSpeedLayout;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BitmapShaderTools;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertDocumentLayout;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPopupMenu;
import org.telegram.ui.Components.DotDividerSpan;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.HashtagActivity;
import org.telegram.ui.Components.HintView;
import org.telegram.ui.Components.InstantCameraView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.MentionsContainerView;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.Reactions.AnimatedEmojiEffect;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.Reactions.ReactionsUtils;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.SpeedIconDrawable;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.EmojiAnimationsOverlay;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MessageStatisticActivity;
import org.telegram.ui.NotificationsCustomSettingsActivity;
import org.telegram.ui.PinchToZoomHelper;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ReportBottomSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.CaptionContainerView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.WrappedResourceProvider;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class PeerStoriesView extends SizeNotifierFrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static boolean DISABLE_STORY_REPOSTING = false;
    public static final float SHARE_BUTTON_OFFSET = 46;
    private final static long IMAGE_LIVE_TIME = 10_000;
    private final ImageView optionsIconView;
    private final FrameLayout muteIconContainer;
    private final RLottieImageView muteIconView;
    private final ImageView noSoundIconView;
    private final Theme.ResourcesProvider resourcesProvider;
//    private final CloseFriendsBadge closeFriendsBadge;
    private final StoryPrivacyButton privacyButton;
    private final FrameLayout likeButtonContainer;
    private StoriesLikeButton storiesLikeButton;
    private HintView2 privacyHint;
    private HintView2 soundTooltip;
    private HintView2 reactionsLongpressTooltip;
    private int reactionsContainerIndex;
    private final StoryViewer storyViewer;
    private final StoryCaptionView storyCaptionView;
    private CaptionContainerView storyEditCaptionView;
    private final ImageView shareButton;
    @Nullable
    private ImageView repostButton;
    private final LinearLayout bottomActionsLinearLayout;
    @Nullable
    private FrameLayout repostButtonContainer;
    private AnimatedTextView.AnimatedTextDrawable reactionsCounter;
    @Nullable
    private AnimatedTextView.AnimatedTextDrawable repostCounter;
    private AnimatedFloat reactionsCounterProgress;
    private AnimatedFloat repostCounterProgress;
    private boolean reactionsCounterVisible;
    private boolean repostCounterVisible;
    private long currentImageTime;
    private long lastDrawTime;
    private boolean switchEventSent;
    private boolean lastNoThumb;
    private boolean attachedToWindow;
    private boolean allowDrawSurface = true;

    public FrameLayout storyContainer;
    private final ImageReceiver imageReceiver;
    private final ImageReceiver leftPreloadImageReceiver;
    private final ImageReceiver rightPreloadImageReceiver;
    private final ArrayList<ReactionImageHolder> preloadReactionHolders = new ArrayList<>();
    private Runnable onImageReceiverThumbLoaded;

    private StoryMediaAreasView storyAreasView;
    private EmojiAnimationsOverlay emojiAnimationsOverlay;

    private SelfStoriesPreviewView.ImageHolder viewsThumbImageReceiver;
    private float viewsThumbAlpha;

    private final AvatarDrawable avatarDrawable;
    PeerHeaderView headerView;
    private final StoryLinesDrawable storyLines;
    private StoryPositionView storyPositionView;

    private int shiftDp = -5;
    ActionBarMenuSubItem editStoryItem;
    CustomPopupMenu popupMenu;

    TL_stories.PeerStories userStories;
    final ArrayList<TL_stories.StoryItem> storyItems;
    final ArrayList<StoriesController.UploadingStory> uploadingStories;
    final SharedResources sharedResources;
    RoundRectOutlineProvider outlineProvider;

    ArrayList<Integer> day;

    int count;
    private long dialogId;
    boolean isSelf;
    boolean isChannel;
    boolean isGroup;
    boolean isPremiumBlocked;

    private float alpha = 1f;
    private int previousSelectedPotision = -1;
    private int selectedPosition;
    boolean isActive;

    private int listPosition;
    private int linesPosition, linesCount;

    public final StoryItemHolder currentStory = new StoryItemHolder();
    private final BitmapShaderTools bitmapShaderTools;

    Delegate delegate;
    private boolean paused;
    StoriesController storiesController;
    private boolean isUploading, isEditing, isFailed;
    private FrameLayout selfView;
    ChatActivityEnterView chatActivityEnterView;
    private ValueAnimator changeBoundAnimator;
    ReactionsContainerLayout reactionsContainerLayout;
    private LinearLayout premiumBlockedText;
    private TextView premiumBlockedText1;
    private TextView premiumBlockedText2;

    private StoryFailView failView;
    private ViewPropertyAnimator failViewAnimator;

    Paint inputBackgroundPaint;

    int lastKeyboardHeight;
    ValueAnimator keyboardAnimator;
    float progressToKeyboard = -1;
    float progressToDismiss = -1;
    float lastAnimatingKeyboardHeight = -1;
    int lastOpenedKeyboardHeight;
    boolean animateKeyboardOpening;
    public boolean keyboardVisible;
    private boolean wasBigScreen;
    private boolean BIG_SCREEN;
    private int realKeyboardHeight;
    private int classGuid = ConnectionsManager.generateClassGuid();
    private TextView selfStatusView;
    private AvatarsImageView selfAvatarsView;
    private int currentAccount;
    private int totalStoriesCount;
    private boolean deletedPeer;
    private View selfAvatarsContainer;
    boolean showViewsProgress;
    private Runnable cancellableViews;
    private boolean isRecording;
    private float animatingKeyboardHeight;
    private ChatAttachAlert chatAttachAlert;
    private InstantCameraView instantCameraView;
    private int enterViewBottomOffset;
    private boolean isLongPressed;

    final VideoPlayerSharedScope playerSharedScope;
    final AnimationNotificationsLocker notificationsLocker;
    private AnimatedFloat progressToHideInterface = new AnimatedFloat(this);
    private AnimatedFloat linesAlpha = new AnimatedFloat(this);
    private float prevToHideProgress;
    public long videoDuration;
    private boolean allowShare, allowRepost, allowShareLink;
    public boolean forceUpdateOffsets;
    private HintView mediaBanTooltip;

    public PinchToZoomHelper pinchToZoomHelper = new PinchToZoomHelper();
    private boolean imageChanged;
    public ShareAlert shareAlert;
    private FrameLayout unsupportedContainer;
    private TextView replyDisabledTextView;
    public boolean unsupported;
    private boolean isVideoStory;
    private MentionsContainerView mentionContainer;
    private float muteIconViewAlpha = 1f;
    private boolean isVisible;
    boolean inBlackoutMode;
    boolean checkBlackoutMode;
    private boolean messageSent;
    private boolean isCaptionPartVisible;
    private boolean stealthModeIsActive;
    private ImageReceiver reactionEffectImageReceiver;
    private AnimatedEmojiEffect emojiReactionEffect;
    private ImageReceiver reactionMoveImageReceiver;
    private AnimatedEmojiDrawable reactionMoveDrawable;
    private boolean drawAnimatedEmojiAsMovingReaction;
    private boolean drawReactionEffect;
    private ReactionsContainerLayout likesReactionLayout;
    private boolean likesReactionShowing;
    private float likesReactionShowProgress;
    private boolean movingReaction;
    private float movingReactionProgress;
    private int movingReactionFromX, movingReactionFromY, movingReactionFromSize;
    private Runnable reactionsTooltipRunnable;
    private float viewsThumbScale;
    private float viewsThumbPivotY;
    private boolean userCanSeeViews;

    private ChooseSpeedLayout speedLayout;
    private ActionBarMenuSubItem speedItem;

    public PeerStoriesView(@NonNull Context context, StoryViewer storyViewer, SharedResources sharedResources, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        pinchToZoomHelper.setCallback(new PinchToZoomHelper.Callback() {
            @Override
            public void onZoomStarted(MessageObject messageObject) {
                delegate.setIsInPinchToZoom(true);
            }

            @Override
            public void onZoomFinished(MessageObject messageObject) {
                delegate.setIsInPinchToZoom(false);
            }
        });
        playerSharedScope = new VideoPlayerSharedScope();
        notificationsLocker = new AnimationNotificationsLocker();
        this.storyItems = new ArrayList<>();
        this.uploadingStories = new ArrayList<>();

        this.imageReceiver = new ImageReceiver() {

            @Override
            protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                boolean r = super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                if (type == TYPE_THUMB && onImageReceiverThumbLoaded != null) {
                    onImageReceiverThumbLoaded.run();
                    onImageReceiverThumbLoaded = null;
                }
                return r;
            }
        };
        this.imageReceiver.setCrossfadeWithOldImage(false);
        this.imageReceiver.setAllowLoadingOnAttachedOnly(true);
        this.imageReceiver.ignoreNotifications = true;
        this.imageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_LOW);

        this.reactionEffectImageReceiver = new ImageReceiver(this);
        this.reactionEffectImageReceiver.setAllowLoadingOnAttachedOnly(true);
        this.reactionEffectImageReceiver.ignoreNotifications = true;
        this.reactionEffectImageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_HIGH);

        this.reactionMoveImageReceiver = new ImageReceiver(this);
        this.reactionMoveImageReceiver.setAllowLoadingOnAttachedOnly(true);
        this.reactionMoveImageReceiver.ignoreNotifications = true;
        this.reactionMoveImageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_HIGH);

        this.leftPreloadImageReceiver = new ImageReceiver();
        this.leftPreloadImageReceiver.setAllowLoadingOnAttachedOnly(true);
        this.leftPreloadImageReceiver.ignoreNotifications = true;
        this.leftPreloadImageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_LOW);

        this.rightPreloadImageReceiver = new ImageReceiver();
        this.rightPreloadImageReceiver.setAllowLoadingOnAttachedOnly(true);
        this.rightPreloadImageReceiver.ignoreNotifications = true;
        this.rightPreloadImageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_LOW);
        imageReceiver.setPreloadingReceivers(Arrays.asList(leftPreloadImageReceiver, rightPreloadImageReceiver));

        this.avatarDrawable = new AvatarDrawable();
        this.storyViewer = storyViewer;
        this.sharedResources = sharedResources;
        this.bitmapShaderTools = sharedResources.bitmapShaderTools;
        storiesController = MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController();
        sharedResources.dimPaint.setColor(Color.BLACK);
        inputBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);

        storyAreasView = new StoryMediaAreasView(context, storyContainer, resourcesProvider) {
            @Override
            protected void onHintVisible(boolean hintVisible) {
                if (delegate != null) {
                    delegate.setIsHintVisible(hintVisible);
                }
            }

            @Override
            protected void presentFragment(BaseFragment fragment) {
                if (storyViewer != null) {
                    storyViewer.presentFragment(fragment);
                }
            }

            @Override
            public void showEffect(StoryReactionWidgetView v) {
                if (!isSelf && currentStory.storyItem != null) {
                    ReactionsLayoutInBubble.VisibleReaction newReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(v.mediaArea.reaction);
                    ReactionsLayoutInBubble.VisibleReaction currentReaction = ReactionsLayoutInBubble.VisibleReaction.fromTL(currentStory.storyItem.sent_reaction);
                    if (!Objects.equals(newReaction, currentReaction)) {
                        likeStory(newReaction);
                    }
                }
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                v.playAnimation();
                emojiAnimationsOverlay.showAnimationForWidget(v);
            }

            @Override
            protected Bitmap getPlayingBitmap() {
                return PeerStoriesView.this.getPlayingBitmap();
            }
        };

        storyContainer = new HwFrameLayout(context) {

            final AnimatedFloat progressToAudio = new AnimatedFloat(this, 150, CubicBezierInterpolator.DEFAULT);
            final AnimatedFloat progressToFullBlackoutA = new AnimatedFloat(this, 150, CubicBezierInterpolator.DEFAULT);
            final CellFlickerDrawable loadingDrawable = new CellFlickerDrawable(32, 102, 240);
            final AnimatedFloat loadingDrawableAlpha2 = new AnimatedFloat(this);
            final AnimatedFloat loadingDrawableAlpha = new AnimatedFloat(this);
            {
                loadingDrawableAlpha2.setDuration(500);
                loadingDrawableAlpha.setDuration(100);
            }

            boolean splitDrawing;
            boolean drawOverlayed;

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!isActive) {
                    headerView.backupImageView.getImageReceiver().setVisible(true, true);
                }
                if (!unsupported) {
                    if (playerSharedScope.renderView != null || storyAreasView != null && (storyAreasView.hasSelectedForScale() || storyAreasView.parentHighlightScaleAlpha.isInProgress())) {
                        invalidate();
                    }
                    canvas.save();
                    pinchToZoomHelper.applyTransform(canvas);
                    if (playerSharedScope.renderView != null && playerSharedScope.firstFrameRendered) {
                        //playerSharedScope.surfaceView.draw(canvas);
                        if (!imageReceiver.hasBitmapImage()) {
                            sharedResources.imageBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight() + 1);
                            sharedResources.imageBackgroundDrawable.draw(canvas);
                        }
                        imageReceiver.setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight() + 1);
                        imageReceiver.draw(canvas);
                        if (isActive) {
                            if (storyViewer.USE_SURFACE_VIEW && playerSharedScope.player != null && playerSharedScope.player.paused && playerSharedScope.player.playerStubBitmap != null && playerSharedScope.player.stubAvailable) {
                                float sx = getMeasuredWidth() / (float) playerSharedScope.player.playerStubBitmap.getWidth();
                                float sy = getMeasuredHeight() / (float) playerSharedScope.player.playerStubBitmap.getHeight();
                                canvas.save();
                                canvas.scale(sx, sy);
                                canvas.drawBitmap(playerSharedScope.player.playerStubBitmap, 0, 0, playerSharedScope.player.playerStubPaint);
                                canvas.restore();
                            } else {
                                if (!storyViewer.USE_SURFACE_VIEW || allowDrawSurface && storyViewer.isShown()) {
                                    playerSharedScope.renderView.draw(canvas);
                                }
                            }
                        }

                    } else {
                        if (playerSharedScope.renderView != null) {
                            invalidate();
                        }
                        if (currentStory.skipped) {
                            canvas.drawColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));
                        } else {
                            if (!imageReceiver.hasBitmapImage()) {
                                sharedResources.imageBackgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight() + 1);
                                sharedResources.imageBackgroundDrawable.draw(canvas);
                            }
                            imageReceiver.setImageCoords(0, 0, getMeasuredWidth(), getMeasuredHeight() + 1);
                            imageReceiver.draw(canvas);
                        }
                    }
                    canvas.restore();
                    if (imageChanged) {
                        loadingDrawableAlpha2.set(0, true);
                        loadingDrawableAlpha.set(0, true);
                    }
                    boolean storyDrawing;
                    if (currentStory.isVideo) {
                        storyDrawing = playerSharedScope.renderView != null && playerSharedScope.player != null && playerSharedScope.firstFrameRendered && !(playerSharedScope.player.progress == 0 && playerSharedScope.isBuffering() && !playerSharedScope.player.paused);
                    } else {
                        storyDrawing = imageReceiver.hasNotThumb();
                    }
                    loadingDrawableAlpha2.set(isActive && !storyDrawing && currentStory.uploadingStory == null ? 1f : 0f);
                    loadingDrawableAlpha.set(loadingDrawableAlpha2.get() == 1f ? 1f : 0);

                    if (loadingDrawableAlpha.get() > 0) {
                        AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        loadingDrawable.setAlpha((int) (255 * loadingDrawableAlpha.get()));
                        loadingDrawable.setParentWidth(getMeasuredWidth() * 2);
                        loadingDrawable.animationSpeedScale = 1.3f;
                        loadingDrawable.draw(canvas, AndroidUtilities.rectTmp, dp(10), this);
                    }
                    imageChanged = false;
                } else {
                    canvas.drawColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));
                }
                if (storyCaptionView.getAlpha() > 0) {
                    if (storyCaptionView.getAlpha() == 1f) {
                        canvas.save();
                    } else {
                        canvas.saveLayerAlpha(0, 0, storyCaptionView.getMeasuredWidth(), storyCaptionView.getMeasuredHeight(), (int) (255 * storyCaptionView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                    }
                    storyAreasView.draw(canvas);
                    canvas.restore();
                }

                if (!lastNoThumb && imageReceiver.hasNotThumb()) {
                    lastNoThumb = true;
                    PeerStoriesView.this.invalidate();
                }
                float hideInterfaceAlpha = getHideInterfaceAlpha();

                sharedResources.topOverlayGradient.setAlpha(0xFF);
                sharedResources.topOverlayGradient.draw(canvas);
                float progressToFullBlackout = 0;
                if ((isSelf || !BIG_SCREEN) || storyCaptionView.getVisibility() == View.VISIBLE) {
                    if (storyCaptionView.getVisibility() == View.VISIBLE) {
                        int gradientHeight = dp(72);
                        int gradientTop = (int) (storyCaptionView.getTextTop() - dp(24)) + storyCaptionView.getTop();
                        int gradientBottom = gradientTop + gradientHeight;
                        float startFullBlackoutFrom = getMeasuredHeight() * 0.65f;
                        boolean hideCaptionWithInterface = hideCaptionWithInterface();
                        if ((startFullBlackoutFrom - gradientTop) / dp(60) > 0 && storyCaptionView.isTouched() && storyCaptionView.hasScroll()) {
                            int maxGradientTop = (int) (storyCaptionView.getMaxTop() - dp(24)) + storyCaptionView.getTop();
                            if ((startFullBlackoutFrom - maxGradientTop) / dp(60) > 0) {
                                inBlackoutMode = true;
                            }
                        } else if (checkBlackoutMode) {
                            checkBlackoutMode = false;
                            int maxGradientTop = (int) (storyCaptionView.getMaxTop() - dp(24)) + storyCaptionView.getTop();
                            if ((startFullBlackoutFrom - maxGradientTop) / dp(60) > 0) {
                                inBlackoutMode = true;
                            }
                        } else if (storyCaptionView.getProgressToBlackout() == 0) {
                            inBlackoutMode = false;
                        }
                        float hideCaptionAlpha = hideCaptionWithInterface ? hideInterfaceAlpha : 1f;
                        progressToFullBlackout = progressToFullBlackoutA.set(inBlackoutMode ? 1f : 0);
                        if (progressToFullBlackout > 0) {
                            splitDrawing = true;
                            drawOverlayed = false;
                            super.dispatchDraw(canvas);
                            splitDrawing = false;
                            drawLines(canvas);
                            sharedResources.gradientBackgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.6f * progressToFullBlackout * hideCaptionAlpha)));
                            canvas.drawPaint(sharedResources.gradientBackgroundPaint);
                        }
                        if (progressToFullBlackout < 1f) {
                            canvas.save();
                            sharedResources.gradientBackgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.506f * (1f - progressToFullBlackout) * hideCaptionAlpha)));
                            sharedResources.bottomOverlayGradient.setAlpha((int) (255 * (1f - progressToFullBlackout) * hideCaptionAlpha));
                            sharedResources.bottomOverlayGradient.setBounds(0, gradientTop, getMeasuredWidth(), gradientBottom);
                            sharedResources.bottomOverlayGradient.draw(canvas);
                            canvas.drawRect(0, gradientBottom, getMeasuredWidth(), getMeasuredHeight(), sharedResources.gradientBackgroundPaint);
                            canvas.restore();
                        }
                        if (progressToFullBlackout > 0 && storyCaptionView.getAlpha() > 0) {
                            storyCaptionView.disableDraw(false);
                            if (storyCaptionView.getAlpha() != 1f) {
                                canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), (int) (255 * storyCaptionView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                            } else {
                                canvas.save();
                            }
                            canvas.translate(storyCaptionView.getX(), storyCaptionView.getY() - storyCaptionView.getScrollY());
                            storyCaptionView.draw(canvas);
                            canvas.restore();
                        }
                        storyCaptionView.disableDraw(progressToFullBlackout > 0);
                        if (progressToFullBlackout > 0) {
                            splitDrawing = true;
                            drawOverlayed = true;
                            super.dispatchDraw(canvas);
                            splitDrawing = false;
                        }
                    } else {
                        int bottomGradientHeight = BIG_SCREEN ? dp(56) : dp(110);
                        if ((isSelf || !BIG_SCREEN) && storyCaptionView.getVisibility() == View.VISIBLE) {
                            bottomGradientHeight *= 2.5f;
                        }
                        sharedResources.bottomOverlayGradient.setBounds(0, storyContainer.getMeasuredHeight() - bottomGradientHeight, getMeasuredWidth(), storyContainer.getMeasuredHeight());

                        sharedResources.bottomOverlayGradient.setAlpha((int) (255 * hideInterfaceAlpha));
                        sharedResources.bottomOverlayGradient.draw(canvas);
                    }
                }
                if (viewsThumbAlpha != 0 && viewsThumbImageReceiver != null) {
                    viewsThumbImageReceiver.draw(canvas, viewsThumbAlpha, viewsThumbScale, 0, 0, getMeasuredWidth(), getMeasuredHeight() + 1);
                }

                progressToAudio.set(isRecording ? 1f : 0);

                if (isActive) {
                    boolean isCaption = storyCaptionView.getVisibility() == View.VISIBLE && (inBlackoutMode || storyCaptionView.isTouched());
                    isCaptionPartVisible = storyCaptionView.getVisibility() == View.VISIBLE && (storyCaptionView.getProgressToBlackout() > 0);
                    delegate.setIsCaption(isCaption);
                    delegate.setIsCaptionPartVisible(isCaptionPartVisible);
                }
                if (progressToFullBlackout <= 0) {
                    super.dispatchDraw(canvas);
                    drawLines(canvas);
                }

                if (emojiAnimationsOverlay != null) {
                    emojiAnimationsOverlay.draw(canvas);
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == storyAreasView) {
                    return true;
                }
                if (splitDrawing) {
                    if (Bulletin.getVisibleBulletin() != null && child == Bulletin.getVisibleBulletin().getLayout()) {
                        if (drawOverlayed) {
                            return super.drawChild(canvas, child, drawingTime);
                        } else {
                            return true;
                        }
                    } else {
                        return super.drawChild(canvas, child, drawingTime);
                    }
                } else {
                    return super.drawChild(canvas, child, drawingTime);
                }
            }

            private void drawLines(Canvas canvas) {
                if (imageReceiver.hasNotThumb() || (currentStory.isVideo && playerSharedScope.firstFrameRendered)) {
                    currentStory.checkSendView();
                }

                float timeProgress = 0;
                float hideInterfaceAlpha = getHideInterfaceAlpha();
                if (currentStory.isVideo()) {
                    if (playerSharedScope.player != null) {
                        float p = playerSharedScope.player.getPlaybackProgress(videoDuration);
                        timeProgress = Utilities.clamp(p, 1f, 0f);
                        if (playerSharedScope.firstFrameRendered && storyAreasView != null) {
                            storyAreasView.shine();
                        }
                    }
                    invalidate();
                } else if (!paused && isActive && !isUploading && !isEditing && !isFailed && imageReceiver.hasNotThumb()) {
                    long currentTime = System.currentTimeMillis();
                    if (lastDrawTime != 0) {
                        if (!isCaptionPartVisible) {
                            if (currentImageTime <= 0 && currentTime - lastDrawTime > 0 && storyAreasView != null) {
                                storyAreasView.shine();
                            }
                            currentImageTime += currentTime - lastDrawTime;
                        }
                    }
                    lastDrawTime = currentTime;
                    timeProgress = Utilities.clamp(currentImageTime / (float) IMAGE_LIVE_TIME, 1f, 0f);
                    invalidate();
                } else {
                    timeProgress = Utilities.clamp(currentImageTime / (float) IMAGE_LIVE_TIME, 1f, 0f);
                }

                float zoomTimeProgress = timeProgress;
                if (playerSharedScope != null && playerSharedScope.player != null && playerSharedScope.player.currentSeek >= 0) {
                    zoomTimeProgress = playerSharedScope.player.currentSeek;
                }

                if (!switchEventSent && timeProgress == 1f && !(currentStory.isVideo && isCaptionPartVisible) && !isLongPressed) {
                    switchEventSent = true;
                    post(() -> {
                        if (delegate != null) {
                            if (isUploading || isEditing || isFailed) {
                                if (currentStory.isVideo()) {
                                    playerSharedScope.player.loopBack();
                                } else {
                                    currentImageTime = 0;
                                }
                            } else {
                                delegate.shouldSwitchToNext();
                            }
                        }
                    });
                }
                if (storyViewer.storiesList != null && storyViewer.storiesList.type != StoriesController.StoriesList.TYPE_SEARCH) {
                    if (storyPositionView == null) {
                        storyPositionView = new StoryPositionView();
                    }
                    storyPositionView.draw(canvas, hideInterfaceAlpha * alpha * (1f - outT), listPosition, storyViewer.storiesList.getCount(), this, headerView);
                }

                canvas.save();
                canvas.translate(0, dp(8) - dp(8) * outT);
                boolean buffering = currentStory.isVideo() && playerSharedScope.isBuffering();
                boolean zoom = isLongPressed && (currentStory != null && currentStory.isVideo) && (storyViewer != null && storyViewer.inSeekingMode);
                float linesInterfaceAlpha = linesAlpha.set(!isLongPressed || zoom);
                storyLines.draw(canvas, getMeasuredWidth(), linesPosition, timeProgress, linesCount, linesInterfaceAlpha, alpha * (1f - outT), buffering, zoom, zoomTimeProgress);
                canvas.restore();
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                emojiAnimationsOverlay.onAttachedToWindow();
                Bulletin.addDelegate(this, new Bulletin.Delegate() {
                    @Override
                    public int getTopOffset(int tag) {
                        return dp(58);
                    }

                    public boolean clipWithGradient(int tag) {
                        return tag == 1 || tag == 2 || tag == 3;
                    }

                    @Override
                    public void onShow(Bulletin bulletin) {
                        if (bulletin != null && bulletin.tag == 2 && delegate != null) {
                            delegate.setBulletinIsVisible(true);
                        }
                    }

                    @Override
                    public void onHide(Bulletin bulletin) {
                        if (bulletin != null && bulletin.tag == 2 && delegate != null) {
                            delegate.setBulletinIsVisible(false);
                        }
                    }

                    @Override
                    public int getBottomOffset(int tag) {
                        return BIG_SCREEN ? 0 : dp(64);
                    }
                });
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                emojiAnimationsOverlay.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
                if (delegate != null) {
                    delegate.setBulletinIsVisible(false);
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                LayoutParams layoutParams = (LayoutParams) muteIconContainer.getLayoutParams();
                if (drawLinesAsCounter()) {
                    layoutParams.rightMargin = dp(2);
                    layoutParams.topMargin = dp(15 + 40);
                } else {
                    layoutParams.rightMargin = dp(2 + 40);
                    layoutParams.topMargin = dp(15);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        storyContainer.setClipChildren(false);
        emojiAnimationsOverlay = new EmojiAnimationsOverlay(storyContainer, currentAccount);

        storyContainer.addView(storyAreasView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        storyCaptionView = new StoryCaptionView(getContext(), storyViewer.resourcesProvider) {
            @Override
            public void onLinkClick(CharacterStyle span, View spoilersTextView) {
                if (span instanceof URLSpanUserMention) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(Utilities.parseLong(((URLSpanUserMention) span).getURL()));
                    if (user != null) {
                        MessagesController.getInstance(currentAccount).openChatOrProfileWith(user, null, storyViewer.fragment, 0, false);
                    }
                } else if (span instanceof URLSpanNoUnderline) {
                    String str = ((URLSpanNoUnderline) span).getURL();
                    if (str != null && (str.startsWith("#") || str.startsWith("$"))) {
                        if (str.contains("@")) {
                            if (storyViewer != null) {
                                storyViewer.presentFragment(new HashtagActivity(str));
                            }
                        } else {
                            Bundle args = new Bundle();
                            args.putInt("type", MediaActivity.TYPE_STORIES_SEARCH);
                            args.putString("hashtag", str);
                            if (storyViewer != null) {
                                storyViewer.presentFragment(new MediaActivity(args, null));
                            }
                        }
                    } else {
                        String username = Browser.extractUsername(str);
                        if (username != null) {
                            username = username.toLowerCase();
                            if (str.startsWith("@")) {
                                MessagesController.getInstance(currentAccount).openByUserName(username, storyViewer.fragment, 0, null);
                            } else {
                                processExternalUrl(0, str, span, false);
                            }
                        } else {
                            processExternalUrl(0, str, span, false);
                        }
                    }
                } else if (span instanceof URLSpan) {
                    String url = ((URLSpan) span).getURL();
                    processExternalUrl(2, url, span, span instanceof URLSpanReplacement);
                } else if (span instanceof URLSpanMono) {
                    ((URLSpanMono) span).copyToClipboard();
                    BulletinFactory.of(storyContainer, resourcesProvider).createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show();
                } else if (span instanceof ClickableSpan) {
                    ((ClickableSpan) span).onClick(spoilersTextView);
                }
            }

            private void processExternalUrl(int type, String url, CharacterStyle span, boolean forceAlert) {
                if (forceAlert || AndroidUtilities.shouldShowUrlInAlert(url)) {
                    if (type == 0 || type == 2) {
                        boolean forceNotInternalForApps = false;
                        if (span instanceof URLSpanReplacement && ((URLSpanReplacement) span).getTextStyleRun() != null && (((URLSpanReplacement) span).getTextStyleRun().flags & TextStyleSpan.FLAG_STYLE_TEXT_URL) != 0) {
                            forceNotInternalForApps = true;
                        }
                        AlertsCreator.showOpenUrlAlert(storyViewer.fragment, url, true, true, true, forceNotInternalForApps, null, resourcesProvider);
                    } else if (type == 1) {
                        AlertsCreator.showOpenUrlAlert(storyViewer.fragment, url, true, true, false, null, resourcesProvider);
                    }
                } else {
                    if (type == 0) {
                        Browser.openUrl(getContext(), Uri.parse(url), true, true, null);
                    } else if (type == 1) {
                        Browser.openUrl(getContext(), Uri.parse(url), false, false, null);
                    } else if (type == 2) {
                        Browser.openUrl(getContext(), Uri.parse(url), false, true, null);
                    }
                }
            }

            @Override
            public void onLinkLongPress(URLSpan span, View spoilersTextView, Runnable done) {
                final String urlFinal = span.getURL();
                String formattedUrl = span.getURL();
                try {
                    try {
                        Uri uri = Uri.parse(formattedUrl);
                        formattedUrl = Browser.replaceHostname(uri, Browser.IDN_toUnicode(uri.getHost()), null);
                    } catch (Exception e) {
                        FileLog.e(e, false);
                    }
                    formattedUrl = URLDecoder.decode(formattedUrl.replaceAll("\\+", "%2b"), "UTF-8");
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
                BottomSheet.Builder builder = new BottomSheet.Builder(getContext(), false, resourcesProvider);
                builder.setTitle(formattedUrl);
                builder.setTitleMultipleLines(true);
                builder.setItems(currentStory != null && !currentStory.allowScreenshots() ? new CharSequence[] {LocaleController.getString(R.string.Open)} : new CharSequence[]{LocaleController.getString(R.string.Open), LocaleController.getString(R.string.Copy)}, (dialog, which) -> {
                    if (which == 0) {
                        onLinkClick(span, spoilersTextView);
                    } else if (which == 1) {
                        AndroidUtilities.addToClipboard(urlFinal);
                        BulletinFactory.of(storyContainer, resourcesProvider).createCopyLinkBulletin().show();
                    }
                });
                builder.setOnPreDismissListener(di -> done.run());
                BottomSheet sheet = builder.create();
                sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                delegate.showDialog(sheet);
            }

            @Override
            public void onReplyClick(Reply reply) {
                if (reply == null) return;
                if (reply.isRepostMessage && reply.peerId != null && reply.messageId != null) {
                    Bundle args = new Bundle();
                    if (reply.peerId >= 0) {
                        args.putLong("user_id", reply.peerId);
                    } else {
                        args.putLong("chat_id", -reply.peerId);
                    }
                    args.putInt("message_id", reply.messageId);
                    storyViewer.presentFragment(new ChatActivity(args));
                    return;
                }
                if (reply.peerId == null || reply.storyId == null) {
                    BulletinFactory.of(storyContainer, resourcesProvider)
                        .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.StoryHidAccount))
                        .setTag(3)
                        .show(true);
                    return;
                }
                MessagesController.getInstance(currentAccount).getStoriesController().resolveStoryLink(reply.peerId, reply.storyId, fwdStoryItem -> {
                    if (fwdStoryItem != null) {
                        BaseFragment lastFragment = LaunchActivity.getLastFragment();
                        if (lastFragment == null) {
                            return;
                        }
                        fwdStoryItem.dialogId = reply.peerId;
                        StoryViewer overlayStoryViewer = lastFragment.createOverlayStoryViewer();
                        overlayStoryViewer.open(getContext(), fwdStoryItem, null);
                        overlayStoryViewer.setOnCloseListener(() -> {
                            storyViewer.updatePlayingMode();
                        });
                        storyViewer.updatePlayingMode();
                    } else {
                        BulletinFactory.of(storyContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.story_bomb2, LocaleController.getString(R.string.StoryNotFound))
                            .setTag(3)
                            .show(true);
                    }
                });
            }

            @Override
            public void onEmojiClick(AnimatedEmojiSpan span) {
                if (span == null || delegate == null) {
                    return;
                }
                TLRPC.Document document = span.document;
                if (document == null) {
                    document = AnimatedEmojiDrawable.findDocument(currentAccount, span.documentId);
                }
                if (document == null) {
                    return;
                }
                Bulletin bulletin = BulletinFactory.of(storyContainer, resourcesProvider).createContainsEmojiBulletin(document, BulletinFactory.CONTAINS_EMOJI_IN_STORY, set -> {
                    ArrayList<TLRPC.InputStickerSet> inputSets = new ArrayList<>(1);
                    inputSets.add(set);
                    EmojiPacksAlert alert = new EmojiPacksAlert(storyViewer.fragment, getContext(), resourcesProvider, inputSets);
                    if (delegate != null) {
                        delegate.showDialog(alert);
                    }
                });
                if (bulletin == null) {
                    return;
                }
                bulletin.tag = 1;
                bulletin.show(true);
            }
        };
        storyCaptionView.captionTextview.setOnClickListener(v -> {
            if (storyCaptionView.expanded) {
                if (!storyCaptionView.textSelectionHelper.isInSelectionMode()) {
                    storyCaptionView.collapse();
                } else {
                    storyCaptionView.checkCancelTextSelection();
                }
            } else {
                checkBlackoutMode = true;
                storyCaptionView.expand();
            }
        });

        shareButton = new ImageView(context);
        shareButton.setImageDrawable(sharedResources.shareDrawable);
        int padding = dp(8);
        shareButton.setPadding(padding, padding, padding, padding);
        shareButton.setOnClickListener(v -> {
            shareStory(true);
        });
        ScaleStateListAnimator.apply(shareButton);

        if (!DISABLE_STORY_REPOSTING) {
            repostButton = new ImageView(context);
            repostButton.setImageDrawable(sharedResources.repostDrawable);
            repostButton.setPadding(padding, padding, padding, padding);

            repostButtonContainer = new FrameLayout(getContext()) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (isChannel && repostCounter != null) {
                        canvas.save();
                        canvas.translate(getMeasuredWidth() - repostCounter.getCurrentWidth() - AndroidUtilities.dp(6), 0);
                        float repostScale = repostCounterProgress.set(repostCounterVisible ? 1f : 0);
                        canvas.scale(repostScale, repostScale, repostCounter.getCurrentWidth() / 2f, AndroidUtilities.dp(20));
                        repostCounter.setAlpha(0xFF);
                        repostCounter.draw(canvas);
                        canvas.restore();
                    }
                }

                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return who == repostCounter || super.verifyDrawable(who);
                }
            };
            if (repostCounter != null) {
                repostCounter.setCallback(repostButtonContainer);
            }
            repostButtonContainer.setWillNotDraw(false);
            repostButtonContainer.setOnClickListener(v -> tryToOpenRepostStory());
        }

        likeButtonContainer = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (isChannel && reactionsCounter != null) {
                    canvas.save();
                    canvas.translate(getMeasuredWidth() - reactionsCounter.getCurrentWidth() - AndroidUtilities.dp(6), 0);
                    float reactionScale = reactionsCounterProgress.set(reactionsCounterVisible ? 1f : 0);
                    canvas.scale(reactionScale, reactionScale, reactionsCounter.getCurrentWidth() / 2f, AndroidUtilities.dp(20));
                    reactionsCounter.setAlpha(0xFF);
                    reactionsCounter.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == reactionsCounter || super.verifyDrawable(who);
            }
        };
        if (reactionsCounter != null) {
            reactionsCounter.setCallback(likeButtonContainer);
        }
        likeButtonContainer.setWillNotDraw(false);
        likeButtonContainer.setOnClickListener(v -> {
            if (currentStory.storyItem != null && currentStory.storyItem.sent_reaction == null) {
                applyMessageToChat(() -> {
                    likeStory(null);
                });
            } else {
                likeStory(null);
            }
        });
        likeButtonContainer.setOnLongClickListener(v -> {
            if (reactionsTooltipRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(reactionsTooltipRunnable);
                reactionsTooltipRunnable = null;
            }
            SharedConfig.setStoriesReactionsLongPressHintUsed(true);
            if (reactionsLongpressTooltip != null) {
                reactionsLongpressTooltip.hide();
            }
            checkReactionsLayoutForLike();
            storyViewer.windowView.dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
            showLikesReaction(true);
            return true;
        });
        storiesLikeButton = new StoriesLikeButton(context, sharedResources);
        storiesLikeButton.setPadding(padding, padding, padding, padding);
        likeButtonContainer.addView(storiesLikeButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT));
        if (repostButtonContainer != null) {
            repostButtonContainer.addView(repostButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT));
        }
        ScaleStateListAnimator.apply(likeButtonContainer, 0.3f, 5f);
        if (repostButtonContainer != null) {
            ScaleStateListAnimator.apply(repostButtonContainer, 0.3f, 5f);
        }

        imageReceiver.setAllowLoadingOnAttachedOnly(true);
        imageReceiver.setParentView(storyContainer);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider = new RoundRectOutlineProvider(10);
            storyContainer.setOutlineProvider(outlineProvider);
            storyContainer.setClipToOutline(true);
        }
        addView(storyContainer);
        headerView = new PeerHeaderView(context, currentStory);
        headerView.setOnClickListener(v -> {
            if (UserConfig.getInstance(currentAccount).clientUserId == dialogId) {
                Bundle args = new Bundle();
                args.putInt("type", MediaActivity.TYPE_STORIES);
                args.putLong("dialog_id", dialogId);
                MediaActivity mediaActivity = new MediaActivity(args, null);
                storyViewer.presentFragment(mediaActivity);
            } else {
                if (dialogId > 0) {
                    storyViewer.presentFragment(ProfileActivity.of(dialogId));
                } else {
                    storyViewer.presentFragment(ChatActivity.of(dialogId));
                }
            }
        });
        storyContainer.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 17, 0, 0));

        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.setDuration(150);
        layoutTransition.disableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        bottomActionsLinearLayout = new LinearLayout(context);
        bottomActionsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        bottomActionsLinearLayout.setLayoutTransition(layoutTransition);
        bottomActionsLinearLayout.addView(shareButton, LayoutHelper.createLinear(40, 40, Gravity.RIGHT));
        if (repostButtonContainer != null) {
            bottomActionsLinearLayout.addView(repostButtonContainer, LayoutHelper.createLinear(40, 40, Gravity.RIGHT));
        }
        bottomActionsLinearLayout.addView(likeButtonContainer, LayoutHelper.createLinear(40, 40, Gravity.RIGHT));

        addView(bottomActionsLinearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 0, 0,4,0));

        optionsIconView = new ImageView(context);
        optionsIconView.setImageDrawable(sharedResources.optionsDrawable);
        optionsIconView.setPadding(dp(8), dp(8), dp(8), dp(8));
        optionsIconView.setBackground(Theme.createSelectorDrawable(Color.WHITE));
        storyContainer.addView(optionsIconView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 2, 15, 2, 0));

        optionsIconView.setOnClickListener(v -> {
            delegate.setPopupIsVisible(true);
            editStoryItem = null;
            final boolean[] popupStillVisible = new boolean[] { false };
            if (isSelf) {
                MessagesController.getInstance(currentAccount).getStoriesController().loadBlocklistAtFirst();
                MessagesController.getInstance(currentAccount).getStoriesController().loadSendAs();
                MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().load();
            }
            final boolean userCanEditStory = isSelf || MessagesController.getInstance(currentAccount).getStoriesController().canEditStory(currentStory.storyItem);
            final boolean canEditStory = isSelf || ((isChannel || isBotsPreview()) && userCanEditStory);
            final boolean speedControl = currentStory.isVideo;
            popupMenu = new CustomPopupMenu(getContext(), resourcesProvider, speedControl) {
                private boolean edit;

                private void addViewStatistics(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, TL_stories.StoryItem storyItem) {
                    if (isChannel) {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (chat != null) {
                            TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(chat.id);
                            if (chatFull == null) {
                                chatFull = MessagesStorage.getInstance(currentAccount).loadChatInfo(chat.id, true, new CountDownLatch(1), false, false);
                            }
                            if (chatFull != null && chatFull.can_view_stats) {
                                ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_stats, LocaleController.getString(R.string.ViewStatistics), false, resourcesProvider).setOnClickListener(v -> {
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                    storyItem.dialogId = dialogId;
                                    storyItem.messageId = storyItem.id;
                                    MessageObject msg = new MessageObject(currentAccount, storyItem);
                                    msg.generateThumbs(false);
                                    storyViewer.presentFragment(new MessageStatisticActivity(msg, chat.id, false) {
                                        @Override
                                        public Theme.ResourcesProvider getResourceProvider() {
                                            return new DarkThemeResourceProvider();
                                        }

                                        @Override
                                        public boolean isLightStatusBar() {
                                            return false;
                                        }
                                    });
                                });
                            }
                        }
                    }
                }

                private void addSpeedLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, boolean addGap) {
                    if (!speedControl || currentStory != null && currentStory.uploadingStory != null) {
                        speedLayout = null;
                        speedItem = null;
                        return;
                    }

                    speedLayout = new ChooseSpeedLayout(getContext(), popupLayout.getSwipeBack(), new ChooseSpeedLayout.Callback() {
                        @Override
                        public void onSpeedSelected(float speed, boolean isFinal, boolean closeMenu) {
                            if (storyViewer != null) {
                                storyViewer.setSpeed(speed);
                            }
                            updateSpeedItem(isFinal);
                            if (closeMenu && popupLayout.getSwipeBack() != null) {
                                popupLayout.getSwipeBack().closeForeground();
                            }
                        }
                    });
                    speedLayout.update(StoryViewer.currentSpeed, true);

                    speedItem = new ActionBarMenuSubItem(getContext(), false, false, false, resourcesProvider);
                    speedItem.setTextAndIcon(LocaleController.getString(R.string.Speed), R.drawable.msg_speed, null);
                    updateSpeedItem(true);
                    speedItem.setMinimumWidth(AndroidUtilities.dp(196));
                    speedItem.setRightIcon(R.drawable.msg_arrowright);
                    popupLayout.addView(speedItem);
                    LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) speedItem.getLayoutParams();
                    if (LocaleController.isRTL) {
                        layoutParams.gravity = Gravity.RIGHT;
                    }
                    layoutParams.width = LayoutHelper.MATCH_PARENT;
                    layoutParams.height = AndroidUtilities.dp(48);
                    speedItem.setLayoutParams(layoutParams);
                    int swipeBackIndex = popupLayout.addViewToSwipeBack(speedLayout.speedSwipeBackLayout);
                    speedItem.openSwipeBackLayout = () -> {
                        if (popupLayout.getSwipeBack() != null) {
                            popupLayout.getSwipeBack().openForeground(swipeBackIndex);
                        }
                    };
                    speedItem.setOnClickListener(view -> {
                        speedItem.openSwipeBack();
                    });

                    popupLayout.swipeBackGravityRight = true;

                    if (addGap) {
                        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider, Theme.key_actionBarDefaultSubmenuSeparator);
                        gap.setTag(R.id.fit_width_tag, 1);
                        popupLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                    }
                }

                @Override
                protected void onCreate(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
                    if (canEditStory || currentStory.uploadingStory != null) {
                        TL_stories.StoryItem storyItem = currentStory.storyItem;
                        if (currentStory.uploadingStory != null) {
                            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_cancel, LocaleController.getString(R.string.Cancel), false, resourcesProvider);
                            item.setOnClickListener(v -> {
                                if (currentStory.uploadingStory != null) {
                                    currentStory.uploadingStory.cancel();
                                    updateStoryItems();
                                }
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }
                        if (storyItem == null) {
                            return;
                        }
                        String str = currentStory.isVideo() ? LocaleController.getString(R.string.SaveVideo) : LocaleController.getString(R.string.SaveImage);

                        if (isSelf) {
                            final StoryPrivacyBottomSheet.StoryPrivacy storyPrivacy = new StoryPrivacyBottomSheet.StoryPrivacy(currentAccount, storyItem.privacy);
                            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_view_file, LocaleController.getString(R.string.WhoCanSee), false, resourcesProvider);
                            item.setSubtext(storyPrivacy.toString());
                            item.setOnClickListener(v -> {
                                editPrivacy(storyPrivacy, storyItem);
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                            item.setItemHeight(56);
                        }
                        addSpeedLayout(popupLayout, false);
                        if (isSelf || speedControl) {
                            ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider, Theme.key_actionBarDefaultSubmenuSeparator);
                            gap.setTag(R.id.fit_width_tag, 1);
                            popupLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                        }

                        if (!unsupported && (isBotsPreview() || MessagesController.getInstance(currentAccount).storiesEnabled()) && userCanEditStory) {
                            editStoryItem = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_edit, LocaleController.getString(isBotsPreview() ? R.string.EditBotPreview : R.string.EditStory), false, resourcesProvider);
                            editStoryItem.setOnClickListener(v -> {
                                if (v.getAlpha() < 1) {
                                    AndroidUtilities.shakeViewSpring(v, shiftDp = -shiftDp);
                                    BulletinFactory.of(storyContainer, resourcesProvider).createErrorBulletin("Wait until current upload is complete").show();
                                    return;
                                }
                                Activity activity = AndroidUtilities.findActivity(context);
                                if (activity == null) {
                                    return;
                                }
                                edit = true;
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                                Runnable openEdit = () -> {
                                    StoryRecorder editor = StoryRecorder.getInstance(activity, currentAccount);
                                    long time = 0;
                                    if (playerSharedScope != null && playerSharedScope.player != null) {
                                        time = playerSharedScope.player.currentPosition;
                                    }
                                    StoryEntry entry = MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().getForEdit(currentStory.storyItem.dialogId, currentStory.storyItem);
                                    if (entry == null || entry.isRepostMessage || entry.file == null || !entry.file.exists()) {
                                        entry = StoryEntry.fromStoryItem(currentStory.getPath(), currentStory.storyItem);
                                        entry.editStoryPeerId = dialogId;
                                    }
                                    if (entry != null) {
                                        entry = entry.copy();
                                    }
                                    if (isBotsPreview()) {
                                        entry.botId = dialogId;
                                        entry.editingBotPreview = MessagesController.toInputMedia(currentStory.storyItem.media);
                                        if (storyViewer.storiesList instanceof StoriesController.BotPreviewsList) {
                                            StoriesController.BotPreviewsList list = (StoriesController.BotPreviewsList) storyViewer.storiesList;
                                            entry.botLang = list.lang_code;
                                        }
                                    }
                                    editor.openEdit(StoryRecorder.SourceView.fromStoryViewer(storyViewer), entry, time, true);
                                    editor.setOnFullyOpenListener(() -> {
                                        editOpened = true;
                                        setActive(false);
                                    });
                                    editor.setOnPrepareCloseListener((t, close, sent, did) -> {
                                        final long start = System.currentTimeMillis();
                                        if (playerSharedScope.player == null) {
                                            delegate.setPopupIsVisible(false);
                                            setActive(true);
                                            editOpened = false;
                                            onImageReceiverThumbLoaded = () -> {
                                                AndroidUtilities.cancelRunOnUIThread(close);
                                                AndroidUtilities.runOnUIThread(close);
                                            };
                                            if (sent) {
                                                updatePosition();
                                            }
                                            AndroidUtilities.runOnUIThread(close, 400);
                                            return;
                                        }
                                        playerSharedScope.firstFrameRendered = playerSharedScope.player.firstFrameRendered = false;
                                        playerSharedScope.player.setOnReadyListener(() -> {
                                            AndroidUtilities.cancelRunOnUIThread(close);
                                            AndroidUtilities.runOnUIThread(close, Math.max(0, 32L - (System.currentTimeMillis() - start)));
                                        });
                                        delegate.setPopupIsVisible(false);
                                        if (muteIconView != null) {
                                            muteIconView.setAnimation(sharedResources.muteDrawable);
                                        }
                                        if (videoDuration > 0 && t > videoDuration - 1400) {
                                            t = 0L;
                                        }
                                        setActive(t, true);
                                        editOpened = false;
                                        AndroidUtilities.runOnUIThread(close, 400);
                                        if (sent) {
                                            updatePosition();
                                        }
                                    });
                                };
                                if (!delegate.releasePlayer(openEdit)) {
                                    openEdit.run();
                                }
                            });
                            if (storiesController.hasUploadingStories(dialogId) && currentStory.isVideo && !SharedConfig.allowPreparingHevcPlayers()) {
                                editStoryItem.setAlpha(0.5f);
                            }
                        }

                        if (currentStory.storyItem != null && currentStory.isVideo && (currentStory.storyItem.pinned || isEditBotsPreview())) {
                            ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.menu_cover_stories, LocaleController.getString(R.string.StoryEditCoverMenu), false, resourcesProvider);
                            item.setOnClickListener(v -> {
                                File f = currentStory.getPath();
                                if (f == null || !f.exists()) {
                                    showDownloadAlert();
                                    return;
                                }
                                Activity activity = AndroidUtilities.findActivity(context);
                                if (activity == null) {
                                    return;
                                }
                                edit = true;
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                                Runnable openEdit = () -> {
                                    StoryRecorder editor = StoryRecorder.getInstance(activity, currentAccount);
                                    long time = 0;
                                    if (playerSharedScope != null && playerSharedScope.player != null) {
                                        time = playerSharedScope.player.currentPosition;
                                    }
                                    StoryEntry entry = StoryEntry.fromStoryItem(currentStory.getPath(), currentStory.storyItem);
                                    entry.editStoryPeerId = dialogId;
                                    entry.cover = StoryEntry.getCoverTime(currentStory.storyItem);
                                    if (entry != null) {
                                        entry = entry.copy();
                                    }
                                    entry.isEditingCover = true;
                                    entry.editingCoverDocument = currentStory.storyItem.media.document;
                                    final TL_stories.StoryItem finalStoryItem = currentStory.storyItem;
                                    entry.updateDocumentRef = whenDone -> {
                                        if (finalStoryItem instanceof StoriesController.BotPreview && ((StoriesController.BotPreview) finalStoryItem).list != null) {
                                            StoriesController.BotPreviewsList list = ((StoriesController.BotPreview) finalStoryItem).list;
                                            list.reload(() -> {
                                                for (int i = 0; i < list.messageObjects.size(); ++i) {
                                                    MessageObject msg = list.messageObjects.get(i);
                                                    if (msg == null || msg.storyItem == null || msg.storyItem.media == null) continue;
                                                    if (storyItem.media.document != null) {
                                                        if (msg.storyItem.media.document == null) continue;
                                                        if (msg.storyItem.media.document.id == storyItem.media.document.id) {
                                                            whenDone.run(msg.storyItem.media.document);
                                                            return;
                                                        }
                                                    }
                                                }
                                                whenDone.run(null);
                                            });
                                        } else {
                                            TL_stories.TL_stories_getStoriesByID req = new TL_stories.TL_stories_getStoriesByID();
                                            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(finalStoryItem.dialogId);
                                            req.id.add(finalStoryItem.id);
                                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                                                if (res instanceof TL_stories.TL_stories_stories) {
                                                    TL_stories.TL_stories_stories stories = (TL_stories.TL_stories_stories) res;
                                                    MessagesController.getInstance(currentAccount).putUsers(stories.users, false);
                                                    MessagesController.getInstance(currentAccount).putChats(stories.chats, false);
                                                    for (int i = 0; i < stories.stories.size(); ++i) {
                                                        if (stories.stories.get(i).id == finalStoryItem.id) {
                                                            whenDone.run(stories.stories.get(i).media.document);
                                                            return;
                                                        }
                                                    }
                                                }
                                                whenDone.run(null);
                                            }));
                                        }
                                    };
                                    if (isBotsPreview()) {
                                        entry.botId = dialogId;
                                        entry.editingBotPreview = MessagesController.toInputMedia(currentStory.storyItem.media);
                                        if (storyViewer.storiesList instanceof StoriesController.BotPreviewsList) {
                                            StoriesController.BotPreviewsList list = (StoriesController.BotPreviewsList) storyViewer.storiesList;
                                            entry.botLang = list.lang_code;
                                        }
                                    }
                                    editor.openEdit(StoryRecorder.SourceView.fromStoryViewer(storyViewer), entry, time, true);
                                    editor.setOnFullyOpenListener(() -> {
                                        editOpened = true;
                                        setActive(false);
                                    });
                                    editor.setOnPrepareCloseListener((t, close, sent, did) -> {
                                        final long start = System.currentTimeMillis();
                                        if (playerSharedScope.player == null) {
                                            delegate.setPopupIsVisible(false);
                                            setActive(true);
                                            editOpened = false;
                                            onImageReceiverThumbLoaded = () -> {
                                                AndroidUtilities.cancelRunOnUIThread(close);
                                                AndroidUtilities.runOnUIThread(close);
                                            };
                                            if (sent) {
                                                updatePosition();
                                            }
                                            AndroidUtilities.runOnUIThread(close, 400);
                                            return;
                                        }
                                        playerSharedScope.firstFrameRendered = playerSharedScope.player.firstFrameRendered = false;
                                        playerSharedScope.player.setOnReadyListener(() -> {
                                            AndroidUtilities.cancelRunOnUIThread(close);
                                            AndroidUtilities.runOnUIThread(close, Math.max(0, 32L - (System.currentTimeMillis() - start)));
                                        });
                                        delegate.setPopupIsVisible(false);
                                        if (muteIconView != null) {
                                            muteIconView.setAnimation(sharedResources.muteDrawable);
                                        }
                                        if (videoDuration > 0 && t > videoDuration - 1400) {
                                            t = 0L;
                                        }
                                        setActive(t, true);
                                        editOpened = false;
                                        AndroidUtilities.runOnUIThread(close, 400);
                                        if (sent) {
                                            updatePosition();
                                        }
                                    });
                                };
                                if (!delegate.releasePlayer(openEdit)) {
                                    openEdit.run();
                                }
                            });
                        }

                        if (isSelf || (isChannel && MessagesController.getInstance(currentAccount).getStoriesController().canEditStories(storyItem.dialogId))) {
                            final boolean pin = !storyItem.pinned;
                            String title;
                            if (isSelf) {
                                title = pin ? LocaleController.getString(R.string.SaveToProfile) : LocaleController.getString(R.string.ArchiveStory);
                            } else {
                                title = pin ? LocaleController.getString(R.string.SaveToPosts) : LocaleController.getString(R.string.RemoveFromPosts);
                            }
                            ActionBarMenuItem.addItem(popupLayout, pin ? R.drawable.msg_save_story : R.drawable.menu_unsave_story, title, false, resourcesProvider).setOnClickListener(v -> {
                                ArrayList<TL_stories.StoryItem> storyItems = new ArrayList<>();
                                storyItems.add(storyItem);
                                MessagesController.getInstance(currentAccount).getStoriesController().updateStoriesPinned(dialogId, storyItems, pin, success -> {
                                    if (success) {
                                        storyItem.pinned = pin;
                                        if (isSelf) {
                                            BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(pin ? R.raw.contact_check : R.raw.chats_archived, pin ? LocaleController.getString(R.string.StoryPinnedToProfile) : LocaleController.getString(R.string.StoryArchivedFromProfile)).show();
                                        } else {
                                            if (pin) {
                                                BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check,
                                                        LocaleController.getString(R.string.StoryPinnedToPosts),
                                                        LocaleController.getString(R.string.StoryPinnedToPostsDescription)
                                                ).show();
                                            } else {
                                                BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.chats_archived, LocaleController.getString(R.string.StoryUnpinnedFromPosts)).show();
                                            }
                                        }
                                    } else {
                                        BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError)).show();
                                    }
                                });
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }

                        addViewStatistics(popupLayout, storyItem);

                        if (!unsupported) {
                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_gallery, str, false, resourcesProvider).setOnClickListener(v -> {
                                saveToGallery();
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }

                        if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !isChannel) {
                            createStealthModeItem(popupLayout);
                        }

                        if (isChannel && allowShareLink) {
                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_link, LocaleController.getString(R.string.CopyLink), false, resourcesProvider).setOnClickListener(v -> {
                                AndroidUtilities.addToClipboard(currentStory.createLink());
                                onLinkCopied();
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }

                        if (allowShareLink) {
                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_shareout, LocaleController.getString(R.string.BotShare), false, resourcesProvider).setOnClickListener(v -> {
                                shareStory(false);
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }

                        if (isSelf || MessagesController.getInstance(currentAccount).getStoriesController().canDeleteStory(currentStory.storyItem)) {
                            ActionBarMenuSubItem deleteItem = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_delete, LocaleController.getString(R.string.Delete), false, resourcesProvider);
                            deleteItem.setSelectorColor(Theme.multAlpha(Theme.getColor(Theme.key_text_RedBold, resourcesProvider), .12f));
                            deleteItem.setColors(resourcesProvider.getColor(Theme.key_text_RedBold), resourcesProvider.getColor(Theme.key_text_RedBold));
                            deleteItem.setOnClickListener(v -> {
                                deleteStory();
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }
                    } else {
//                        ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_mute, LocaleController.getString(R.string.Mute), false, resourcesProvider).setOnClickListener(v -> {
//                            if (popupMenu != null) {
//                                popupMenu.dismiss();
//                            }
//                        });

                        addSpeedLayout(popupLayout, true);

                        final String key = NotificationsController.getSharedPrefKey(dialogId, 0);
                        boolean muted = !NotificationsCustomSettingsActivity.areStoriesNotMuted(currentAccount, dialogId);
                        TLRPC.User user = null;
                        TLRPC.Chat chat = null;
                        TLObject object;
                        if (dialogId > 0) {
                            object = user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        } else {
                            object = chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        }
                        String name = user == null ? (chat == null ? "" : chat.title) : UserObject.getFirstName(user).trim();
                        int index = name.indexOf(" ");
                        if (index > 0) {
                            name = name.substring(0, index);
                        }
                        String finalName = name;
                        if (!UserObject.isService(dialogId) && !isBotsPreview()) {
                            if (!muted) {
                                ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_mute, LocaleController.getString(R.string.NotificationsStoryMute2), false, resourcesProvider);
                                item.setOnClickListener(v -> {
                                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, false).apply();
                                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialogId, 0);

                                    BulletinFactory.of(storyContainer, resourcesProvider).createUsersBulletin(Arrays.asList(object), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryMutedHint", R.string.NotificationsStoryMutedHint, finalName))).setTag(2).show();
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                                item.setMultiline(false);
                            } else {
                                ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_unmute, LocaleController.getString(R.string.NotificationsStoryUnmute2), false, resourcesProvider);
                                item.setOnClickListener(v -> {
                                    MessagesController.getNotificationsSettings(currentAccount).edit().putBoolean("stories_" + key, true).apply();
                                    NotificationsController.getInstance(currentAccount).updateServerNotificationsSettings(dialogId, 0);
                                    BulletinFactory.of(storyContainer, resourcesProvider).createUsersBulletin(Arrays.asList(object), AndroidUtilities.replaceTags(LocaleController.formatString("NotificationsStoryUnmutedHint", R.string.NotificationsStoryUnmutedHint, finalName))).setTag(2).show();
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                                item.setMultiline(false);
                            }
                            boolean canShowArchive;
                            boolean storiesIsHidden;
                            if (dialogId > 0) {
                                canShowArchive = user != null && user.contact;
                                storiesIsHidden = user != null && user.stories_hidden;
                            } else {
                                canShowArchive = chat != null && !ChatObject.isNotInChat(chat);
                                storiesIsHidden = chat != null && chat.stories_hidden;
                            }
                            if (canShowArchive) {
                                if (!storiesIsHidden) {
                                    ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_archive, LocaleController.getString(R.string.ArchivePeerStories), false, resourcesProvider).setOnClickListener(v -> {
                                        toggleArchiveForStory(dialogId);
                                        if (popupMenu != null) {
                                            popupMenu.dismiss();
                                        }
                                    });
                                } else {
                                    ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_unarchive, LocaleController.getString(R.string.UnarchiveStories), false, resourcesProvider).setOnClickListener(v -> {
                                        toggleArchiveForStory(dialogId);
                                        if (popupMenu != null) {
                                            popupMenu.dismiss();
                                        }
                                    });
                                }
                            }
                        }

                        if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && currentStory.isVideo) {
                            createQualityItem(popupLayout);
                        }

                        if (!unsupported && allowShare) {
                            if (UserConfig.getInstance(currentAccount).isPremium()) {
                                ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_gallery, LocaleController.getString(R.string.SaveToGallery), false, resourcesProvider).setOnClickListener(v -> {
                                    saveToGallery();
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                            } else if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
                                Drawable lockIcon = ContextCompat.getDrawable(context, R.drawable.msg_gallery_locked2);
                                lockIcon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 0.5f), PorterDuff.Mode.MULTIPLY));
                                CombinedDrawable combinedDrawable = new CombinedDrawable(
                                        ContextCompat.getDrawable(context, R.drawable.msg_gallery_locked1),
                                        lockIcon
                                ) {
                                    @Override
                                    public void setColorFilter(ColorFilter colorFilter) {

                                    }
                                };
                                ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_gallery, LocaleController.getString(R.string.SaveToGallery), false, resourcesProvider);
                                item.setIcon(combinedDrawable);
                                item.setOnClickListener(v -> {
                                    item.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                    BulletinFactory bulletinFactory = BulletinFactory.global();
                                    if (bulletinFactory != null) {
                                        bulletinFactory.createSimpleBulletin(R.raw.ic_save_to_gallery, AndroidUtilities.replaceSingleTag(
                                                LocaleController.getString(R.string.SaveStoryToGalleryPremiumHint),
                                                () -> {
                                                    PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(storyViewer.fragment, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
                                                    delegate.showDialog(sheet);
                                                })).show();
                                    }
                                });
                            }
                        }

                        if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() && !isChannel) {
                            createStealthModeItem(popupLayout);
                        }
                        if (allowShareLink) {
                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_link2, LocaleController.getString(R.string.CopyLink), false, resourcesProvider).setOnClickListener(v -> {
                                AndroidUtilities.addToClipboard(currentStory.createLink());
                                onLinkCopied();
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }
                        if (allowShareLink) {
                            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_shareout, LocaleController.getString(R.string.BotShare), false, resourcesProvider).setOnClickListener(v -> {
                                shareStory(false);
                                if (popupMenu != null) {
                                    popupMenu.dismiss();
                                }
                            });
                        }

                        if (currentStory.storyItem != null) {
                            if (currentStory.storyItem.translated && TextUtils.equals(currentStory.storyItem.translatedLng, TranslateAlert2.getToLanguage())) {
                                ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_translate, LocaleController.getString(R.string.HideTranslation), false, resourcesProvider).setOnClickListener(v -> {
                                    currentStory.storyItem.translated = false;
                                    MessagesController.getInstance(currentAccount).getStoriesController().getStoriesStorage().updateStoryItem(currentStory.storyItem.dialogId, currentStory.storyItem);
                                    cancelTextSelection();
                                    updatePosition();
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                            } else if (MessagesController.getInstance(currentAccount).getTranslateController().canTranslateStory(currentStory.storyItem)) {
                                ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_translate, LocaleController.getString(R.string.TranslateMessage), false, resourcesProvider).setOnClickListener(v -> {
                                    currentStory.storyItem.translated = true;
                                    cancelTextSelection();
                                    if (delegate != null) {
                                        delegate.setTranslating(true);
                                    }
                                    MessagesController.getInstance(currentAccount).getStoriesController().getStoriesStorage().updateStoryItem(currentStory.storyItem.dialogId, currentStory.storyItem);
                                    final long start = System.currentTimeMillis();
                                    final Runnable finishTranslate = () -> {
                                        if (delegate != null) {
                                            delegate.setTranslating(false);
                                        }
                                        PeerStoriesView.this.updatePosition();
                                        checkBlackoutMode = true;
                                        storyCaptionView.expand(true);
                                    };
                                    MessagesController.getInstance(currentAccount).getTranslateController().translateStory(currentStory.storyItem, () -> AndroidUtilities.runOnUIThread(finishTranslate, Math.max(0, 500L - (System.currentTimeMillis() - start))));
                                    updatePosition();
                                    checkBlackoutMode = true;
                                    storyCaptionView.expand(true);
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                            }
                        }

                        addViewStatistics(popupLayout, currentStory.storyItem);

                        if (!unsupported) {
                            if (!UserObject.isService(dialogId) && !isBotsPreview()) {
                                ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_report, LocaleController.getString(R.string.ReportChat), false, resourcesProvider).setOnClickListener(v -> {
                                    if (storyViewer != null) storyViewer.setOverlayVisible(true);
                                    ReportBottomSheet.openStory(currentAccount, getContext(), currentStory.storyItem, BulletinFactory.of(storyContainer, resourcesProvider), resourcesProvider, status -> {
                                        if (storyViewer != null) storyViewer.setOverlayVisible(false);
                                    });
                                    if (popupMenu != null) {
                                        popupMenu.dismiss();
                                    }
                                });
                            }
                        }
                    }

                    final boolean hasStickers = currentStory != null && (
//                        currentStory.uploadingStory != null && currentStory.uploadingStory.entry != null && currentStory.uploadingStory.entry.stickers != null && !currentStory.uploadingStory.entry.stickers.isEmpty() ||
                        currentStory.storyItem != null && currentStory.storyItem.media != null && (
                             MessageObject.isDocumentHasAttachedStickers(currentStory.storyItem.media.document) ||
                             currentStory.storyItem.media.photo != null && currentStory.storyItem.media.photo.has_stickers
                        )
                    );
                    ArrayList<TLRPC.InputStickerSet> setsFromCaption = getAnimatedEmojiSets(currentStory);
                    final boolean hasEmojis = setsFromCaption != null && !setsFromCaption.isEmpty();
                    if (hasStickers || hasEmojis) {
                        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(context, resourcesProvider, Theme.key_actionBarDefaultSubmenuSeparator);
                        gap.setTag(R.id.fit_width_tag, 1);
                        popupLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));

                        TLObject obj = currentStory.storyItem.media.document != null ? currentStory.storyItem.media.document : currentStory.storyItem.media.photo;
                        StoryContainsEmojiButton btn = new StoryContainsEmojiButton(context, currentAccount, obj, currentStory.storyItem, hasStickers, setsFromCaption, resourcesProvider);
                        btn.setOnClickListener(v -> {
                            BottomSheet alert = btn.getAlert();
                            if (alert != null && delegate != null) {
                                delegate.showDialog(alert);
                                popupMenu.dismiss();
                            }
                        });
                        btn.setTag(R.id.fit_width_tag, 1);
                        popupLayout.addView(btn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }
                }

                @Override
                protected void onDismissed() {
                    if (!edit && !popupStillVisible[0]) {
                        AndroidUtilities.runOnUIThread(() -> {
                            delegate.setPopupIsVisible(false);
                        });
                    }
                    popupMenu = null;
                    editStoryItem = null;
                }
            };
            popupMenu.show(optionsIconView, 0, -ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(6));
        });

        muteIconContainer = new FrameLayout(context);
        storyContainer.addView(muteIconContainer, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 2, 15, 2 + 40, 0));

        muteIconView = new RLottieImageView(context);
        muteIconView.setPadding(dp(6), dp(6), dp(6), dp(6));
        muteIconContainer.addView(muteIconView);

        noSoundIconView = new ImageView(context);
        noSoundIconView.setPadding(dp(6), dp(6), dp(6), dp(6));
        noSoundIconView.setImageDrawable(sharedResources.noSoundDrawable);
        muteIconContainer.addView(noSoundIconView);
        noSoundIconView.setVisibility(View.GONE);

        privacyButton = new StoryPrivacyButton(context);
        privacyButton.setOnClickListener(v -> {
            TL_stories.StoryItem storyItem = currentStory.storyItem;
            if (storyItem == null) {
                return;
            }
            if (isSelf) {
                StoryPrivacyBottomSheet.StoryPrivacy privacy = new StoryPrivacyBottomSheet.StoryPrivacy(currentAccount, storyItem.privacy);
                editPrivacy(privacy, storyItem);
            } else {
                if (privacyHint == null) {
                    privacyHint = new HintView2(getContext(), HintView2.DIRECTION_TOP)
                            .setMultilineText(true)
                            .setTextAlign(Layout.Alignment.ALIGN_CENTER)
                            .setOnHiddenListener(() -> delegate.setIsHintVisible(false));
                    privacyHint.setPadding(dp(8), 0, dp(8), 0);
                    storyContainer.addView(privacyHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 52, 0, 0));
                }
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user == null) {
                    return;
                }
                String firstName = user.first_name;
                int index;
                if ((index = firstName.indexOf(' ')) > 0) {
                    firstName = firstName.substring(0, index);
                }
                CharSequence text;
                boolean twoLines = true;
                if (storyItem.close_friends) {
                    privacyHint.setInnerPadding(15, 8, 15, 8);
                    text = AndroidUtilities.replaceTags(LocaleController.formatString("StoryCloseFriendsHint", R.string.StoryCloseFriendsHint, firstName));
                } else if (storyItem.contacts) {
                    privacyHint.setInnerPadding(11, 6, 11, 7);
                    text = AndroidUtilities.replaceTags(LocaleController.formatString("StoryContactsHint", R.string.StoryContactsHint, firstName));
                    twoLines = false;
                } else if (storyItem.selected_contacts) {
                    privacyHint.setInnerPadding(15, 8, 15, 8);
                    text = AndroidUtilities.replaceTags(LocaleController.formatString("StorySelectedContactsHint", R.string.StorySelectedContactsHint, firstName));
                } else {
                    return;
                }
                text = Emoji.replaceEmoji(text, privacyHint.getTextPaint().getFontMetricsInt(), false);
                privacyHint.setMaxWidthPx(twoLines ? HintView2.cutInFancyHalf(text, privacyHint.getTextPaint()) : storyContainer.getMeasuredWidth());
                privacyHint.setText(text);
                privacyHint.setJoint(1, -(storyContainer.getWidth() - privacyButton.getCenterX()) / AndroidUtilities.density);
                delegate.setIsHintVisible(true);
                if (privacyHint.shown()) {
                    BotWebViewVibrationEffect.IMPACT_LIGHT.vibrate();
                }
                privacyHint.show();
            }
        });
        storyContainer.addView(privacyButton, LayoutHelper.createFrame(60, 40, Gravity.RIGHT | Gravity.TOP, 2, 15, 2 + 40, 0));

        muteIconContainer.setOnClickListener(v -> {
            if (currentStory.hasSound()) {
                storyViewer.toggleSilentMode();
                if (storyViewer.soundEnabled()) {
                    MessagesController.getGlobalMainSettings().edit().putInt("taptostorysoundhint", 3).apply();
                }
            } else {
                showNoSoundHint(true);
            }
        });

        storyLines = new StoryLinesDrawable(this, sharedResources);

        storyContainer.addView(storyCaptionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 64, 0, 0));

        muteIconContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        optionsIconView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        shareButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        likeButtonContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        if (repostButtonContainer != null) {
            repostButtonContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.WHITE, 100)));
        }

        View overlay = storyCaptionView.textSelectionHelper.getOverlayView(context);
        if (overlay != null) {
            AndroidUtilities.removeFromParent(overlay);
            addView(overlay);
        }
        storyCaptionView.textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                delegate.setIsInSelectionMode(storyCaptionView.textSelectionHelper.isInSelectionMode());
            }
        });
        storyCaptionView.textSelectionHelper.setParentView(this);
    }

    private void createStealthModeItem(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        if (isBotsPreview()) return;
        if (UserConfig.getInstance(currentAccount).isPremium()) {
            ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_stories_stealth2, LocaleController.getString(R.string.StealthModeButton), false, resourcesProvider).setOnClickListener(v -> {
                if (stealthModeIsActive) {
                    StealthModeAlert.showStealthModeEnabledBulletin();
                } else {
                    StealthModeAlert stealthModeAlert = new StealthModeAlert(getContext(), getY() + storyContainer.getY(), StealthModeAlert.TYPE_FROM_STORIES, resourcesProvider);
                    delegate.showDialog(stealthModeAlert);
                }
                if (popupMenu != null) {
                    popupMenu.dismiss();
                }
            });
        } else {
            Drawable lockIcon2 = ContextCompat.getDrawable(getContext(), R.drawable.msg_gallery_locked2);
            lockIcon2.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 0.5f), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable2 = new CombinedDrawable(
                    ContextCompat.getDrawable(getContext(), R.drawable.msg_stealth_locked),
                    lockIcon2
            ) {
                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }
            };
            ActionBarMenuSubItem item2 = ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_stories_stealth2, LocaleController.getString(R.string.StealthModeButton), false, resourcesProvider);
            item2.setOnClickListener(v -> {
                StealthModeAlert stealthModeAlert = new StealthModeAlert(getContext(), getY() + storyContainer.getY(), StealthModeAlert.TYPE_FROM_STORIES, resourcesProvider);
                delegate.showDialog(stealthModeAlert);
                if (popupMenu != null) {
                    popupMenu.dismiss();
                }
            });
            item2.setIcon(combinedDrawable2);
        }
    }

    private void createQualityItem(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        final boolean qualityFull = MessagesController.getInstance(currentAccount).storyQualityFull;
        if (UserConfig.getInstance(currentAccount).isPremium()) {
            ActionBarMenuItem.addItem(popupLayout, qualityFull ? R.drawable.menu_quality_sd : R.drawable.menu_quality_hd, LocaleController.getString(qualityFull ? R.string.StoryQualityDecrease : R.string.StoryQualityIncrease), false, resourcesProvider).setOnClickListener(v -> {
                final boolean newQualityFull = !qualityFull;
                MessagesController.getInstance(currentAccount).setStoryQuality(newQualityFull);
                BulletinFactory.of(storyContainer, resourcesProvider)
                    .createSimpleBulletin(
                        R.raw.chats_infotip,
                        LocaleController.getString(newQualityFull ? R.string.StoryQualityIncreasedTitle : R.string.StoryQualityDecreasedTitle),
                        LocaleController.getString(newQualityFull ? R.string.StoryQualityIncreasedMessage : R.string.StoryQualityDecreasedMessage)
                    )
                    .show();
                if (popupMenu != null) {
                    popupMenu.dismiss();
                }
            });
        } else {
            Drawable lockIcon2 = ContextCompat.getDrawable(getContext(), R.drawable.msg_gallery_locked2);
            lockIcon2.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 0.5f), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable2 = new CombinedDrawable(
                    ContextCompat.getDrawable(getContext(), R.drawable.menu_quality_hd2),
                    lockIcon2
            ) {
                @Override
                public void setColorFilter(ColorFilter colorFilter) {

                }
            };
            combinedDrawable2.setIconSize(dp(24), dp(24));
            combinedDrawable2.setIconOffset(dp(1), -dp(2));
            ActionBarMenuSubItem item2 = ActionBarMenuItem.addItem(popupLayout, R.drawable.menu_quality_hd, LocaleController.getString(R.string.StoryQualityIncrease), false, resourcesProvider);
            item2.setOnClickListener(v -> {
                BottomSheet sheet = new BottomSheet(getContext(), false, resourcesProvider);
                sheet.fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dp(16), 0, dp(16), 0);

                BackupImageView imageView = new BackupImageView(getContext());
                imageView.getImageReceiver().setAutoRepeat(1);
//                imageView.setScaleType(ImageView.ScaleType.CENTER);
//                imageView.setAutoRepeat(true);
//                imageView.setAnimation(R.raw.utyan_cache, 150, 150);
                MediaDataController.getInstance(currentAccount).setPlaceholderImage(imageView, AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME_2, "😎", "150_150");
                layout.addView(imageView, LayoutHelper.createLinear(150, 150, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));

                TextView headerView = new TextView(getContext());
                headerView.setTypeface(AndroidUtilities.bold());
                headerView.setGravity(Gravity.CENTER);
                headerView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                headerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
                headerView.setText(LocaleController.getString(R.string.StoryQualityPremium));
                layout.addView(headerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 12, 0, 12, 0));

                TextView descriptionView = new TextView(getContext());
                descriptionView.setGravity(Gravity.CENTER);
                descriptionView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3, resourcesProvider));
                descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                descriptionView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.StoryQualityPremiumText)));
                layout.addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 32, 9, 32, 19));

                ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);
                button.setText(LocaleController.getString(R.string.StoryQualityIncrease), false);
                SpannableStringBuilder lock = new SpannableStringBuilder("l");
                ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.mini_switch_lock);
                coloredImageSpan.setTopOffset(1);
                lock.setSpan(coloredImageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                button.setSubText(new SpannableStringBuilder().append(lock).append(LocaleController.getString(R.string.OptionPremiumRequiredTitle)), false);
                layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_HORIZONTAL));
                button.setOnClickListener(view -> {
                    delegate.showDialog(new PremiumFeatureBottomSheet(storyViewer.fragment, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false));
                    sheet.dismiss();
                });

                sheet.setCustomView(layout);
                delegate.showDialog(sheet);
                if (popupMenu != null) {
                    popupMenu.dismiss();
                }
            });
            item2.setIcon(combinedDrawable2);
        }
    }

    private void showLikesReaction(boolean show) {
        if (likesReactionShowing == show || currentStory.storyItem == null) {
            return;
        }
        likesReactionShowing = show;
        if (show) {
            likesReactionLayout.setVisibility(View.VISIBLE);
        }
        likesReactionLayout.setStoryItem(currentStory.storyItem);
        delegate.setIsLikesReaction(show);
        if (show) {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(likesReactionShowProgress, show ? 1f : 0f);
            likesReactionLayout.setTransitionProgress(likesReactionShowProgress);
            valueAnimator.addUpdateListener(animation -> {
                likesReactionShowProgress = (float) animation.getAnimatedValue();
                likesReactionLayout.setTransitionProgress(likesReactionShowProgress);
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!show) {
                        likesReactionLayout.setVisibility(View.GONE);
                        likesReactionLayout.reset();
                    }
                }
            });
            valueAnimator.setDuration(200);
            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            valueAnimator.start();
        } else {
            if (likesReactionLayout.getReactionsWindow() != null) {
                likesReactionLayout.getReactionsWindow().dismissWithAlpha();
            }
            likesReactionLayout.animate().alpha(0).setDuration(150).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    likesReactionShowProgress = 0;
                    likesReactionLayout.setAlpha(1f);
                    likesReactionLayout.setVisibility(View.GONE);
                    likesReactionLayout.reset();
                }
            }).start();
        }
    }



    private void likeStory(ReactionsLayoutInBubble.VisibleReaction visibleReaction) {
        if (currentStory.storyItem == null) {
            return;
        }
        boolean hasReactionOld = currentStory.storyItem != null && currentStory.storyItem.sent_reaction != null;
        TLRPC.Reaction oldReaction = currentStory.storyItem.sent_reaction;
        if (currentStory.storyItem.sent_reaction == null || visibleReaction != null) {
            if (visibleReaction == null) {
                TLRPC.TL_availableReaction reaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get("\u2764");
                if (reaction != null) {
                    drawAnimatedEmojiAsMovingReaction = false;
                    TLRPC.Document document = reaction.around_animation;
                    String filer = ReactionsEffectOverlay.getFilterForAroundAnimation();
                    reactionEffectImageReceiver.setImage(ImageLocation.getForDocument(document), filer, null, null, null, 0);
                    if (reactionEffectImageReceiver.getLottieAnimation() != null) {
                        reactionEffectImageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                    }
                    drawReactionEffect = true;
                    ReactionsLayoutInBubble.VisibleReaction likeReaction = ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(reaction);
                    storiesController.setStoryReaction(dialogId, currentStory.storyItem, likeReaction);
                }
            } else {
                animateLikeButton();
                storiesController.setStoryReaction(dialogId, currentStory.storyItem, visibleReaction);
            }
        } else {
            animateLikeButton();
            storiesController.setStoryReaction(dialogId, currentStory.storyItem, null);
        }
        boolean added = false;
        boolean counterChanged = false;
        if (currentStory.storyItem == null || currentStory.storyItem.sent_reaction == null) {
            if (hasReactionOld) {
                counterChanged = true;
            }
            storiesLikeButton.setReaction(null);
        } else {
            if (!hasReactionOld) {
                counterChanged = true;
            }
            storiesLikeButton.setReaction(ReactionsLayoutInBubble.VisibleReaction.fromTL(currentStory.storyItem.sent_reaction));
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            added = true;
        }
        if (isChannel && counterChanged) {
            if (currentStory.storyItem.views == null) {
                currentStory.storyItem.views = new TL_stories.TL_storyViews();
            }
            currentStory.storyItem.views.reactions_count += added ? 1 : -1;
            if (currentStory.storyItem.views.reactions_count < 0) {
                currentStory.storyItem.views.reactions_count = 0;
            }
        }
        ReactionsUtils.applyForStoryViews(oldReaction, currentStory.storyItem.sent_reaction, currentStory.storyItem.views);
        updateUserViews(true);
    }

    private void animateLikeButton() {
        View oldLikeButton = storiesLikeButton;
        oldLikeButton.animate().alpha(0).scaleX(0.8f).scaleY(0.8f).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                AndroidUtilities.removeFromParent(oldLikeButton);
            }
        }).setDuration(150).start();
        int padding = dp(8);
        storiesLikeButton = new StoriesLikeButton(getContext(), sharedResources);
        storiesLikeButton.setPadding(padding, padding, padding, padding);
        storiesLikeButton.setAlpha(0);
        storiesLikeButton.setScaleX(0.8f);
        storiesLikeButton.setScaleY(0.8f);
        storiesLikeButton.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150);
        likeButtonContainer.addView(storiesLikeButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT));
        drawReactionEffect = false;
    }

    private ArrayList<TLRPC.InputStickerSet> getAnimatedEmojiSets(StoryItemHolder storyHolder) {
        if (storyHolder != null) {
            HashSet<Long> ids = new HashSet<>();
            ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>();
            if (storyHolder.storyItem != null && storyHolder.storyItem.media_areas != null) {
                for (int i = 0; i < storyHolder.storyItem.media_areas.size(); ++i) {
                    TL_stories.MediaArea mediaArea = storyHolder.storyItem.media_areas.get(i);
                    if (mediaArea instanceof TL_stories.TL_mediaAreaSuggestedReaction && mediaArea.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                        long documentId = ((TLRPC.TL_reactionCustomEmoji) mediaArea.reaction).document_id;
                        TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                        if (document == null) {
                            continue;
                        }
                        TLRPC.InputStickerSet set = MessageObject.getInputStickerSet(document);
                        if (set == null) {
                            continue;
                        }
                        if (ids.contains(set.id)) {
                            continue;
                        }
                        ids.add(set.id);
                        inputStickerSets.add(set);
                    }
                }
            }
            if (storyHolder.storyItem != null && storyHolder.storyItem.entities != null && !storyHolder.storyItem.entities.isEmpty()) {
                for (int i = 0; i < storyHolder.storyItem.entities.size(); ++i) {
                    TLRPC.MessageEntity messageEntity = storyHolder.storyItem.entities.get(i);
                    if (!(messageEntity instanceof TLRPC.TL_messageEntityCustomEmoji)) {
                        continue;
                    }
                    TLRPC.Document document = ((TLRPC.TL_messageEntityCustomEmoji) messageEntity).document;
                    if (document == null) {
                        document = AnimatedEmojiDrawable.findDocument(currentAccount,  ((TLRPC.TL_messageEntityCustomEmoji) messageEntity).document_id);
                    }
                    if (document == null) {
                        continue;
                    }
                    TLRPC.InputStickerSet set = MessageObject.getInputStickerSet(document);
                    if (ids.contains(set.id)) {
                        continue;
                    }
                    ids.add(set.id);
                    inputStickerSets.add(set);
                }
            } else if (storyHolder.uploadingStory != null && storyHolder.uploadingStory.entry != null) {
                if (storyHolder.uploadingStory.entry.mediaEntities != null) {
                    for (int i = 0; i < storyHolder.uploadingStory.entry.mediaEntities.size(); ++i) {
                        VideoEditedInfo.MediaEntity entity = storyHolder.uploadingStory.entry.mediaEntities.get(i);
                        if (entity.type == VideoEditedInfo.MediaEntity.TYPE_REACTION && entity.mediaArea != null && entity.mediaArea.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                            long documentId = ((TLRPC.TL_reactionCustomEmoji) entity.mediaArea.reaction).document_id;
                            TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                            if (document == null) {
                                continue;
                            }
                            TLRPC.InputStickerSet set = MessageObject.getInputStickerSet(document);
                            if (set == null) {
                                continue;
                            }
                            if (ids.contains(set.id)) {
                                continue;
                            }
                            ids.add(set.id);
                            inputStickerSets.add(set);
                        }
                    }
                }
                CharSequence caption = storyHolder.uploadingStory.entry.caption;
                if (!(caption instanceof Spanned)) {
                    return inputStickerSets;
                }
                AnimatedEmojiSpan[] spans = ((Spanned) caption).getSpans(0, caption.length(), AnimatedEmojiSpan.class);
                if (spans == null) {
                    return inputStickerSets;
                }
                for (int i = 0; i < spans.length; ++i) {
                    TLRPC.Document document = spans[i].document;
                    if (document == null) {
                        document = AnimatedEmojiDrawable.findDocument(currentAccount, spans[i].documentId);
                    }
                    if (document == null) {
                        continue;
                    }
                    TLRPC.InputStickerSet set = MessageObject.getInputStickerSet(document);
                    if (ids.contains(set.id)) {
                        continue;
                    }
                    ids.add(set.id);
                    inputStickerSets.add(set);
                }
            }
            return inputStickerSets;
        }
        return null;
    }

    private void toggleArchiveForStory(long dialogId) {
        boolean hide;
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        TLObject object = null;
        String name = null;
        if (dialogId > 0) {
            user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            object = user;
            name = user.first_name;
            hide = !user.stories_hidden;
        } else {
            chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            object = chat;
            name = chat.title;
            hide = !chat.stories_hidden;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);

        TLObject finalObject = object;

        String finalName = name;
        AndroidUtilities.runOnUIThread(() -> {
            messagesController.getStoriesController().toggleHidden(dialogId, hide, false, true);
            BulletinFactory.UndoObject undoObject = new BulletinFactory.UndoObject();
            undoObject.onUndo = () -> {
                messagesController.getStoriesController().toggleHidden(dialogId, !hide, false, true);
            };
            undoObject.onAction = () -> {
                messagesController.getStoriesController().toggleHidden(dialogId, hide, true, true);
            };
            CharSequence str;
            if (!hide) {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("StoriesMovedToDialogs", R.string.StoriesMovedToDialogs, ContactsController.formatName(finalName, null, 10)));
            } else {
                str = AndroidUtilities.replaceTags(LocaleController.formatString("StoriesMovedToContacts", R.string.StoriesMovedToContacts, ContactsController.formatName(finalName, null, 10)));
            }
            BulletinFactory.of(storyContainer, resourcesProvider).createUsersBulletin(Arrays.asList(finalObject), str, null, undoObject).setTag(2).show();
        }, 200);
    }

    private boolean drawLinesAsCounter() {
        return false;//linesCount > 20;
    }

    private void createFailView() {
        if (failView != null) {
            return;
        }
        failView = new StoryFailView(getContext(), resourcesProvider);
        failView.setOnClickListener(v -> {
            if (currentStory != null && currentStory.uploadingStory != null) {
                currentStory.uploadingStory.tryAgain();
                updatePosition();
            }
        });
        failView.setAlpha(0f);
        failView.setVisibility(View.GONE);
        addView(failView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    private void createPremiumBlockedText() {
        if (premiumBlockedText != null) {
            return;
        }
        if (chatActivityEnterView == null) {
            createEnterView();
        }
        premiumBlockedText = new LinearLayout(getContext());
        premiumBlockedText.setOrientation(LinearLayout.HORIZONTAL);

        ImageView imageView = new ImageView(getContext());
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setScaleX(1.35f);
        imageView.setScaleY(1.35f);
        imageView.setImageResource(R.drawable.mini_switch_lock);
        imageView.setColorFilter(new PorterDuffColorFilter(0xFF858585, PorterDuff.Mode.SRC_IN));

        premiumBlockedText1 = new TextView(getContext());
        premiumBlockedText1.setTextColor(0xFF858585);
        premiumBlockedText1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        premiumBlockedText1.setText(LocaleController.getString(isGroup ? R.string.StoryGroupRepliesLocked : R.string.StoryRepliesLocked));

        premiumBlockedText2 = new TextView(getContext());
        premiumBlockedText2.setTextColor(0xFFFFFFFF);
        premiumBlockedText2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        premiumBlockedText2.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(40), 0x1affffff, 0x32ffffff));
        premiumBlockedText2.setGravity(Gravity.CENTER);
        ScaleStateListAnimator.apply(premiumBlockedText2);
        premiumBlockedText2.setText(LocaleController.getString(R.string.StoryRepliesLockedButton));
        premiumBlockedText2.setPadding(dp(7), 0, dp(7), 0);

        premiumBlockedText.addView(imageView, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 12, 1, 4, 0));
        premiumBlockedText.addView(premiumBlockedText1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        premiumBlockedText.addView(premiumBlockedText2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 19, Gravity.CENTER_VERTICAL, 5, 0, 0, 0));

        chatActivityEnterView.addView(premiumBlockedText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void updatePremiumBlockedText() {
        if (premiumBlockedText1 != null) {
            premiumBlockedText1.setText(LocaleController.getString(isGroup ? R.string.StoryGroupRepliesLocked : R.string.StoryRepliesLocked));
        }
        if (premiumBlockedText2 != null) {
            premiumBlockedText2.setText(LocaleController.getString(R.string.StoryRepliesLockedButton));
        }
    }

    private Activity findActivity() {
        Activity _activity;
        if (storyViewer != null && storyViewer.parentActivity != null) {
            _activity = storyViewer.parentActivity;
        } else {
            _activity = AndroidUtilities.findActivity(getContext());
        }
        if (_activity == null) {
            return LaunchActivity.instance;
        }
        return _activity;
    }

    private BaseFragment fragmentForLimit() {
        return new BaseFragment() {
            @Override
            public boolean isLightStatusBar() {
                return false;
            }

            @Override
            public Activity getParentActivity() {
                return findActivity();
            }

            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return new WrappedResourceProvider(resourcesProvider) {
                    @Override
                    public void appendColors() {
                        sparseIntArray.append(Theme.key_dialogBackground, 0xFF1F1F1F);
                        sparseIntArray.append(Theme.key_windowBackgroundGray, 0xFF333333);
                    }
                };
            }

            @Override
            public boolean presentFragment(BaseFragment fragment) {
                if (PeerStoriesView.this.storyViewer != null) {
                    PeerStoriesView.this.storyViewer.presentFragment(fragment);
                }
                return true;
            }

            @Override
            public Dialog showDialog(Dialog dialog) {
                if (PeerStoriesView.this.storyViewer != null) {
                    PeerStoriesView.this.storyViewer.showDialog(dialog);
                } else if (dialog != null) {
                    dialog.show();
                }
                return dialog;
            }
        };
    }

    private TL_stories.TL_premium_boostsStatus boostsStatus;
    private ChannelBoostsController.CanApplyBoost canApplyBoost;

    private void showPremiumBlockedToast() {
        if (isGroup) {
            if (boostsStatus != null && canApplyBoost != null) {
                LimitReachedBottomSheet.openBoostsForRemoveRestrictions(fragmentForLimit(), boostsStatus, canApplyBoost, dialogId, true);
                return;
            }
            if (storyViewer != null) {
                storyViewer.setOverlayVisible(true);
            }
            MessagesController.getInstance(currentAccount).getBoostsController().getBoostsStats(dialogId, boostsStatus -> {
                if (boostsStatus == null) {
                    if (storyViewer != null) {
                        storyViewer.setOverlayVisible(false);
                    }
                    return;
                }
                this.boostsStatus = boostsStatus;
                MessagesController.getInstance(currentAccount).getBoostsController().userCanBoostChannel(dialogId, boostsStatus, canApplyBoost -> {
                    this.canApplyBoost = canApplyBoost;
                    LimitReachedBottomSheet.openBoostsForRemoveRestrictions(fragmentForLimit(), boostsStatus, canApplyBoost, dialogId, true);
                    if (storyViewer != null) {
                        storyViewer.setOverlayVisible(false);
                    }
                });
            });
            return;
        }
        AndroidUtilities.shakeViewSpring(chatActivityEnterView, shiftDp = -shiftDp);
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        String username = "";
        if (dialogId >= 0) {
            username = UserObject.getUserName(MessagesController.getInstance(currentAccount).getUser(dialogId));
        }
        Bulletin bulletin;
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            bulletin = BulletinFactory.of(storyContainer, resourcesProvider)
                    .createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedRepliesNonPremium, username)));
        } else {
            bulletin = BulletinFactory.of(storyContainer, resourcesProvider)
                .createSimpleBulletin(R.raw.star_premium_2, AndroidUtilities.replaceTags(LocaleController.formatString(R.string.UserBlockedRepliesNonPremium, username)), LocaleController.getString(R.string.UserBlockedNonPremiumButton), () -> {
                    if (storyViewer != null) {
                        storyViewer.presentFragment(new PremiumPreviewFragment("noncontacts"));
                    }
                });
        }
        bulletin.show();
    }

    private void updateSpeedItem(boolean isFinal) {
        if (speedItem == null || speedLayout == null) return;
        if (speedItem.getVisibility() != View.VISIBLE) {
            return;
        }
        if (isFinal) {
            if (Math.abs(StoryViewer.currentSpeed - 0.2f) < 0.05f) {
                speedItem.setSubtext(LocaleController.getString(R.string.VideoSpeedVerySlow));
            } else if (Math.abs(StoryViewer.currentSpeed - 0.5f) < 0.05f) {
                speedItem.setSubtext(LocaleController.getString(R.string.VideoSpeedSlow));
            } else if (Math.abs(StoryViewer.currentSpeed - 1.0f) < 0.05f) {
                speedItem.setSubtext(LocaleController.getString(R.string.VideoSpeedNormal));
            } else if (Math.abs(StoryViewer.currentSpeed - 1.5f) < 0.05f) {
                speedItem.setSubtext(LocaleController.getString(R.string.VideoSpeedFast));
            } else if (Math.abs(StoryViewer.currentSpeed - 2f) < 0.05f) {
                speedItem.setSubtext(LocaleController.getString(R.string.VideoSpeedVeryFast));
            } else {
                speedItem.setSubtext(LocaleController.formatString(R.string.VideoSpeedCustom, SpeedIconDrawable.formatNumber(StoryViewer.currentSpeed) + "x"));
            }
        }
        speedLayout.update(StoryViewer.currentSpeed, isFinal);
    }

    private void createEnterView() {
        Theme.ResourcesProvider emojiResourceProvider = new WrappedResourceProvider(resourcesProvider) {
            @Override
            public void appendColors() {
                sparseIntArray.put(Theme.key_chat_emojiPanelBackground, ColorUtils.setAlphaComponent(Color.WHITE, 30));
            }
        };
        chatActivityEnterView = new ChatActivityEnterView(AndroidUtilities.findActivity(getContext()), this, null, true, emojiResourceProvider) {

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (!isEnabled()) {
                    AndroidUtilities.rectTmp.set(0, 0, getWidth() + (premiumBlockedText2 != null ? 1.5f * attachLayoutPaddingTranslationX : 0), getHeight());
                    boolean hit = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                    if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                        if (hit && premiumBlockedText2 != null) {
                            premiumBlockedText2.setPressed(true);
                        }
                    } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                        if (premiumBlockedText2 != null) {
                            if (hit && premiumBlockedText2.isPressed()) {
                                showPremiumBlockedToast();
                            }
                            premiumBlockedText2.setPressed(false);
                        }
                    } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {
                        if (premiumBlockedText2 != null) {
                            premiumBlockedText2.setPressed(false);
                        }
                    }
                    return premiumBlockedText2 != null && premiumBlockedText2.isPressed();
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public void setHorizontalPadding(float padding, float progress, boolean allowShare) {
                float leftPadding = -padding * (1f - progress);
                if (premiumBlockedText != null) {
                    premiumBlockedText.setTranslationX(-leftPadding);
                }
                super.setHorizontalPadding(padding, progress, allowShare);
            }

            private Animator messageEditTextAnimator;
            private int chatActivityEnterViewAnimateFromTop;
            int lastContentViewHeight;
            int messageEditTextPredrawHeigth;
            int messageEditTextPredrawScrollY;

            @Override
            protected boolean showConfirmAlert(Runnable onConfirmed) {
                return applyMessageToChat(onConfirmed);
            }

            public void checkAnimation() {
                int t = getBackgroundTop();
                if (chatActivityEnterViewAnimateFromTop != 0 && t != chatActivityEnterViewAnimateFromTop) {
                    int dy = animatedTop + chatActivityEnterViewAnimateFromTop - t;
                    animatedTop = dy;
                    forceUpdateOffsets = true;
                    if (changeBoundAnimator != null) {
                        changeBoundAnimator.removeAllListeners();
                        changeBoundAnimator.cancel();
                    }

                    if (topView != null && topView.getVisibility() == View.VISIBLE) {
                        topView.setTranslationY(animatedTop + (1f - topViewEnterProgress) * topView.getLayoutParams().height);
                        if (topLineView != null) {
                            topLineView.setTranslationY(animatedTop);
                        }
                    }

                    PeerStoriesView.this.invalidate();
                    changeBoundAnimator = ValueAnimator.ofFloat(dy, 0);
                    changeBoundAnimator.addUpdateListener(a -> {
                        float top = (float) a.getAnimatedValue();
                        animatedTop = (int) top;
                        forceUpdateOffsets = true;
                        PeerStoriesView.this.invalidate();
                        invalidate();
                    });
                    changeBoundAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            PeerStoriesView.this.invalidate();
                            animatedTop = 0;
                            forceUpdateOffsets = true;
                            if (topView != null && topView.getVisibility() == View.VISIBLE) {
                                topView.setTranslationY(animatedTop + (1f - topViewEnterProgress) * topView.getLayoutParams().height);
                                if (topLineView != null) {
                                    topLineView.setTranslationY(animatedTop);
                                }
                            }
                            changeBoundAnimator = null;
                        }
                    });
                    changeBoundAnimator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
                    changeBoundAnimator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                    changeBoundAnimator.start();
                    chatActivityEnterViewAnimateFromTop = 0;
                }
                if (shouldAnimateEditTextWithBounds) {
                    float dy = (messageEditTextPredrawHeigth - messageEditText.getMeasuredHeight()) + (messageEditTextPredrawScrollY - messageEditText.getScrollY());
                    messageEditText.setOffsetY(messageEditText.getOffsetY() - dy);
                    ValueAnimator a = ValueAnimator.ofFloat(messageEditText.getOffsetY(), 0);
                    a.addUpdateListener(animation -> messageEditText.setOffsetY((float) animation.getAnimatedValue()));
                    if (messageEditTextAnimator != null) {
                        messageEditTextAnimator.cancel();
                    }
                    messageEditTextAnimator = a;
                    a.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
                    a.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
                    a.start();
                    shouldAnimateEditTextWithBounds = false;
                }
                lastContentViewHeight = getMeasuredHeight();
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                if (chatActivityEnterView != null) {
                    shouldAnimateEditTextWithBounds = true;
                    messageEditTextPredrawHeigth = messageEditText.getMeasuredHeight();
                    messageEditTextPredrawScrollY = messageEditText.getScrollY();
                    invalidate();
                    PeerStoriesView.this.invalidate();
                    chatActivityEnterViewAnimateFromTop = chatActivityEnterView.getBackgroundTop();
                }
            }

            @Override
            protected void updateRecordInterface(int recordState, boolean animated) {
                super.updateRecordInterface(recordState, animated);
                checkRecording();
            }

            @Override
            protected void isRecordingStateChanged() {
                super.isRecordingStateChanged();
                checkRecording();
            }

            private void checkRecording() {
                final boolean wasRecording = isRecording;
                isRecording = chatActivityEnterView.isRecordingAudioVideo() || chatActivityEnterView.seekbarVisible() || (recordedAudioPanel != null && recordedAudioPanel.getVisibility() == View.VISIBLE);
                if (wasRecording != isRecording) {
                    if (isActive) {
                        delegate.setIsRecording(isRecording);
                    }
                    invalidate();
                    storyContainer.invalidate();
                }
            }

            @Override
            public void extendActionMode(Menu menu) {
                ChatActivity.fillActionModeMenu(menu, null, false);
            }
        };
        chatActivityEnterView.getEditField().useAnimatedTextDrawable();
        chatActivityEnterView.setOverrideKeyboardAnimation(true);
        chatActivityEnterView.setClipChildren(false);
        chatActivityEnterView.setDelegate(new ChatActivityEnterView.ChatActivityEnterViewDelegate() {
            @Override
            public void onMessageSend(CharSequence message, boolean notify, int scheduleDate) {
                if (isRecording) {
                    AndroidUtilities.runOnUIThread(() -> {
                        afterMessageSend();
                    }, 200);
                } else {
                    afterMessageSend();
                }
            }

            @Override
            public void needSendTyping() {

            }

            @Override
            public void onTextChanged(CharSequence text, boolean bigChange, boolean fromDraft) {
                if (mentionContainer == null) {
                    createMentionsContainer();
                }
                if (mentionContainer.getAdapter() != null) {
                    mentionContainer.setDialogId(dialogId);
                    mentionContainer.getAdapter().setUserOrChat(MessagesController.getInstance(currentAccount).getUser(dialogId), MessagesController.getInstance(currentAccount).getChat(-dialogId));
                    mentionContainer.getAdapter().searchUsernameOrHashtag(text, chatActivityEnterView.getCursorPosition(), null, false, false);
                }
                invalidate();
            }

            @Override
            public void onTextSelectionChanged(int start, int end) {

            }

            @Override
            public void onTextSpansChanged(CharSequence text) {

            }

            @Override
            public void onAttachButtonHidden() {

            }

            @Override
            public void onAttachButtonShow() {

            }

            @Override
            public void onWindowSizeChanged(int size) {

            }

            @Override
            public void onStickersTab(boolean opened) {

            }

            @Override
            public void onMessageEditEnd(boolean loading) {

            }

            @Override
            public void didPressAttachButton() {
                openAttachMenu();
            }

            @Override
            public void needStartRecordVideo(int state, boolean notify, int scheduleDate, int ttl, long effectId) {
                checkInstantCameraView();
                if (instantCameraView != null) {
                    if (state == 0) {
                        instantCameraView.showCamera(false);
                    } else if (state == 1 || state == 3 || state == 4) {
                        instantCameraView.send(state, notify, scheduleDate, ttl, effectId);
                    } else if (state == 2 || state == 5) {
                        instantCameraView.cancel(state == 2);
                    }
                }
            }

            @Override
            public void toggleVideoRecordingPause() {
                if (instantCameraView != null) {
                    instantCameraView.togglePause();
                }
            }

            @Override
            public void needChangeVideoPreviewState(int state, float seekProgress) {
                if (instantCameraView != null) {
                    instantCameraView.changeVideoPreviewState(state, seekProgress);
                }
            }

            @Override
            public void onSwitchRecordMode(boolean video) {

            }

            @Override
            public void onPreAudioVideoRecord() {

            }

            @Override
            public void needStartRecordAudio(int state) {

            }

            @Override
            public void needShowMediaBanHint() {
                if (isGroup) {
                    showPremiumBlockedToast();
                    return;
                }
                if (mediaBanTooltip == null) {
                    mediaBanTooltip = new HintView(getContext(), 9, resourcesProvider);
                    mediaBanTooltip.setVisibility(View.GONE);
                    addView(mediaBanTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 10, 0, 10, 0));
                }
                String title;
                if (dialogId >= 0) {
                    title = UserObject.getFirstName(MessagesController.getInstance(currentAccount).getUser(dialogId));
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    title = chat != null ? chat.title : "";
                }
                mediaBanTooltip.setText(AndroidUtilities.replaceTags(LocaleController.formatString(chatActivityEnterView.isInVideoMode() ? R.string.VideoMessagesRestrictedByPrivacy : R.string.VoiceMessagesRestrictedByPrivacy, title)));
                mediaBanTooltip.showForView(chatActivityEnterView.getAudioVideoButtonContainer(), true);
            }

            @Override
            public void onStickersExpandedChange() {
                requestLayout();
            }

            @Override
            public void onUpdateSlowModeButton(View button, boolean show, CharSequence time) {

            }

            @Override
            public void onSendLongClick() {

            }

            @Override
            public void onAudioVideoInterfaceUpdated() {

            }

            @Override
            public TL_stories.StoryItem getReplyToStory() {
                return currentStory.storyItem;
            }

            @Override
            public boolean onceVoiceAvailable() {
                TLRPC.User user = null;
                if (dialogId >= 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                } else {
                    return false;
                }
                return user != null && !UserObject.isUserSelf(user) && !user.bot;
            }
        });
        setDelegate(chatActivityEnterView);
        chatActivityEnterView.shouldDrawBackground = false;
        chatActivityEnterView.shouldDrawRecordedAudioPanelInParent = true;
        chatActivityEnterView.setAllowStickersAndGifs(true, true, true);
        chatActivityEnterView.updateColors();
        chatActivityEnterView.isStories = true;
        addView(chatActivityEnterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));

        chatActivityEnterView.recordingGuid = classGuid;
        playerSharedScope.viewsToInvalidate.add(storyContainer);
        playerSharedScope.viewsToInvalidate.add(PeerStoriesView.this);
        if (attachedToWindow) {
            chatActivityEnterView.onResume();
        }
        checkStealthMode(false);

        if (isBotsPreview()) {
            chatActivityEnterView.setVisibility(View.GONE);
        }

        reactionsContainerIndex = getChildCount();
    }

    private void createMentionsContainer() {
        mentionContainer = new MentionsContainerView(getContext(), dialogId, 0, storyViewer.fragment, PeerStoriesView.this, resourcesProvider) {
            @Override
            public void drawRoundRect(Canvas canvas, Rect rect, float radius) {
                bitmapShaderTools.setBounds(getX(), -getY(), getX() + getMeasuredWidth(), -getY() + getMeasuredHeight());
                AndroidUtilities.rectTmp.set(rect);
                AndroidUtilities.rectTmp.offset(0, 0);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, bitmapShaderTools.paint);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, inputBackgroundPaint);
                if (AndroidUtilities.rectTmp.top < getMeasuredHeight() - 1) {
                    canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight() - 1, resourcesProvider.getPaint(Theme.key_paint_divider));
                }
            }
            @Override
            protected boolean isStories() {
                return true;
            }
        };
        mentionContainer.withDelegate(new MentionsContainerView.Delegate() {
            @Override
            public void onStickerSelected(TLRPC.TL_document document, String query, Object parent) {
                SendMessagesHelper.getInstance(currentAccount).sendSticker(document, query, dialogId, null, null, currentStory.storyItem, null, null, true, 0, false, parent, null, 0);
                chatActivityEnterView.addStickerToRecent(document);
                chatActivityEnterView.setFieldText("");
                afterMessageSend();
            }

            @Override
            public void replaceText(int start, int len, CharSequence replacingString, boolean allowShort) {
                chatActivityEnterView.replaceWithText(start, len, replacingString, allowShort);
            }

            @Override
            public Paint.FontMetricsInt getFontMetrics() {
                return chatActivityEnterView.getEditField().getPaint().getFontMetricsInt();
            }

            @Override
            public void addEmojiToRecent(String code) {
                chatActivityEnterView.addEmojiToRecent(code);
            }

            @Override
            public void sendBotInlineResult(TLRPC.BotInlineResult result, boolean notify, int scheduleDate) {
                long uid = mentionContainer.getAdapter().getContextBotId();
                HashMap<String, String> params = new HashMap<>();
                params.put("id", result.id);
                params.put("query_id", "" + result.query_id);
                params.put("bot", "" + uid);
                params.put("bot_name", mentionContainer.getAdapter().getContextBotName());
                SendMessagesHelper.prepareSendingBotContextResult(storyViewer.fragment, getAccountInstance(), result, params, dialogId, null, null, currentStory.storyItem, null, notify, scheduleDate, null, 0);
                chatActivityEnterView.setFieldText("");
                afterMessageSend();
                MediaDataController.getInstance(currentAccount).increaseInlineRating(uid);
            }
        });
        addView(mentionContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.BOTTOM));
    }

    private boolean applyMessageToChat(Runnable runnable) {
        if (SharedConfig.stealthModeSendMessageConfirm > 0 && stealthModeIsActive) {
            SharedConfig.stealthModeSendMessageConfirm--;
            SharedConfig.updateStealthModeSendMessageConfirm(SharedConfig.stealthModeSendMessageConfirm);
            AlertDialog alertDialog = new AlertDialog(getContext(), 0, resourcesProvider);
            alertDialog.setTitle(LocaleController.getString(R.string.StealthModeConfirmTitle));
            alertDialog.setMessage(LocaleController.getString(R.string.StealthModeConfirmMessage));
            alertDialog.setPositiveButton(LocaleController.getString(R.string.Proceed), (dialog, which) -> {
                runnable.run();
            });
            alertDialog.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
            alertDialog.show();
        } else {
            runnable.run();
        }
        return true;
    }

    private void saveToGallery() {
        if (currentStory.storyItem == null && currentStory.uploadingStory == null) {
            return;
        }
        if (currentStory.storyItem instanceof TL_stories.TL_storyItemSkipped) {
            return;
        }
        File f = currentStory.getPath();
        boolean isVideo = currentStory.isVideo();
        if (f != null && f.exists()) {
            MediaController.saveFile(f.toString(), getContext(), isVideo ? 1 : 0, null, null, uri -> {
                BulletinFactory.createSaveToGalleryBulletin(storyContainer, isVideo, resourcesProvider).show();
            });
        } else {
            showDownloadAlert();
        }
    }

    private void showDownloadAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.getString(R.string.AppName));
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        //  boolean alreadyDownloading = currentMessageObject != null && currentMessageObject.isVideo() && FileLoader.getInstance(currentMessageObject.currentAccount).isLoadingFile(currentFileNames[0]);
//        if (alreadyDownloading) {
//            builder.setMessage(LocaleController.getString(R.string.PleaseStreamDownload));
//        } else {
        builder.setMessage(LocaleController.getString(R.string.PleaseDownload));
        // }
        delegate.showDialog(builder.create());
    }

    private void openAttachMenu() {
        if (chatActivityEnterView == null) {
            return;
        }
        createChatAttachView();
        chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            chatActivityEnterView.closeKeyboard();
        }
        chatAttachAlert.setMaxSelectedPhotos(-1, true);
        chatAttachAlert.setDialogId(dialogId);
        chatAttachAlert.init();
        chatAttachAlert.getCommentView().setText(chatActivityEnterView.getFieldText());
        delegate.showDialog(chatAttachAlert);
    }

    private void createChatAttachView() {
        if (chatAttachAlert == null) {
            chatAttachAlert = new ChatAttachAlert(getContext(), null, false, false, true, resourcesProvider) {
                @Override
                public void onDismissAnimationStart() {
                    if (chatAttachAlert != null) {
                        chatAttachAlert.setFocusable(false);
                    }
                    if (chatActivityEnterView != null && chatActivityEnterView.getEditField() != null) {
                        chatActivityEnterView.getEditField().requestFocus();
                    }
                }
            };
            chatAttachAlert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {

                @Override
                public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, long effectId, boolean invertMedia, boolean forceDocument) {
                    if (!storyViewer.isShowing) {
                        return;
                    }
                    TL_stories.StoryItem storyItem = currentStory.storyItem;
                    if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemSkipped) {
                        return;
                    }
                    if (button == 8 || button == 7 || button == 4 && !chatAttachAlert.getPhotoLayout().getSelectedPhotos().isEmpty()) {
                        if (button != 8) {
                            chatAttachAlert.dismiss(true);
                        }
                        HashMap<Object, Object> selectedPhotos = chatAttachAlert.getPhotoLayout().getSelectedPhotos();
                        ArrayList<Object> selectedPhotosOrder = chatAttachAlert.getPhotoLayout().getSelectedPhotosOrder();
                        if (!selectedPhotos.isEmpty()) {
                            for (int i = 0; i < Math.ceil(selectedPhotos.size() / 10f); ++i) {
                                int count = Math.min(10, selectedPhotos.size() - (i * 10));
                                ArrayList<SendMessagesHelper.SendingMediaInfo> photos = new ArrayList<>();
                                for (int a = 0; a < count; a++) {
                                    if (i * 10 + a >= selectedPhotosOrder.size()) {
                                        continue;
                                    }
                                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) selectedPhotos.get(selectedPhotosOrder.get(i * 10 + a));

                                    SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
                                    if (!photoEntry.isVideo && photoEntry.imagePath != null) {
                                        info.path = photoEntry.imagePath;
                                    } else if (photoEntry.path != null) {
                                        info.path = photoEntry.path;
                                    }
                                    info.thumbPath = photoEntry.thumbPath;
                                    info.isVideo = photoEntry.isVideo;
                                    info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                                    info.entities = photoEntry.entities;
                                    info.masks = photoEntry.stickers;
                                    info.ttl = photoEntry.ttl;
                                    info.videoEditedInfo = photoEntry.editedInfo;
                                    info.canDeleteAfter = photoEntry.canDeleteAfter;
                                    info.updateStickersOrder = SendMessagesHelper.checkUpdateStickersOrder(photoEntry.caption);
                                    info.hasMediaSpoilers = photoEntry.hasSpoiler;
                                    photos.add(info);
                                    photoEntry.reset();
                                }
                                boolean updateStickersOrder = false;
                                if (i == 0) {
                                    updateStickersOrder = photos.get(0).updateStickersOrder;
                                }
                                SendMessagesHelper.prepareSendingMedia(getAccountInstance(), photos, dialogId, null, null, storyItem, null, button == 4 || forceDocument, arg, null, notify, scheduleDate, 0, updateStickersOrder, null, null, 0, 0, false);
                            }
                            chatActivityEnterView.setFieldText("");
                            afterMessageSend();
                        }
//                        if (scheduleDate != 0) {
//                            if (scheduledMessagesCount == -1) {
//                                scheduledMessagesCount = 0;
//                            }
//                            scheduledMessagesCount += selectedPhotos.size();
//                            updateScheduledInterface(true);
//                        }
                        return;
                    } else if (chatAttachAlert != null) {
                        chatAttachAlert.dismissWithButtonClick(button);
                    }
                    // processSelectedAttach(button);
                }

                @Override
                public View getRevealView() {
                    return chatActivityEnterView.getAttachButton();
                }

                @Override
                public void onCameraOpened() {
                    chatActivityEnterView.closeKeyboard();
                }

                @Override
                public void doOnIdle(Runnable runnable) {
                    NotificationCenter.getInstance(currentAccount).doOnIdle(runnable);
                }

                @Override
                public void sendAudio(ArrayList<MessageObject> audios, CharSequence caption, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
                    TL_stories.StoryItem storyItem = currentStory.storyItem;
                    if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemSkipped) {
                        return;
                    }
                    SendMessagesHelper.prepareSendingAudioDocuments(getAccountInstance(), audios, caption != null ? caption : null, dialogId, null, null, storyItem, notify, scheduleDate, null, null, 0, effectId, invertMedia);
                    afterMessageSend();
                }

                @Override
                public boolean needEnterComment() {
                    return needEnterText();
                }
            });
            chatAttachAlert.getPhotoLayout().loadGalleryPhotos();
            chatAttachAlert.setAllowEnterCaption(true);
            chatAttachAlert.init();
            chatAttachAlert.setDocumentsDelegate(new ChatAttachAlertDocumentLayout.DocumentSelectActivityDelegate() {
                @Override
                public void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate, long effectId, boolean invertMedia) {
                    TL_stories.StoryItem storyItem = currentStory.storyItem;
                    if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemSkipped) {
                        return;
                    }
                    SendMessagesHelper.prepareSendingDocuments(getAccountInstance(), files, files, null, caption, null, dialogId, null, null, storyItem, null, null, notify, scheduleDate, null, null, 0, 0, false);
                    afterMessageSend();
                }

                @Override
                public void startDocumentSelectActivity() {
                    try {
                        Intent photoPickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
                        if (Build.VERSION.SDK_INT >= 18) {
                            photoPickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        }
                        photoPickerIntent.setType("*/*");
                        storyViewer.startActivityForResult(photoPickerIntent, 21);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            chatAttachAlert.getCommentView().setText(chatActivityEnterView.getFieldText());
        }
    }

    private void tryToOpenRepostStory() {
        if (!MessagesController.getInstance(currentAccount).storiesEnabled()) {
            return;
        }
        File f = currentStory.getPath();
        if (f != null && f.exists()) {
            if (shareAlert != null) {
                shareAlert.dismiss();
            }
            AndroidUtilities.runOnUIThread(PeerStoriesView.this::openRepostStory, 120);
        } else {
            showDownloadAlert();
        }
    }

    private void shareStory(boolean internal) {
        if (currentStory.storyItem != null && storyViewer.fragment != null) {
            TL_stories.StoryItem storyItem = currentStory.storyItem;
            String link = currentStory.createLink();
            if (internal) {
                Theme.ResourcesProvider shareResourceProvider = new WrappedResourceProvider(resourcesProvider) {
                    @Override
                    public void appendColors() {
                        sparseIntArray.put(Theme.key_chat_emojiPanelBackground, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));
                        sparseIntArray.put(Theme.key_chat_messagePanelIcons, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
                    }
                };
                TLRPC.Chat chat = isChannel ? MessagesController.getInstance(currentAccount).getChat(-dialogId) : null;
                final boolean canRepost = !DISABLE_STORY_REPOSTING && MessagesController.getInstance(currentAccount).storiesEnabled() && (!isChannel && !UserObject.isService(dialogId) || ChatObject.isPublic(chat));
                shareAlert = new ShareAlert(storyViewer.fragment.getContext(), null, null, link, null, false, link, null, false, false, canRepost, shareResourceProvider) {

                    @Override
                    public void dismissInternal() {
                        super.dismissInternal();
                        shareAlert = null;
                    }

                    @Override
                    protected void onShareStory(View cell) {
                        tryToOpenRepostStory();
                    }

                    @Override
                    protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic) {
                        super.onSend(dids, count, topic);
                        BulletinFactory bulletinFactory = BulletinFactory.of(storyContainer, resourcesProvider);
                        if (bulletinFactory != null) {
                            if (dids.size() == 1) {
                                long did = dids.keyAt(0);
                                if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                                    bulletinFactory.createSimpleBulletin(R.raw.saved_messages, AndroidUtilities.replaceTags(LocaleController.formatString("StorySharedToSavedMessages", R.string.StorySharedToSavedMessages)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                } else if (did < 0) {
                                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                                    bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString("StorySharedTo", R.string.StorySharedTo, topic != null ? topic.title : chat.title)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                } else {
                                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                                    bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatString("StorySharedTo", R.string.StorySharedTo, user.first_name)), Bulletin.DURATION_PROLONG).hideAfterBottomSheet(false).show();
                                }
                            } else {
                                bulletinFactory.createSimpleBulletin(R.raw.forward, AndroidUtilities.replaceTags(LocaleController.formatPluralString("StorySharedToManyChats", dids.size(), dids.size()))).hideAfterBottomSheet(false).show();
                            }
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    }
                };
                shareAlert.forceDarkThemeForHint = true;
                currentStory.storyItem.dialogId = dialogId;
                shareAlert.setStoryToShare(currentStory.storyItem);
                shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
                    @Override
                    public boolean didCopy() {
                        onLinkCopied();
                        return true;
                    }
                });
                delegate.showDialog(shareAlert);
            } else {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, link);
                LaunchActivity.instance.startActivityForResult(Intent.createChooser(intent, LocaleController.getString(R.string.StickersShare)), 500);
            }
        }
    }

    private void openRepostStory() {
        Activity activity = AndroidUtilities.findActivity(getContext());
        if (activity == null) {
            return;
        }
        Runnable openRepost = () -> {
            StoryRecorder editor = StoryRecorder.getInstance(activity, currentAccount);
            long time = 0;
            if (playerSharedScope != null && playerSharedScope.player != null) {
                time = playerSharedScope.player.currentPosition;
            }
            StoryEntry entry = StoryEntry.repostStoryItem(currentStory.getPath(), currentStory.storyItem);
            editor.openForward(StoryRecorder.SourceView.fromStoryViewer(storyViewer), entry, time, true);
            editor.setOnFullyOpenListener(() -> {
                editOpened = true;
                setActive(false);
            });
            editor.setOnPrepareCloseListener((t, close, sent, did) -> {
                if (sent) {
                    DialogStoriesCell.StoryCell cell = null;
                    DialogStoriesCell storiesCell = null;
                    if (storyViewer.fragment != null) {
                        INavigationLayout layout = storyViewer.fragment.getParentLayout();
                        if (layout != null) {
                            List<BaseFragment> fragmentList = layout.getFragmentStack();
                            ArrayList<BaseFragment> toClose = new ArrayList<>();
                            for (int i = fragmentList.size() - 1; i >= 0; --i) {
                                BaseFragment fragment = fragmentList.get(i);
                                if (fragment instanceof DialogsActivity) {
                                    DialogsActivity dialogsActivity = (DialogsActivity) fragment;
                                    dialogsActivity.closeSearching();
                                    storiesCell = dialogsActivity.dialogStoriesCell;
                                    if (storiesCell != null) {
                                        cell = storiesCell.findStoryCell(did);
                                    }
                                    for (int j = 0; j < toClose.size(); ++j) {
                                        layout.removeFragmentFromStack(toClose.get(j));
                                    }
                                    break;
                                }
                                toClose.add(fragment);
                            }
                        }
                    }
                    if (storyViewer.fragment != null) {
                        storyViewer.fragment.clearSheets();
                    }
                    storyViewer.instantClose();
                    editOpened = false;
                    final DialogStoriesCell.StoryCell finalCell = cell;
                    if (storiesCell != null && storiesCell.scrollTo(did)) {
                        final DialogStoriesCell finalStoriesCell = storiesCell;
                        storiesCell.afterNextLayout(() -> {
                            DialogStoriesCell.StoryCell cell2 = finalCell;
                            if (cell2 == null) {
                                cell2 = finalStoriesCell.findStoryCell(did);
                            }
                            editor.replaceSourceView(StoryRecorder.SourceView.fromStoryCell(cell2));
                            close.run();
                        });
                    } else {
                        editor.replaceSourceView(StoryRecorder.SourceView.fromStoryCell(finalCell));
                        AndroidUtilities.runOnUIThread(close, 400);
                    }
                    return;
                }
                final long start = System.currentTimeMillis();
                if (playerSharedScope != null && playerSharedScope.player == null) {
                    delegate.setPopupIsVisible(false);
                    setActive(true);
                    editOpened = false;
                    onImageReceiverThumbLoaded = () -> {
                        AndroidUtilities.cancelRunOnUIThread(close);
                        AndroidUtilities.runOnUIThread(close);
                    };
                    if (sent) {
                        updatePosition();
                    }
                    AndroidUtilities.runOnUIThread(close, 400);
                    return;
                }
                playerSharedScope.firstFrameRendered = playerSharedScope.player.firstFrameRendered = false;
                playerSharedScope.player.setOnReadyListener(() -> {
                    AndroidUtilities.cancelRunOnUIThread(close);
                    AndroidUtilities.runOnUIThread(close, Math.max(0, 32L - (System.currentTimeMillis() - start)));
                });
                delegate.setPopupIsVisible(false);
                if (muteIconView != null) {
                    muteIconView.setAnimation(sharedResources.muteDrawable);
                }
                if (videoDuration > 0 && t > videoDuration - 1400) {
                    t = 0L;
                }
                setActive(t, true);
                editOpened = false;
                AndroidUtilities.runOnUIThread(close, 400);
                if (sent) {
                    updatePosition();
                }
            });
        };
        if (!delegate.releasePlayer(openRepost)) {
            AndroidUtilities.runOnUIThread(openRepost, 80);
        }
    }

    private void onLinkCopied() {
        if (currentStory.storyItem == null) {
            return;
        }
        TL_stories.TL_stories_exportStoryLink exportStoryLink = new TL_stories.TL_stories_exportStoryLink();
        exportStoryLink.id = currentStory.storyItem.id;
        exportStoryLink.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(exportStoryLink, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void setDay(long dialogId, ArrayList<Integer> day, int selectedPosition) {
        this.dialogId = dialogId;
        this.day = day;
        bindInternal(selectedPosition);
    }

    public void setDialogId(long dialogId, int selectedPosition) {
        if (this.dialogId != dialogId) {
            currentStory.clear();
        }
        this.dialogId = dialogId;
        this.day = null;
        bindInternal(selectedPosition);
        if (storyViewer.overrideUserStories != null) {
            storiesController.loadSkippedStories(storyViewer.overrideUserStories, true);
        } else {
            storiesController.loadSkippedStories(dialogId);
        }
    }

    private void bindInternal(int startFromPosition) {
        deletedPeer = false;
        forceUpdateOffsets = true;
        userCanSeeViews = false;
        isChannel = false;
        isGroup = false;
        if (dialogId >= 0) {
            isSelf = dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
            isPremiumBlocked = !UserConfig.getInstance(currentAccount).isPremium() && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(dialogId);
            avatarDrawable.setInfo(currentAccount, user);
            headerView.backupImageView.getImageReceiver().setForUserOrChat(user, avatarDrawable);
            if (isSelf) {
                headerView.titleView.setText(LocaleController.getString(R.string.SelfStoryTitle));
                headerView.titleView.setRightDrawable(null);
            } else {
                if (user != null && user.verified) {
                    Drawable verifyDrawable = ContextCompat.getDrawable(getContext(), R.drawable.verified_profile).mutate();
                    verifyDrawable.setAlpha(255);
                    CombinedDrawable drawable = new CombinedDrawable(verifyDrawable, null);
                    drawable.setFullsize(true);
                    drawable.setCustomSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                    headerView.titleView.setRightDrawable(drawable);
                } else {
                    headerView.titleView.setRightDrawable(null);
                }
                if (user != null) {
                    CharSequence text = AndroidUtilities.removeDiacritics(ContactsController.formatName(user));
                    text = Emoji.replaceEmoji(text, headerView.titleView.getPaint().getFontMetricsInt(), false);
                    headerView.titleView.setText(text);
                } else {
                    headerView.titleView.setText(null);
                }
            }
        } else {
            isSelf = false;
            isChannel = true;

            if (storiesController.canEditStories(dialogId) || BuildVars.DEBUG_PRIVATE_VERSION) {
                userCanSeeViews = true;
            }
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            isGroup = !ChatObject.isChannelAndNotMegaGroup(chat);
            if (isGroup && MessagesController.getInstance(currentAccount).getChatFull(-dialogId) == null) {
                MessagesStorage.getInstance(currentAccount).loadChatInfo(-dialogId, true, new CountDownLatch(1), false, false);
            }
            isPremiumBlocked = isGroup && !ChatObject.canSendPlain(chat);
            avatarDrawable.setInfo(currentAccount, chat);
            headerView.backupImageView.getImageReceiver().setForUserOrChat(chat, avatarDrawable);
            headerView.titleView.setText(AndroidUtilities.removeDiacritics(chat.title));

            if (chat != null && chat.verified) {
                Drawable verifyDrawable = ContextCompat.getDrawable(getContext(), R.drawable.verified_profile).mutate();
                verifyDrawable.setAlpha(255);
                CombinedDrawable drawable = new CombinedDrawable(verifyDrawable, null);
                drawable.setFullsize(true);
                drawable.setCustomSize(AndroidUtilities.dp(16), AndroidUtilities.dp(16));
                headerView.titleView.setRightDrawable(drawable);
            } else {
                headerView.titleView.setRightDrawable(null);
            }
        }
        if (isActive && (isSelf || isChannel)) {
            storiesController.pollViewsForSelfStories(dialogId, true);
        }
        updateStoryItems();
        this.selectedPosition = startFromPosition;
        if (this.selectedPosition < 0) {
            this.selectedPosition = 0;
        }
        currentImageTime = 0;
        switchEventSent = false;
        boostsStatus = null;
        canApplyBoost = null;
        if (isChannel) {
            createSelfPeerView();
            if (chatActivityEnterView == null && isGroup) {
                createEnterView();
            }
            if (chatActivityEnterView != null) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                chatActivityEnterView.setVisibility(!isBotsPreview() && isGroup && (ChatObject.canSendPlain(chat) || ChatObject.isPossibleRemoveChatRestrictionsByBoosts(chat)) ? View.VISIBLE : View.GONE);
                chatActivityEnterView.getEditField().setText(storyViewer.getDraft(dialogId, currentStory.storyItem));
                chatActivityEnterView.setDialogId(dialogId, currentAccount);
                chatActivityEnterView.updateRecordButton(chat, null);
            }
            if (reactionsCounter == null) {
                reactionsCounter = new AnimatedTextView.AnimatedTextDrawable();
                reactionsCounter.setCallback(likeButtonContainer);
                reactionsCounter.setTextColor(resourcesProvider.getColor(Theme.key_windowBackgroundWhiteBlackText));
                reactionsCounter.setTextSize(AndroidUtilities.dp(14));
                reactionsCounterProgress = new AnimatedFloat(likeButtonContainer);
            }

            if (repostButtonContainer != null && repostCounter == null) {
                repostCounter = new AnimatedTextView.AnimatedTextDrawable();
                repostCounter.setCallback(repostButtonContainer);
                repostCounter.setTextColor(resourcesProvider.getColor(Theme.key_windowBackgroundWhiteBlackText));
                repostCounter.setTextSize(AndroidUtilities.dp(14));
                repostCounterProgress = new AnimatedFloat(repostButtonContainer);
            }

            if (startFromPosition == -1) {
                updateSelectedPosition();
            }
            updatePosition();
            count = getStoriesCount();
            storyContainer.invalidate();
            invalidate();
        } else if (isSelf) {
            createSelfPeerView();
            selfView.setVisibility(View.VISIBLE);
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setVisibility(View.GONE);
            }
            if (startFromPosition == -1) {
                if (day != null) {
                    int index = day.indexOf(storyViewer.dayStoryId);
                    if (index < 0) {
                        if (!day.isEmpty()) {
                            if (storyViewer.dayStoryId > day.get(0)) {
                                index = 0;
                            } else if (storyViewer.dayStoryId < day.get(day.size() - 1)) {
                                index = day.size() - 1;
                            }
                        }
                    }
                    selectedPosition = Math.max(0, index);
                } else if (!uploadingStories.isEmpty()) {
                    selectedPosition = storyItems.size();
                } else {
                    for (int i = 0; i < storyItems.size(); i++) {
                        if (storyItems.get(i).justUploaded || storyItems.get(i).id > storiesController.dialogIdToMaxReadId.get(dialogId)) {
                            selectedPosition = i;
                            break;
                        }
                    }
                }
            }
            updatePosition();
            storyContainer.invalidate();
            invalidate();
        } else {
            if (chatActivityEnterView == null) {
                createEnterView();
            }
            if (isPremiumBlocked && premiumBlockedText == null) {
                createPremiumBlockedText();
            }
            if (premiumBlockedText != null) {
                if (isPremiumBlocked) {
                    updatePremiumBlockedText();
                }
                premiumBlockedText.setVisibility(isPremiumBlocked ? View.VISIBLE : View.GONE);
            }
            if (failView != null) {
                failView.setVisibility(View.GONE);
            }
            if (startFromPosition == -1) {
                updateSelectedPosition();
            }
            updatePosition();
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setVisibility(isBotsPreview() || UserObject.isService(dialogId) ? View.GONE : View.VISIBLE);
                chatActivityEnterView.getEditField().setText(storyViewer.getDraft(dialogId, currentStory.storyItem));
                chatActivityEnterView.setDialogId(dialogId, currentAccount);
                TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                if (userFull != null) {
                    chatActivityEnterView.updateRecordButton(null, userFull);
                } else {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    MessagesController.getInstance(currentAccount).loadFullUser(user, classGuid, false);
                }
            }
            count = getStoriesCount();
            if (selfView != null) {
                selfView.setVisibility(View.GONE);
            }

            storyContainer.invalidate();
            invalidate();
        }
        checkStealthMode(false);
    }

    private void createUnsupportedContainer() {
        if (unsupportedContainer != null) {
            return;
        }
        FrameLayout frameLayout = new FrameLayout(getContext());

        LinearLayout linearLayout = new LinearLayout(getContext());
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView textView = new TextView(getContext());
        textView.setTypeface(AndroidUtilities.bold());
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setText(LocaleController.getString(R.string.StoryUnsupported));
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

        TextView buttonTextView = new TextView(getContext());
        ScaleStateListAnimator.apply(buttonTextView);
        buttonTextView.setText(LocaleController.getString(R.string.AppUpdate));
        buttonTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        buttonTextView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTypeface(AndroidUtilities.bold());
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        buttonTextView.setBackground(
                Theme.createSimpleSelectorRoundRectDrawable(dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider), 30))
        );
        buttonTextView.setOnClickListener(v -> {
            if (ApplicationLoader.isStandaloneBuild()) {
                if (LaunchActivity.instance != null) {
                    LaunchActivity.instance.checkAppUpdate(true, null);
                }
            } else if (BuildVars.isHuaweiStoreApp()){
                Browser.openUrl(getContext(), BuildVars.HUAWEI_STORE_URL);
            } else {
                Browser.openUrl(getContext(), BuildVars.PLAYSTORE_APP_URL);
            }
        });
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 0));

        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 72, 0, 72, 0));
        storyContainer.addView(frameLayout);
        unsupportedContainer = frameLayout;
    }


    public void preloadMainImage(long dialogId) {
        if (this.dialogId == dialogId && day == null) {
            return;
        }
        this.dialogId = dialogId;
        updateStoryItems();
        updateSelectedPosition();
        updatePosition(true);
        if (storyViewer.overrideUserStories != null) {
            storiesController.loadSkippedStories(storyViewer.overrideUserStories, true);
        } else {
            storiesController.loadSkippedStories(dialogId);
        }
    }

    private void updateSelectedPosition() {
        if (day != null) {
            int offset = 0;
            if (uploadingStories != null && !uploadingStories.isEmpty()) {
                offset = uploadingStories.size();
                for (int i = 0; i < uploadingStories.size(); ++i) {
                    if (Long.hashCode(uploadingStories.get(i).random_id) == storyViewer.dayStoryId) {
                        selectedPosition = i;
                        return;
                    }
                }
            }
            int index = day.indexOf(storyViewer.dayStoryId);
            if (index < 0) {
                if (!day.isEmpty()) {
                    if (storyViewer.dayStoryId > day.get(0)) {
                        index = 0;
                    } else if (storyViewer.dayStoryId < day.get(day.size() - 1)) {
                        index = day.size() - 1;
                    }
                }
            }
            selectedPosition = offset + index;
        } else {
            selectedPosition = storyViewer.savedPositions.get(dialogId, -1);
            if (selectedPosition == -1) {
                if (!storyViewer.isSingleStory && userStories != null && userStories.max_read_id > 0) {
                    for (int i = 0; i < storyItems.size(); i++) {
                        if (storyItems.get(i).id > userStories.max_read_id) {
                            selectedPosition = i;
                            break;
                        }
                    }
                }
            }
        }
        if (selectedPosition == -1) {
            selectedPosition = 0;
        }
    }

    private void updateStoryItems() {
        storyItems.clear();
        if (storyViewer.isSingleStory) {
            storyItems.add(storyViewer.singleStory);
        } else if (day != null && storyViewer.storiesList != null) {
            if (storyViewer.storiesList instanceof StoriesController.BotPreviewsList) {
                uploadingStories.clear();
                ArrayList<StoriesController.UploadingStory> list = MessagesController.getInstance(currentAccount).getStoriesController().getUploadingStories(dialogId);
                final String lang_code = ((StoriesController.BotPreviewsList) storyViewer.storiesList).lang_code;
                if (list != null) {
                    for (int i = 0; i < list.size(); ++i) {
                        StoriesController.UploadingStory story = list.get(i);
                        if (story.entry != null && !story.entry.isEdit && TextUtils.equals(story.entry.botLang, lang_code)) {
                            uploadingStories.add(story);
                        }
                    }
                }
            }
            for (int id : day) {
                MessageObject messageObject = storyViewer.storiesList.findMessageObject(id);
                if (messageObject != null && messageObject.storyItem != null) {
                    storyItems.add(messageObject.storyItem);
                }
            }
        } else if (storyViewer.storiesList != null) {
            // TODO: actually load more stories
            for (int i = 0; i < storyViewer.storiesList.messageObjects.size(); ++i) {
                storyItems.add(storyViewer.storiesList.messageObjects.get(i).storyItem);
            }
        } else {
            if (storyViewer.overrideUserStories != null && DialogObject.getPeerDialogId(storyViewer.overrideUserStories.peer) == dialogId) {
                userStories = storyViewer.overrideUserStories;
            } else {
                userStories = storiesController.getStories(dialogId);
                if (userStories == null) {
                    userStories = storiesController.getStoriesFromFullPeer(dialogId);
                }
            }
            totalStoriesCount = 0;
            if (userStories != null) {
                totalStoriesCount = userStories.stories.size();
                storyItems.addAll(userStories.stories);
            }
            uploadingStories.clear();
            ArrayList<StoriesController.UploadingStory> list = storiesController.getUploadingStories(dialogId);
            if (list != null) {
                uploadingStories.addAll(list);
            }
        }
        count = getStoriesCount();
    }

    private void createSelfPeerView() {
        if (selfView != null) {
            return;
        }
        selfView = new FrameLayout(getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (selfAvatarsContainer.getVisibility() == View.VISIBLE) {
                    int w = (int) (selfStatusView.getX() + selfStatusView.getMeasuredWidth() - selfAvatarsContainer.getX() + AndroidUtilities.dp(10));
                    if (selfAvatarsContainer.getLayoutParams().width != w) {
                        selfAvatarsContainer.getLayoutParams().width = w;
                        selfAvatarsContainer.invalidate();
                        selfAvatarsContainer.requestLayout();
                    }
                }
                super.dispatchDraw(canvas);
            }
        };
        selfView.setClickable(true);
        addView(selfView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 0, 0, 56 + 80, 0));

        selfAvatarsContainer = new View(getContext()) {

            LoadingDrawable loadingDrawable = new LoadingDrawable();
            AnimatedFloat animatedFloat = new AnimatedFloat(250, CubicBezierInterpolator.DEFAULT);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                animatedFloat.setParent(this);
                animatedFloat.set(showViewsProgress ? 1f : 0, false);
                if (animatedFloat.get() != 0) {

                    if (animatedFloat.get() != 1f) {
                        canvas.saveLayerAlpha(0, 0, getLayoutParams().width, getMeasuredHeight(), (int) (animatedFloat.get() * 255), Canvas.ALL_SAVE_FLAG);
                    } else {
                        canvas.save();
                    }
                    AndroidUtilities.rectTmp.set(0, 0, getLayoutParams().width, getMeasuredHeight());
                    loadingDrawable.setBounds(AndroidUtilities.rectTmp);
                    loadingDrawable.setRadiiDp(24);
                    loadingDrawable.setColors(ColorUtils.setAlphaComponent(Color.WHITE, 20), ColorUtils.setAlphaComponent(Color.WHITE, 50), ColorUtils.setAlphaComponent(Color.WHITE, 50), ColorUtils.setAlphaComponent(Color.WHITE, 70));
                    loadingDrawable.draw(canvas);
                    invalidate();
                    canvas.restore();
                }
            }
        };
        selfAvatarsContainer.setOnClickListener(v -> {
            showUserViewsDialog();
        });
        selfView.addView(selfAvatarsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, 0, 9, 11, 0, 0));

        selfAvatarsView = new HwAvatarsImageView(getContext(), false);
        selfAvatarsView.setAvatarsTextSize(AndroidUtilities.dp(18));
        selfView.addView(selfAvatarsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 13, 13, 0, 0));

        selfStatusView = new TextView(getContext());
        selfStatusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        selfStatusView.setTextColor(Color.WHITE);
        selfView.addView(selfStatusView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 0, 9));

        ImageView imageView = new ImageView(getContext());
        imageView.setImageDrawable(sharedResources.deleteDrawable);

//        int padding = AndroidUtilities.dp(12);
//        imageView.setPadding(padding, padding, padding, padding);
//        selfView.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
//        imageView.setOnClickListener(v -> {
//            deleteStory();
//        });


        selfAvatarsContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(15), 0, ColorUtils.setAlphaComponent(Color.WHITE, 120)));
        imageView.setBackground(Theme.createCircleSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, 120), -AndroidUtilities.dp(2), -AndroidUtilities.dp(2)));
    }

    private void deleteStory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        builder.setTitle(LocaleController.getString(isBotsPreview() ? R.string.DeleteBotPreviewTitle : R.string.DeleteStoryTitle));
        builder.setMessage(LocaleController.getString(isBotsPreview() ? R.string.DeleteBotPreviewSubtitle : R.string.DeleteStorySubtitle));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            currentStory.cancelOrDelete();
            updateStoryItems();
            if (isActive && count == 0) {
                delegate.switchToNextAndRemoveCurrentPeer();
                return;
            }
            if (selectedPosition >= count) {
                selectedPosition = count - 1;
            } else if (selectedPosition < 0) {
                selectedPosition = 0;
            }
            updatePosition();
            if (storyViewer != null) {
                storyViewer.checkSelfStoriesView();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (DialogInterface.OnClickListener) (dialog, which) -> {
            dialog.dismiss();
        });
        AlertDialog dialog = builder.create();
        delegate.showDialog(dialog);
        dialog.redPositive();
    }

    private void showUserViewsDialog() {
//        if (StoriesUtilities.hasExpiredViews(currentStory.storyItem)) {
//            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
//            BulletinFactory bulletinFactory = BulletinFactory.global();
//            if (bulletinFactory != null) {
//                bulletinFactory.createErrorBulletin(AndroidUtilities.replaceTags(LocaleController.getString(R.string.ExpiredViewsStub))).show();
//            }
//        } else {
            storyViewer.openViews();
        //}
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        sharedResources.topOverlayGradient.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(72));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        updateViewOffsets();
        if (isChannel && reactionsCounter != null) {
            reactionsCounter.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(40));
        }
        if (isChannel && repostCounter != null) {
            repostCounter.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(40));
        }
        super.dispatchDraw(canvas);
        if (movingReaction) {
            float cX = bottomActionsLinearLayout.getX() + likeButtonContainer.getX() + likeButtonContainer.getMeasuredWidth() / 2f;
            float cY = bottomActionsLinearLayout.getY() + likeButtonContainer.getY() + likeButtonContainer.getMeasuredHeight() / 2f;
            int size = AndroidUtilities.dp(24);
            float finalX = AndroidUtilities.lerp(movingReactionFromX, cX - size / 2f, CubicBezierInterpolator.EASE_OUT.getInterpolation(movingReactionProgress));
            float finalY = AndroidUtilities.lerp(movingReactionFromY, cY - size / 2f, movingReactionProgress);
            int finalSize = AndroidUtilities.lerp(movingReactionFromSize, size, movingReactionProgress);
            if (drawAnimatedEmojiAsMovingReaction) {
                if (reactionMoveDrawable != null) {
                    reactionMoveDrawable.setBounds((int) finalX, (int) finalY, (int) (finalX + finalSize), (int) (finalY + finalSize));
                    reactionMoveDrawable.draw(canvas);
                }
            } else {
                reactionMoveImageReceiver.setImageCoords(finalX, finalY, finalSize, finalSize);
                reactionMoveImageReceiver.draw(canvas);
            }
        }
        if (drawReactionEffect) {
            float cX = bottomActionsLinearLayout.getX() + likeButtonContainer.getX() + likeButtonContainer.getMeasuredWidth() / 2f;
            float cY = bottomActionsLinearLayout.getY() + likeButtonContainer.getY() + likeButtonContainer.getMeasuredHeight() / 2f;
            int size = AndroidUtilities.dp(120);
            if (!drawAnimatedEmojiAsMovingReaction) {
                reactionEffectImageReceiver.setImageCoords(cX - size / 2f, cY - size / 2f, size, size);
                reactionEffectImageReceiver.draw(canvas);
                if (reactionEffectImageReceiver.getLottieAnimation() != null && reactionEffectImageReceiver.getLottieAnimation().isLastFrame()) {
                    drawReactionEffect = false;
                }
            } else {
                if (emojiReactionEffect != null) {
                   //emojiReactionEffect.setBounds(0, 0, size, size);
                    emojiReactionEffect.setBounds((int) (cX - size / 2f), (int) (cY - size / 2f), (int) (cX + size / 2f), (int) (cY + size / 2f));
                    emojiReactionEffect.draw(canvas);
                    if (emojiReactionEffect.isDone()) {
                        emojiReactionEffect.removeView(this);
                        emojiReactionEffect = null;
                        drawReactionEffect = false;
                    }
                } else {
                    drawReactionEffect = false;
                }
            }
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.drawRecordedPannel(canvas);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        imageReceiver.onAttachedToWindow();
        rightPreloadImageReceiver.onAttachedToWindow();
        leftPreloadImageReceiver.onAttachedToWindow();
        reactionEffectImageReceiver.onAttachedToWindow();
        reactionMoveImageReceiver.onAttachedToWindow();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onResume();
        }
        for (int i = 0; i < preloadReactionHolders.size(); i++) {
            preloadReactionHolders.get(i).onAttachedToWindow(true);
        }
       // sharedResources.muteDrawable.addView(muteIconView);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storyQualityUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stealthModeChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesLimitUpdate);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        imageReceiver.onDetachedFromWindow();
        rightPreloadImageReceiver.onDetachedFromWindow();
        leftPreloadImageReceiver.onDetachedFromWindow();
        reactionEffectImageReceiver.onDetachedFromWindow();
        reactionMoveImageReceiver.onDetachedFromWindow();
        if (chatActivityEnterView != null) {
            chatActivityEnterView.onPause();
        }
        if (reactionMoveDrawable != null) {
            reactionMoveDrawable.removeView(PeerStoriesView.this);
            reactionMoveDrawable = null;
        }
        if (emojiReactionEffect != null) {
            emojiReactionEffect.removeView(PeerStoriesView.this);
            emojiReactionEffect = null;
        }
        for (int i = 0; i < preloadReactionHolders.size(); i++) {
            preloadReactionHolders.get(i).onAttachedToWindow(false);
        }
        //sharedResources.muteDrawable.removeView(muteIconView);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storyQualityUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stealthModeChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesLimitUpdate);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userIsPremiumBlockedUpadted);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesUpdated || id == NotificationCenter.storiesListUpdated && storyViewer.storiesList == args[0]) {
            if (delegate != null && delegate.isClosed()) {
                return;
            }
            if (isActive) {
                updateStoryItems();
                if (count == 0) {
                    if (!deletedPeer) {
                        deletedPeer = true;
                        delegate.switchToNextAndRemoveCurrentPeer();
                    }
                    return;
                }
                if (selectedPosition >= storyItems.size() + uploadingStories.size()) {
                    selectedPosition = storyItems.size() + uploadingStories.size() - 1;
                }
                updatePosition();
                if (isSelf || isChannel) {
                    updateUserViews(true);
                }
            }
            if (storyViewer.overrideUserStories != null) {
                storiesController.loadSkippedStories(storyViewer.overrideUserStories, true);
            } else if (dialogId != 0) {
                storiesController.loadSkippedStories(dialogId);
            }
            if (editStoryItem != null) {
                editStoryItem.animate().alpha(storiesController.hasUploadingStories(dialogId) && currentStory.isVideo && !SharedConfig.allowPreparingHevcPlayers() ? .5f : 1f).start();
            }
        } else if (id == NotificationCenter.storyQualityUpdate) {
            updatePosition();
        } else if (id == NotificationCenter.emojiLoaded) {
            storyCaptionView.captionTextview.invalidate();
        } else if (id == NotificationCenter.stealthModeChanged) {
            checkStealthMode(true);
        } else if (id == NotificationCenter.storiesLimitUpdate) {
            StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit == null || delegate == null) {
                return;
            }
            final LimitReachedBottomSheet sheet = new LimitReachedBottomSheet(fragmentForLimit(), findActivity(), storyLimit.getLimitReachedType(), currentAccount, null);
            delegate.showDialog(sheet);
        } else if (id == NotificationCenter.userIsPremiumBlockedUpadted) {
            boolean wasPremiumBlocked = isPremiumBlocked;
            isPremiumBlocked = dialogId >= 0 && !UserConfig.getInstance(currentAccount).isPremium() && MessagesController.getInstance(currentAccount).isUserPremiumBlocked(dialogId);
            if (isPremiumBlocked != wasPremiumBlocked) {
                updatePosition();
                checkStealthMode(true);
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            if (args[0] instanceof TLRPC.ChatFull) {
                TLRPC.ChatFull chatInfo = (TLRPC.ChatFull) args[0];
                if (dialogId == -chatInfo.id) {
                    updatePosition();
                }
            }
        }
    }

    Runnable updateStealthModeTimer = () -> checkStealthMode(true);

    private void checkStealthMode(boolean animated) {
        if (chatActivityEnterView == null || !isVisible || !attachedToWindow) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(updateStealthModeTimer);
        TL_stories.TL_storiesStealthMode stealthMode = storiesController.getStealthMode();
        if (isPremiumBlocked) {
            stealthModeIsActive = false;
            chatActivityEnterView.setEnabled(false);
            chatActivityEnterView.setOverrideHint(" ", animated);
        } else if (stealthMode != null && ConnectionsManager.getInstance(currentAccount).getCurrentTime() < stealthMode.active_until_date) {
            stealthModeIsActive = true;
            int time = stealthMode.active_until_date - ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            int minutes = time / 60;
            int seconds = time % 60;
            String textToMeasure = LocaleController.formatString("StealthModeActiveHint", R.string.StealthModeActiveHintShort, String.format(Locale.US, "%02d:%02d", 99, 99));
            int w = (int) chatActivityEnterView.getEditField().getPaint().measureText(textToMeasure);
            chatActivityEnterView.setEnabled(true);
            if (w * 1.2f >= chatActivityEnterView.getEditField().getMeasuredWidth()) {
                chatActivityEnterView.setOverrideHint(LocaleController.formatString("StealthModeActiveHintShort", R.string.StealthModeActiveHintShort, ""), String.format(Locale.US, "%02d:%02d", minutes, seconds), animated);
            } else {
                chatActivityEnterView.setOverrideHint(LocaleController.formatString("StealthModeActiveHint", R.string.StealthModeActiveHint, String.format(Locale.US, "%02d:%02d", minutes, seconds)), animated);
            }
            AndroidUtilities.runOnUIThread(updateStealthModeTimer, 1000);
        } else {
            stealthModeIsActive = false;
            chatActivityEnterView.setEnabled(true);
            chatActivityEnterView.setOverrideHint(LocaleController.getString(isGroup ? R.string.ReplyToGroupStory : R.string.ReplyPrivately), animated);
        }
    }

    public void updatePosition() {
        updatePosition(false);
    }

    private void updatePosition(boolean preload) {
        if (storyItems.isEmpty() && uploadingStories.isEmpty()) {
            return;
        }
        forceUpdateOffsets = true;
        TL_stories.StoryItem oldStoryItem = currentStory.storyItem;
        StoriesController.UploadingStory oldUploadingStory = currentStory.uploadingStory;

        String filter = StoriesUtilities.getStoryImageFilter();

        lastNoThumb = false;
        unsupported = false;
        int position = selectedPosition;

        final boolean wasUploading = isUploading;
        final boolean wasEditing = isEditing;
        final boolean wasFailed = isFailed;

        final StoriesController.UploadingStory uploadingStory;
        final TL_stories.StoryItem storyItem;

        if (storyViewer != null && storyViewer.storiesList != null && storyViewer.storiesList.type == StoriesController.StoriesList.TYPE_BOTS) {
            uploadingStory = position >= 0 && position < uploadingStories.size() ? uploadingStories.get(position) : null;
            int p = position - uploadingStories.size();
            storyItem = p >= 0 && p < storyItems.size() ? storyItems.get(p) : null;
        } else {
            storyItem = position >= 0 && position < storyItems.size() ? storyItems.get(position) : null;
            int p = position - storyItems.size();
            uploadingStory = p >= 0 && p < uploadingStories.size() ? uploadingStories.get(p) : null;
        }

        currentStory.editingSourceItem = null;
        if (uploadingStory != null) {
            isUploading = true;
            isEditing = false;
            isFailed = uploadingStory.failed;
            isUploading = !isFailed;
            Drawable thumbDrawable = null;
            imageReceiver.setCrossfadeWithOldImage(false);
            imageReceiver.setCrossfadeDuration(ImageReceiver.DEFAULT_CROSSFADE_DURATION);
            if (uploadingStory.entry.thumbBitmap != null) {
                Bitmap blurredBitmap = Bitmap.createBitmap(uploadingStory.entry.thumbBitmap);
                Utilities.blurBitmap(blurredBitmap, 3, 1, blurredBitmap.getWidth(), blurredBitmap.getHeight(), blurredBitmap.getRowBytes());
                thumbDrawable = new BitmapDrawable(blurredBitmap);
            }
            if (uploadingStory.isVideo || uploadingStory.hadFailed) {
                imageReceiver.setImage(null, null, ImageLocation.getForPath(uploadingStory.firstFramePath), filter, null, null, thumbDrawable, 0, null, null, 0);
            } else {
                imageReceiver.setImage(null, null, ImageLocation.getForPath(uploadingStory.path), filter, null, null, thumbDrawable, 0, null, null, 0);
            }
            currentStory.set(uploadingStory);
            storyAreasView.set(null, StoryMediaAreasView.getMediaAreasFor(uploadingStory.entry), emojiAnimationsOverlay);
            allowShare = allowRepost = allowShareLink = false;
        } else {
            isUploading = false;
            isEditing = false;
            isFailed = false;
            if (storyItem == null) {
                storyViewer.close(true);
                return;
            }
            StoriesController.UploadingStory editingStory = storiesController.findEditingStory(dialogId, storyItem);
            if (editingStory != null) {
                isEditing = true;
                imageReceiver.setCrossfadeWithOldImage(false);
                imageReceiver.setCrossfadeDuration(onImageReceiverThumbLoaded == null ? ImageReceiver.DEFAULT_CROSSFADE_DURATION : 0);
                if (editingStory.isVideo) {
                    imageReceiver.setImage(null, null, ImageLocation.getForPath(editingStory.firstFramePath), filter, /*messageObject.strippedThumb*/null, 0, null, null, 0);
                } else {
                    imageReceiver.setImage(null, null, ImageLocation.getForPath(editingStory.firstFramePath), filter, /*messageObject.strippedThumb*/null, 0, null, null, 0);
                }
                currentStory.set(editingStory);
                storyAreasView.set(null, StoryMediaAreasView.getMediaAreasFor(editingStory.entry), emojiAnimationsOverlay);
                currentStory.editingSourceItem = storyItem;
                allowShare = allowRepost = allowShareLink = false;
            } else {
                boolean isVideo = storyItem.media != null && MessageObject.isVideoDocument(storyItem.media.getDocument());
                Drawable thumbDrawable = null;
                storyItem.dialogId = dialogId;
                imageReceiver.setCrossfadeWithOldImage(wasEditing);
                imageReceiver.setCrossfadeDuration(ImageReceiver.DEFAULT_CROSSFADE_DURATION);
                if (storyItem.media instanceof TLRPC.TL_messageMediaUnsupported) {
                    unsupported = true;
                } else if (storyItem.attachPath != null) {
                    if (storyItem.media == null) {
                        isVideo = storyItem.attachPath.toLowerCase().endsWith(".mp4");
                    }
                    if (isVideo) {
                        if (storyItem.media != null) {
                            thumbDrawable = ImageLoader.createStripedBitmap(storyItem.media.getDocument().thumbs);
                        }
                        if (storyItem.firstFramePath != null && ImageLoader.getInstance().isInMemCache(ImageLocation.getForPath(storyItem.firstFramePath).getKey(null, null, false) + "@" + filter, false)) {
                            imageReceiver.setImage(null, null, ImageLocation.getForPath(storyItem.firstFramePath), filter, null, null, thumbDrawable, 0, null, null, 0);
                        } else {
                            imageReceiver.setImage(null, null, ImageLocation.getForPath(storyItem.attachPath), filter + "_pframe", null, null, thumbDrawable, 0, null, null, 0);
                        }
                    } else {
                        TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
                        if (photo != null) {
                            thumbDrawable = ImageLoader.createStripedBitmap(photo.sizes);
                        }
                        if (wasEditing) {
                            imageReceiver.setImage(ImageLocation.getForPath(storyItem.attachPath), filter, ImageLocation.getForPath(storyItem.firstFramePath), filter, thumbDrawable, 0, null, null, 0);
                        } else {
                            imageReceiver.setImage(ImageLocation.getForPath(storyItem.attachPath), filter, null, null, thumbDrawable, 0, null, null, 0);
                        }
                    }
                } else {
                    if ((storyViewer.storiesList != null || storyViewer.isSingleStory) && storyViewer.transitionViewHolder != null && storyViewer.transitionViewHolder.storyImage != null && storyViewer.transitionViewHolder.storyId == storyItem.id) {
                        thumbDrawable = storyViewer.transitionViewHolder.storyImage.getDrawable();
                    }
                    storyItem.dialogId = dialogId;
                    if (isVideo) {
                        TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.getDocument().thumbs, 1000);
                        if (thumbDrawable == null) {
                            thumbDrawable = ImageLoader.createStripedBitmap(storyItem.media.getDocument().thumbs);
                        }
                        imageReceiver.setImage(null, null, ImageLocation.getForDocument(storyItem.media.getDocument()), filter + "_pframe", ImageLocation.getForDocument(size, storyItem.media.getDocument()), filter, thumbDrawable, 0, null, storyItem, 0);
                    } else {
                        TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
                        if (photo != null && photo.sizes != null) {
                            if (thumbDrawable == null) {
                                thumbDrawable = ImageLoader.createStripedBitmap(photo.sizes);
                            }
                            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                            TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
//                            if (thumbSize != size) {
//                                imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), filter, ImageLocation.getForPhoto(thumbSize, photo), filter, thumbDrawable, 0, null, storyItem, 0);
//                            } else {
                                imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), filter, null, null, thumbDrawable, 0, null, storyItem, 0);
                          //  }
                        } else {
                            imageReceiver.clearImage();
                        }
                    }
                }
                storyItem.dialogId = dialogId;
                storyAreasView.set(preload ? null : storyItem, emojiAnimationsOverlay);
                currentStory.set(storyItem);
                allowShare = allowShareLink = !unsupported && currentStory.storyItem != null && !(currentStory.storyItem instanceof TL_stories.TL_storyItemDeleted) && !(currentStory.storyItem instanceof TL_stories.TL_storyItemSkipped);
                if (allowShare) {
                    allowShare = currentStory.allowScreenshots() && currentStory.storyItem.isPublic;
                }
                if (allowShare) {
                    allowShare = currentStory.storyItem.pinned || !StoriesUtilities.isExpired(currentAccount, currentStory.storyItem);
                }
                allowRepost = allowShare;
                if (allowRepost && isChannel) {
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    allowRepost = chat != null && ChatObject.isPublic(chat);
                }
                if (allowShareLink) {
                    if (isChannel) {
                        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        allowShareLink = chat != null && ChatObject.getPublicUsername(chat) != null;
                    } else {
                        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        allowShareLink = user != null && UserObject.getPublicUsername(user) != null && currentStory.storyItem.isPublic;
                    }
                }
                NotificationsController.getInstance(currentAccount).processReadStories(dialogId, storyItem.id);
            }
        }

        if (currentStory.storyItem != null && !preload) {
            storyViewer.dayStoryId = currentStory.storyItem.id;
        }

        storyViewer.storiesViewPager.checkAllowScreenshots();
        imageChanged = true;
        if (isSelf || isChannel) {
            updateUserViews(false);
        }

        final boolean sameId =
            getStoryId(currentStory.storyItem, currentStory.uploadingStory) == getStoryId(oldStoryItem, oldUploadingStory) ||
            oldUploadingStory != null && currentStory.storyItem != null && TextUtils.equals(oldUploadingStory.path, currentStory.storyItem.attachPath);
        boolean animateSubtitle = sameId && (isEditing != wasEditing || isUploading != wasUploading || isFailed != wasFailed);

        boolean storyChanged = false;
        if (!(
            oldUploadingStory != null && oldUploadingStory.path != null && oldUploadingStory.path.equals(currentStory.getLocalPath()) ||
            (oldStoryItem != null && currentStory.storyItem != null && oldStoryItem.id == currentStory.storyItem.id)
        )) {
            storyChanged = true;
            if (chatActivityEnterView != null) {
                if (oldStoryItem != null && !TextUtils.isEmpty(chatActivityEnterView.getEditField().getText())) {
                    storyViewer.saveDraft(oldStoryItem.dialogId, oldStoryItem, chatActivityEnterView.getEditField().getText());
                }
                chatActivityEnterView.getEditField().setText(storyViewer.getDraft(dialogId, currentStory.storyItem));
            }
            emojiAnimationsOverlay.clear();
            currentImageTime = 0;
            switchEventSent = false;

            if (currentStory.uploadingStory != null) {
                if (headerView.radialProgress != null) {
                    headerView.radialProgress.setProgress(currentStory.uploadingStory.progress, false);
                }
                headerView.backupImageView.invalidate();
            } else if (!animateSubtitle) {
                headerView.progressToUploading = 0;
            }
            Bulletin.hideVisible(storyContainer);
            storyCaptionView.reset();
            cancelWaiting();
        }

        if (storyChanged || oldUploadingStory != null && currentStory.uploadingStory == null) {
            headerView.setOnSubtitleClick(null);
            CharSequence subtitle = null;
            if (currentStory.uploadingStory != null) {
                if (currentStory.uploadingStory.failed) {
                    subtitle = LocaleController.getString(R.string.FailedToUploadStory);
                } else {
                    subtitle = StoriesUtilities.getUploadingStr(headerView.subtitleView[0], false, isEditing);
                }
            } else if (isBotsPreview()) {
                if (currentStory.storyItem == null || currentStory.storyItem.media == null) {
                    subtitle = "";
                } else if (currentStory.storyItem.media.document != null) {
                    subtitle = LocaleController.formatStoryDate(currentStory.storyItem.media.document.date);
                } else if (currentStory.storyItem.media.photo != null) {
                    subtitle = LocaleController.formatStoryDate(currentStory.storyItem.media.photo.date);
                } else {
                    subtitle = "";
                }
            } else if (currentStory.storyItem != null) {
                if (currentStory.storyItem.date == -1) {
                    subtitle = LocaleController.getString(R.string.CachedStory);
                } else if (currentStory.getReply() != null) {
                    StoryCaptionView.Reply reply = currentStory.getReply();

                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    SpannableString repostIcon = new SpannableString("r");
                    repostIcon.setSpan(new ColoredImageSpan(R.drawable.mini_repost_story), 0, repostIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append(repostIcon).append(" ");
                    if (reply.peerId != null) {
                        AvatarSpan avatar = new AvatarSpan(headerView.subtitleView[0], currentAccount, 15);
                        SpannableString avatarStr = new SpannableString("a");
                        avatarStr.setSpan(avatar, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ssb.append(avatarStr).append(" ");
                        if (reply.peerId > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(reply.peerId);
                            avatar.setUser(user);
                            ssb.append(UserObject.getUserName(user));
                        } else {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-reply.peerId);
                            avatar.setChat(chat);
                            if (chat != null) {
                                ssb.append(chat.title);
                            }
                        }
                    } else if (currentStory.storyItem.fwd_from.from_name != null) {
                        ssb.append(currentStory.storyItem.fwd_from.from_name);
                    }
                    headerView.setOnSubtitleClick(v -> {
                        if (reply.peerId != null) {
                            Bundle args = new Bundle();
                            if (reply.peerId >= 0) {
                                args.putLong("user_id", reply.peerId);
                            } else {
                                args.putLong("chat_id", -reply.peerId);
                            }
                            if (reply.isRepostMessage && reply.messageId != null) {
                                args.putInt("message_id", reply.messageId);
                                storyViewer.presentFragment(new ChatActivity(args));
                            } else {
                                storyViewer.presentFragment(new ProfileActivity(args));
                            }
                        } else {
                            BulletinFactory.of(storyContainer, resourcesProvider)
                                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.StoryHidAccount))
                                .setTag(3)
                                .show(true);
                        }
                    });

                    SpannableString dot = new SpannableString(".");
                    DotDividerSpan dotDividerSpan = new DotDividerSpan();
                    dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                    dotDividerSpan.setSize(5);
                    dot.setSpan(dotDividerSpan, 0, dot.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    ssb.append(" ").append(dot).append(" ").append(LocaleController.formatShortDate(currentStory.storyItem.date));
                    subtitle = ssb;
                    animateSubtitle = false;
                } else if (isGroup && currentStory.storyItem != null && currentStory.storyItem.from_id != null) {
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    AvatarSpan avatar = new AvatarSpan(headerView.subtitleView[0], currentAccount, 15);
                    SpannableString avatarStr = new SpannableString("a");
                    avatarStr.setSpan(avatar, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ssb.append(avatarStr).append(" ");
                    final long peerId = DialogObject.getPeerDialogId(currentStory.storyItem.from_id);
                    if (peerId > 0) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        avatar.setUser(user);
                        ssb.append(UserObject.getUserName(user));
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                        avatar.setChat(chat);
                        if (chat != null) {
                            ssb.append(chat.title);
                        }
                    }
                    headerView.setOnSubtitleClick(v -> {
                        Bundle args = new Bundle();
                        if (peerId >= 0) {
                            args.putLong("user_id", peerId);
                        } else {
                            args.putLong("chat_id", -peerId);
                        }
                        storyViewer.presentFragment(new ProfileActivity(args));
                    });

                    SpannableString dot = new SpannableString(".");
                    DotDividerSpan dotDividerSpan = new DotDividerSpan();
                    dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                    dotDividerSpan.setSize(5);
                    dot.setSpan(dotDividerSpan, 0, dot.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    ssb.append(" ").append(dot).append(" ").append(LocaleController.formatShortDate(currentStory.storyItem.date));
                    subtitle = ssb;
                    animateSubtitle = false;
                } else {
                    CharSequence string = LocaleController.formatStoryDate(currentStory.storyItem.date);
                    if (currentStory.storyItem.edited) {
                        SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(string);
                        DotDividerSpan dotDividerSpan = new DotDividerSpan();
                        dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f));
                        dotDividerSpan.setSize(5);
                        spannableStringBuilder.append(" . ").setSpan(dotDividerSpan, spannableStringBuilder.length() - 2, spannableStringBuilder.length() - 1, 0);
                        spannableStringBuilder.append(LocaleController.getString(R.string.EditedMessage));
                        string = spannableStringBuilder;
                    }
                    subtitle = string;
                }
            }
            if (subtitle != null) {
                if (storyViewer != null && storyViewer.storiesList != null && currentStory.storyItem != null && storyViewer.storiesList.isPinned(currentStory.storyItem.id)) {
                    if (!(subtitle instanceof SpannableStringBuilder)) {
                        subtitle = new SpannableStringBuilder(subtitle);
                    }

                    SpannableString pin = new SpannableString("p ");
                    pin.setSpan(new ColoredImageSpan(R.drawable.msg_pin_mini), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ((SpannableStringBuilder) subtitle).insert(0, pin);
                }
                headerView.setSubtitle(subtitle, animateSubtitle);
            }
            if (privacyHint != null) {
                privacyHint.hide(false);
            }
            if (soundTooltip != null) {
                soundTooltip.hide(false);
            }
        }
        if (
            oldStoryItem != currentStory.storyItem ||
            oldUploadingStory != currentStory.uploadingStory ||
            currentStory.captionTranslated != (currentStory.storyItem != null && currentStory.storyItem.translated && currentStory.storyItem.translatedText != null && TextUtils.equals(currentStory.storyItem.translatedLng, TranslateAlert2.getToLanguage()))
        ) {
            currentStory.updateCaption();
        }
        if (currentStory.captionTranslated || oldStoryItem != currentStory.storyItem) {
            if (delegate != null) {
                delegate.setTranslating(false);
            }
        }

        if (unsupported) {
            createUnsupportedContainer();
            createReplyDisabledView();
            unsupportedContainer.setVisibility(View.VISIBLE);
            replyDisabledTextView.setVisibility(View.VISIBLE);
            allowShare = allowRepost = allowShareLink = false;
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setVisibility(View.GONE);
            }
            if (selfView != null) {
                selfView.setVisibility(View.GONE);
            }
            if (bottomActionsLinearLayout != null) {
                bottomActionsLinearLayout.setVisibility(View.VISIBLE);
            }
        } else {
            TLRPC.Chat chat = dialogId < 0 ? MessagesController.getInstance(currentAccount).getChat(-dialogId) : null;
            if ((UserObject.isService(dialogId) || isBotsPreview()) && chatActivityEnterView != null) {
                chatActivityEnterView.setVisibility(View.GONE);
            } else if (!isSelf && (!isChannel || isGroup && (ChatObject.canSendPlain(chat) || ChatObject.isPossibleRemoveChatRestrictionsByBoosts(chat))) && chatActivityEnterView != null) {
                chatActivityEnterView.setVisibility(View.VISIBLE);
            }
            if (isPremiumBlocked && premiumBlockedText == null) {
                createPremiumBlockedText();
            }
            if (premiumBlockedText != null) {
                if (isPremiumBlocked) {
                    updatePremiumBlockedText();
                }
                premiumBlockedText.setVisibility(isPremiumBlocked ? View.VISIBLE : View.GONE);
            }
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setEnabled(!isPremiumBlocked);
            }
            if (isSelf && selfView != null) {
                selfView.setVisibility(View.VISIBLE);
            }
            if (unsupportedContainer != null) {
                unsupportedContainer.setVisibility(View.GONE);
            }
            if (UserObject.isService(dialogId)) {
                createReplyDisabledView();
                replyDisabledTextView.setVisibility(View.VISIBLE);
            } else if (replyDisabledTextView != null) {
                replyDisabledTextView.setVisibility(View.GONE);
            }
            if (bottomActionsLinearLayout != null) {
                bottomActionsLinearLayout.setVisibility(isBotsPreview() ? View.GONE : View.VISIBLE);
            }
        }

        if ((currentStory.caption != null || currentStory.getReply() != null) && !unsupported) {
            storyCaptionView.captionTextview.setText(currentStory.caption, currentStory.getReply(), storyViewer.isTranslating && !currentStory.captionTranslated && currentStory.storyItem != null && currentStory.storyItem.translated, oldStoryItem == currentStory.storyItem);
            storyCaptionView.setVisibility(View.VISIBLE);
        } else {
            if (isActive) {
                delegate.setIsCaption(false);
                delegate.setIsCaptionPartVisible(isCaptionPartVisible = false);
            }
            storyCaptionView.setVisibility(View.GONE);
        }
        storyContainer.invalidate();
        if (delegate != null && isSelectedPeer()) {
            delegate.onPeerSelected(dialogId, selectedPosition);
        }
        if (isChannel) {
            shareButton.setVisibility(allowShare ? View.VISIBLE : View.INVISIBLE);
            if (repostButtonContainer != null) {
                repostButtonContainer.setVisibility(allowRepost ? View.VISIBLE : View.GONE);
            }
            likeButtonContainer.setVisibility(isFailed ? View.GONE : View.VISIBLE);
        } else {
            shareButton.setVisibility(allowShare ? View.VISIBLE : View.INVISIBLE);
            if (repostButtonContainer != null) {
                repostButtonContainer.setVisibility(View.GONE);
            }
            likeButtonContainer.setVisibility(isSelf ? View.GONE : View.VISIBLE);
            likeButtonContainer.getLayoutParams().width = AndroidUtilities.dp(40);
        }
        likeButtonContainer.requestLayout();
        storyViewer.savedPositions.append(dialogId, position);


        if (isActive) {
            requestVideoPlayer(0);
            updatePreloadImages();
            imageReceiver.bumpPriority();
        }

        listPosition = 0;
        if (storyViewer.storiesList != null && currentStory.storyItem != null) {
            int id = currentStory.storyItem.id;
            for (int i = 0; i < storyViewer.storiesList.messageObjects.size(); ++i) {
                MessageObject obj = storyViewer.storiesList.messageObjects.get(i);
                if (obj != null && obj.getId() == id) {
                    listPosition = i;
                    break;
                }
            }
        }
        linesPosition = selectedPosition;
        linesCount = count;
        if (storyViewer.reversed) {
            linesPosition = linesCount - 1 - linesPosition;
        }

        if (currentStory.isVideo()) {
            muteIconContainer.setVisibility(View.VISIBLE);
            muteIconViewAlpha = currentStory.hasSound() ? 1f : 0.5f;
            if (currentStory.hasSound()) {
                muteIconView.setVisibility(View.VISIBLE);
                noSoundIconView.setVisibility(View.GONE);
            } else {
                muteIconView.setVisibility(View.GONE);
                noSoundIconView.setVisibility(View.VISIBLE);
            }
            muteIconContainer.setAlpha(muteIconViewAlpha * (1f - outT));
        } else {
            muteIconContainer.setVisibility(View.GONE);
        }

        if (currentStory.uploadingStory != null) {
            privacyButton.set(isSelf, currentStory.uploadingStory, sameId && editedPrivacy);
        } else if (currentStory.storyItem != null) {
            privacyButton.set(isSelf, currentStory.storyItem, sameId && editedPrivacy);
        } else {
            privacyButton.set(isSelf, (TL_stories.StoryItem) null, sameId && editedPrivacy);
        }
        editedPrivacy = false;
        privacyButton.setTranslationX(muteIconContainer.getVisibility() == View.VISIBLE ? -AndroidUtilities.dp(44) : 0);
        if (storyChanged) {
            drawReactionEffect = false;
            if (currentStory.storyItem == null || currentStory.storyItem.sent_reaction == null) {
                storiesLikeButton.setReaction(null);
            } else {
                storiesLikeButton.setReaction(ReactionsLayoutInBubble.VisibleReaction.fromTL(currentStory.storyItem.sent_reaction));
            }
        }

        if (currentStory.uploadingStory != null && currentStory.uploadingStory.failed) {
            createFailView();
            failView.set(currentStory.uploadingStory.entry.error);
            failView.setVisibility(View.VISIBLE);
            if (failViewAnimator != null) {
                failViewAnimator.cancel();
                failViewAnimator = null;
            }
            if (sameId) {
                failViewAnimator = failView.animate().alpha(1f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                failViewAnimator.start();
            } else {
                failView.setAlpha(1f);
            }
        } else if (failView != null) {
            if (failViewAnimator != null) {
                failViewAnimator.cancel();
                failViewAnimator = null;
            }
            if (sameId && failView.getVisibility() == View.VISIBLE) {
                failViewAnimator = failView.animate().alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> failView.setVisibility(View.GONE));
                failViewAnimator.start();
            } else {
                failView.setAlpha(0f);
                failView.setVisibility(View.GONE);
            }
        }

        sharedResources.setIconMuted(!storyViewer.soundEnabled(), false);
        if (isActive && currentStory.storyItem != null) {
            FileLog.d("StoryViewer displayed story dialogId=" + dialogId + " storyId=" + currentStory.storyItem.id + " " + currentStory.getMediaDebugString());
        }
        if (isSelf) {
            SelfStoryViewsPage.preload(currentAccount, dialogId, currentStory.storyItem);
        }
        headerView.titleView.setPadding(0, 0, storyViewer.storiesList != null && storyViewer.storiesList.getCount() != linesCount ? AndroidUtilities.dp(56) : 0, 0);

        MessagesController.getInstance(currentAccount).getTranslateController().detectStoryLanguage(currentStory.storyItem);

        if (!preload && !isSelf && reactionsTooltipRunnable == null && !SharedConfig.storyReactionsLongPressHint && SharedConfig.storiesIntroShown) {
            AndroidUtilities.runOnUIThread(reactionsTooltipRunnable = () -> {
                if (!storyViewer.isShown()) {
                    return;
                }
                reactionsTooltipRunnable = null;
                if (reactionsLongpressTooltip == null) {
                    reactionsLongpressTooltip = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM).setJoint(1, -22);
                    reactionsLongpressTooltip.setBgColor(ColorUtils.setAlphaComponent(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.13f), 240));
                    reactionsLongpressTooltip.setBounce(false);
                    reactionsLongpressTooltip.setText(LocaleController.getString(R.string.ReactionLongTapHint));
                    reactionsLongpressTooltip.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
                    storyContainer.addView(reactionsLongpressTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, BIG_SCREEN ? 0 : 56));
                }
                reactionsLongpressTooltip.show();
                SharedConfig.setStoriesReactionsLongPressHintUsed(true);
            }, 500);
        }

        if ((soundTooltip == null || !soundTooltip.shown()) && currentStory.hasSound() && !storyViewer.soundEnabled() && MessagesController.getGlobalMainSettings().getInt("taptostorysoundhint", 0) < 2) {
            AndroidUtilities.cancelRunOnUIThread(showTapToSoundHint);
            AndroidUtilities.runOnUIThread(showTapToSoundHint, 250);
        }

        if (optionsIconView != null) {
            optionsIconView.setVisibility(isBotsPreview() && !isEditBotsPreview() && (currentStory == null || !currentStory.isVideo) ? View.GONE : View.VISIBLE);
        }
    }

    private boolean isEditBotsPreview() {
        if (!isBotsPreview()) return false;
        TLRPC.User bot = MessagesController.getInstance(currentAccount).getUser(storyViewer.storiesList.dialogId);
        return bot != null && bot.bot && bot.bot_can_edit;
    }

    private boolean isBotsPreview() {
        return (
            storyViewer != null &&
            storyViewer.storiesList != null &&
            storyViewer.storiesList.type == StoriesController.StoriesList.TYPE_BOTS
        );
    }

    private final Runnable showTapToSoundHint = () -> {
        showNoSoundHint(false);
        MessagesController.getGlobalMainSettings().edit().putInt("taptostorysoundhint", MessagesController.getGlobalMainSettings().getInt("taptostorysoundhint", 0) + 1).apply();
    };

    private void createReplyDisabledView() {
        if (replyDisabledTextView != null) {
            return;
        }
        replyDisabledTextView = new TextView(getContext()) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
            }
        };
        replyDisabledTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        replyDisabledTextView.setTextColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        replyDisabledTextView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        replyDisabledTextView.setText(LocaleController.getString(R.string.StoryReplyDisabled));
        addView(replyDisabledTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.LEFT, 16, 0, 16, 0));
    }

    ArrayList<Uri> uriesToPrepare = new ArrayList<>();
    ArrayList<TLRPC.Document> documentsToPrepare = new ArrayList<>();

    private void updatePreloadImages() {
        int maxWidth = Math.max(AndroidUtilities.getRealScreenSize().x, AndroidUtilities.getRealScreenSize().y);
        int filterSize = (int) (maxWidth / AndroidUtilities.density);
        String filter = filterSize + "_" + filterSize;

        uriesToPrepare.clear();
        documentsToPrepare.clear();
        for (int i = 0; i < preloadReactionHolders.size(); i++) {
            preloadReactionHolders.get(i).onAttachedToWindow(false);
        }
        preloadReactionHolders.clear();

        for (int i = 0; i < 2; i++) {
            int position = selectedPosition;
            ImageReceiver imageReceiver;
            if (i == 0) {
                position--;
                imageReceiver = leftPreloadImageReceiver;
                if (position < 0) {
                    imageReceiver.clearImage();
                    continue;
                }
            } else {
                position++;
                imageReceiver = rightPreloadImageReceiver;
                if (position >= getStoriesCount()) {
                    imageReceiver.clearImage();
                    continue;
                }
            }
            if (!uploadingStories.isEmpty() && position >= storyItems.size()) {
                position -= storyItems.size();
                StoriesController.UploadingStory uploadingStory = uploadingStories.get(position);
                setStoryImage(uploadingStory, imageReceiver, filter);
            } else {
                if (storyItems.isEmpty()) {
                    continue;
                }
                if (position < 0) {
                    position = 0;
                }
                if (position >= storyItems.size()) {
                    position = storyItems.size() - 1;
                }
                TL_stories.StoryItem storyItem = storyItems.get(position);
                storyItem.dialogId = dialogId;
                setStoryImage(storyItem, imageReceiver, filter);

                boolean isVideo = storyItem.media != null && MessageObject.isVideoDocument(storyItem.media.getDocument());
                if (isVideo) {
                    TLRPC.Document document = storyItem.media.getDocument();
                    if (storyItem.fileReference == 0) {
                        storyItem.fileReference = FileLoader.getInstance(currentAccount).getFileReference(storyItem);
                    }
                    String params = null;
                    try {
                        params = "?account=" + currentAccount +
                                "&id=" + document.id +
                                "&hash=" + document.access_hash +
                                "&dc=" + document.dc_id +
                                "&size=" + document.size +
                                "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                                "&rid=" + storyItem.fileReference +
                                "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                                "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]) +
                                "&sid=" + storyItem.id + "&did=" + storyItem.dialogId;
                        uriesToPrepare.add(Uri.parse("tg://" + FileLoader.getAttachFileName(document) + params));
                        documentsToPrepare.add(document);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }

                if (storyItem.media_areas != null) {
                    for (int k = 0; k < storyItem.media_areas.size(); k++) {
                        if (storyItem.media_areas.get(k) instanceof TL_stories.TL_mediaAreaSuggestedReaction) {
                            TL_stories.TL_mediaAreaSuggestedReaction reaction = (TL_stories.TL_mediaAreaSuggestedReaction) storyItem.media_areas.get(k);
                            ReactionImageHolder reactionImageHolder = new ReactionImageHolder(this);
                            reactionImageHolder.setVisibleReaction(ReactionsLayoutInBubble.VisibleReaction.fromTL(reaction.reaction));
                            reactionImageHolder.onAttachedToWindow(attachedToWindow);
                            preloadReactionHolders.add(reactionImageHolder);
                        }
                    }
                }
            }
        }
//        if (selfAvatarsContainer != null) {
//            selfAvatarsContainer.setEnabled(!StoriesUtilities.hasExpiredViews(currentStory.storyItem));
//        }
        delegate.preparePlayer(documentsToPrepare, uriesToPrepare);
    }

    private void setStoryImage(TL_stories.StoryItem storyItem, ImageReceiver imageReceiver, String filter) {
        StoriesController.UploadingStory editingStory = storiesController.findEditingStory(dialogId, storyItem);
        if (editingStory != null) {
            setStoryImage(editingStory, imageReceiver, filter);
            return;
        }
        boolean isVideo = storyItem.media != null && MessageObject.isVideoDocument(storyItem.media.getDocument());

        if (storyItem.attachPath != null) {
            if (storyItem.media == null) {
                isVideo = storyItem.attachPath.toLowerCase().endsWith(".mp4");
            }
            if (isVideo) {
                imageReceiver.setImage(ImageLocation.getForPath(storyItem.attachPath), filter + "_pframe", ImageLocation.getForPath(storyItem.firstFramePath), filter, null, null, /*messageObject.strippedThumb*/null, 0, null, null, 0);
            } else {
                imageReceiver.setImage(ImageLocation.getForPath(storyItem.attachPath), filter, null, null, /*messageObject.strippedThumb*/null, 0, null, null, 0);
            }
        } else {
            if (isVideo) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.getDocument().thumbs, 1000);
                imageReceiver.setImage(ImageLocation.getForDocument(storyItem.media.getDocument()), filter + "_pframe", ImageLocation.getForDocument(size, storyItem.media.getDocument()), filter, null, null, null, 0, null, storyItem, 0);
            } else {
                TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
                if (photo != null && photo.sizes != null) {
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                    TLRPC.PhotoSize thumbSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 800);
//                    if (thumbSize != size) {
//                        imageReceiver.setImage(ImageLocation.getForPhoto(size, photo), filter, ImageLocation.getForPhoto(thumbSize, photo), filter, null, null, null, 0, null, storyItem, 0);
//                    } else {
                        imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), filter, null, null, null, 0, null, storyItem, 0);
                    //}
                } else {
                    imageReceiver.clearImage();
                }
            }
        }
    }

    private void setStoryImage(StoriesController.UploadingStory uploadingStory, ImageReceiver imageReceiver, String filter) {
        if (uploadingStory.isVideo) {
            imageReceiver.setImage(null, null, ImageLocation.getForPath(uploadingStory.firstFramePath), filter, null, null, null, 0, null, null, 0);
        } else {
            imageReceiver.setImage(ImageLocation.getForPath(uploadingStory.path), filter, null, null, null, 0, null, null, 0);
        }
    }

    private void cancelWaiting() {
        if (cancellableViews != null) {
            cancellableViews.run();
            cancellableViews = null;
        }
        showViewsProgress = false;
        if (isActive) {
            delegate.setIsWaiting(false);
        }
    }

    private void updateUserViews(boolean animated) {
        TL_stories.StoryItem storyItem = currentStory.storyItem;
        if (storyItem == null) {
            storyItem = currentStory.editingSourceItem;
        }
        if (!isChannel && !isSelf) {
            return;
        }
        if (storyItem != null) {
            if (isChannel) {
                if (storyItem.views == null) {
                    storyItem.views = new TL_stories.TL_storyViews();
                }
                if (storyItem.views.views_count <= 0) {
                    storyItem.views.views_count = 1;
                }
                if (repostCounter != null && storyItem.views.forwards_count > 0) {
                    repostCounter.setText(Integer.toString(storyItem.views.forwards_count), animated && repostCounterVisible);
                    repostCounterVisible = true;
                } else {
                    repostCounterVisible = false;
                }
                if (storyItem.views.reactions_count > 0) {
                    reactionsCounter.setText(Integer.toString(storyItem.views.reactions_count), animated && reactionsCounterVisible);
                    reactionsCounterVisible = true;
                } else {
                    reactionsCounterVisible = false;
                }
                if (!animated) {
                    reactionsCounterProgress.set(reactionsCounterVisible ? 1f : 0, true);
                    if (repostCounterProgress != null) {
                        repostCounterProgress.set(repostCounterVisible ? 1f : 0, true);
                    }
                }
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (!(isGroup && (ChatObject.canSendPlain(chat) || ChatObject.isPossibleRemoveChatRestrictionsByBoosts(chat))) && storyItem.views.views_count > 0) {
                    selfStatusView.setText(storyViewer.storiesList == null ? LocaleController.getString(R.string.NobodyViews) : LocaleController.getString(R.string.NobodyViewsArchived));
                    selfStatusView.setTranslationX(AndroidUtilities.dp(16));
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                    stringBuilder.append("d  ");
                    ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_views);
                    stringBuilder.setSpan(span, stringBuilder.length() - 3, stringBuilder.length() - 2, 0);
                    stringBuilder.append(AndroidUtilities.formatWholeNumber(storyItem.views.views_count, 0));
                    selfStatusView.setText(stringBuilder);
                } else {
                    selfStatusView.setText("");
                }
                likeButtonContainer.getLayoutParams().width = (int) (AndroidUtilities.dp(40) + (reactionsCounterVisible ? (reactionsCounter.getAnimateToWidth() + AndroidUtilities.dp(4)) : 0));
                ((MarginLayoutParams) selfView.getLayoutParams()).rightMargin = AndroidUtilities.dp(40) + likeButtonContainer.getLayoutParams().width;
                if (repostButtonContainer != null) {
                    repostButtonContainer.getLayoutParams().width = (int) (AndroidUtilities.dp(40) + (repostCounterVisible ? (repostCounter.getAnimateToWidth() + AndroidUtilities.dp(4)) : 0));
                    ((MarginLayoutParams) selfView.getLayoutParams()).rightMargin += repostButtonContainer.getLayoutParams().width;
                    repostButtonContainer.requestLayout();
                }
                selfView.requestLayout();
                likeButtonContainer.requestLayout();
                selfAvatarsView.setVisibility(View.GONE);
                selfAvatarsContainer.setVisibility(View.GONE);
                storyAreasView.onStoryItemUpdated(currentStory.storyItem, animated);
            } else {
                if (storyItem.views != null && storyItem.views.views_count > 0) {
                    int avatarsCount = 0;
                    int k = 0;
                    for (int i = 0; i < storyItem.views.recent_viewers.size(); i++) {
                        TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(storyItem.views.recent_viewers.get(i));
                        if (object != null) {
                            selfAvatarsView.setObject(avatarsCount, currentAccount, object);
                            avatarsCount++;
                        }
                        if (avatarsCount >= 3) {
                            break;
                        }
                    }
                    k = avatarsCount;
                    while (avatarsCount < 3) {
                        selfAvatarsView.setObject(avatarsCount, currentAccount, null);
                        avatarsCount++;
                    }
                    selfAvatarsView.commitTransition(false);
                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(LocaleController.formatPluralStringComma("Views", storyItem.views.views_count));
                    if (storyItem.views.reactions_count > 0) {
                        spannableStringBuilder.append("  d ");
                        ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_views_likes);
                        span.setOverrideColor(0xFFFF2E38);
                        span.setTopOffset(AndroidUtilities.dp(0.2f));
                        spannableStringBuilder.setSpan(span, spannableStringBuilder.length() - 2, spannableStringBuilder.length() - 1, 0);
                        spannableStringBuilder.append(String.valueOf(storyItem.views.reactions_count));
                    }
                    if (storyItem.views.forwards_count > 0) {
                        spannableStringBuilder.append("  d ");
                        ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_repost_story);
                        span.setOverrideColor(0xFF27E861);
                        span.setTopOffset(AndroidUtilities.dp(0.2f));
                        spannableStringBuilder.setSpan(span, spannableStringBuilder.length() - 2, spannableStringBuilder.length() - 1, 0);
                        spannableStringBuilder.append(String.valueOf(storyItem.views.forwards_count));
                    }
                    selfStatusView.setText(spannableStringBuilder);
                    if (k == 0) {
                        selfAvatarsView.setVisibility(View.GONE);
                        selfStatusView.setTranslationX(AndroidUtilities.dp(16));
                    } else {
                        selfAvatarsView.setVisibility(View.VISIBLE);
                        selfStatusView.setTranslationX(AndroidUtilities.dp(13) + AndroidUtilities.dp(24) + AndroidUtilities.dp(20) * (k - 1) + AndroidUtilities.dp(10));
                    }
                    selfAvatarsContainer.setVisibility(View.VISIBLE);
                } else {
                    selfStatusView.setText(storyViewer.storiesList == null ? LocaleController.getString(R.string.NobodyViews) : LocaleController.getString(R.string.NobodyViewsArchived));
                    selfStatusView.setTranslationX(AndroidUtilities.dp(16));
                    selfAvatarsView.setVisibility(View.GONE);
                    selfAvatarsContainer.setVisibility(View.GONE);
                }
                likeButtonContainer.getLayoutParams().width = AndroidUtilities.dp(40);
                bottomActionsLinearLayout.requestLayout();
            }
        } else {
            selfStatusView.setText("");
            selfAvatarsContainer.setVisibility(View.GONE);
            selfAvatarsView.setVisibility(View.GONE);
        }
    }

    private void requestVideoPlayer(long t) {
        if (isActive) {
            Uri uri = null;
            if (currentStory.isVideo()) {
                TLRPC.Document document = null;
                if (currentStory.getLocalPath() != null && new File(currentStory.getLocalPath()).exists()) {
                    uri = Uri.fromFile(new File(currentStory.getLocalPath()));
                    FileLog.d("StoryViewer requestVideoPlayer(" + t + "): playing from attachPath " + uri);
                    videoDuration = 0;
                } else if (currentStory.storyItem != null) {
                    currentStory.storyItem.dialogId = dialogId;
                    try {
                        document = currentStory.storyItem.media.getDocument();
                        if (currentStory.storyItem.fileReference == 0) {
                            currentStory.storyItem.fileReference = FileLoader.getInstance(currentAccount).getFileReference(currentStory.storyItem);
                        }
                        String params = "?account=" + currentAccount +
                                "&id=" + document.id +
                                "&hash=" + document.access_hash +
                                "&dc=" + document.dc_id +
                                "&size=" + document.size +
                                "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                                "&rid=" + currentStory.storyItem.fileReference +
                                "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                                "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]) +
                                "&sid=" + currentStory.storyItem.id + "&did=" + currentStory.storyItem.dialogId;
                        uri = Uri.parse("tg://" + FileLoader.getAttachFileName(document) + params);
                        FileLog.d("StoryViewer requestVideoPlayer(" + t + "): playing from " + uri);
                        videoDuration = (long) (MessageObject.getDocumentDuration(document) * 1000);
                    } catch (Exception exception) {
                        uri = null;
                    }
                }
                if (uri == null) {
                    FileLog.d("PeerStoriesView.requestVideoPlayer(" + t + "): playing from null?");
                }
                delegate.requestPlayer(document, uri, t, playerSharedScope);
                storyContainer.invalidate();
            } else {
                FileLog.d("PeerStoriesView.requestVideoPlayer(" + t + "): null, not a video");
                delegate.requestPlayer(null, null, 0, playerSharedScope);
                playerSharedScope.renderView = null;
                playerSharedScope.firstFrameRendered = false;
            }
        } else {
            playerSharedScope.renderView = null;
        }
//        imageReceiver.setImage(ImageLocation.getForPath(uploadingStory.path), ImageLoader.AUTOPLAY_FILTER, null, null, /*messageObject.strippedThumb*/null, 0, null, null, 0);
    }

    public boolean isSelectedPeer() {
        return false;
    }

    public boolean switchToNext(boolean forward) {
        if (storyViewer.reversed) {
            forward = !forward;
        }
        if (forward) {
            if (selectedPosition < getStoriesCount() - 1) {
                selectedPosition++;
                updatePosition();
                return true;
            }
        } else {
            if (selectedPosition > 0) {
                selectedPosition--;
                updatePosition();
                return true;
            }
        }
        return false;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void drawPlayingBitmap(int w, int h, Canvas canvas) {
        if (playerSharedScope.renderView != null && playerSharedScope.surfaceView != null) {
            Bitmap surfaceBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                AndroidUtilities.getBitmapFromSurface(playerSharedScope.surfaceView, surfaceBitmap);
            }
            if (surfaceBitmap != null) {
                canvas.drawBitmap(surfaceBitmap, 0, 0, null);
            }
        } else if (playerSharedScope.renderView != null && playerSharedScope.textureView != null) {
            Bitmap textureBitmap = playerSharedScope.textureView.getBitmap(w, h);
            if (textureBitmap != null) {
                canvas.drawBitmap(textureBitmap, 0, 0, null);
            }
        } else {
            canvas.save();
            canvas.scale(w / (float) storyContainer.getMeasuredWidth(), h / (float) storyContainer.getMeasuredHeight());
            imageReceiver.draw(canvas);
            canvas.restore();
        }
    }

    public Bitmap getPlayingBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(storyContainer.getWidth(), storyContainer.getHeight(), Bitmap.Config.ARGB_8888);
        drawPlayingBitmap(bitmap.getWidth(), bitmap.getHeight(), new Canvas(bitmap));
        return bitmap;
    }

    public void createBlurredBitmap(Canvas canvas, Bitmap bitmap) {
        drawPlayingBitmap(bitmap.getWidth(), bitmap.getHeight(), canvas);
        int color = AndroidUtilities.getDominantColor(bitmap);
        float brightness = AndroidUtilities.computePerceivedBrightness(color);
        if (brightness < 0.15f) {
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.4f)));
        }

        Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
        Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
    }

    public void stopPlaying(boolean stop) {
        if (stop) {
            imageReceiver.stopAnimation();
            imageReceiver.setAllowStartAnimation(false);
        } else {
            imageReceiver.startAnimation();
            imageReceiver.setAllowStartAnimation(true);
        }
    }

    public long getCurrentPeer() {
        return dialogId;
    }

    public ArrayList<Integer> getCurrentDay() {
        return day;
    }

    public void setPaused(boolean paused) {
        if (this.paused != paused) {
            this.paused = paused;
            stopPlaying(paused);
            lastDrawTime = 0;
            storyContainer.invalidate();
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public boolean closeKeyboardOrEmoji() {
        if (likesReactionShowing) {
            if (likesReactionLayout.getReactionsWindow() != null) {
                if (realKeyboardHeight > 0) {
                    AndroidUtilities.hideKeyboard(likesReactionLayout.getReactionsWindow().windowView);
                } else {
                    likesReactionLayout.getReactionsWindow().dismiss();
                }
                return true;
            }
            showLikesReaction(false);
            return true;
        }
        if (storyAreasView != null) {
            storyAreasView.closeHint();
        }
        if (storyCaptionView.textSelectionHelper.isInSelectionMode()) {
            storyCaptionView.textSelectionHelper.clear(false);
            return true;
        }
        if (privacyHint != null) {
            privacyHint.hide();
        }
        if (soundTooltip != null) {
            soundTooltip.hide();
        }

        if (mediaBanTooltip != null) {
            mediaBanTooltip.hide(true);
        }
        if (storyEditCaptionView != null && storyEditCaptionView.onBackPressed()) {
            return true;
        } else if (popupMenu != null && popupMenu.isShowing()) {
            popupMenu.dismiss();
            return true;
        } else if (checkRecordLocked(false)) {
            return true;
        } else if (reactionsContainerLayout != null && reactionsContainerLayout.getReactionsWindow() != null && reactionsContainerLayout.getReactionsWindow().isShowing()) {
            reactionsContainerLayout.getReactionsWindow().dismiss();
            return true;
        } else if (chatActivityEnterView != null && chatActivityEnterView.isPopupShowing()) {
            if (realKeyboardHeight > 0) {
                AndroidUtilities.hideKeyboard(chatActivityEnterView.getEmojiView());
            } else {
                chatActivityEnterView.hidePopup(true, false);
            }
            return true;
        } else if (getKeyboardHeight() >= AndroidUtilities.dp(20)) {
            if (chatActivityEnterView != null) {
                storyViewer.saveDraft(dialogId, currentStory.storyItem, chatActivityEnterView.getEditText());
            }
            AndroidUtilities.hideKeyboard(chatActivityEnterView);
            return true;
        } else if (storyCaptionView.getVisibility() == View.VISIBLE && storyCaptionView.getProgressToBlackout() > 0) {
            storyCaptionView.collapse();
            inBlackoutMode = false;
            storyContainer.invalidate();
            return true;
        }
        return false;
    }

    public boolean findClickableView(ViewGroup container, float x, float y, boolean swipeToDissmiss) {
        if (container == null) {
            return false;
        }
        if (privacyHint != null && privacyHint.shown()) {
            return true;
        }
        if (soundTooltip != null && soundTooltip.shown()) {
            return true;
        }

        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (child == storyCaptionView) {
                child.getHitRect(AndroidUtilities.rectTmp2);
                if (AndroidUtilities.rectTmp2.contains((int) x, (int) y) && storyCaptionView.allowInterceptTouchEvent(x, y - child.getTop())) {
                    return true;
                }
            }
            child.getHitRect(AndroidUtilities.rectTmp2);
            if (child == storyAreasView && !storyAreasView.hasSelected() && (x < dp(60) || x > container.getMeasuredWidth() - dp(60))) {
                if (storyAreasView.hasClickableViews(x, y)) {
                    return true;
                }
            } else if (keyboardVisible && child == chatActivityEnterView && y > AndroidUtilities.rectTmp2.top) {
                return true;
            } else if (!swipeToDissmiss && AndroidUtilities.rectTmp2.contains((int) x, (int) y) && (((child.isClickable() || child == reactionsContainerLayout) && child.isEnabled()) || (chatActivityEnterView != null && child == chatActivityEnterView.getRecordCircle()))) {
                return true;
            } else if (child.isEnabled() && child instanceof ViewGroup && findClickableView((ViewGroup) child, x - child.getX(), y - child.getY(), swipeToDissmiss)) {
                return true;
            }
        }
        return false;
    }

    public void setAccount(int currentAccount) {
        this.currentAccount = currentAccount;
        storiesController = MessagesController.getInstance(currentAccount).storiesController;
        emojiAnimationsOverlay.setAccount(currentAccount);
        if (reactionsContainerLayout != null) {
            reactionsContainerLayout.setCurrentAccount(currentAccount);
            reactionsContainerLayout.setMessage(null, null, true);
        }
        if (likesReactionLayout != null) {
            likesReactionLayout.setCurrentAccount(currentAccount);
        }
    }

    public boolean editOpened;

    public void setActive(boolean active) {
        setActive(0, active);
    }

    private static int activeCount;
    public void setActive(long t, boolean active) {
        if (isActive != active) {
            activeCount += active ? 1 : -1;
            isActive = active;

            if (isActive) {
                if (useSurfaceInViewPagerWorkAround()) {
                    delegate.setIsSwiping(true);
                    AndroidUtilities.cancelRunOnUIThread(allowDrawSurfaceRunnable);
                    AndroidUtilities.runOnUIThread(allowDrawSurfaceRunnable, 100);
                }
                requestVideoPlayer(t);
                updatePreloadImages();
                muteIconView.setAnimation(sharedResources.muteDrawable);
                isActive = true;
                headerView.backupImageView.getImageReceiver().setVisible(true, true);
                if (currentStory.storyItem != null) {
                    FileLog.d("StoryViewer displayed story dialogId=" + dialogId + " storyId=" + currentStory.storyItem.id + " " + currentStory.getMediaDebugString());
                }
            } else {
                cancelTextSelection();
                muteIconView.clearAnimationDrawable();
                viewsThumbImageReceiver = null;
                isLongPressed = false;
                progressToHideInterface.set(0, true);
                storyContainer.invalidate();
                invalidate();
                cancelWaiting();
                delegate.setIsRecording(false);

//                rightPreloadImageReceiver.clearImage();
//                leftPreloadImageReceiver.clearImage();
            }
            imageReceiver.setFileLoadingPriority(isActive ? FileLoader.PRIORITY_HIGH : FileLoader.PRIORITY_NORMAL_UP);
            leftPreloadImageReceiver.setFileLoadingPriority(isActive ? FileLoader.PRIORITY_NORMAL_UP : FileLoader.PRIORITY_LOW);
            rightPreloadImageReceiver.setFileLoadingPriority(isActive ? FileLoader.PRIORITY_NORMAL_UP : FileLoader.PRIORITY_LOW);
            if (isSelf || isChannel) {
                storiesController.pollViewsForSelfStories(dialogId, isActive);
            }
        }
    }

    public void progressToDismissUpdated() {
        if (BIG_SCREEN) {
            invalidate();
        }
    }

    public void reset() {
        headerView.backupImageView.getImageReceiver().setVisible(true, true);
        if (changeBoundAnimator != null) {
            chatActivityEnterView.reset();
            chatActivityEnterView.setAlpha(1f - outT);
        }
        if (reactionsContainerLayout != null) {
            reactionsContainerLayout.reset();
        }
        if (likesReactionLayout != null) {
            likesReactionLayout.reset();
        }
        if (instantCameraView != null) {
            AndroidUtilities.removeFromParent(instantCameraView);
            instantCameraView.hideCamera(true);
            instantCameraView = null;
        }
        setActive(false);
        setIsVisible(false);
        isLongPressed = false;
        progressToHideInterface.set(0, false);
        viewsThumbImageReceiver = null;
        messageSent = false;
        cancelTextSelection();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0 || requestCode == 2) {
                createChatAttachView();
                if (chatAttachAlert != null) {
                    chatAttachAlert.getPhotoLayout().onActivityResultFragment(requestCode, data, null);
                }
            } else if (requestCode == 21) {
                if (data == null) {
                    showAttachmentError();
                    return;
                }
                if (data.getData() != null) {
                    sendUriAsDocument(data.getData());
                } else if (data.getClipData() != null) {
                    ClipData clipData = data.getClipData();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        sendUriAsDocument(clipData.getItemAt(i).getUri());
                    }
                } else {
                    showAttachmentError();
                }
                if (chatAttachAlert != null) {
                    chatAttachAlert.dismiss();
                }
                afterMessageSend();
            }
        }
    }

    private void sendUriAsDocument(Uri uri) {
        if (uri == null) {
            return;
        }
        TL_stories.StoryItem storyItem = currentStory.storyItem;
        if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemSkipped) {
            return;
        }
        String extractUriFrom = uri.toString();
        if (extractUriFrom.contains("com.google.android.apps.photos.contentprovider")) {
            try {
                String firstExtraction = extractUriFrom.split("/1/")[1];
                int index = firstExtraction.indexOf("/ACTUAL");
                if (index != -1) {
                    firstExtraction = firstExtraction.substring(0, index);
                    String secondExtraction = URLDecoder.decode(firstExtraction, "UTF-8");
                    uri = Uri.parse(secondExtraction);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        String tempPath = AndroidUtilities.getPath(uri);
        String originalPath = tempPath;
        boolean sendAsUri = false;
        if (!BuildVars.NO_SCOPED_STORAGE) {
            sendAsUri = true;
        } else if (tempPath == null) {
            originalPath = uri.toString();
            tempPath = MediaController.copyFileToCache(uri, "file");

            if (tempPath == null) {
                showAttachmentError();
                return;
            }
        }
        if (sendAsUri) {
            SendMessagesHelper.prepareSendingDocument(getAccountInstance(), null, null, uri, null, null, dialogId, null, null, storyItem, null, null, true, 0, null, null, 0, false);
        } else {
            SendMessagesHelper.prepareSendingDocument(getAccountInstance(), tempPath, originalPath, null, null, null, dialogId, null, null, storyItem, null, null, true, 0, null, null, 0, false);
        }
    }

    private void showAttachmentError() {
        BulletinFactory.of(storyContainer, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.UnsupportedAttachment), resourcesProvider).show();
    }

    public void setLongpressed(boolean isLongpressed) {
        if (isActive) {
            this.isLongPressed = isLongpressed;
            invalidate();
        }
    }

    public boolean showKeyboard() {
        if (chatActivityEnterView == null || replyDisabledTextView != null && replyDisabledTextView.getVisibility() == View.VISIBLE) {
            return false;
        }
        EditTextCaption editText = chatActivityEnterView.getEditField();
        if (editText == null) {
            return false;
        }
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
        return true;
    }

    public void checkPinchToZoom(MotionEvent ev) {
        pinchToZoomHelper.checkPinchToZoom(ev, storyContainer, null, null,  null,null);
    }


    public void setIsVisible(boolean visible) {
        if (this.isVisible == visible) {
            return;
        }
        isVisible = visible;
        if (visible) {
            imageReceiver.setCurrentAlpha(1f);
            checkStealthMode(false);
        }
    }

    public ArrayList<TL_stories.StoryItem> getStoryItems() {
        return storyItems;
    }

    public void selectPosition(int position) {
        if (selectedPosition != position) {
            selectedPosition = position;
            updatePosition();
        }
    }

    public void cancelTouch() {
        storyCaptionView.cancelTouch();
    }

    public void onActionDown(MotionEvent ev) {
        if (privacyHint != null && privacyHint.shown() && privacyButton != null &&
            !privacyHint.containsTouch(ev, getX() + storyContainer.getX() + privacyHint.getX(), getY() + storyContainer.getY() + privacyHint.getY()) &&
            !hitButton(privacyButton, ev)
        ) {
            privacyHint.hide();
        }
        if (soundTooltip != null && soundTooltip.shown() && muteIconContainer != null &&
            !soundTooltip.containsTouch(ev, getX() + storyContainer.getX() + soundTooltip.getX(), getY() + storyContainer.getY() + soundTooltip.getY()) &&
            !hitButton(muteIconContainer, ev)
        ) {
            soundTooltip.hide();
        }
    }

    private boolean hitButton(View v, MotionEvent e) {
        float ox = getX() + storyContainer.getX() + v.getX(), oy = getY() + storyContainer.getY() + v.getY();
        return (
            e.getX() >= ox && e.getX() <= ox + v.getWidth() &&
            e.getY() >= oy && e.getY() <= oy + v.getHeight()
        );
    }

    Runnable allowDrawSurfaceRunnable = new Runnable() {
        @Override
        public void run() {
            if (isActive && allowDrawSurface) {
                delegate.setIsSwiping(false);
            }
        }
    };

    public void setOffset(float position) {
        boolean allowDrawSurface = position == 0;
        if (this.allowDrawSurface != allowDrawSurface) {
            this.allowDrawSurface = allowDrawSurface;
            storyContainer.invalidate();
            if (isActive) {
                if (useSurfaceInViewPagerWorkAround()) {
                    if (allowDrawSurface) {
                        AndroidUtilities.cancelRunOnUIThread(allowDrawSurfaceRunnable);
                        AndroidUtilities.runOnUIThread(allowDrawSurfaceRunnable, 250);
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(allowDrawSurfaceRunnable);
                        delegate.setIsSwiping(true);
                    }
                }
            }
        }
    }

    //surface can missing in view pager
    public boolean useSurfaceInViewPagerWorkAround() {
        return storyViewer.USE_SURFACE_VIEW && Build.VERSION.SDK_INT < 33;
    }

    public void showNoSoundHint(boolean nosound) {
        if (soundTooltip == null) {
            soundTooltip = new HintView2(getContext(), HintView2.DIRECTION_TOP).setJoint(1, -56);
            soundTooltip.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            storyContainer.addView(soundTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 0, 52, 0, 0));
        }
        soundTooltip.setText(LocaleController.getString(nosound ? R.string.StoryNoSound : R.string.StoryTapToSound));
        soundTooltip.show();
    }

    public boolean checkTextSelectionEvent(MotionEvent ev) {
        if (storyCaptionView.textSelectionHelper.isInSelectionMode()) {
            float xOffset = getX();
            float yOffset = getY() + ((View) getParent()).getY();
            ev.offsetLocation(-xOffset, -yOffset);
            if (storyCaptionView.textSelectionHelper.getOverlayView(getContext()).onTouchEvent(ev)) {
                return true;
            } else {
                ev.offsetLocation(xOffset, yOffset);
            }
        }
        return false;
    }

    public void cancelTextSelection() {
        if (storyCaptionView.textSelectionHelper.isInSelectionMode()) {
            storyCaptionView.textSelectionHelper.clear();
        }
    }

    public boolean checkReactionEvent(MotionEvent ev) {
        if (likesReactionLayout != null) {
            View view = likesReactionLayout;
            float xOffset = getX();
            float yOffset = getY() + ((View) getParent()).getY();
            if (likesReactionLayout.getReactionsWindow() != null && likesReactionLayout.getReactionsWindow().windowView != null) {
                ev.offsetLocation(-xOffset, -yOffset - likesReactionLayout.getReactionsWindow().windowView.getTranslationY());
                likesReactionLayout.getReactionsWindow().windowView.dispatchTouchEvent(ev);
                return true;
            }
            view.getHitRect(AndroidUtilities.rectTmp2);

            AndroidUtilities.rectTmp2.offset((int) xOffset, (int) yOffset);
            if (ev.getAction() == MotionEvent.ACTION_DOWN && !AndroidUtilities.rectTmp2.contains((int) ev.getX(), (int) ev.getY())) {
                showLikesReaction(false);
                return true;
            } else {
                ev.offsetLocation(-AndroidUtilities.rectTmp2.left, -AndroidUtilities.rectTmp2.top);
                view.dispatchTouchEvent(ev);
                return true;
            }
        }
        return false;
    }

    public boolean viewsAllowed() {
        return isSelf || (isChannel && userCanSeeViews);
    }

    public static class PeerHeaderView extends FrameLayout {

        public BackupImageView backupImageView;
        public SimpleTextView titleView;
        private TextView[] subtitleView = new TextView[2];
        RadialProgress radialProgress;
        StoryItemHolder storyItemHolder;
        Paint radialProgressPaint;
        private float progressToUploading;

        private boolean uploading;
        private boolean uploadedTooFast;

        public PeerHeaderView(@NonNull Context context, StoryItemHolder holder) {
            super(context);
            this.storyItemHolder = holder;
            backupImageView = new BackupImageView(context) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (imageReceiver.getVisible()) {
                        AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                        drawUploadingProgress(canvas, AndroidUtilities.rectTmp, true, 1f);
                    }
                    super.onDraw(canvas);
                }
            };
            backupImageView.setRoundRadius(dp(16));
            addView(backupImageView, LayoutHelper.createFrame(32, 32, 0, 12, 2, 0, 0));
            setClipChildren(false);

            titleView = new SimpleTextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setPivotY(getMeasuredHeight() / 2f);
                }
            };
            titleView.setTextSize(14);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setMaxLines(1);
            titleView.setEllipsizeByGradient(dp(4));
            titleView.setPivotX(0);
          //  titleView.setSingleLine(true);
          //  titleView.setEllipsize(TextUtils.TruncateAt.END);
            NotificationCenter.listenEmojiLoading(titleView);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 54, 0, 86, 0));

            for (int a = 0; a < 2; ++a) {
                subtitleView[a] = new TextView(context);
                subtitleView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                subtitleView[a].setMaxLines(1);
                subtitleView[a].setSingleLine(true);
                subtitleView[a].setEllipsize(TextUtils.TruncateAt.MIDDLE);
                subtitleView[a].setTextColor(Color.WHITE);
                subtitleView[a].setPadding(dp(3), 0, dp(3), dp(1));
                addView(subtitleView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 51, 18, 83, 0));
            }

            titleView.setTextColor(Color.WHITE);
        }

        public void setSubtitle(CharSequence text) {
            setSubtitle(text, false);
        }

        public void setOnSubtitleClick(View.OnClickListener listener) {
            subtitleView[0].setOnClickListener(listener);
            subtitleView[0].setClickable(listener != null);
            subtitleView[0].setBackground(listener == null ? null : Theme.createSelectorDrawable(0x30ffffff, Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        }

        private ValueAnimator subtitleAnimator;
        public void setSubtitle(CharSequence text, boolean animated) {
            if (subtitleAnimator != null) {
                subtitleAnimator.cancel();
                subtitleAnimator = null;
            }
            if (animated) {
                subtitleView[1].setOnClickListener(null);
                subtitleView[1].setText(subtitleView[0].getText());
                subtitleView[1].setVisibility(View.VISIBLE);
                subtitleView[1].setAlpha(1f);
                subtitleView[1].setTranslationY(0);
                subtitleView[0].setText(text);
                subtitleView[0].setVisibility(View.VISIBLE);
                subtitleView[0].setAlpha(0f);
                subtitleView[0].setTranslationY(-AndroidUtilities.dp(4));
                subtitleAnimator = ValueAnimator.ofFloat(0, 1);
                subtitleAnimator.addUpdateListener(anm -> {
                    float t = (float) anm.getAnimatedValue();
                    subtitleView[0].setAlpha(t);
                    subtitleView[0].setTranslationY((1f - t) * -AndroidUtilities.dp(4));
                    subtitleView[1].setAlpha(1f - t);
                    subtitleView[1].setTranslationY(t * AndroidUtilities.dp(4));
                });
                subtitleAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        subtitleView[1].setVisibility(View.GONE);
                        subtitleView[0].setAlpha(1f);
                        subtitleView[0].setTranslationY(0);
                    }
                });
                subtitleAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                subtitleAnimator.setDuration(340);
                subtitleAnimator.start();
            } else {
                subtitleView[0].setVisibility(View.VISIBLE);
                subtitleView[0].setAlpha(1f);
                subtitleView[0].setText(text);
                subtitleView[1].setVisibility(View.GONE);
                subtitleView[1].setAlpha(0f);
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (!isEnabled()) {
                return false;
            }
            return super.dispatchTouchEvent(ev);
        }

        public void drawUploadingProgress(Canvas canvas, RectF rect, boolean allowDraw, float progressToOpen) {
            if ((storyItemHolder != null && storyItemHolder.uploadingStory != null) || progressToUploading != 0) {
                float progress = 1f;
                final boolean disappearing;
                if (storyItemHolder != null && storyItemHolder.uploadingStory != null && !storyItemHolder.uploadingStory.failed) {
                    progressToUploading = 1f;
                    progress = storyItemHolder.uploadingStory.progress;
                    disappearing = false;
                    if (!uploading) {
                        uploading = true;
                    }
                } else {
                    disappearing = true;
                    if (uploading) {
                        uploading = false;
                        uploadedTooFast = radialProgress.getAnimatedProgress() < .2f;
                    }
                    if (!uploadedTooFast) {
                        progressToUploading = Utilities.clamp(progressToUploading - (1000f / AndroidUtilities.screenRefreshRate) / 300f, 1f, 0);
                    }
                }
                if (radialProgress == null) {
                    radialProgress = new RadialProgress(backupImageView);
                    radialProgress.setBackground(null, true, false);
                }
                radialProgress.setDiff(0);
                ImageReceiver imageReceiver = backupImageView.getImageReceiver();
                float offset = AndroidUtilities.dp(3) - AndroidUtilities.dp(6) * (1f - progressToUploading);
                radialProgress.setProgressRect(
                        (int) (rect.left - offset), (int) (rect.top - offset),
                        (int) (rect.right + offset), (int) (rect.bottom + offset)
                );
                radialProgress.setProgress(disappearing ? 1 : Utilities.clamp(progress, 1f, 0), true);
                if (uploadedTooFast && disappearing && radialProgress.getAnimatedProgress() >= .9f) {
                    progressToUploading = Utilities.clamp(progressToUploading - (1000f / AndroidUtilities.screenRefreshRate) / 300f, 1f, 0);
                }
                if (allowDraw) {
                    if (progressToOpen != 1f) {
                        Paint paint = StoriesUtilities.getUnreadCirclePaint(imageReceiver, false);
                        paint.setAlpha((int) (255 * progressToUploading));
                        radialProgress.setPaint(paint);
                        radialProgress.draw(canvas);
                    }
                    if (radialProgressPaint == null) {
                        radialProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        radialProgressPaint.setColor(Color.WHITE);
                        radialProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));
                        radialProgressPaint.setStyle(Paint.Style.STROKE);
                        radialProgressPaint.setStrokeCap(Paint.Cap.ROUND);
                    }
                    radialProgressPaint.setAlpha((int) (255 * progressToOpen * progressToUploading));
                    radialProgress.setPaint(radialProgressPaint);
                    radialProgress.draw(canvas);
                }
            }
        }
    }

    public int getStoriesCount() {
        return uploadingStories.size() + Math.max(totalStoriesCount, storyItems.size());
    }

    public interface Delegate {
        void onPeerSelected(long dialogId, int position);

        void onCloseClick();

        void onCloseLongClick();

        void onEnterViewClick();

        void shouldSwitchToNext();

        void switchToNextAndRemoveCurrentPeer();

        void setHideEnterViewProgress(float v);

        void showDialog(Dialog dialog);

        void updatePeers();

        void requestAdjust(boolean b);

        void setKeyboardVisible(boolean keyboardVisible);

        void setAllowTouchesByViewPager(boolean b);

        void requestPlayer(TLRPC.Document document, Uri uri, long t, VideoPlayerSharedScope scope);

        boolean releasePlayer(Runnable whenReleased);

        boolean isClosed();

        float getProgressToDismiss();

        void setIsRecording(boolean recordingAudioVideo);

        void setIsWaiting(boolean b);

        int getKeyboardHeight();

        void setIsCaption(boolean b);

        void setIsCaptionPartVisible(boolean b);

        void setPopupIsVisible(boolean b);

        void setTranslating(boolean b);

        void setBulletinIsVisible(boolean b);

        void setIsInPinchToZoom(boolean b);

        void preparePlayer(ArrayList<TLRPC.Document> documents, ArrayList<Uri> uries);

        void setIsHintVisible(boolean visible);

        void setIsSwiping(boolean swiping);

        void setIsInSelectionMode(boolean selectionMode);

        void setIsLikesReaction(boolean show);
    }

    public class StoryItemHolder {
        public TL_stories.StoryItem storyItem = null;
        public StoriesController.UploadingStory uploadingStory = null;
        public TL_stories.StoryItem editingSourceItem;
        boolean skipped;
        private boolean isVideo;

        public boolean captionTranslated;
        public CharSequence caption;

        private StoryCaptionView.Reply reply;

        private String getMediaDebugString() {
            if (storyItem != null && storyItem.media != null) {
                if (storyItem.media.photo != null) {
                    return "photo#" + storyItem.media.photo.id + "at" + storyItem.media.photo.dc_id + "dc";
                } else if (storyItem.media.document != null) {
                    return "doc#" + storyItem.media.document.id + "at" + storyItem.media.document.dc_id + "dc";
                }
            } else if (uploadingStory != null) {
                return "uploading from " + uploadingStory.path;
            }
            return "unknown";
        }

        public StoryCaptionView.Reply getReply() {
            if (reply == null) {
                if (storyItem != null) {
                    reply = StoryCaptionView.Reply.from(currentAccount, storyItem);
                } else if (uploadingStory != null) {
                    reply = StoryCaptionView.Reply.from(uploadingStory);
                }
            }
            return reply;
        }

        public void updateCaption() {
            captionTranslated = false;
            if (currentStory.uploadingStory != null) {
                caption = currentStory.uploadingStory.entry.caption;
                caption = Emoji.replaceEmoji(caption, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(caption);
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (dialogId < 0 || MessagesController.getInstance(currentAccount).storyEntitiesAllowed(user)) {
                    MessageObject.addLinks(true, spannableStringBuilder);
                }
            } else if (currentStory.storyItem != null) {
                if (currentStory.storyItem.translated && currentStory.storyItem.translatedText != null && TextUtils.equals(currentStory.storyItem.translatedLng, TranslateAlert2.getToLanguage())) {
                    captionTranslated = true;
                    TLRPC.TL_textWithEntities text = currentStory.storyItem.translatedText;
                    caption = text.text;
                    caption = Emoji.replaceEmoji(caption, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    if (caption != null && text.entities != null) {
                        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text.text);
                        spannableStringBuilder = SpannableStringBuilder.valueOf(MessageObject.replaceAnimatedEmoji(spannableStringBuilder, text.entities, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), false));
                        SpannableStringBuilder.valueOf(Emoji.replaceEmoji(spannableStringBuilder, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), false));
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        final boolean entitiesAllowed = dialogId < 0 || MessagesController.getInstance(currentAccount).storyEntitiesAllowed(user);
                        if (entitiesAllowed) {
                            MessageObject.addLinks(true, spannableStringBuilder);
                        }
                        MessageObject.addEntitiesToText(spannableStringBuilder, text.entities, false, true, true, false, entitiesAllowed ? MessageObject.ENTITIES_ALL : MessageObject.ENTITIES_ONLY_HASHTAGS);
                        caption = spannableStringBuilder;
                    }
                } else {
                    caption = currentStory.storyItem.caption;
                    caption = Emoji.replaceEmoji(caption, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    if (caption != null && currentStory.storyItem.entities != null) {
                        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(currentStory.storyItem.caption);
                        spannableStringBuilder = SpannableStringBuilder.valueOf(MessageObject.replaceAnimatedEmoji(spannableStringBuilder, currentStory.storyItem.entities, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), false));
                        SpannableStringBuilder.valueOf(Emoji.replaceEmoji(spannableStringBuilder, storyCaptionView.captionTextview.getPaint().getFontMetricsInt(), false));
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        final boolean entitiesAllowed = dialogId < 0 || MessagesController.getInstance(currentAccount).storyEntitiesAllowed(user);
                        if (entitiesAllowed) {
                            MessageObject.addLinks(true, spannableStringBuilder);
                        }
                        MessageObject.addEntitiesToText(spannableStringBuilder, currentStory.storyItem.entities, false, true, true, false, entitiesAllowed ? MessageObject.ENTITIES_ALL : MessageObject.ENTITIES_ONLY_HASHTAGS);
                        caption = spannableStringBuilder;
                    }
                }
            }
        }

        void set(TL_stories.StoryItem storyItem) {
            this.storyItem = storyItem;
            this.reply = null;
            this.uploadingStory = null;
            skipped = storyItem instanceof TL_stories.TL_storyItemSkipped;
            isVideo = isVideoInternal();
        }

        private boolean isVideoInternal() {
            if (uploadingStory != null) {
                return uploadingStory.isVideo;
            }
            if (storyItem != null && storyItem.media != null && storyItem.media.getDocument() != null) {
                final TLRPC.Document document = storyItem.media.getDocument();
                if (MessageObject.isVideoDocument(document)) {
                    return true;
                }
                if ("video/mp4".equals(document.mime_type)) {
                    return true;
                }
                return false;
            }
            if (storyItem != null && storyItem.media == null && storyItem.attachPath != null) {
                return storyItem.attachPath.toLowerCase().endsWith(".mp4");
            }
            return false;
        }

        void set(StoriesController.UploadingStory uploadingStory) {
            this.uploadingStory = uploadingStory;
            this.reply = null;
            this.storyItem = null;
            skipped = false;
            isVideo = isVideoInternal();
        }

        public void clear() {
            this.uploadingStory = null;
            this.storyItem = null;
        }

        void cancelOrDelete() {
            if (storyItem instanceof StoriesController.BotPreview) {
                ((StoriesController.BotPreview) storyItem).list.delete(storyItem.media);
            } else if (storyItem != null) {
                storiesController.deleteStory(dialogId, storyItem);
            } else if (uploadingStory != null) {
                uploadingStory.cancel();
            }
        }

        public boolean isEmpty() {
            return false;
        }

        public void checkSendView() {
            TL_stories.PeerStories userStories = PeerStoriesView.this.userStories;
            if (userStories == null) {
                userStories = storiesController.getStories(dialogId);
                if (userStories == null) {
                    TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
                    if (userFull != null) {
                        userStories = userFull.stories;
                    }
                }
            }
            if (isActive && this.storyItem != null && userStories != null && ((!StoriesUtilities.hasExpiredViews(storyItem) && (this.storyItem.id > userStories.max_read_id || this.storyItem.id > storiesController.dialogIdToMaxReadId.get(dialogId, 0))) || isSelf)) {
                if (storyViewer.overrideUserStories != null) {
                    if (storiesController.markStoryAsRead(storyViewer.overrideUserStories, storyItem, true)) {
                        storyViewer.unreadStateChanged = true;
                    }
                } else {
                    if (storiesController.markStoryAsRead(dialogId, storyItem)) {
                        storyViewer.unreadStateChanged = true;
                    }
                }
            } else if (isActive && this.storyItem != null && storyViewer.storiesList != null) {
                if (storyViewer.storiesList.markAsRead(this.storyItem.id)) {
                    storyViewer.unreadStateChanged = true;
                }
            }
        }

        public String getLocalPath() {
            if (storyItem != null) {
                return storyItem.attachPath;
            } else if (uploadingStory != null) {
                return null;//uploadingStory.path;
            }
            return null;
        }

        boolean isVideo() {
            return isVideo;
        }

        boolean hasSound() {
            if (!isVideo) {
                return false;
            }
            TLRPC.Document document;
            if (storyItem != null && storyItem.media != null && (document = storyItem.media.getDocument()) != null) {
                for (int i = 0; i < document.attributes.size(); i++) {
                    TLRPC.DocumentAttribute attribute = document.attributes.get(i);
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo && attribute.nosound) {
                        return false;
                    }
                }
                return true;
            } else if (uploadingStory != null) {
                return !uploadingStory.entry.muted;
            }
            return true;
        }

        public String createLink() {
            if (currentStory.storyItem == null) {
                return null;
            }
            if (dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (UserObject.getPublicUsername(user) == null) {
                    return null;
                }
                return String.format(Locale.US, "https://t.me/%1$s/s/%2$s", UserObject.getPublicUsername(user), currentStory.storyItem.id);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (ChatObject.getPublicUsername(chat) == null) {
                    return null;
                }
                return String.format(Locale.US, "https://t.me/%1$s/s/%2$s", ChatObject.getPublicUsername(chat), currentStory.storyItem.id);
            }
        }

        public File getPath() {
            if (getLocalPath() != null) {
                return new File(getLocalPath());
            }
            if (storyItem != null) {
                if (storyItem.media != null && storyItem.media.getDocument() != null) {
                    return FileLoader.getInstance(currentAccount).getPathToAttach(storyItem.media.getDocument());
                } else if (storyItem.media != null && storyItem.media.photo != null) {
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.photo.sizes, Integer.MAX_VALUE);
                    File file = FileLoader.getInstance(currentAccount).getPathToAttach(size, true);
                    if (!file.exists()) {
                        file = FileLoader.getInstance(currentAccount).getPathToAttach(size, false);
                    }
                    return file;
                }
            }
            return null;
        }

        public boolean forCloseFriends() {
            if (uploadingStory != null) {
                if (uploadingStory.entry.privacy != null) {
                    return uploadingStory.entry.privacy.isCloseFriends();
                } else if (uploadingStory.entry.privacyRules != null) {
                    for (int i = 0; i < uploadingStory.entry.privacyRules.size(); ++i) {
                        if (uploadingStory.entry.privacyRules.get(i) instanceof TLRPC.TL_inputPrivacyValueAllowCloseFriends) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            return storyItem != null && storyItem.close_friends;
        }

        public boolean allowScreenshots() {
            if (uploadingStory != null) {
                return uploadingStory.entry.allowScreenshots;
            }
            if (storyItem != null) {
                if (storyItem.noforwards) {
                    return false;
                }
                if (storyItem.pinned) {
                    final long did = storyItem.dialogId;
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    if (chat != null && chat.noforwards) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    public static int getStoryId(TL_stories.StoryItem storyItem, StoriesController.UploadingStory uploadingStory) {
        if (storyItem != null) {
            return storyItem.id;
        } else if (uploadingStory != null && uploadingStory.entry != null) {
            return uploadingStory.entry.editStoryId;
        } else {
            return 0;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (storyViewer.ATTACH_TO_FRAGMENT) {
            FrameLayout.LayoutParams layoutParams = (LayoutParams) getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.statusBarHeight;
        }
        FrameLayout.LayoutParams layoutParams;
        if (isActive && shareAlert == null) {
            realKeyboardHeight = delegate.getKeyboardHeight();
        } else {
            realKeyboardHeight = 0;
        }
        int heightWithKeyboard;
        if (storyViewer.ATTACH_TO_FRAGMENT) {
            heightWithKeyboard = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            heightWithKeyboard = MeasureSpec.getSize(heightMeasureSpec) + realKeyboardHeight;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int viewPagerHeight;
        if (heightWithKeyboard > (int) (16f * width / 9f)) {
            viewPagerHeight = (int) (16f * width / 9f);
            if (viewPagerHeight > heightWithKeyboard) {
                viewPagerHeight = heightWithKeyboard;
            }
        } else {
            viewPagerHeight = heightWithKeyboard;
        }
        if (realKeyboardHeight < AndroidUtilities.dp(20)) {
            realKeyboardHeight = 0;
        }
        int keyboardHeight = realKeyboardHeight;
        if (likesReactionLayout != null && likesReactionLayout.getReactionsWindow() != null && likesReactionLayout.getReactionsWindow().isShowing()) {
            likesReactionLayout.getReactionsWindow().windowView.animate().translationY(-realKeyboardHeight)
                    .setDuration(AdjustPanLayoutHelper.keyboardDuration)
                    .setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator)
                    .start();
            keyboardHeight = 0;
        } else {
            if (chatActivityEnterView != null && (chatActivityEnterView.isPopupShowing() || chatActivityEnterView.isWaitingForKeyboard())) {
                if (chatActivityEnterView.getEmojiView().getMeasuredHeight() == 0) {
                    keyboardHeight = chatActivityEnterView.getEmojiPadding();
                } else if (chatActivityEnterView.isStickersExpanded()) {
                    chatActivityEnterView.checkStickresExpandHeight();
                    keyboardHeight = chatActivityEnterView.getStickersExpandedHeight();
                } else {
                    keyboardHeight = chatActivityEnterView.getVisibleEmojiPadding();
                }
            }
        }
        boolean keyboardVisibleOld = keyboardVisible;
        if (lastKeyboardHeight != keyboardHeight) {
            keyboardVisible = false;
            if (keyboardHeight > 0 && isActive) {
                keyboardVisible = true;
                messageSent = false;
                lastOpenedKeyboardHeight = keyboardHeight;
                checkReactionsLayout();
                ReactionsEffectOverlay.dismissAll();
            } else {
                if (chatActivityEnterView != null) {
                    storyViewer.saveDraft(dialogId, currentStory.storyItem, chatActivityEnterView.getEditText());
                }
            }
            if (keyboardVisible && mentionContainer != null) {
                mentionContainer.setVisibility(View.VISIBLE);
            }
            if (!keyboardVisible && reactionsContainerLayout != null) {
                reactionsContainerLayout.reset();
            }
            headerView.setEnabled(!keyboardVisible);
            optionsIconView.setEnabled(!keyboardVisible);
            if (chatActivityEnterView != null) {
                chatActivityEnterView.checkReactionsButton(!keyboardVisible);
            }
            if (isActive && keyboardVisible) {
                delegate.setKeyboardVisible(true);
            }
            lastKeyboardHeight = keyboardHeight;
            if (keyboardAnimator != null) {
                keyboardAnimator.cancel();
            }
            notificationsLocker.lock();
            keyboardAnimator = ValueAnimator.ofFloat(animatingKeyboardHeight, keyboardHeight);
            keyboardAnimator.addUpdateListener(animation -> {
                animatingKeyboardHeight = (float) animation.getAnimatedValue();
                invalidate();
            });
            keyboardAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    notificationsLocker.unlock();
                    animatingKeyboardHeight = lastKeyboardHeight;
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.onOverrideAnimationEnd();
                    }
                    if (isActive && !keyboardVisible) {
                        delegate.setKeyboardVisible(false);
                    }
                    if (!keyboardVisible && mentionContainer != null) {
                        mentionContainer.setVisibility(View.GONE);
                    }
                    forceUpdateOffsets = true;
                    invalidate();
                }
            });
            if (keyboardVisible) {
                keyboardAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                keyboardAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                storyViewer.cancelSwipeToReply();
            } else {
                keyboardAnimator.setDuration(500);
                keyboardAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            }

            keyboardAnimator.start();

            if (keyboardVisible != keyboardVisibleOld) {
                if (keyboardVisible) {
                    createBlurredBitmap(bitmapShaderTools.getCanvas(), bitmapShaderTools.getBitmap());
                } else {
                    if (chatActivityEnterView != null) {
                        chatActivityEnterView.getEditField().clearFocus();
                    }
                }
                animateKeyboardOpening = true;
            } else {
                animateKeyboardOpening = false;
            }
        }
        if (chatActivityEnterView != null && chatActivityEnterView.getEmojiView() != null) {
            layoutParams = (LayoutParams) chatActivityEnterView.getEmojiView().getLayoutParams();
            layoutParams.gravity = Gravity.BOTTOM;
        }
        layoutParams = (LayoutParams) storyContainer.getLayoutParams();
        layoutParams.height = viewPagerHeight;
        BIG_SCREEN = heightWithKeyboard - viewPagerHeight > AndroidUtilities.dp(64);
        int top = layoutParams.topMargin = (heightWithKeyboard - (viewPagerHeight + (BIG_SCREEN ? AndroidUtilities.dp(64) : 0))) >> 1;
        if (BIG_SCREEN) {
            enterViewBottomOffset = -top + heightWithKeyboard - viewPagerHeight - AndroidUtilities.dp(64);
        } else {
            enterViewBottomOffset = -top + heightWithKeyboard - viewPagerHeight;
        }
        if (BIG_SCREEN != wasBigScreen) {
            storyContainer.setLayoutParams(layoutParams);
        }
        if (selfView != null) {
            layoutParams = (LayoutParams) selfView.getLayoutParams();
            if (BIG_SCREEN) {
                layoutParams.topMargin = top + viewPagerHeight + AndroidUtilities.dp(8);
            } else {
                layoutParams.topMargin = top + viewPagerHeight - AndroidUtilities.dp(48);
            }
        }

        if (replyDisabledTextView != null) {
            layoutParams = (LayoutParams) replyDisabledTextView.getLayoutParams();
            if (!BIG_SCREEN) {
                replyDisabledTextView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.75f)));
                layoutParams.topMargin = top + viewPagerHeight - AndroidUtilities.dp(12) - AndroidUtilities.dp(40);
            } else {
                replyDisabledTextView.setTextColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
                layoutParams.topMargin = top + viewPagerHeight + AndroidUtilities.dp(12);
            }
        }
        if (instantCameraView != null) {
            layoutParams = (LayoutParams) instantCameraView.getLayoutParams();
            if (keyboardHeight == 0) {
                layoutParams.bottomMargin = heightWithKeyboard - (top + viewPagerHeight - AndroidUtilities.dp(64));
            } else {
                layoutParams.bottomMargin = keyboardHeight + AndroidUtilities.dp(64);
            }
        }

        if (!BIG_SCREEN) {
            ((LayoutParams) bottomActionsLinearLayout.getLayoutParams()).topMargin = top + viewPagerHeight - AndroidUtilities.dp(12) - AndroidUtilities.dp(40);
            int bottomPadding = isSelf ? AndroidUtilities.dp(40) : AndroidUtilities.dp(56);
            ((LayoutParams) storyCaptionView.getLayoutParams()).bottomMargin = bottomPadding;
            if (wasBigScreen != BIG_SCREEN) {
                storyCaptionView.setLayoutParams((LayoutParams) storyCaptionView.getLayoutParams());
            }
            storyCaptionView.blackoutBottomOffset = bottomPadding;
        } else {
            ((LayoutParams) bottomActionsLinearLayout.getLayoutParams()).topMargin = top + viewPagerHeight + AndroidUtilities.dp(12);
            ((LayoutParams) storyCaptionView.getLayoutParams()).bottomMargin = AndroidUtilities.dp(8);
            if (wasBigScreen != BIG_SCREEN) {
                storyCaptionView.setLayoutParams((LayoutParams) storyCaptionView.getLayoutParams());
            }
            storyCaptionView.blackoutBottomOffset = AndroidUtilities.dp(8);
        }

        forceUpdateOffsets = true;

        float headerRightMargin = AndroidUtilities.dp(8) + AndroidUtilities.dp(40);
//        if (closeFriendsBadge.getVisibility() == View.VISIBLE) {
//            headerRightMargin += AndroidUtilities.dp(40);
//        }
        if (privacyButton.getVisibility() == View.VISIBLE) {
            headerRightMargin += AndroidUtilities.dp(60);
        }
        if (muteIconContainer.getVisibility() == View.VISIBLE) {
            headerRightMargin += AndroidUtilities.dp(40);
        }
        layoutParams = (LayoutParams) headerView.titleView.getLayoutParams();
        if (layoutParams.rightMargin != headerRightMargin) {
            layoutParams.rightMargin = (int) headerRightMargin;
            layoutParams = (LayoutParams) headerView.subtitleView[0].getLayoutParams();
            layoutParams.rightMargin = (int) headerRightMargin;
            layoutParams = (LayoutParams) headerView.subtitleView[1].getLayoutParams();
            layoutParams.rightMargin = (int) headerRightMargin;
            headerView.forceLayout();
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(heightWithKeyboard, MeasureSpec.EXACTLY));
        wasBigScreen = BIG_SCREEN;
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        progressToKeyboard = -1;
        forceUpdateOffsets = true;
        invalidate();
    }

    AnimatedFloat progressToRecording = new AnimatedFloat(this);
    AnimatedFloat progressToTextA = new AnimatedFloat(this);
    AnimatedFloat progressToStickerExpanded = new AnimatedFloat(this);
    float progressToReply;

    private void updateViewOffsets() {
        float progressToDismissLocal = delegate.getProgressToDismiss();
        float progressToKeyboardLocal;
        progressToHideInterface.set(isLongPressed ? 1f : 0);

        if (lastOpenedKeyboardHeight != 0 && animateKeyboardOpening) {
            progressToKeyboardLocal = MathUtils.clamp(animatingKeyboardHeight / lastOpenedKeyboardHeight, 0, 1f);
        } else {
            progressToKeyboardLocal = keyboardVisible ? 1f : 0;
        }
        float progressToRecord = progressToRecording.get();
        float progressToText = progressToTextA.get();
        float progressToStickerExpandedLocal = progressToStickerExpanded.get();
        progressToRecording.set(isRecording ? 1f : 0);
        if (!messageSent) {
            progressToTextA.set(chatActivityEnterView != null && !TextUtils.isEmpty(chatActivityEnterView.getFieldText()) ? 1f : 0);
        }
        progressToStickerExpanded.set(chatActivityEnterView != null && chatActivityEnterView.isStickersExpanded() ? 1f : 0);
        if (chatActivityEnterView != null) {
            chatActivityEnterView.checkAnimation();
        }
        boolean popupVisible = chatActivityEnterView != null && chatActivityEnterView.isPopupShowing();
        float hideInterfaceAlpha = getHideInterfaceAlpha();
        if (BIG_SCREEN) {
            inputBackgroundPaint.setColor(ColorUtils.blendARGB(
                    ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.13f),
                    ColorUtils.setAlphaComponent(Color.BLACK, 170),
                    progressToKeyboard
            ));
            inputBackgroundPaint.setAlpha((int) (inputBackgroundPaint.getAlpha() * (1f - progressToDismiss) * hideInterfaceAlpha * (1f - outT)));
        } else {
            inputBackgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (140 * hideInterfaceAlpha * (1f - outT))));
        }
        if (
            forceUpdateOffsets ||
            progressToReply != storyViewer.swipeToReplyProgress ||
            progressToHideInterface.get() != prevToHideProgress ||
            lastAnimatingKeyboardHeight != animatingKeyboardHeight ||
            progressToKeyboardLocal != progressToKeyboard ||
            progressToDismissLocal != progressToDismiss ||
            progressToRecord != progressToRecording.get() ||
            popupVisible ||
            progressToStickerExpandedLocal != progressToStickerExpanded.get() ||
            progressToText != progressToTextA.get()
        ) {
            forceUpdateOffsets = false;
            lastAnimatingKeyboardHeight = animatingKeyboardHeight;
            if (progressToHideInterface.get() != prevToHideProgress) {
                storyContainer.invalidate();
            }
            if (progressToDismissLocal != 0) {
                //fix jittering caption shadow
                storyContainer.setLayerType(LAYER_TYPE_HARDWARE, null);
            } else {
                storyContainer.setLayerType(LAYER_TYPE_NONE, null);
            }
            prevToHideProgress = progressToHideInterface.get();
            progressToDismiss = progressToDismissLocal;
            progressToKeyboard = progressToKeyboardLocal;
            progressToReply = storyViewer.swipeToReplyProgress;
        } else {
            return;
        }

        if (reactionsContainerLayout != null) {
            reactionsContainerLayout.setVisibility(progressToKeyboard > 0 ? View.VISIBLE : View.GONE);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            if (child.getVisibility() != View.VISIBLE || child == selfView || child.getTag(R.id.parent_tag) != null || child == storyCaptionView.textSelectionHelper.getOverlayView(getContext())) {
                if (child == selfView) {
                    if (BIG_SCREEN) {
                        child.setAlpha((1f - progressToDismiss) * hideInterfaceAlpha * (1f - outT));
                    } else {
                        child.setAlpha(hideInterfaceAlpha * (1f - outT));
                    }
                }
                continue;
            }
            if (chatActivityEnterView != null && child == chatActivityEnterView.getEmojiView()) {
                child.setTranslationY(chatActivityEnterView.getEmojiView().getMeasuredHeight() - animatingKeyboardHeight);
            } else if (child instanceof HintView) {
                HintView hintView = (HintView) child;
                hintView.updatePosition();
            } else if (child != instantCameraView && child != storyContainer && child != shareButton && child != bottomActionsLinearLayout && child != repostButtonContainer && child != mediaBanTooltip && child != likeButtonContainer && (likesReactionLayout == null || likesReactionLayout.getReactionsWindow() == null || child != likesReactionLayout.getReactionsWindow().windowView)) {
                float alpha;
                float translationY = -enterViewBottomOffset * (1f - progressToKeyboard) - animatingKeyboardHeight - AndroidUtilities.dp(8) * (1f - progressToKeyboard) - AndroidUtilities.dp(20) * storyViewer.swipeToReplyProgress;
                if (BIG_SCREEN) {
                    alpha = (1f - progressToDismiss) * hideInterfaceAlpha;
                } else {
                    alpha = 1f * hideInterfaceAlpha;
                }
                if (child == replyDisabledTextView) {
                    translationY = - AndroidUtilities.dp(20) * storyViewer.swipeToReplyProgress;
                }
                if (child == mentionContainer) {
                    translationY -= chatActivityEnterView.getMeasuredHeight() - chatActivityEnterView.getAnimatedTop();
                    alpha = progressToKeyboard;
                    child.invalidate();
                }
                if (child == reactionsContainerLayout) {
                    float finalProgress = progressToKeyboard * (1f - progressToRecording.get()) * (1f - progressToStickerExpandedLocal) * (1f - progressToTextA.get());
                    float finalAlpha = finalProgress * alpha * 1f;
                    if (child.getAlpha() != 0 && finalAlpha == 0) {
                        reactionsContainerLayout.reset();
                    }
                    child.setAlpha(finalAlpha);
                    float s = 0.8f + 0.2f * finalProgress;
                    child.setScaleX(s);
                    child.setScaleY(s);
                } else {
                    child.setTranslationY(translationY);
                    if (chatActivityEnterView == null || child != chatActivityEnterView.controlsView) {
                        child.setAlpha(alpha);
                    }
                }
            }
        }
        shareButton.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
        likeButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
        if (repostButtonContainer != null) {
            repostButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
        }

        for (int i = 0; i < storyContainer.getChildCount(); i++) {
            View child = storyContainer.getChildAt(i);
            if (child == null) {
                continue;
            }
            if (child == headerView || child == optionsIconView || child == muteIconContainer || child == selfView || child == storyCaptionView || child == privacyButton) {
                float alpha = 1f;
                if (child == muteIconContainer) {
                    alpha = muteIconViewAlpha;
                }
                if (child == storyCaptionView) {
                    boolean hideCaptionWithInterface = hideCaptionWithInterface();
                    child.setAlpha(alpha * (hideCaptionWithInterface ? hideInterfaceAlpha : 1f) * (1f - outT));
                } else {
                    child.setAlpha(alpha * hideInterfaceAlpha * (1f - outT));
                }
            } else {
                child.setAlpha(hideInterfaceAlpha);
            }
        }
        if (chatActivityEnterView != null) {
            chatActivityEnterView.setHorizontalPadding(AndroidUtilities.dp(10), progressToKeyboard, allowShare || isGroup);
            if (chatActivityEnterView.getEmojiView() != null) {
                chatActivityEnterView.getEmojiView().setAlpha(progressToKeyboard);
            }
        }
    }

    private boolean hideCaptionWithInterface() {
        return true;//storyCaptionView.getProgressToBlackout() == 0;;
    }

    private float getHideInterfaceAlpha() {
        return (1f - progressToHideInterface.get()) * (1f - storyViewer.getProgressToSelfViews());
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == mentionContainer) {
            canvas.save();
            float bottom = mentionContainer.getY() + mentionContainer.getMeasuredHeight();
            canvas.clipRect(0, mentionContainer.getY(), getMeasuredWidth(), bottom);
            boolean rez = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return rez;
        } else if (child == chatActivityEnterView) {
            sharedResources.rect1.set(0,
                    chatActivityEnterView.getY() + chatActivityEnterView.getAnimatedTop(),
                    getMeasuredWidth() + AndroidUtilities.dp(20),
                    getMeasuredHeight()
            );
            float rightOffset = dp(40);
            if (allowShare) {
                rightOffset += dp(SHARE_BUTTON_OFFSET);
            }
            if (allowRepost && isChannel) {
                rightOffset += dp(SHARE_BUTTON_OFFSET);
            }
            if (likeButtonContainer != null && likeButtonContainer.getVisibility() == View.VISIBLE) {
                rightOffset -= dp(40);
                rightOffset += likeButtonContainer.getLayoutParams().width;
            }
            sharedResources.rect2.set(
                    dp(10),
                    (chatActivityEnterView.getBottom() - dp(48) + chatActivityEnterView.getTranslationY() + dp(2)),
                    getMeasuredWidth() - dp(10) - rightOffset,
                    (chatActivityEnterView.getY() + chatActivityEnterView.getMeasuredHeight() - dp(2))
            );
            if (chatActivityEnterView.getMeasuredHeight() > AndroidUtilities.dp(50)) {
                chatActivityEnterView.getEditField().setTranslationY((1f - progressToKeyboard) * (chatActivityEnterView.getMeasuredHeight() - AndroidUtilities.dp(50)));
            } else {
                chatActivityEnterView.getEditField().setTranslationY(0);
            }
            float radius = AndroidUtilities.dp(50) / 2f * (1f - progressToKeyboard);
            bitmapShaderTools.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
            AndroidUtilities.lerp(sharedResources.rect2, sharedResources.rect1, progressToKeyboard, sharedResources.finalRect);
            if (progressToKeyboard > 0) {
                bitmapShaderTools.paint.setAlpha((int) (255 * progressToKeyboard));
                canvas.drawRoundRect(sharedResources.finalRect, radius, radius, bitmapShaderTools.paint);
            }
            canvas.drawRoundRect(sharedResources.finalRect, radius, radius, inputBackgroundPaint);
            if (progressToKeyboard < 0.5f) {
                canvas.save();
                canvas.clipRect(sharedResources.finalRect);
                boolean rez = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return rez;
            }
        } else if (chatActivityEnterView != null && chatActivityEnterView.isPopupView(child)) {
            canvas.save();
            canvas.clipRect(sharedResources.finalRect);
            boolean res = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return res;
        } else if (child == reactionsContainerLayout && chatActivityEnterView != null) {
            child.setTranslationY(-reactionsContainerLayout.getMeasuredHeight() + (chatActivityEnterView.getY() + chatActivityEnterView.getAnimatedTop()) - AndroidUtilities.dp(18));
            if (progressToKeyboard > 0) {
                sharedResources.dimPaint.setAlpha((int) (125 * progressToKeyboard));
                canvas.drawRect(0, 0, getMeasuredWidth(), chatActivityEnterView.getY() + chatActivityEnterView.getAnimatedTop(), sharedResources.dimPaint);
            }
        } else if (child == likesReactionLayout) {
            child.setTranslationY(-(likesReactionLayout.getMeasuredHeight() - likesReactionLayout.getPaddingBottom()) + likeButtonContainer.getY() + bottomActionsLinearLayout.getY() - AndroidUtilities.dp(18));
//            if (progressToKeyboard > 0) {
//                sharedResources.dimPaint.setAlpha((int) (125 * progressToKeyboard));
//                canvas.drawRect(0, 0, getMeasuredWidth(), chatActivityEnterView.getY() + chatActivityEnterView.getAnimatedTop(), sharedResources.dimPaint);
//            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void checkInstantCameraView() {
        if (instantCameraView != null) {
            return;
        }

        instantCameraView = new InstantCameraView(getContext(), new InstantCameraView.Delegate() {
            @Override
            public View getFragmentView() {
                return PeerStoriesView.this;
            }

            @Override
            public void sendMedia(MediaController.PhotoEntry photoEntry, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, boolean forceDocument) {
                if (photoEntry == null) {
                    return;
                }
                TL_stories.StoryItem storyItem = currentStory.storyItem;
                if (storyItem == null || storyItem instanceof TL_stories.TL_storyItemSkipped) {
                    return;
                }
                storyItem.dialogId = dialogId;
                if (photoEntry.isVideo) {
                    if (videoEditedInfo != null) {
                        SendMessagesHelper.prepareSendingVideo(getAccountInstance(), photoEntry.path, videoEditedInfo, dialogId, null, null, storyItem, null, photoEntry.entities, photoEntry.ttl, null, notify, scheduleDate, forceDocument, photoEntry.hasSpoiler, photoEntry.caption, null, 0, 0);
                    } else {
                        SendMessagesHelper.prepareSendingVideo(getAccountInstance(), photoEntry.path, null, dialogId, null, null, storyItem, null, photoEntry.entities, photoEntry.ttl, null, notify, scheduleDate, forceDocument, photoEntry.hasSpoiler, photoEntry.caption, null, 0, 0);
                    }
                } else {
                    if (photoEntry.imagePath != null) {
                        SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), photoEntry.imagePath, photoEntry.thumbPath, null, dialogId, null, null, storyItem, null, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, null, videoEditedInfo, notify, scheduleDate, 0, forceDocument, photoEntry.caption, null, 0, 0);
                    } else if (photoEntry.path != null) {
                        SendMessagesHelper.prepareSendingPhoto(getAccountInstance(), photoEntry.path, photoEntry.thumbPath, null, dialogId, null, null, storyItem, null, photoEntry.entities, photoEntry.stickers, null, photoEntry.ttl, null, videoEditedInfo, notify, scheduleDate, 0, forceDocument, photoEntry.caption, null, 0, 0);
                    }
                }
                afterMessageSend();
            }

            @Override
            public Activity getParentActivity() {
                return AndroidUtilities.findActivity(getContext());
            }

            @Override
            public int getClassGuid() {
                return classGuid;
            }

            @Override
            public long getDialogId() {
                return dialogId;
            }

        }, resourcesProvider);
        instantCameraView.drawBlur = false;
        int i = Math.min(indexOfChild(chatActivityEnterView.getRecordCircle()), indexOfChild(chatActivityEnterView.controlsView));
        addView(instantCameraView, i, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
    }

    private void afterMessageSend() {
        if (instantCameraView != null) {
            instantCameraView.resetCameraFile();
            instantCameraView.cancel(false);
        }
        storyViewer.clearDraft(dialogId, currentStory.storyItem);
        messageSent = true;
        storyViewer.closeKeyboardOrEmoji();
        BulletinFactory bulletinFactory = BulletinFactory.of(storyContainer, resourcesProvider);
        if (bulletinFactory != null) {
            bulletinFactory.createSimpleBulletin(R.raw.forward, LocaleController.getString(R.string.MessageSent), LocaleController.getString(R.string.ViewInChat), Bulletin.DURATION_PROLONG, this::openChat).hideAfterBottomSheet(false).show(false);
        }
        MessagesController.getInstance(currentAccount).ensureMessagesLoaded(dialogId, 0, null);
    }

    private void openChat() {
        Bundle bundle = new Bundle();
        if (dialogId < 0) {
            bundle.putLong("chat_id", -dialogId);
        } else {
            bundle.putLong("user_id", dialogId);
        }
        TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).getDialog(dialogId);
        if (dialog != null) {
            bundle.putInt("message_id", dialog.top_message);
        }
        ChatActivity chatActivity = new ChatActivity(bundle);
        storyViewer.presentFragment(chatActivity);
    }

    private AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    public static class VideoPlayerSharedScope {
        StoryViewer.VideoPlayerHolder player;
        SurfaceView surfaceView;
        View renderView;
        TextureView textureView;
        boolean firstFrameRendered;
        ArrayList<View> viewsToInvalidate = new ArrayList<>();

        public void invalidate() {
            for (int i = 0; i < viewsToInvalidate.size(); i++) {
                viewsToInvalidate.get(i).invalidate();
            }
        }

        public boolean isBuffering() {
            return player != null && player.isBuffering();
        }
    }

    void checkReactionsLayout() {
        if (reactionsContainerLayout == null) {
            reactionsContainerLayout = new ReactionsContainerLayout(ReactionsContainerLayout.TYPE_STORY, LaunchActivity.getLastFragment(), getContext(), currentAccount, new WrappedResourceProvider(resourcesProvider) {
                @Override
                public void appendColors() {
                    sparseIntArray.put(Theme.key_chat_emojiPanelBackground, ColorUtils.setAlphaComponent(Color.WHITE, 30));
                }
            });
            reactionsContainerLayout.setHint(LocaleController.getString(isGroup ? R.string.StoryGroupReactionsHint : R.string.StoryReactionsHint));
            reactionsContainerLayout.skipEnterAnimation = true;
            addView(reactionsContainerLayout, reactionsContainerIndex, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 52 + 20, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 64));
            reactionsContainerLayout.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
                @Override
                public void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                    onReactionClickedInternal(view, visibleReaction, longpress, addToRecent, !longpress);
                }

                void onReactionClickedInternal(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent, boolean allowConfirm) {
                    if (allowConfirm && applyMessageToChat(() -> {
                            onReactionClickedInternal(view, visibleReaction, longpress, addToRecent, false);
                        })) {
                        return;
                    }
                    ReactionsEffectOverlay effectOverlay;
                    if (longpress && visibleReaction.emojicon != null) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        effectOverlay = new ReactionsEffectOverlay(
                                view.getContext(), null,
                                reactionsContainerLayout, null,
                                view, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f,
                                visibleReaction, currentAccount,
                                ReactionsEffectOverlay.LONG_ANIMATION, true);
                    } else {
                        effectOverlay = new ReactionsEffectOverlay(
                                view.getContext(), null,
                                reactionsContainerLayout, null,
                                view, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f,
                                visibleReaction, currentAccount,
                                ReactionsEffectOverlay.ONLY_MOVE_ANIMATION, true);
                    }
                    ReactionsEffectOverlay.currentOverlay = effectOverlay;
                    effectOverlay.windowView.setTag(R.id.parent_tag, 1);
                    addView(effectOverlay.windowView);
                    effectOverlay.started = true;
                    effectOverlay.startTime = System.currentTimeMillis();
                    TLRPC.Document document;
                    if (visibleReaction.emojicon != null) {
                        document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(visibleReaction.emojicon);
                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(visibleReaction.emojicon, dialogId);
                        params.replyToStoryItem = currentStory.storyItem;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                    } else {
                        document = AnimatedEmojiDrawable.findDocument(currentAccount, visibleReaction.documentId);
                        String emoticon = MessageObject.findAnimatedEmojiEmoticon(document, null);
                        if (emoticon == null) {
                            if (reactionsContainerLayout.getReactionsWindow() != null) {
                                reactionsContainerLayout.getReactionsWindow().dismissWithAlpha();
                            }
                            closeKeyboardOrEmoji();
                            return;
                        }
                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(emoticon, dialogId);
                        params.entities = new ArrayList<>();
                        TLRPC.TL_messageEntityCustomEmoji customEmojiEntitiy = new TLRPC.TL_messageEntityCustomEmoji();
                        customEmojiEntitiy.document_id = visibleReaction.documentId;
                        customEmojiEntitiy.offset = 0;
                        customEmojiEntitiy.length = emoticon.length();
                        params.entities.add(customEmojiEntitiy);
                        params.replyToStoryItem = currentStory.storyItem;
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(params);
                    }

                    BulletinFactory.of(storyContainer, resourcesProvider).createEmojiBulletin(document,
                            LocaleController.getString(R.string.ReactionSent),
                            LocaleController.getString(R.string.ViewInChat), () -> {
                                openChat();
                            }).setDuration(Bulletin.DURATION_PROLONG).show();
                    if (reactionsContainerLayout.getReactionsWindow() != null) {
                        reactionsContainerLayout.getReactionsWindow().dismissWithAlpha();
                    }
                    closeKeyboardOrEmoji();
                }

                @Override
                public void hideMenu() {

                }

                @Override
                public void drawRoundRect(Canvas canvas, RectF rect, float radius, float offsetX, float offsetY, int alpha, boolean isWindow) {
                    bitmapShaderTools.setBounds(-offsetX, -offsetY,
                            -offsetX + getMeasuredWidth(),
                            -offsetY + getMeasuredHeight());
                    if (radius > 0) {
                        canvas.drawRoundRect(rect, radius, radius, bitmapShaderTools.paint);
                        canvas.drawRoundRect(rect, radius, radius, inputBackgroundPaint);
                    } else {
                        canvas.drawRect(rect, bitmapShaderTools.paint);
                        canvas.drawRect(rect, inputBackgroundPaint);
                    }
                }

                @Override
                public boolean needEnterText() {
                    return PeerStoriesView.this.needEnterText();
                }

                @Override
                public void onEmojiWindowDismissed() {
                    delegate.requestAdjust(false);
                }
            });
            reactionsContainerLayout.setMessage(null, null, true);
        }
        reactionsContainerLayout.setFragment(LaunchActivity.getLastFragment());
        reactionsContainerLayout.setHint(LocaleController.getString(isGroup ? R.string.StoryGroupReactionsHint : R.string.StoryReactionsHint));
    }

    void checkReactionsLayoutForLike() {
        if (likesReactionLayout == null) {
            likesReactionLayout = new ReactionsContainerLayout(ReactionsContainerLayout.TYPE_STORY_LIKES, LaunchActivity.getLastFragment(), getContext(), currentAccount, new WrappedResourceProvider(resourcesProvider) {
                @Override
                public void appendColors() {
                    sparseIntArray.put(Theme.key_chat_emojiPanelBackground, ColorUtils.setAlphaComponent(Color.WHITE, 30));
                }
            });
            likesReactionLayout.setPadding(0, 0, 0, AndroidUtilities.dp(22));

            addView(likesReactionLayout, getChildCount() - 1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 52 + 22, Gravity.TOP | Gravity.RIGHT, 0, 0, 12, 64));
            likesReactionLayout.setVisibility(View.GONE);
            likesReactionLayout.setDelegate(new ReactionsContainerLayout.ReactionsContainerDelegate() {
                @Override
                public void onReactionClicked(View view, ReactionsLayoutInBubble.VisibleReaction visibleReaction, boolean longpress, boolean addToRecent) {
                    Runnable runnable = () -> {
                        movingReaction = true;
                        boolean[] effectStarted = {false};
                        View oldLikeButton = storiesLikeButton;
                        oldLikeButton.animate().alpha(0).scaleX(0.8f).scaleY(0.8f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                AndroidUtilities.removeFromParent(oldLikeButton);
                            }
                        }).setDuration(150).start();
                        int padding = dp(8);
                        storiesLikeButton = new StoriesLikeButton(getContext(), sharedResources);
                        storiesLikeButton.setPadding(padding, padding, padding, padding);
                        likeButtonContainer.addView(storiesLikeButton, LayoutHelper.createFrame(40, 40, Gravity.LEFT));

                        if (reactionMoveDrawable != null) {
                            reactionMoveDrawable.removeView(PeerStoriesView.this);
                            reactionMoveDrawable = null;
                        }
                        if (emojiReactionEffect != null) {
                            emojiReactionEffect.removeView(PeerStoriesView.this);
                            emojiReactionEffect = null;
                        }
                        drawAnimatedEmojiAsMovingReaction = false;
                        if (visibleReaction.documentId != 0) {
                            drawAnimatedEmojiAsMovingReaction = true;
                            reactionMoveDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, currentAccount, visibleReaction.documentId);
                            reactionMoveDrawable.addView(PeerStoriesView.this);
                        } else if (visibleReaction.emojicon != null) {
                            TLRPC.TL_availableReaction availableReaction = MediaDataController.getInstance(currentAccount).getReactionsMap().get(visibleReaction.emojicon);
                            if (availableReaction != null) {
                                TLRPC.Document document = availableReaction.select_animation;//availableReaction.appear_animation;
                                reactionMoveImageReceiver.setImage(null, null, ImageLocation.getForDocument(document), "60_60", null, null, null, 0, null, null, 0);
                                document = availableReaction.around_animation;
                                String filer = ReactionsEffectOverlay.getFilterForAroundAnimation();
                                reactionEffectImageReceiver.setImage(ImageLocation.getForDocument(document), filer, null, null, null, 0);
                                if (reactionEffectImageReceiver.getLottieAnimation() != null) {
                                    reactionEffectImageReceiver.getLottieAnimation().setCurrentFrame(0, false, true);
                                }
                            }
                        }
                        storiesLikeButton.setReaction(visibleReaction);
                        if (isChannel && currentStory.storyItem.sent_reaction == null) {
                            if (currentStory.storyItem.views == null) {
                                currentStory.storyItem.views = new TL_stories.TL_storyViews();
                            }
                            currentStory.storyItem.views.reactions_count++;
                            ReactionsUtils.applyForStoryViews(null, currentStory.storyItem.sent_reaction, currentStory.storyItem.views);
                            updateUserViews(true);
                        }
                        if (visibleReaction.documentId != 0 && storiesLikeButton.emojiDrawable != null) {
                            emojiReactionEffect = AnimatedEmojiEffect.createFrom(storiesLikeButton.emojiDrawable, false, true);
                            emojiReactionEffect.setView(PeerStoriesView.this);
                        }
                        storiesController.setStoryReaction(dialogId, currentStory.storyItem, visibleReaction);
                        int[] childCoords = new int[2];
                        view.getLocationInWindow(childCoords);
                        int[] parentCoords = new int[2];
                        PeerStoriesView.this.getLocationInWindow(parentCoords);
                        movingReactionFromX = (int) childCoords[0] - parentCoords[0];
                        movingReactionFromY = (int) childCoords[1] - parentCoords[1];
                        movingReactionFromSize = view.getMeasuredHeight();

                        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
                        movingReactionProgress = 0;
                        PeerStoriesView.this.invalidate();
                        StoriesLikeButton storiesLikeButtonFinal = storiesLikeButton;
                        storiesLikeButtonFinal.setAllowDrawReaction(false);
                        storiesLikeButtonFinal.prepareAnimateReaction(visibleReaction);
                        animator.addUpdateListener(animation -> {
                            movingReactionProgress = (float) animator.getAnimatedValue();
                            PeerStoriesView.this.invalidate();
                            if (movingReactionProgress > 0.8f && !effectStarted[0]) {
                                effectStarted[0] = true;
                                drawReactionEffect = true;
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            }
                        });
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                movingReaction = false;
                                movingReactionProgress = 1f;
                                PeerStoriesView.this.invalidate();
                                if (!effectStarted[0]) {
                                    effectStarted[0] = true;
                                    drawReactionEffect = true;
                                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                                }
                                storiesLikeButtonFinal.setAllowDrawReaction(true);
                                storiesLikeButtonFinal.animateVisibleReaction();

                                if (reactionMoveDrawable != null) {
                                    reactionMoveDrawable.removeView(PeerStoriesView.this);
                                    reactionMoveDrawable = null;
                                }
                            }
                        });
                        animator.setDuration(220);
                        animator.start();
                        showLikesReaction(false);
                    };
                    if (!longpress) {
                        applyMessageToChat(runnable);
                    } else {
                        runnable.run();
                    }
                }

                @Override
                public boolean needEnterText() {
                    delegate.requestAdjust(false);
                    return false;
                }
            });
            likesReactionLayout.setMessage(null, null, true);
        } else {
            bringChildToFront(likesReactionLayout);
            likesReactionLayout.reset();
        }
        likesReactionLayout.setFragment(LaunchActivity.getLastFragment());
    }

    public boolean needEnterText() {
        if (chatActivityEnterView == null) {
            return false;
        }
        boolean keyboardVisible = chatActivityEnterView.isKeyboardVisible();
        if (keyboardVisible) {
            chatActivityEnterView.showEmojiView();
        }
        AndroidUtilities.runOnUIThread(() -> {
            delegate.requestAdjust(true);
        }, 300);

        return keyboardVisible;
    }

    public void setViewsThumbImageReceiver(float alpha, float scale, float pivotY, SelfStoriesPreviewView.ImageHolder viewsThumbImageReceiver) {
        this.viewsThumbAlpha = alpha;
        this.viewsThumbScale = 1f / scale;
        this.viewsThumbPivotY = pivotY;
        if (this.viewsThumbImageReceiver == viewsThumbImageReceiver) {
            return;
        }
        this.viewsThumbImageReceiver = viewsThumbImageReceiver;
        if (viewsThumbImageReceiver != null && viewsThumbImageReceiver.receiver.getBitmap() != null) {
            imageReceiver.updateStaticDrawableThump(viewsThumbImageReceiver.receiver.getBitmap().copy(Bitmap.Config.ARGB_8888, false));
        }
    }

    public static class SharedResources {

        public final Paint barPaint;
        public final Paint selectedBarPaint;
        private final Paint gradientBackgroundPaint;
        private final Drawable topOverlayGradient;
        private final Drawable bottomOverlayGradient;
        public final Drawable imageBackgroundDrawable;
        public final BitmapShaderTools bitmapShaderTools = new BitmapShaderTools();

        private final RectF rect1 = new RectF();
        private final RectF rect2 = new RectF();
        private final RectF finalRect = new RectF();
        private final Paint dimPaint = new Paint();
        public Drawable shareDrawable;
        public Drawable repostDrawable;
        public Drawable likeDrawable;
        public Drawable likeDrawableFilled;
        public Drawable optionsDrawable;
        public Drawable deleteDrawable;
        public RLottieDrawable noSoundDrawable;
       // public ReplaceableIconDrawable muteDrawable;
        public RLottieDrawable muteDrawable;

        SharedResources(Context context) {
            shareDrawable = ContextCompat.getDrawable(context, R.drawable.media_share);
            likeDrawable = ContextCompat.getDrawable(context, R.drawable.media_like);
            repostDrawable = ContextCompat.getDrawable(context, R.drawable.media_repost);
            likeDrawableFilled = ContextCompat.getDrawable(context, R.drawable.media_like_active);
            likeDrawableFilled.setColorFilter(new PorterDuffColorFilter(0xFFFF2E38, PorterDuff.Mode.MULTIPLY));
            optionsDrawable = ContextCompat.getDrawable(context, R.drawable.media_more);
            deleteDrawable = ContextCompat.getDrawable(context, R.drawable.msg_delete);
            muteDrawable = new RLottieDrawable(R.raw.media_mute_unmute, "media_mute_unmute", AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
           // muteDrawable = new ReplaceableIconDrawable(context);
            noSoundDrawable = new RLottieDrawable(R.raw.media_mute_unmute, "media_mute_unmute", AndroidUtilities.dp(28), AndroidUtilities.dp(28), true, null);
            noSoundDrawable.setCurrentFrame(20, false, true);
            noSoundDrawable.stop();
          //  muteDrawable = new CrossOutDrawable(context, R.drawable.msg_unmute, -1);
            //muteDrawable.setOffsets(-AndroidUtilities.dp(1), AndroidUtilities.dp(0), -AndroidUtilities.dp(1));
            barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            barPaint.setColor(0x55ffffff);
            selectedBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            selectedBarPaint.setColor(0xffffffff);

            int gradientColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f));
            topOverlayGradient = ContextCompat.getDrawable(context, R.drawable.shadow_story_top);
            bottomOverlayGradient = ContextCompat.getDrawable(context, R.drawable.shadow_story_bottom);//new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, gradientColor});
          //  bottomOverlayGradient.setShape(GradientDrawable.RECTANGLE);

            gradientBackgroundPaint = new Paint();
            gradientBackgroundPaint.setColor(gradientColor);

            imageBackgroundDrawable = new ColorDrawable(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.1f));
        }

        public void setIconMuted(boolean muted, boolean animated) {
            if (!animated) {
                muteDrawable.setCurrentFrame(muted ? 20 : 0, false);
                muteDrawable.setCustomEndFrame(muted ? 20 : 0);
                return;
            }
            if (muted) {
                if (muteDrawable.getCurrentFrame() > 20) {
                    muteDrawable.setCurrentFrame(0, false);
                }
                muteDrawable.setCustomEndFrame(20);
                muteDrawable.start();
            } else {
                if (muteDrawable.getCurrentFrame() == 0 || muteDrawable.getCurrentFrame() >= 43) {
                    return;
                }
                muteDrawable.setCustomEndFrame(43);
                muteDrawable.start();
            }
        }
    }

    private boolean editedPrivacy;
    private void editPrivacy(StoryPrivacyBottomSheet.StoryPrivacy currentPrivacy, TL_stories.StoryItem storyItem) {
        delegate.showDialog(new StoryPrivacyBottomSheet(getContext(), storyItem.pinned ? Integer.MAX_VALUE : storyItem.expire_date - storyItem.date, resourcesProvider)
            .setValue(currentPrivacy)
            .enableSharing(false)
//            .allowSmallChats(false)
            .isEdit(true)
            .whenSelectedRules((privacy, a, b, sendAs, whenDone) -> {
                TL_stories.TL_stories_editStory editStory = new TL_stories.TL_stories_editStory();
                editStory.peer = MessagesController.getInstance(currentAccount).getInputPeer(storyItem.dialogId);
                editStory.id = storyItem.id;
                editStory.flags |= 4;
                editStory.privacy_rules = privacy.rules;
                ConnectionsManager.getInstance(currentAccount).sendRequest(editStory, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (whenDone != null) {
                        whenDone.run();
                    }
                    if (error == null || "STORY_NOT_MODIFIED".equals(error.text)) {

                        storyItem.parsedPrivacy = privacy;
                        storyItem.privacy = privacy.toValue();
                        storyItem.close_friends = privacy.type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS;
                        storyItem.contacts = privacy.type == StoryPrivacyBottomSheet.TYPE_CONTACTS;
                        storyItem.selected_contacts = privacy.type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS;
                        MessagesController.getInstance(currentAccount).getStoriesController().updateStoryItem(storyItem.dialogId, storyItem);
                        editedPrivacy = true;

                        if (privacy.type == StoryPrivacyBottomSheet.TYPE_EVERYONE) {
                            BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("StorySharedToEveryone")).show();
                        } else if (privacy.type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS) {
                            BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("StorySharedToCloseFriends")).show();
                        } else if (privacy.type == StoryPrivacyBottomSheet.TYPE_CONTACTS) {
                            if (privacy.selectedUserIds.isEmpty()) {
                                BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check, LocaleController.getString("StorySharedToAllContacts")).show();
                            } else {
                                BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check, LocaleController.formatPluralString("StorySharedToAllContactsExcluded", privacy.selectedUserIds.size())).show();
                            }
                        } else if (privacy.type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS) {
                            HashSet<Long> userIds = new HashSet<>();
                            userIds.addAll(privacy.selectedUserIds);
                            for (ArrayList<Long> ids : privacy.selectedUserIdsByGroup.values()) {
                                userIds.addAll(ids);
                            }
                            BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.contact_check, LocaleController.formatPluralString("StorySharedToContacts", userIds.size())).show();
                        }
                    } else {
                        BulletinFactory.of(storyContainer, resourcesProvider).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.UnknownError)).show();
                    }

                    updatePosition();
                }));
            }, false));
    }

    public boolean checkRecordLocked(boolean forceCloseOnDiscard) {
        if (chatActivityEnterView != null && chatActivityEnterView.isRecordLocked()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
            if (chatActivityEnterView.isInVideoMode()) {
                builder.setTitle(LocaleController.getString(R.string.DiscardVideoMessageTitle));
                builder.setMessage(LocaleController.getString(R.string.DiscardVideoMessageDescription));
            } else {
                builder.setTitle(LocaleController.getString(R.string.DiscardVoiceMessageTitle));
                builder.setMessage(LocaleController.getString(R.string.DiscardVoiceMessageDescription));
            }
            builder.setPositiveButton(LocaleController.getString(R.string.DiscardVoiceMessageAction), (dialog, which) -> {
                if (chatActivityEnterView != null) {
                    if (forceCloseOnDiscard) {
                        storyViewer.close(true);
                    } else {
                        chatActivityEnterView.cancelRecordingAudioVideo();
                    }
                }
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Continue), null);
            delegate.showDialog(builder.create());
            return true;
        }
        return false;
    }

    private ValueAnimator outAnimator;
    private float outT;
    public void animateOut(boolean out) {
        if (outAnimator != null) {
            outAnimator.cancel();
        }
        outAnimator = ValueAnimator.ofFloat(outT, out ? 1 : 0);
        outAnimator.addUpdateListener(anm -> {
            outT = (float) anm.getAnimatedValue();
            headerView.setTranslationY(-AndroidUtilities.dp(8) * outT);
            headerView.setAlpha(1f - outT);
            optionsIconView.setTranslationY(-AndroidUtilities.dp(8) * outT);
            optionsIconView.setAlpha(1f - outT);
            muteIconContainer.setTranslationY(-AndroidUtilities.dp(8) * outT);
            muteIconContainer.setAlpha(muteIconViewAlpha * (1f - outT));
            if (selfView != null) {
                selfView.setTranslationY(AndroidUtilities.dp(8) * outT);
                selfView.setAlpha(1f - outT);
            }
            if (privacyButton != null) {
                privacyButton.setTranslationY(-AndroidUtilities.dp(8) * outT);
                privacyButton.setAlpha(1f - outT);
            }
            storyCaptionView.setAlpha(1f - outT);
            final float progressToDismissLocal = delegate == null ? 0 : delegate.getProgressToDismiss();
            final float hideInterfaceAlpha = getHideInterfaceAlpha();
            if (likeButtonContainer != null) {
                likeButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
            }
            if (shareButton != null) {
                shareButton.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
            }
            if (repostButtonContainer != null) {
                repostButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
            }
            if (chatActivityEnterView != null) {
                chatActivityEnterView.setAlpha(1f - outT);
                invalidate();
            }
            storyContainer.invalidate();
        });
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                outT = out ? 1 : 0;
                headerView.setTranslationY(-AndroidUtilities.dp(8) * outT);
                headerView.setAlpha(1f - outT);
                optionsIconView.setTranslationY(-AndroidUtilities.dp(8) * outT);
                optionsIconView.setAlpha(1f - outT);
                muteIconContainer.setTranslationY(-AndroidUtilities.dp(8) * outT);
                muteIconContainer.setAlpha(muteIconViewAlpha * (1f - outT));
                if (selfView != null) {
                    selfView.setTranslationY(AndroidUtilities.dp(8) * outT);
                    selfView.setAlpha(1f - outT);
                }
                if (privacyButton != null) {
                    privacyButton.setTranslationY(-AndroidUtilities.dp(8) * outT);
                    privacyButton.setAlpha(1f - outT);
                }
                storyCaptionView.setAlpha(1f - outT);
                final float progressToDismissLocal = delegate == null ? 0 : delegate.getProgressToDismiss();
                final float hideInterfaceAlpha = getHideInterfaceAlpha();
                if (likeButtonContainer != null) {
                    likeButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
                }
                if (shareButton != null) {
                    shareButton.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
                }
                if (repostButtonContainer != null) {
                    repostButtonContainer.setAlpha(hideInterfaceAlpha * (1f - progressToDismissLocal) * (1f - outT));
                }
                if (chatActivityEnterView != null) {
                    chatActivityEnterView.setAlpha(1f - outT);
                    invalidate();
                }
                storyContainer.invalidate();
            }
        });
        outAnimator.setDuration(420);
        outAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        outAnimator.start();
    }

    public int getListPosition() {
        return listPosition;
    }
}
