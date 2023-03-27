package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.PaymentFormActivity;

import java.util.ArrayList;

@SuppressWarnings("FieldCanBeLocal")
public class UndoView extends FrameLayout {

    private TextView infoTextView;
    private TextView subinfoTextView;
    private TextView undoTextView;
    private ImageView undoImageView;
    private RLottieImageView leftImageView;
    private BackupImageView avatarImageView;
    private LinearLayout undoButton;
    private int undoViewHeight;

    private BaseFragment parentFragment;

    private Object currentInfoObject;

    private int currentAccount = UserConfig.selectedAccount;

    private TextPaint textPaint;
    private Paint progressPaint;
    private RectF rect;

    private long timeLeft;
    private int prevSeconds;
    private String timeLeftString;
    private int textWidth;

    private int currentAction = -1;
    private ArrayList<Long> currentDialogIds;
    private Runnable currentActionRunnable;
    private Runnable currentCancelRunnable;

    private long lastUpdateTime;

    private float additionalTranslationY;

    private boolean isShown;

    private boolean fromTop;

    public final static int ACTION_CLEAR = 0;
    public final static int ACTION_DELETE = 1;
    public final static int ACTION_ARCHIVE = 2;
    public final static int ACTION_ARCHIVE_HINT = 3;
    public final static int ACTION_ARCHIVE_FEW = 4;
    public final static int ACTION_ARCHIVE_FEW_HINT = 5;
    public final static int ACTION_ARCHIVE_HIDDEN = 6;
    public final static int ACTION_ARCHIVE_PINNED = 7;
    public final static int ACTION_CONTACT_ADDED = 8;
    public final static int ACTION_OWNER_TRANSFERED_CHANNEL = 9;
    public final static int ACTION_OWNER_TRANSFERED_GROUP = 10;
    public final static int ACTION_QR_SESSION_ACCEPTED = 11;
    public final static int ACTION_THEME_CHANGED = 12;
    public final static int ACTION_QUIZ_CORRECT = 13;
    public final static int ACTION_QUIZ_INCORRECT = 14;
    public final static int ACTION_FILTERS_AVAILABLE = 15;
    public final static int ACTION_DICE_INFO = 16;
    public final static int ACTION_DICE_NO_SEND_INFO = 17;
    public final static int ACTION_TEXT_INFO = 18;
    public final static int ACTION_CACHE_WAS_CLEARED = 19;
    public final static int ACTION_ADDED_TO_FOLDER = 20;
    public final static int ACTION_REMOVED_FROM_FOLDER = 21;
    public final static int ACTION_PROFILE_PHOTO_CHANGED = 22;
    public final static int ACTION_CHAT_UNARCHIVED = 23;
    public final static int ACTION_PROXIMITY_SET = 24;
    public final static int ACTION_PROXIMITY_REMOVED = 25;
    public final static int ACTION_CLEAR_FEW = 26;
    public final static int ACTION_DELETE_FEW = 27;

    public final static int ACTION_VOIP_MUTED = 30;
    public final static int ACTION_VOIP_UNMUTED = 31;
    public final static int ACTION_VOIP_REMOVED = 32;
    public final static int ACTION_VOIP_LINK_COPIED = 33;
    public final static int ACTION_VOIP_INVITED = 34;
    public final static int ACTION_VOIP_MUTED_FOR_YOU = 35;
    public final static int ACTION_VOIP_UNMUTED_FOR_YOU = 36;
    public final static int ACTION_VOIP_USER_CHANGED = 37;
    public final static int ACTION_VOIP_CAN_NOW_SPEAK = 38;
    public final static int ACTION_VOIP_RECORDING_STARTED = 39;
    public final static int ACTION_VOIP_RECORDING_FINISHED = 40;
    public final static int ACTION_VOIP_INVITE_LINK_SENT = 41;
    public final static int ACTION_VOIP_SOUND_MUTED = 42;
    public final static int ACTION_VOIP_SOUND_UNMUTED = 43;
    public final static int ACTION_VOIP_USER_JOINED = 44;
    public final static int ACTION_VOIP_VIDEO_RECORDING_STARTED = 100;
    public final static int ACTION_VOIP_VIDEO_RECORDING_FINISHED = 101;

    public final static int ACTION_IMPORT_NOT_MUTUAL = 45;
    public final static int ACTION_IMPORT_GROUP_NOT_ADMIN = 46;
    public final static int ACTION_IMPORT_INFO = 47;

    public final static int ACTION_MESSAGE_COPIED = 52;
    public final static int ACTION_FWD_MESSAGES = 53;
    public final static int ACTION_NOTIFY_ON = 54;
    public final static int ACTION_NOTIFY_OFF = 55;
    public final static int ACTION_USERNAME_COPIED = 56;
    public final static int ACTION_HASHTAG_COPIED = 57;
    public final static int ACTION_TEXT_COPIED = 58;
    public final static int ACTION_LINK_COPIED = 59;
    public final static int ACTION_PHONE_COPIED = 60;
    public final static int ACTION_SHARE_BACKGROUND = 61;

    public final static int ACTION_AUTO_DELETE_ON = 70;
    public final static int ACTION_AUTO_DELETE_OFF = 71;
    public final static int ACTION_REPORT_SENT = 74;
    public final static int ACTION_GIGAGROUP_CANCEL = 75;
    public final static int ACTION_GIGAGROUP_SUCCESS = 76;

    public final static int ACTION_PAYMENT_SUCCESS = 77;
    public final static int ACTION_PIN_DIALOGS = 78;
    public final static int ACTION_UNPIN_DIALOGS = 79;
    public final static int ACTION_EMAIL_COPIED = 80;
    public final static int ACTION_CLEAR_DATES = 81;

    public final static int ACTION_PREVIEW_MEDIA_DESELECTED = 82;
    public static int ACTION_RINGTONE_ADDED = 83;
    public final static int ACTION_PREMIUM_TRANSCRIPTION = 84;
    public final static int ACTION_HINT_SWIPE_TO_REPLY = 85;
    public final static int ACTION_PREMIUM_ALL_FOLDER = 86;

    public final static int ACTION_PROXY_ADDED = 87;

    private CharSequence infoText;
    private int hideAnimationType = 1;
    Drawable backgroundDrawable;
    private final Theme.ResourcesProvider resourcesProvider;

    public class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result;
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    CharacterStyle[] links = buffer.getSpans(widget.getSelectionStart(), widget.getSelectionEnd(), CharacterStyle.class);
                    if (links == null || links.length == 0) {
                        return false;
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    CharacterStyle[] links = buffer.getSpans(widget.getSelectionStart(), widget.getSelectionEnd(), CharacterStyle.class);
                    if (links != null && links.length > 0) {
                        didPressUrl(links[0]);
                    }
                    Selection.removeSelection(buffer);
                    result = true;
                } else {
                    result = super.onTouchEvent(widget, buffer, event);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    public UndoView(Context context) {
        this(context, null, false, null);
    }

    public UndoView(Context context, BaseFragment parent) {
        this(context, parent, false, null);
    }

    public UndoView(Context context, BaseFragment parent, boolean top, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        parentFragment = parent;
        fromTop = top;

        infoTextView = new TextView(context);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTextView.setTextColor(getThemedColor(Theme.key_undo_infoColor));
        infoTextView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));
        infoTextView.setMovementMethod(new LinkMovementMethodMy());
        addView(infoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 45, 13, 0, 0));

        subinfoTextView = new TextView(context);
        subinfoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subinfoTextView.setTextColor(getThemedColor(Theme.key_undo_infoColor));
        subinfoTextView.setLinkTextColor(getThemedColor(Theme.key_undo_cancelColor));
        subinfoTextView.setHighlightColor(0);
        subinfoTextView.setSingleLine(true);
        subinfoTextView.setEllipsize(TextUtils.TruncateAt.END);
        subinfoTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        addView(subinfoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 58, 27, 8, 0));

        leftImageView = new RLottieImageView(context);
        leftImageView.setScaleType(ImageView.ScaleType.CENTER);
        leftImageView.setLayerColor("info1.**", getThemedColor(Theme.key_undo_background) | 0xff000000);
        leftImageView.setLayerColor("info2.**", getThemedColor(Theme.key_undo_background) | 0xff000000);
        leftImageView.setLayerColor("luc12.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc11.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc10.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc9.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc8.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc7.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc6.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc5.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc4.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc3.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc2.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("luc1.**", getThemedColor(Theme.key_undo_infoColor));
        leftImageView.setLayerColor("Oval.**", getThemedColor(Theme.key_undo_infoColor));
        addView(leftImageView, LayoutHelper.createFrame(54, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 3, 0, 0, 0));

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(15));
        addView(avatarImageView, LayoutHelper.createFrame(30, 30, Gravity.CENTER_VERTICAL | Gravity.LEFT, 15, 0, 0, 0));

        undoButton = new LinearLayout(context);
        undoButton.setOrientation(LinearLayout.HORIZONTAL);
        undoButton.setBackground(Theme.createRadSelectorDrawable(getThemedColor(Theme.key_undo_cancelColor) & 0x22ffffff, AndroidUtilities.dp(2), AndroidUtilities.dp(2)));
        addView(undoButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 11, 0));
        undoButton.setOnClickListener(v -> {
            if (!canUndo()) {
                return;
            }
            hide(false, 1);
        });

        undoImageView = new ImageView(context);
        undoImageView.setImageResource(R.drawable.chats_undo);
        undoImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_undo_cancelColor), PorterDuff.Mode.MULTIPLY));
        undoButton.addView(undoImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 4, 4, 0, 4));

        undoTextView = new TextView(context);
        undoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        undoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        undoTextView.setTextColor(getThemedColor(Theme.key_undo_cancelColor));
        undoTextView.setText(LocaleController.getString("Undo", R.string.Undo));
        undoButton.addView(undoTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 6, 4, 8, 4));

        rect = new RectF(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15 + 18), AndroidUtilities.dp(15 + 18));

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(getThemedColor(Theme.key_undo_infoColor));

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setColor(getThemedColor(Theme.key_undo_infoColor));

        setWillNotDraw(false);
        backgroundDrawable = Theme.createRoundRectDrawable(AndroidUtilities.dp(10), getThemedColor(Theme.key_undo_background));

        setOnTouchListener((v, event) -> true);

        setVisibility(INVISIBLE);
    }

    public void setColors(int background, int text) {
        Theme.setDrawableColor(backgroundDrawable, background);
        infoTextView.setTextColor(text);
        subinfoTextView.setTextColor(text);
        leftImageView.setLayerColor("info1.**", background | 0xff000000);
        leftImageView.setLayerColor("info2.**", background | 0xff000000);
    }

    private boolean isTooltipAction() {
        return currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT ||
                currentAction == ACTION_ARCHIVE_PINNED || currentAction == ACTION_CONTACT_ADDED || currentAction == ACTION_PROXY_ADDED || currentAction == ACTION_OWNER_TRANSFERED_CHANNEL ||
                currentAction == ACTION_OWNER_TRANSFERED_GROUP || currentAction == ACTION_QUIZ_CORRECT || currentAction == ACTION_QUIZ_INCORRECT || currentAction == ACTION_CACHE_WAS_CLEARED ||
                currentAction == ACTION_ADDED_TO_FOLDER || currentAction == ACTION_REMOVED_FROM_FOLDER || currentAction == ACTION_PROFILE_PHOTO_CHANGED ||
                currentAction == ACTION_CHAT_UNARCHIVED || currentAction == ACTION_VOIP_MUTED || currentAction == ACTION_VOIP_UNMUTED || currentAction == ACTION_VOIP_REMOVED ||
                currentAction == ACTION_VOIP_LINK_COPIED || currentAction == ACTION_VOIP_INVITED || currentAction == ACTION_VOIP_MUTED_FOR_YOU || currentAction == ACTION_VOIP_UNMUTED_FOR_YOU ||
                currentAction == ACTION_REPORT_SENT || currentAction == ACTION_VOIP_USER_CHANGED || currentAction == ACTION_VOIP_CAN_NOW_SPEAK || currentAction == ACTION_VOIP_RECORDING_STARTED ||
                currentAction == ACTION_VOIP_RECORDING_FINISHED || currentAction == ACTION_VOIP_SOUND_MUTED || currentAction == ACTION_VOIP_SOUND_UNMUTED || currentAction == ACTION_PAYMENT_SUCCESS ||
                currentAction == ACTION_VOIP_USER_JOINED || currentAction == ACTION_PIN_DIALOGS || currentAction == ACTION_UNPIN_DIALOGS || currentAction == ACTION_VOIP_VIDEO_RECORDING_STARTED ||
                currentAction == ACTION_VOIP_VIDEO_RECORDING_FINISHED || currentAction == ACTION_RINGTONE_ADDED;
    }

    private boolean hasSubInfo() {
        return currentAction == ACTION_QR_SESSION_ACCEPTED || currentAction == ACTION_PROXIMITY_SET || currentAction == ACTION_ARCHIVE_HIDDEN || currentAction == ACTION_ARCHIVE_HINT || currentAction == ACTION_ARCHIVE_FEW_HINT ||
                currentAction == ACTION_QUIZ_CORRECT || currentAction == ACTION_QUIZ_INCORRECT ||
                currentAction == ACTION_REPORT_SENT || currentAction == ACTION_ARCHIVE_PINNED && MessagesController.getInstance(currentAccount).dialogFilters.isEmpty() || currentAction == ACTION_RINGTONE_ADDED || currentAction == ACTION_HINT_SWIPE_TO_REPLY;
    }

    public boolean isMultilineSubInfo() {
        return currentAction == ACTION_THEME_CHANGED || currentAction == ACTION_FILTERS_AVAILABLE || currentAction == ACTION_PROXIMITY_SET || currentAction == ACTION_REPORT_SENT
                || currentAction == ACTION_RINGTONE_ADDED;
    }

    public void setAdditionalTranslationY(float value) {
        if (additionalTranslationY != value) {
            additionalTranslationY = value;
            updatePosition();
        }
    }

    public Object getCurrentInfoObject() {
        return currentInfoObject;
    }

    public void hide(boolean apply, int animated) {
        if (getVisibility() != VISIBLE || !isShown) {
            return;
        }
        currentInfoObject = null;
        isShown = false;
        if (currentActionRunnable != null) {
            if (apply) {
                currentActionRunnable.run();
            }
            currentActionRunnable = null;
        }
        if (currentCancelRunnable != null) {
            if (!apply) {
                currentCancelRunnable.run();
            }
            currentCancelRunnable = null;
        }
        if (currentAction == ACTION_CLEAR || currentAction == ACTION_DELETE || currentAction == ACTION_CLEAR_FEW || currentAction == ACTION_DELETE_FEW) {
            for (int a = 0; a < currentDialogIds.size(); a++) {
                long did = currentDialogIds.get(a);
                MessagesController.getInstance(currentAccount).removeDialogAction(did, currentAction == ACTION_CLEAR || currentAction == ACTION_CLEAR_FEW, apply);
                onRemoveDialogAction(did, currentAction);
            }
        }
        if (animated != 0) {
            AnimatorSet animatorSet = new AnimatorSet();
            if (animated == 1) {
                animatorSet.playTogether(ObjectAnimator.ofFloat(this, "enterOffset", (fromTop ? -1.0f : 1.0f) * (enterOffsetMargin + undoViewHeight)));
                animatorSet.setDuration(250);
            } else {
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.8f),
                        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.8f),
                        ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f));
                animatorSet.setDuration(180);
            }
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setVisibility(INVISIBLE);
                    setScaleX(1.0f);
                    setScaleY(1.0f);
                    setAlpha(1.0f);
                }
            });
            animatorSet.start();
        } else {
            setEnterOffset((fromTop ? -1.0f : 1.0f) * (enterOffsetMargin + undoViewHeight));
            setVisibility(INVISIBLE);
        }
    }

    protected void onRemoveDialogAction(long currentDialogId, int action) {

    }

    public void didPressUrl(CharacterStyle span) {

    }

    public void showWithAction(long did, int action, Runnable actionRunnable) {
        showWithAction(did, action, null, null, actionRunnable, null);
    }

    public void showWithAction(long did, int action, Object infoObject) {
        showWithAction(did, action, infoObject, null, null, null);
    }

    public void showWithAction(long did, int action, Runnable actionRunnable, Runnable cancelRunnable) {
        showWithAction(did, action, null, null, actionRunnable, cancelRunnable);
    }

    public void showWithAction(long did, int action, Object infoObject, Runnable actionRunnable, Runnable cancelRunnable) {
        showWithAction(did, action, infoObject, null, actionRunnable, cancelRunnable);
    }

    public void showWithAction(long did, int action, Object infoObject, Object infoObject2, Runnable actionRunnable, Runnable cancelRunnable) {
        ArrayList<Long> ids = new ArrayList<>();
        ids.add(did);
        showWithAction(ids, action, infoObject, infoObject2, actionRunnable, cancelRunnable);
    }

    public void showWithAction(ArrayList<Long> dialogIds, int action, Object infoObject, Object infoObject2, Runnable actionRunnable, Runnable cancelRunnable) {
        if (!AndroidUtilities.shouldShowClipboardToast() && (currentAction == ACTION_MESSAGE_COPIED || currentAction == ACTION_USERNAME_COPIED || currentAction == ACTION_HASHTAG_COPIED || currentAction == ACTION_TEXT_COPIED || currentAction == ACTION_LINK_COPIED || currentAction == ACTION_PHONE_COPIED || currentAction == ACTION_EMAIL_COPIED || currentAction == ACTION_VOIP_LINK_COPIED)) {
            return;
        }
        if (currentActionRunnable != null) {
            currentActionRunnable.run();
        }
        isShown = true;
        currentActionRunnable = actionRunnable;
        currentCancelRunnable = cancelRunnable;
        currentDialogIds = dialogIds;
        long did = dialogIds.get(0);
        currentAction = action;
        timeLeft = 5000;
        currentInfoObject = infoObject;
        lastUpdateTime = SystemClock.elapsedRealtime();
        undoTextView.setText(LocaleController.getString("Undo", R.string.Undo).toUpperCase());
        undoImageView.setVisibility(VISIBLE);
        leftImageView.setPadding(0, 0, 0, 0);
        leftImageView.setScaleX(1);
        leftImageView.setScaleY(1);
        infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        avatarImageView.setVisibility(GONE);

        infoTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) infoTextView.getLayoutParams();
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.topMargin = AndroidUtilities.dp(13);
        layoutParams.bottomMargin = 0;

        leftImageView.setScaleType(ImageView.ScaleType.CENTER);
        FrameLayout.LayoutParams layoutParams2 = (FrameLayout.LayoutParams) leftImageView.getLayoutParams();
        layoutParams2.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        layoutParams2.topMargin = layoutParams2.bottomMargin = 0;
        layoutParams2.leftMargin = AndroidUtilities.dp(3);
        layoutParams2.width = AndroidUtilities.dp(54);
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;

        infoTextView.setMinHeight(0);
        boolean infoOnly = false;
        boolean reversedPlay = false;
        int reversedPlayEndFrame = 0;
        if ((actionRunnable == null && cancelRunnable == null) || action == ACTION_RINGTONE_ADDED) {
            setOnClickListener(view -> hide(false, 1));
            setOnTouchListener(null);
        } else {
            setOnClickListener(null);
            setOnTouchListener((v, event) -> true);
        }

        infoTextView.setMovementMethod(null);

        if (isTooltipAction()) {
            CharSequence infoText;
            CharSequence subInfoText;
            @DrawableRes
            int icon;
            int size = 36;
            boolean iconIsDrawable = false;

            if (action == ACTION_RINGTONE_ADDED) {
                subinfoTextView.setSingleLine(false);
                infoText = LocaleController.getString("SoundAdded", R.string.SoundAdded);
                subInfoText = AndroidUtilities.replaceSingleTag(LocaleController.getString("SoundAddedSubtitle", R.string.SoundAddedSubtitle), actionRunnable);
                currentActionRunnable = null;
                icon = R.raw.sound_download;
                timeLeft = 4000;
            } else if (action == ACTION_REPORT_SENT) {
                subinfoTextView.setSingleLine(false);
                infoText = LocaleController.getString("ReportChatSent", R.string.ReportChatSent);
                subInfoText = LocaleController.formatString("ReportSentInfo", R.string.ReportSentInfo);
                icon = R.raw.ic_admin;
                timeLeft = 4000;
            } else if (action == ACTION_VOIP_INVITED) {
                TLRPC.User user = (TLRPC.User) infoObject;
                TLRPC.Chat chat = (TLRPC.Chat) infoObject2;
                if (ChatObject.isChannelOrGiga(chat)) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChannelInvitedUser", R.string.VoipChannelInvitedUser, UserObject.getFirstName(user)));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupInvitedUser", R.string.VoipGroupInvitedUser, UserObject.getFirstName(user)));
                }
                subInfoText = null;
                icon = 0;
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setTextSize(AndroidUtilities.dp(12));
                avatarDrawable.setInfo(user);
                avatarImageView.setForUserOrChat(user, avatarDrawable);
                avatarImageView.setVisibility(VISIBLE);
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_USER_JOINED) {
                TLRPC.Chat currentChat = (TLRPC.Chat) infoObject2;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChannelUserJoined", R.string.VoipChannelUserJoined, UserObject.getFirstName(user)));
                    } else {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChatUserJoined", R.string.VoipChatUserJoined, UserObject.getFirstName(user)));
                    }
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    if (ChatObject.isChannelOrGiga(currentChat)) {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChannelChatJoined", R.string.VoipChannelChatJoined, chat.title));
                    } else {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChatChatJoined", R.string.VoipChatChatJoined, chat.title));
                    }
                }
                subInfoText = null;
                icon = 0;
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setTextSize(AndroidUtilities.dp(12));
                avatarDrawable.setInfo((TLObject) infoObject);
                avatarImageView.setForUserOrChat((TLObject) infoObject, avatarDrawable);
                avatarImageView.setVisibility(VISIBLE);
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_USER_CHANGED) {
                AvatarDrawable avatarDrawable = new AvatarDrawable();
                avatarDrawable.setTextSize(AndroidUtilities.dp(12));
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    avatarDrawable.setInfo(user);
                    avatarImageView.setForUserOrChat(user, avatarDrawable);
                    name = ContactsController.formatName(user.first_name, user.last_name);
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    avatarDrawable.setInfo(chat);
                    avatarImageView.setForUserOrChat(chat, avatarDrawable);
                    name = chat.title;
                }
                TLRPC.Chat currentChat = (TLRPC.Chat) infoObject2;
                if (ChatObject.isChannelOrGiga(currentChat)) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipChannelUserChanged", R.string.VoipChannelUserChanged, name));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupUserChanged", R.string.VoipGroupUserChanged, name));
                }
                subInfoText = null;
                icon = 0;
                avatarImageView.setVisibility(VISIBLE);
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_LINK_COPIED) {
                infoText = LocaleController.getString("VoipGroupCopyInviteLinkCopied", R.string.VoipGroupCopyInviteLinkCopied);
                subInfoText = null;
                icon = R.raw.voip_invite;
                timeLeft = 3000;
            } else if (action == ACTION_PAYMENT_SUCCESS) {
                infoText = (CharSequence) infoObject;
                subInfoText = null;
                icon = R.raw.payment_success;
                timeLeft = 5000;
                if (parentFragment != null && infoObject2 instanceof TLRPC.Message) {
                    TLRPC.Message message = (TLRPC.Message) infoObject2;
                    setOnTouchListener(null);
                    infoTextView.setMovementMethod(null);
                    setOnClickListener(v -> {
                        hide(true, 1);
                        TLRPC.TL_payments_getPaymentReceipt req = new TLRPC.TL_payments_getPaymentReceipt();
                        req.msg_id = message.id;
                        req.peer = parentFragment.getMessagesController().getInputPeer(message.peer_id);
                        parentFragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response instanceof TLRPC.TL_payments_paymentReceipt) {
                                parentFragment.presentFragment(new PaymentFormActivity((TLRPC.TL_payments_paymentReceipt) response));
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors);
                    });
                }
            } else if (action == ACTION_VOIP_MUTED) {
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    name = UserObject.getFirstName(user);
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    name = chat.title;
                }
                infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupUserCantNowSpeak", R.string.VoipGroupUserCantNowSpeak, name));
                subInfoText = null;
                icon = R.raw.voip_muted;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_MUTED_FOR_YOU) {
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    name = UserObject.getFirstName(user);
                } else if (infoObject instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    name = chat.title;
                } else {
                    name = "";
                }
                infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupUserCantNowSpeakForYou", R.string.VoipGroupUserCantNowSpeakForYou, name));
                subInfoText = null;
                icon = R.raw.voip_muted;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_UNMUTED) {
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    name = UserObject.getFirstName(user);
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    name = chat.title;
                }
                infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupUserCanNowSpeak", R.string.VoipGroupUserCanNowSpeak, name));
                subInfoText = null;
                icon = R.raw.voip_unmuted;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_CAN_NOW_SPEAK) {
                if (infoObject instanceof TLRPC.Chat) {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupYouCanNowSpeakIn", R.string.VoipGroupYouCanNowSpeakIn, chat.title));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.getString("VoipGroupYouCanNowSpeak", R.string.VoipGroupYouCanNowSpeak));
                }
                subInfoText = null;
                icon = R.raw.voip_allow_talk;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_SOUND_MUTED) {
                TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                if (ChatObject.isChannelOrGiga(chat)) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.getString("VoipChannelSoundMuted", R.string.VoipChannelSoundMuted));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.getString("VoipGroupSoundMuted", R.string.VoipGroupSoundMuted));
                }
                subInfoText = null;
                icon = R.raw.ic_mute;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_SOUND_UNMUTED) {
                TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                if (ChatObject.isChannelOrGiga(chat)) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.getString("VoipChannelSoundUnmuted", R.string.VoipChannelSoundUnmuted));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.getString("VoipGroupSoundUnmuted", R.string.VoipGroupSoundUnmuted));
                }
                subInfoText = null;
                icon = R.raw.ic_unmute;
                timeLeft = 3000;
            } else if (currentAction == ACTION_VOIP_RECORDING_STARTED || currentAction == ACTION_VOIP_VIDEO_RECORDING_STARTED) {
                infoText = AndroidUtilities.replaceTags(currentAction == ACTION_VOIP_RECORDING_STARTED ? LocaleController.getString("VoipGroupAudioRecordStarted", R.string.VoipGroupAudioRecordStarted) : LocaleController.getString("VoipGroupVideoRecordStarted", R.string.VoipGroupVideoRecordStarted));
                subInfoText = null;
                icon = R.raw.voip_record_start;
                timeLeft = 3000;
            } else if (currentAction == ACTION_VOIP_RECORDING_FINISHED || currentAction == ACTION_VOIP_VIDEO_RECORDING_FINISHED) {
                String text = currentAction == ACTION_VOIP_RECORDING_FINISHED ? LocaleController.getString("VoipGroupAudioRecordSaved", R.string.VoipGroupAudioRecordSaved) : LocaleController.getString("VoipGroupVideoRecordSaved", R.string.VoipGroupVideoRecordSaved);
                subInfoText = null;
                icon = R.raw.voip_record_saved;
                timeLeft = 4000;
                infoTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                SpannableStringBuilder builder = new SpannableStringBuilder(text);
                int index1 = text.indexOf("**");
                int index2 = text.lastIndexOf("**");
                if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                    builder.replace(index2, index2 + 2, "");
                    builder.replace(index1, index1 + 2, "");
                    try {
                        builder.setSpan(new URLSpanNoUnderline("tg://openmessage?user_id=" + UserConfig.getInstance(currentAccount).getClientUserId()), index1, index2 - 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                infoText = builder;
            } else if (action == ACTION_VOIP_UNMUTED_FOR_YOU) {
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    name = UserObject.getFirstName(user);
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    name = chat.title;
                }
                infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupUserCanNowSpeakForYou", R.string.VoipGroupUserCanNowSpeakForYou, name));
                subInfoText = null;
                icon = R.raw.voip_unmuted;
                timeLeft = 3000;
            } else if (action == ACTION_VOIP_REMOVED) {
                String name;
                if (infoObject instanceof TLRPC.User) {
                    TLRPC.User user = (TLRPC.User) infoObject;
                    name = UserObject.getFirstName(user);
                } else {
                    TLRPC.Chat chat = (TLRPC.Chat) infoObject;
                    name = chat.title;
                }
                infoText = AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupRemovedFromGroup", R.string.VoipGroupRemovedFromGroup, name));
                subInfoText = null;
                icon = R.raw.voip_group_removed;
                timeLeft = 3000;
            } else if (action == ACTION_OWNER_TRANSFERED_CHANNEL || action == ACTION_OWNER_TRANSFERED_GROUP) {
                TLRPC.User user = (TLRPC.User) infoObject;
                if (action == ACTION_OWNER_TRANSFERED_CHANNEL) {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferChannelToast", R.string.EditAdminTransferChannelToast, UserObject.getFirstName(user)));
                } else {
                    infoText = AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferGroupToast", R.string.EditAdminTransferGroupToast, UserObject.getFirstName(user)));
                }
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_CONTACT_ADDED) {
                TLRPC.User user = (TLRPC.User) infoObject;
                infoText = LocaleController.formatString("NowInContacts", R.string.NowInContacts, UserObject.getFirstName(user));
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_PROXY_ADDED) {
                infoText = LocaleController.formatString(R.string.ProxyAddedSuccess);
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_PROFILE_PHOTO_CHANGED) {
                if (DialogObject.isUserDialog(did)) {
                    if (infoObject == null) {
                        infoText = LocaleController.getString("MainProfilePhotoSetHint", R.string.MainProfilePhotoSetHint);
                    } else {
                        infoText = LocaleController.getString("MainProfileVideoSetHint", R.string.MainProfileVideoSetHint);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        if (infoObject == null) {
                            infoText = LocaleController.getString("MainChannelProfilePhotoSetHint", R.string.MainChannelProfilePhotoSetHint);
                        } else {
                            infoText = LocaleController.getString("MainChannelProfileVideoSetHint", R.string.MainChannelProfileVideoSetHint);
                        }
                    } else {
                        if (infoObject == null) {
                            infoText = LocaleController.getString("MainGroupProfilePhotoSetHint", R.string.MainGroupProfilePhotoSetHint);
                        } else {
                            infoText = LocaleController.getString("MainGroupProfileVideoSetHint", R.string.MainGroupProfileVideoSetHint);
                        }
                    }
                }
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_CHAT_UNARCHIVED) {
                infoText = LocaleController.getString("ChatWasMovedToMainList", R.string.ChatWasMovedToMainList);
                subInfoText = null;
                icon = R.raw.contact_check;
            } else if (action == ACTION_ARCHIVE_HIDDEN) {
                infoText = LocaleController.getString("ArchiveHidden", R.string.ArchiveHidden);
                subInfoText = LocaleController.getString("ArchiveHiddenInfo", R.string.ArchiveHiddenInfo);
                icon = R.raw.chats_swipearchive;
                size = 48;
            } else if (currentAction == ACTION_QUIZ_CORRECT) {
                infoText = LocaleController.getString("QuizWellDone", R.string.QuizWellDone);
                subInfoText = LocaleController.getString("QuizWellDoneInfo", R.string.QuizWellDoneInfo);
                icon = R.raw.wallet_congrats;
                size = 44;
            } else if (currentAction == ACTION_QUIZ_INCORRECT) {
                infoText = LocaleController.getString("QuizWrongAnswer", R.string.QuizWrongAnswer);
                subInfoText = LocaleController.getString("QuizWrongAnswerInfo", R.string.QuizWrongAnswerInfo);
                icon = R.raw.wallet_science;
                size = 44;
            } else if (action == ACTION_ARCHIVE_PINNED) {
                infoText = LocaleController.getString("ArchivePinned", R.string.ArchivePinned);
                if (MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                    subInfoText = LocaleController.getString("ArchivePinnedInfo", R.string.ArchivePinnedInfo);
                } else {
                    subInfoText = null;
                }
                icon = R.raw.chats_infotip;
            } else if (action == ACTION_ADDED_TO_FOLDER || action == ACTION_REMOVED_FROM_FOLDER) {
                MessagesController.DialogFilter filter = (MessagesController.DialogFilter) infoObject2;
                if (did != 0) {
                    long dialogId = did;
                    if (DialogObject.isEncryptedDialog(did)) {
                        TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(dialogId));
                        dialogId = encryptedChat.user_id;
                    }
                    if (DialogObject.isUserDialog(dialogId)) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                        if (action == ACTION_ADDED_TO_FOLDER) {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterUserAddedToExisting", R.string.FilterUserAddedToExisting, UserObject.getFirstName(user), filter.name));
                        } else {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterUserRemovedFrom", R.string.FilterUserRemovedFrom, UserObject.getFirstName(user), filter.name));
                        }
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                        if (action == ACTION_ADDED_TO_FOLDER) {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatAddedToExisting", R.string.FilterChatAddedToExisting, chat.title, filter.name));
                        } else {
                            infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatRemovedFrom", R.string.FilterChatRemovedFrom, chat.title, filter.name));
                        }
                    }
                } else {
                    if (action == ACTION_ADDED_TO_FOLDER) {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatsAddedToExisting", R.string.FilterChatsAddedToExisting, LocaleController.formatPluralString("ChatsSelected", (Integer) infoObject), filter.name));
                    } else {
                        infoText = AndroidUtilities.replaceTags(LocaleController.formatString("FilterChatsRemovedFrom", R.string.FilterChatsRemovedFrom, LocaleController.formatPluralString("ChatsSelected", (Integer) infoObject), filter.name));
                    }
                }
                subInfoText = null;
                icon = action == ACTION_ADDED_TO_FOLDER ? R.raw.folder_in : R.raw.folder_out;
            } else if (action == ACTION_CACHE_WAS_CLEARED) {
                infoText = this.infoText;
                subInfoText = null;
                icon = R.raw.ic_delete;
            } else if (action == ACTION_PREVIEW_MEDIA_DESELECTED) {
                MediaController.PhotoEntry photo = (MediaController.PhotoEntry) infoObject;
                infoText = photo.isVideo ? LocaleController.getString("AttachMediaVideoDeselected", R.string.AttachMediaVideoDeselected) : LocaleController.getString("AttachMediaPhotoDeselected", R.string.AttachMediaPhotoDeselected);
                subInfoText = null;
                icon = 0;
            } else if (action == ACTION_PIN_DIALOGS || action == ACTION_UNPIN_DIALOGS) {
                int count = (Integer) infoObject;
                if (action == ACTION_PIN_DIALOGS) {
                    infoText = LocaleController.formatPluralString("PinnedDialogsCount", count);
                } else {
                    infoText = LocaleController.formatPluralString("UnpinnedDialogsCount", count);
                }
                subInfoText = null;
                icon = currentAction == ACTION_PIN_DIALOGS ? R.raw.ic_pin :  R.raw.ic_unpin;
                if (infoObject2 instanceof Integer) {
                    timeLeft = (int) infoObject2;
                }
            } else {
                if (action == ACTION_ARCHIVE_HINT) {
                    infoText = LocaleController.getString("ChatArchived", R.string.ChatArchived);
                } else {
                    infoText = LocaleController.getString("ChatsArchived", R.string.ChatsArchived);
                }
                if (MessagesController.getInstance(currentAccount).dialogFilters.isEmpty()) {
                    subInfoText = LocaleController.getString("ChatArchivedInfo", R.string.ChatArchivedInfo);
                } else {
                    subInfoText = null;
                }
                icon = R.raw.chats_infotip;
            }

            infoTextView.setText(infoText);
            if (icon != 0) {
                if (iconIsDrawable) {
                    leftImageView.setImageResource(icon);
                } else {
                    leftImageView.setAnimation(icon, size, size);
                    RLottieDrawable drawable = leftImageView.getAnimatedDrawable();
                    drawable.setPlayInDirectionOfCustomEndFrame(reversedPlay);
                    drawable.setCustomEndFrame(reversedPlay ? reversedPlayEndFrame : drawable.getFramesCount());
                }
                leftImageView.setVisibility(VISIBLE);
                if (!iconIsDrawable) {
                    leftImageView.setProgress(reversedPlay ? 1 : 0);
                    leftImageView.playAnimation();
                }
            } else {
                leftImageView.setVisibility(GONE);
            }

            if (subInfoText != null) {
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(6);
                layoutParams.rightMargin = AndroidUtilities.dp(8);
                layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
                layoutParams.rightMargin = AndroidUtilities.dp(8);
                subinfoTextView.setText(subInfoText);
                subinfoTextView.setVisibility(VISIBLE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            } else {
                layoutParams.leftMargin = AndroidUtilities.dp(58);
                layoutParams.topMargin = AndroidUtilities.dp(13);
                layoutParams.rightMargin = AndroidUtilities.dp(8);
                subinfoTextView.setVisibility(GONE);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                infoTextView.setTypeface(Typeface.DEFAULT);
            }

            undoButton.setVisibility(GONE);
        } else if (currentAction == ACTION_IMPORT_NOT_MUTUAL || currentAction == ACTION_IMPORT_GROUP_NOT_ADMIN || currentAction == ACTION_IMPORT_INFO ||
                currentAction == ACTION_MESSAGE_COPIED ||
                currentAction == ACTION_FWD_MESSAGES || currentAction == ACTION_NOTIFY_ON || currentAction == ACTION_NOTIFY_OFF ||  currentAction == ACTION_USERNAME_COPIED ||
                currentAction == ACTION_HASHTAG_COPIED || currentAction == ACTION_TEXT_COPIED || currentAction == ACTION_LINK_COPIED || currentAction == ACTION_PHONE_COPIED ||
                currentAction == ACTION_AUTO_DELETE_OFF || currentAction == ACTION_AUTO_DELETE_ON || currentAction == ACTION_GIGAGROUP_CANCEL || currentAction == ACTION_GIGAGROUP_SUCCESS ||
                currentAction == ACTION_VOIP_INVITE_LINK_SENT || currentAction == ACTION_PIN_DIALOGS || currentAction == ACTION_UNPIN_DIALOGS || currentAction == ACTION_SHARE_BACKGROUND || currentAction == ACTION_EMAIL_COPIED) {
            undoImageView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            infoTextView.setTypeface(Typeface.DEFAULT);
            long hapticDelay = -1;
            if (currentAction == ACTION_GIGAGROUP_SUCCESS) {
                infoTextView.setText(LocaleController.getString("BroadcastGroupConvertSuccess", R.string.BroadcastGroupConvertSuccess));
                leftImageView.setAnimation(R.raw.gigagroup_convert, 36, 36);
                infoOnly = true;
                layoutParams.topMargin = AndroidUtilities.dp(9);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            } else if (currentAction == ACTION_GIGAGROUP_CANCEL) {
                infoTextView.setText(LocaleController.getString("GigagroupConvertCancelHint", R.string.GigagroupConvertCancelHint));
                leftImageView.setAnimation(R.raw.chats_infotip, 36, 36);
                infoOnly = true;
                layoutParams.topMargin = AndroidUtilities.dp(9);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            } else if (action == ACTION_AUTO_DELETE_ON) {
                TLRPC.User user = (TLRPC.User) infoObject;
                int ttl = (Integer) infoObject2;
                subinfoTextView.setSingleLine(false);
                String time = LocaleController.formatTTLString(ttl);
                infoTextView.setText(LocaleController.formatString("AutoDeleteHintOnText", R.string.AutoDeleteHintOnText, time));
                leftImageView.setAnimation(R.raw.fire_on, 36, 36);
                layoutParams.topMargin = AndroidUtilities.dp(9);
                timeLeft = 4000;
                infoOnly = true;
                leftImageView.setPadding(0, 0, 0, AndroidUtilities.dp(3));
            } else if (currentAction == ACTION_AUTO_DELETE_OFF) {
                infoTextView.setText(LocaleController.getString("AutoDeleteHintOffText", R.string.AutoDeleteHintOffText));
                leftImageView.setAnimation(R.raw.fire_off, 36, 36);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                timeLeft = 3000;
                leftImageView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
            } else if (currentAction == ACTION_IMPORT_NOT_MUTUAL) {
                infoTextView.setText(LocaleController.getString("ImportMutualError", R.string.ImportMutualError));
                leftImageView.setAnimation(R.raw.error, 36, 36);
                infoOnly = true;
                layoutParams.topMargin = AndroidUtilities.dp(9);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            } else if (currentAction == ACTION_IMPORT_GROUP_NOT_ADMIN) {
                infoTextView.setText(LocaleController.getString("ImportNotAdmin", R.string.ImportNotAdmin));
                leftImageView.setAnimation(R.raw.error, 36, 36);
                infoOnly = true;
                layoutParams.topMargin = AndroidUtilities.dp(9);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            } else if (currentAction == ACTION_IMPORT_INFO) {
                infoTextView.setText(LocaleController.getString("ImportedInfo", R.string.ImportedInfo));
                leftImageView.setAnimation(R.raw.imported, 36, 36);
                leftImageView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
                infoOnly = true;
                layoutParams.topMargin = AndroidUtilities.dp(9);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            } else if (currentAction == ACTION_MESSAGE_COPIED || currentAction == ACTION_USERNAME_COPIED || currentAction == ACTION_HASHTAG_COPIED || currentAction == ACTION_TEXT_COPIED || currentAction == ACTION_LINK_COPIED || currentAction == ACTION_PHONE_COPIED || currentAction == ACTION_EMAIL_COPIED) {
                if (!AndroidUtilities.shouldShowClipboardToast()) {
                    return;
                }
                int iconRawId = R.raw.copy;
                if (currentAction == ACTION_EMAIL_COPIED) {
                    infoTextView.setText(LocaleController.getString("EmailCopied", R.string.EmailCopied));
                } else if (currentAction == ACTION_PHONE_COPIED) {
                    infoTextView.setText(LocaleController.getString("PhoneCopied", R.string.PhoneCopied));
                } else if (currentAction == ACTION_USERNAME_COPIED) {
                    infoTextView.setText(LocaleController.getString("UsernameCopied", R.string.UsernameCopied));
                } else if (currentAction == ACTION_HASHTAG_COPIED) {
                    infoTextView.setText(LocaleController.getString("HashtagCopied", R.string.HashtagCopied));
                } else if (currentAction == ACTION_MESSAGE_COPIED) {
                    infoTextView.setText(LocaleController.getString("MessageCopied", R.string.MessageCopied));
                } else if (currentAction == ACTION_LINK_COPIED) {
                    iconRawId = R.raw.voip_invite;
                    infoTextView.setText(LocaleController.getString("LinkCopied", R.string.LinkCopied));
                } else {
                    infoTextView.setText(LocaleController.getString("TextCopied", R.string.TextCopied));
                }
                leftImageView.setAnimation(iconRawId, 30, 30);
                timeLeft = 3000;
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            } else if (currentAction == ACTION_NOTIFY_ON) {
                infoTextView.setText(LocaleController.getString("ChannelNotifyMembersInfoOn", R.string.ChannelNotifyMembersInfoOn));
                leftImageView.setAnimation(R.raw.silent_unmute, 30, 30);
                timeLeft = 3000;
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            } else if (currentAction == ACTION_NOTIFY_OFF) {
                infoTextView.setText(LocaleController.getString("ChannelNotifyMembersInfoOff", R.string.ChannelNotifyMembersInfoOff));
                leftImageView.setAnimation(R.raw.silent_mute, 30, 30);
                timeLeft = 3000;
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            } else if (currentAction == ACTION_VOIP_INVITE_LINK_SENT) {
                if (infoObject2 == null) {
                    if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                        infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("InvLinkToSavedMessages", R.string.InvLinkToSavedMessages)));
                    } else {
                        if (DialogObject.isChatDialog(did)) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToGroup", R.string.InvLinkToGroup, chat.title)));
                        } else {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToUser", R.string.InvLinkToUser, UserObject.getFirstName(user))));
                        }
                    }
                } else {
                    int amount = (Integer) infoObject2;
                    infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("InvLinkToChats", R.string.InvLinkToChats, LocaleController.formatPluralString("Chats", amount))));
                }
                leftImageView.setAnimation(R.raw.contact_check, 36, 36);
                timeLeft = 3000;
            } else if (currentAction == ACTION_FWD_MESSAGES) {
                Integer count = (Integer) infoObject;
                if (infoObject2 == null || infoObject2 instanceof TLRPC.TL_forumTopic) {
                    if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                        if (count == 1) {
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("FwdMessageToSavedMessages", R.string.FwdMessageToSavedMessages)));
                        } else {
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("FwdMessagesToSavedMessages", R.string.FwdMessagesToSavedMessages)));
                        }
                        leftImageView.setAnimation(R.raw.saved_messages, 30, 30);
                    } else {
                        if (DialogObject.isChatDialog(did)) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            TLRPC.TL_forumTopic topic = (TLRPC.TL_forumTopic) infoObject2;
                            if (count == 1) {
                                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToGroup", R.string.FwdMessageToGroup, topic != null ? topic.title : chat.title)));
                            } else {
                                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessagesToGroup", R.string.FwdMessagesToGroup, topic != null ? topic.title : chat.title)));
                            }
                        } else {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            if (count == 1) {
                                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToUser", R.string.FwdMessageToUser, UserObject.getFirstName(user))));
                            } else {
                                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessagesToUser", R.string.FwdMessagesToUser, UserObject.getFirstName(user))));
                            }
                        }
                        leftImageView.setAnimation(R.raw.forward, 30, 30);
                        hapticDelay = 300;
                    }
                } else {
                    int amount = (Integer) infoObject2;
                    if (count == 1) {
                        infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("FwdMessageToManyChats", amount)));
                    } else {
                        infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatPluralString("FwdMessagesToManyChats", amount)));
                    }
                    leftImageView.setAnimation(R.raw.forward, 30, 30);
                    hapticDelay = 300;
                }
                timeLeft = 3000;
            } else if (currentAction == ACTION_SHARE_BACKGROUND) {
                Integer count = (Integer) infoObject;
                if (infoObject2 == null) {
                    if (did == UserConfig.getInstance(currentAccount).clientUserId) {
                        infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("BackgroundToSavedMessages", R.string.BackgroundToSavedMessages)));
                        leftImageView.setAnimation(R.raw.saved_messages, 30, 30);
                    } else {
                        if (DialogObject.isChatDialog(did)) {
                            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BackgroundToGroup", R.string.BackgroundToGroup, chat.title)));
                        } else {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BackgroundToUser", R.string.BackgroundToUser, UserObject.getFirstName(user))));
                        }
                        leftImageView.setAnimation(R.raw.forward, 30, 30);
                    }
                } else {
                    int amount = (Integer) infoObject2;
                    infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("BackgroundToChats", R.string.BackgroundToChats, LocaleController.formatPluralString("Chats", amount))));
                    leftImageView.setAnimation(R.raw.forward, 30, 30);
                }
                timeLeft = 3000;
            }
            subinfoTextView.setVisibility(GONE);
            undoTextView.setTextColor(getThemedColor(Theme.key_undo_cancelColor));
            undoButton.setVisibility(GONE);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = AndroidUtilities.dp(8);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
            if (hapticDelay > 0) {
                leftImageView.postDelayed(() -> {
                    leftImageView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }, hapticDelay);
            }
        } else if (currentAction == ACTION_PROXIMITY_SET || currentAction == ACTION_PROXIMITY_REMOVED) {
            int radius = (Integer) infoObject;
            TLRPC.User user = (TLRPC.User) infoObject2;

            undoImageView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            if (radius != 0) {
                infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                leftImageView.clearLayerColors();
                leftImageView.setLayerColor("BODY.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Wibe Big.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Wibe Big 3.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Wibe Small.**", getThemedColor(Theme.key_undo_infoColor));

                infoTextView.setText(LocaleController.getString("ProximityAlertSet", R.string.ProximityAlertSet));
                leftImageView.setAnimation(R.raw.ic_unmute, 28, 28);
                subinfoTextView.setVisibility(VISIBLE);
                subinfoTextView.setSingleLine(false);
                subinfoTextView.setMaxLines(3);

                if (user != null) {
                    subinfoTextView.setText(LocaleController.formatString("ProximityAlertSetInfoUser", R.string.ProximityAlertSetInfoUser, UserObject.getFirstName(user), LocaleController.formatDistance(radius, 2)));
                } else {
                    subinfoTextView.setText(LocaleController.formatString("ProximityAlertSetInfoGroup2", R.string.ProximityAlertSetInfoGroup2, LocaleController.formatDistance(radius, 2)));
                }
                undoButton.setVisibility(GONE);

                layoutParams.topMargin = AndroidUtilities.dp(6);
            } else {
                infoTextView.setTypeface(Typeface.DEFAULT);
                infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                leftImageView.clearLayerColors();
                leftImageView.setLayerColor("Body Main.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Body Top.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Line.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Curve Big.**", getThemedColor(Theme.key_undo_infoColor));
                leftImageView.setLayerColor("Curve Small.**", getThemedColor(Theme.key_undo_infoColor));

                layoutParams.topMargin = AndroidUtilities.dp(14);

                infoTextView.setText(LocaleController.getString("ProximityAlertCancelled", R.string.ProximityAlertCancelled));
                leftImageView.setAnimation(R.raw.ic_mute, 28, 28);
                subinfoTextView.setVisibility(GONE);
                undoTextView.setTextColor(getThemedColor(Theme.key_undo_cancelColor));
                undoButton.setVisibility(VISIBLE);
            }

            layoutParams.leftMargin = AndroidUtilities.dp(58);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_QR_SESSION_ACCEPTED) {
            TLRPC.TL_authorization authorization = (TLRPC.TL_authorization) infoObject;

            infoTextView.setText(LocaleController.getString("AuthAnotherClientOk", R.string.AuthAnotherClientOk));
            leftImageView.setAnimation(R.raw.contact_check, 36, 36);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.topMargin = AndroidUtilities.dp(6);
            subinfoTextView.setText(authorization.app_name);
            subinfoTextView.setVisibility(VISIBLE);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            undoTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteRedText2));
            undoImageView.setVisibility(GONE);
            undoButton.setVisibility(VISIBLE);
            leftImageView.setVisibility(VISIBLE);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_FILTERS_AVAILABLE) {
            timeLeft = 10000;
            undoTextView.setText(LocaleController.getString("Open", R.string.Open));
            infoTextView.setText(LocaleController.getString("FilterAvailableTitle", R.string.FilterAvailableTitle));
            leftImageView.setAnimation(R.raw.filter_new, 36, 36);
            int margin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = margin;
            layoutParams.topMargin = AndroidUtilities.dp(6);

            layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
            layoutParams.rightMargin = margin;

            String text = LocaleController.getString("FilterAvailableText", R.string.FilterAvailableText);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            int index1 = text.indexOf('*');
            int index2 = text.lastIndexOf('*');
            if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                builder.replace(index2, index2 + 1, "");
                builder.replace(index1, index1 + 1, "");
                builder.setSpan(new URLSpanNoUnderline("tg://settings/folders"), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            subinfoTextView.setText(builder);
            subinfoTextView.setVisibility(VISIBLE);
            subinfoTextView.setSingleLine(false);
            subinfoTextView.setMaxLines(2);
            undoButton.setVisibility(VISIBLE);
            undoImageView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);

            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (currentAction == ACTION_DICE_INFO || currentAction == ACTION_DICE_NO_SEND_INFO) {
            timeLeft = 4000;
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setGravity(Gravity.CENTER_VERTICAL);
            infoTextView.setMinHeight(AndroidUtilities.dp(30));
            String emoji = (String) infoObject;
            if ("\uD83C\uDFB2".equals(emoji)) {
                infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DiceInfo2", R.string.DiceInfo2)));
                leftImageView.setImageResource(R.drawable.dice);
            } else {
                if ("\uD83C\uDFAF".equals(emoji)) {
                    infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DartInfo", R.string.DartInfo)));
                } else {
                    String info = LocaleController.getServerString("DiceEmojiInfo_" + emoji);
                    if (!TextUtils.isEmpty(info)) {
                        infoTextView.setText(Emoji.replaceEmoji(info, infoTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                    } else {
                        infoTextView.setText(Emoji.replaceEmoji(LocaleController.formatString("DiceEmojiInfo", R.string.DiceEmojiInfo, emoji), infoTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(14), false));
                    }
                }
                leftImageView.setImageDrawable(Emoji.getEmojiDrawable(emoji));
                leftImageView.setScaleType(ImageView.ScaleType.FIT_XY);
                layoutParams.topMargin = AndroidUtilities.dp(14);
                layoutParams.bottomMargin = AndroidUtilities.dp(14);
                layoutParams2.leftMargin = AndroidUtilities.dp(14);
                layoutParams2.width = AndroidUtilities.dp(26);
                layoutParams2.height = AndroidUtilities.dp(26);
            }
            undoTextView.setText(LocaleController.getString("SendDice", R.string.SendDice));

            int margin;
            if (currentAction == ACTION_DICE_INFO) {
                margin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);
                undoTextView.setVisibility(VISIBLE);
                undoTextView.setTextColor(getThemedColor(Theme.key_undo_cancelColor));
                undoImageView.setVisibility(GONE);
                undoButton.setVisibility(VISIBLE);
            } else {
                margin = AndroidUtilities.dp(8);
                undoTextView.setVisibility(GONE);
                undoButton.setVisibility(GONE);
            }

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = margin;
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.bottomMargin = AndroidUtilities.dp(7);
            layoutParams.height = TableLayout.LayoutParams.MATCH_PARENT;

            subinfoTextView.setVisibility(GONE);
            leftImageView.setVisibility(VISIBLE);
        } else if (currentAction == ACTION_TEXT_INFO) {
            CharSequence info = (CharSequence) infoObject;
            timeLeft = Math.max(4000, Math.min(info.length() / 50 * 1600, 10000));
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoTextView.setGravity(Gravity.CENTER_VERTICAL);
            infoTextView.setText(info);

            undoTextView.setVisibility(GONE);
            undoButton.setVisibility(GONE);
            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = AndroidUtilities.dp(8);
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.bottomMargin = AndroidUtilities.dp(7);
            layoutParams.height = TableLayout.LayoutParams.MATCH_PARENT;

            layoutParams2.gravity = Gravity.TOP | Gravity.LEFT;
            layoutParams2.topMargin = layoutParams2.bottomMargin = AndroidUtilities.dp(8);

            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.chats_infotip, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();

            infoTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        } else if (currentAction == ACTION_THEME_CHANGED) {
            infoTextView.setText(LocaleController.getString("ColorThemeChanged", R.string.ColorThemeChanged));
            leftImageView.setImageResource(R.drawable.toast_pallete);

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = AndroidUtilities.dp(48);
            layoutParams.topMargin = AndroidUtilities.dp(6);

            layoutParams = (FrameLayout.LayoutParams) subinfoTextView.getLayoutParams();
            layoutParams.rightMargin = AndroidUtilities.dp(48);

            String text = LocaleController.getString("ColorThemeChangedInfo", R.string.ColorThemeChangedInfo);
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            int index1 = text.indexOf('*');
            int index2 = text.lastIndexOf('*');
            if (index1 >= 0 && index2 >= 0 && index1 != index2) {
                builder.replace(index2, index2 + 1, "");
                builder.replace(index1, index1 + 1, "");
                builder.setSpan(new URLSpanNoUnderline("tg://settings/themes"), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            subinfoTextView.setText(builder);
            subinfoTextView.setVisibility(VISIBLE);
            subinfoTextView.setSingleLine(false);
            subinfoTextView.setMaxLines(2);
            undoTextView.setVisibility(GONE);
            undoButton.setVisibility(VISIBLE);
            leftImageView.setVisibility(VISIBLE);
        } else if (currentAction == ACTION_PREMIUM_TRANSCRIPTION) {
            infoTextView.setVisibility(VISIBLE);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            infoTextView.setTypeface(Typeface.DEFAULT);
            infoTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("UnlockPremiumTranscriptionHint", R.string.UnlockPremiumTranscriptionHint)));
            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.voice_to_text, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();

            undoTextView.setText(LocaleController.getString("PremiumMore", R.string.PremiumMore));
            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);
            layoutParams.topMargin = layoutParams.bottomMargin = AndroidUtilities.dp(6);
            layoutParams.height = TableLayout.LayoutParams.WRAP_CONTENT;

            avatarImageView.setVisibility(GONE);
            subinfoTextView.setVisibility(GONE);
            undoTextView.setVisibility(VISIBLE);
            undoButton.setVisibility(VISIBLE);
            undoImageView.setVisibility(GONE);
        } else if (currentAction == ACTION_HINT_SWIPE_TO_REPLY) {
            infoTextView.setVisibility(VISIBLE);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            infoTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            infoTextView.setText(LocaleController.getString("SwipeToReplyHint", R.string.SwipeToReplyHint));
            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.hint_swipe_reply, (int) (36 * 1.8f), (int) (36 * 1.8f));
            leftImageView.setProgress(0);
            leftImageView.playAnimation();

            subinfoTextView.setVisibility(VISIBLE);
            subinfoTextView.setText(LocaleController.getString("SwipeToReplyHintMessage", R.string.SwipeToReplyHintMessage));

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.rightMargin = (int) Math.ceil(undoTextView.getPaint().measureText(undoTextView.getText().toString())) + AndroidUtilities.dp(26);
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.height = TableLayout.LayoutParams.WRAP_CONTENT;

            avatarImageView.setVisibility(GONE);
            undoButton.setVisibility(GONE);
        } else if (currentAction == ACTION_ARCHIVE || currentAction == ACTION_ARCHIVE_FEW) {
            if (action == ACTION_ARCHIVE) {
                infoTextView.setText(LocaleController.getString("ChatArchived", R.string.ChatArchived));
            } else {
                infoTextView.setText(LocaleController.getString("ChatsArchived", R.string.ChatsArchived));
            }

            layoutParams.leftMargin = AndroidUtilities.dp(58);
            layoutParams.topMargin = AndroidUtilities.dp(13);
            layoutParams.rightMargin = 0;

            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);

            leftImageView.setVisibility(VISIBLE);
            leftImageView.setAnimation(R.raw.chats_archived, 36, 36);
            leftImageView.setProgress(0);
            leftImageView.playAnimation();
        } else if (action == ACTION_PREVIEW_MEDIA_DESELECTED) {
            layoutParams.leftMargin = AndroidUtilities.dp(58);

            MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) infoObject;
            infoTextView.setText(photoEntry.isVideo ? LocaleController.getString("AttachMediaVideoDeselected", R.string.AttachMediaVideoDeselected) : LocaleController.getString("AttachMediaPhotoDeselected", R.string.AttachMediaPhotoDeselected));
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);

            avatarImageView.setVisibility(VISIBLE);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(2));
            if (photoEntry.thumbPath != null) {
                avatarImageView.setImage(photoEntry.thumbPath, null, Theme.chat_attachEmptyDrawable);
            } else if (photoEntry.path != null) {
                avatarImageView.setOrientation(photoEntry.orientation, true);
                if (photoEntry.isVideo) {
                    avatarImageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                } else {
                    avatarImageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, Theme.chat_attachEmptyDrawable);
                }
            } else {
                avatarImageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
            }
        } else {
            layoutParams.leftMargin = AndroidUtilities.dp(45);
            layoutParams.topMargin = AndroidUtilities.dp(13);
            layoutParams.rightMargin = 0;

            infoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            undoButton.setVisibility(VISIBLE);
            infoTextView.setTypeface(Typeface.DEFAULT);
            subinfoTextView.setVisibility(GONE);
            leftImageView.setVisibility(GONE);

            if (currentAction == ACTION_CLEAR_DATES || currentAction == ACTION_CLEAR || currentAction == ACTION_CLEAR_FEW) {
                infoTextView.setText(LocaleController.getString("HistoryClearedUndo", R.string.HistoryClearedUndo));
            } else if (currentAction == ACTION_DELETE_FEW) {
                infoTextView.setText(LocaleController.getString("ChatsDeletedUndo", R.string.ChatsDeletedUndo));
            } else {
                if (DialogObject.isChatDialog(did)) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                    if (ChatObject.isChannel(chat) && !chat.megagroup) {
                        infoTextView.setText(LocaleController.getString("ChannelDeletedUndo", R.string.ChannelDeletedUndo));
                    } else {
                        infoTextView.setText(LocaleController.getString("GroupDeletedUndo", R.string.GroupDeletedUndo));
                    }
                } else {
                    infoTextView.setText(LocaleController.getString("ChatDeletedUndo", R.string.ChatDeletedUndo));
                }
            }
            if (currentAction != ACTION_CLEAR_DATES) {
                for (int a = 0; a < dialogIds.size(); a++) {
                    MessagesController.getInstance(currentAccount).addDialogAction(dialogIds.get(a), currentAction == ACTION_CLEAR || currentAction == ACTION_CLEAR_FEW);
                }
            }
        }

        AndroidUtilities.makeAccessibilityAnnouncement(infoTextView.getText() + (subinfoTextView.getVisibility() == VISIBLE ? ". " + subinfoTextView.getText() : ""));

        if (isMultilineSubInfo()) {
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getMeasuredWidth();
            if (width == 0) {
                width = AndroidUtilities.displaySize.x;
            }
            width -= AndroidUtilities.dp(16);
            measureChildWithMargins(subinfoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = subinfoTextView.getMeasuredHeight() + AndroidUtilities.dp(27 + 10);
        } else if (hasSubInfo()) {
            undoViewHeight = AndroidUtilities.dp(52);
        } else if (getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getMeasuredWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            if (width <= 0) {
                width = AndroidUtilities.displaySize.x;
            }
            width -= AndroidUtilities.dp(16);
            measureChildWithMargins(infoTextView, MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 0);
            undoViewHeight = infoTextView.getMeasuredHeight() + AndroidUtilities.dp(currentAction == ACTION_DICE_INFO || currentAction == ACTION_DICE_NO_SEND_INFO || currentAction == ACTION_TEXT_INFO || currentAction == ACTION_PREMIUM_TRANSCRIPTION || currentAction == ACTION_PREMIUM_ALL_FOLDER ? 14 : 28);
            if (currentAction == ACTION_TEXT_INFO) {
                undoViewHeight = Math.max(undoViewHeight, AndroidUtilities.dp(52));
            } else if (currentAction == ACTION_PROXIMITY_REMOVED) {
                undoViewHeight = Math.max(undoViewHeight, AndroidUtilities.dp(50));
            } else if (infoOnly) {
                undoViewHeight -= AndroidUtilities.dp(8);
            }
        }

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
            setEnterOffset((fromTop ? -1.0f : 1.0f) * (enterOffsetMargin + undoViewHeight));
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(ObjectAnimator.ofFloat(this, "enterOffset", (fromTop ? -1.0f : 1.0f) * (enterOffsetMargin + undoViewHeight), (fromTop ? 1.0f : -1.0f)));
            animatorSet.setInterpolator(new DecelerateInterpolator());
            animatorSet.setDuration(180);
            animatorSet.start();
        }
    }

    private int enterOffsetMargin = AndroidUtilities.dp(8);

    public void setEnterOffsetMargin(int enterOffsetMargin) {
        this.enterOffsetMargin = enterOffsetMargin;
    }

    protected boolean canUndo() {
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(undoViewHeight, MeasureSpec.EXACTLY));
        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    StaticLayout timeLayout;
    StaticLayout timeLayoutOut;
    int textWidthOut;

    float timeReplaceProgress = 1f;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (additionalTranslationY != 0) {
            canvas.save();

            float bottom = getMeasuredHeight() - enterOffset + AndroidUtilities.dp(9);
            if (bottom > 0) {
                canvas.clipRect(0, 0, getMeasuredWidth(), bottom);
                super.dispatchDraw(canvas);
            }
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (additionalTranslationY != 0) {
            canvas.save();

            float bottom = getMeasuredHeight() - enterOffset + enterOffsetMargin + AndroidUtilities.dp(1);
            if (bottom > 0) {
                canvas.clipRect(0, 0, getMeasuredWidth(), bottom);
                super.dispatchDraw(canvas);
            }
            backgroundDrawable.draw(canvas);
            canvas.restore();
        } else {
            backgroundDrawable.draw(canvas);
        }

        if (currentAction == ACTION_DELETE || currentAction == ACTION_CLEAR || currentAction == ACTION_DELETE_FEW || currentAction == ACTION_CLEAR_FEW || currentAction == ACTION_CLEAR_DATES) {
            int newSeconds = timeLeft > 0 ? (int) Math.ceil(timeLeft / 1000.0f) : 0;
            if (prevSeconds != newSeconds) {
                prevSeconds = newSeconds;
                timeLeftString = String.format("%d", Math.max(1, newSeconds));
                if (timeLayout != null) {
                    timeLayoutOut = timeLayout;
                    timeReplaceProgress = 0;
                    textWidthOut = textWidth;
                }
                textWidth = (int) Math.ceil(textPaint.measureText(timeLeftString));
                timeLayout = new StaticLayout(timeLeftString, textPaint, Integer.MAX_VALUE, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }

            if (timeReplaceProgress < 1f) {
                timeReplaceProgress += 16f / 150f;
                if (timeReplaceProgress > 1f) {
                    timeReplaceProgress = 1f;
                } else {
                    invalidate();
                }
            }

            int alpha = textPaint.getAlpha();

            if (timeLayoutOut != null && timeReplaceProgress < 1f) {
                textPaint.setAlpha((int) (alpha * (1f - timeReplaceProgress)));
                canvas.save();
                canvas.translate(rect.centerX() - textWidth / 2, AndroidUtilities.dp(17.2f) + AndroidUtilities.dp(10) * timeReplaceProgress);
                timeLayoutOut.draw(canvas);
                textPaint.setAlpha(alpha);
                canvas.restore();
            }

            if (timeLayout != null) {
                if (timeReplaceProgress != 1f) {
                    textPaint.setAlpha((int) (alpha * timeReplaceProgress));
                }
                canvas.save();
                canvas.translate(rect.centerX() - textWidth / 2, AndroidUtilities.dp(17.2f) - AndroidUtilities.dp(10) * (1f - timeReplaceProgress));
                timeLayout.draw(canvas);
                if (timeReplaceProgress != 1f) {
                    textPaint.setAlpha(alpha);
                }
                canvas.restore();
            }

           // canvas.drawText(timeLeftString, rect.centerX() - textWidth / 2, AndroidUtilities.dp(28.2f), textPaint);
           // canvas.drawText(timeLeftString, , textPaint);
            canvas.drawArc(rect, -90, -360 * (timeLeft / 5000.0f), false, progressPaint);
        }

        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastUpdateTime;
        timeLeft -= dt;
        lastUpdateTime = newTime;
        if (timeLeft <= 0) {
            hide(true, hideAnimationType);
        }
        
        if (currentAction != ACTION_PREVIEW_MEDIA_DESELECTED) {
            invalidate();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        infoTextView.invalidate();
        leftImageView.invalidate();
    }

    public void setInfoText(CharSequence text) {
        infoText = text;
    }

    public void setHideAnimationType(int hideAnimationType) {
        this.hideAnimationType = hideAnimationType;
    }

    float enterOffset;


    @Keep
    public float getEnterOffset() {
        return enterOffset;
    }

    @Keep
    public void setEnterOffset(float enterOffset) {
        if (this.enterOffset != enterOffset) {
            this.enterOffset = enterOffset;
            updatePosition();
        }
    }

    private void updatePosition() {
        setTranslationY(enterOffset - enterOffsetMargin + AndroidUtilities.dp(8) - additionalTranslationY);
        invalidate();
    }

    @Override
    public Drawable getBackground() {
        return backgroundDrawable;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
