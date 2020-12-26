package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPBaseService;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.GroupCallInvitedCell;
import org.telegram.ui.Cells.GroupCallTextCell;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CheckBoxSquare;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.GroupVoipInviteAlert;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Components.WaveDrawable;
import org.telegram.ui.Components.voip.VoIPToggleButton;

import java.util.ArrayList;

public class GroupCallActivity extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, VoIPBaseService.StateListener {

    private static final int eveyone_can_speak_item = 1;
    private static final int admin_can_speak_item = 2;
    private static final int share_invite_link_item = 3;
    private static final int leave_item = 4;

    private static final int MUTE_BUTTON_STATE_UNMUTE = 0;
    private static final int MUTE_BUTTON_STATE_MUTE = 1;
    private static final int MUTE_BUTTON_STATE_MUTED_BY_ADMIN = 2;
    private static final int MUTE_BUTTON_STATE_CONNECTING = 3;

    public static GroupCallActivity groupCallInstance;
    public static boolean groupCallUiVisible;

    private AccountInstance accountInstance;

    private View actionBarBackground;
    private ActionBar actionBar;
    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private FillLastLinearLayoutManager layoutManager;
    private VoIPToggleButton soundButton;
    private VoIPToggleButton leaveButton;
    private RLottieImageView muteButton;
    private TextView[] muteLabel = new TextView[2];
    private TextView[] muteSubLabel = new TextView[2];
    private FrameLayout buttonsContainer;
    private RadialProgressView radialProgressView;
    private Drawable shadowDrawable;
    private View actionBarShadow;
    private AnimatorSet actionBarAnimation;
    private LaunchActivity parentActivity;
    private UndoView[] undoView = new UndoView[2];

    private ShareAlert shareAlert;

    private boolean delayedGroupCallUpdated;

    private RectF rect = new RectF();

    private boolean enterEventSent;
    private boolean anyEnterEventSent;

    private float scrollOffsetY;

    private boolean scrolling;

    private TLRPC.TL_groupCallParticipant selfDummyParticipant;

    private Paint listViewBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<TLRPC.TL_groupCallParticipant> oldParticipants = new ArrayList<>();
    private ArrayList<Integer> oldInvited = new ArrayList<>();
    private int oldCount;

    private RLottieDrawable bigMicDrawable;

    private final BlobDrawable tinyWaveDrawable;
    private final BlobDrawable bigWaveDrawable;

    private float amplitude;
    private float animateToAmplitude;
    private float animateAmplitudeDiff;

    private RadialGradient radialGradient;
    private final Matrix radialMatrix;
    private final Paint radialPaint;

    private ValueAnimator muteButtonAnimator;

    public TLRPC.Chat currentChat;
    public ChatObject.Call call;

    private TextView titleTextView;
    private ActionBarMenuItem otherItem;
    private ActionBarMenuItem pipItem;
    private ActionBarMenuSubItem inviteItem;
    private ActionBarMenuSubItem everyoneItem;
    private ActionBarMenuSubItem adminItem;
    private ActionBarMenuSubItem leaveItem;
    private View dividerItem;
    private final LinearLayout menuItemsContainer;

    private GroupVoipInviteAlert groupVoipInviteAlert;

    private int muteButtonState = MUTE_BUTTON_STATE_UNMUTE;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Paint paintTmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Paint leaveBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private WeavingState[] states = new WeavingState[4];
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
    private final int[] colorsTmp = new int[3];

    private Runnable unmuteRunnable = () -> VoIPService.getSharedInstance().setMicMute(false, true, false);

    private Runnable pressRunnable = () -> {
        if (!scheduled || VoIPService.getSharedInstance() == null) {
            return;
        }
        muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        updateMuteButton(MUTE_BUTTON_STATE_MUTE, true);
        AndroidUtilities.runOnUIThread(unmuteRunnable, 80);
        scheduled = false;
        pressed = true;
    };

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

    private class WeavingState {
        private float targetX = -1f;
        private float targetY = -1f;
        private float startX;
        private float startY;
        private float duration;
        private float time;
        private Shader shader;
        private Matrix matrix = new Matrix();
        private int currentState;

        public WeavingState(int state) {
            currentState = state;
        }

        public void update(int top, int left, int size, long dt) {
            if (shader == null) {
                return;
            }
            if (duration == 0 || time >= duration) {
                duration = Utilities.random.nextInt(700) + 500;
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
            if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                s = 1f;
            } else {
                s = currentState == MUTE_BUTTON_STATE_MUTE ? 4 : 2.5f;
            }
            float scale = AndroidUtilities.dp(122) / 400.0f * s;
            matrix.reset();
            matrix.postTranslate(x, y);
            matrix.postScale(scale, scale, x + 200, y + 200);

            shader.setLocalMatrix(matrix);
        }

        private void setTarget() {
            if (currentState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                targetX = 0.85f + 0.25f * Utilities.random.nextInt(100) / 100f;
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

    @SuppressWarnings("FieldCanBeLocal")
    private static class LabeledButton extends FrameLayout {

        private ImageView imageView;
        private TextView textView;

        public LabeledButton(Context context, String text, int resId, int color) {
            super(context);

            imageView = new ImageView(context);
            if (Build.VERSION.SDK_INT >= 21) {
                imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(50), color, 0x1fffffff));
            } else {
                imageView.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(50), color, color));
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
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didEndCall);
        super.dismiss();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupCallUpdated) {
            Long callId = (Long) args[1];
            if (call != null && call.call.id == callId) {
                if (call.call instanceof TLRPC.TL_groupCallDiscarded) {
                    dismiss();
                } else {
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
                        applyCallParticipantUpdates();
                    }

                    if (actionBar != null) {
                        int count = call.call.participants_count + (listAdapter.addSelfToCounter() ? 1 : 0);
                        actionBar.setSubtitle(LocaleController.formatPluralString("Members", count));
                    }
                    updateState(true, (Boolean) args[2]);
                }
            }
        } else if (id == NotificationCenter.webRtcMicAmplitudeEvent) {
            float amplitude = (float) args[0];
            setAmplitude(amplitude * 4000.0f);
            if (listView != null) {
                TLRPC.TL_groupCallParticipant participant = call.participants.get(accountInstance.getUserConfig().getClientUserId());
                if (participant != null) {
                    ArrayList<TLRPC.TL_groupCallParticipant> array = delayedGroupCallUpdated ? oldParticipants : call.sortedParticipants;
                    int idx = array.indexOf(participant);
                    if (idx >= 0) {
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(idx + listAdapter.usersStartRow);
                        if (holder != null && holder.itemView instanceof GroupCallUserCell) {
                            GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                            cell.setAmplitude(amplitude * 15.0f);
                            if (holder.itemView == scrimView) {
                                containerView.invalidate();
                            }
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.needShowAlert) {
            int num = (Integer) args[0];
            if (num == 6) {
                String text = (String) args[1];
                String error;
                if ("ANONYMOUS_CALLS_DISABLED".equals(text) || "GROUPCALL_ANONYMOUS_FORBIDDEN".equals(text)) {
                    error = LocaleController.getString("VoipGroupJoinAnonymousAdmin", R.string.VoipGroupJoinAnonymousAdmin);
                } else {
                    error = LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + text;
                }

                AlertDialog.Builder builder = AlertsCreator.createSimpleAlert(getContext(), LocaleController.getString("VoipGroupVoiceChat", R.string.VoipGroupVoiceChat), error);
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
            if (chatFull.id == currentChat.id) {
                updateItems();
                updateState(isShowing(), false);
            }
        } else if (id == NotificationCenter.didLoadChatAdmins) {
            int chatId = (Integer) args[0];
            if (chatId == currentChat.id) {
                updateItems();
                updateState(isShowing(), false);
            }
        }
    }

    private void applyCallParticipantUpdates() {
        int count = listView.getChildCount();
        View minChild = null;
        int minPosition = 0;
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
            if (holder != null) {
                if (minChild == null || minPosition > holder.getAdapterPosition()) {
                    minChild = child;
                    minPosition = holder.getAdapterPosition();
                }
            }
        }
        try {
            UpdateCallback updateCallback = new UpdateCallback(listAdapter);
            setOldRows(listAdapter.addMemberRow, listAdapter.selfUserRow, listAdapter.usersStartRow, listAdapter.usersEndRow, listAdapter.invitedStartRow, listAdapter.invitedEndRow);
            listAdapter.updateRows();
            DiffUtil.calculateDiff(diffUtilsCallback).dispatchUpdatesTo(updateCallback);
        } catch (Exception e) {
            FileLog.e(e);
            listAdapter.notifyDataSetChanged();
        }
        if (minChild != null) {
            layoutManager.scrollToPositionWithOffset(minPosition, minChild.getTop() - listView.getPaddingTop());
        }
        oldParticipants.clear();
        oldParticipants.addAll(call.sortedParticipants);
        oldInvited.clear();
        oldInvited.addAll(call.invitedUsers);
        oldCount = listAdapter.getItemCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallUserCell || child instanceof GroupCallInvitedCell) {
                RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                if (holder != null) {
                    if (child instanceof GroupCallUserCell) {
                        ((GroupCallUserCell) child).setDrawDivider(holder.getAdapterPosition() != listAdapter.getItemCount() - 2);
                    } else if (child instanceof GroupCallInvitedCell) {
                        ((GroupCallInvitedCell) child).setDrawDivider(holder.getAdapterPosition() != listAdapter.getItemCount() - 2);
                    }
                }
            }
        }
    }

    private void updateItems() {
        TLRPC.Chat newChat = accountInstance.getMessagesController().getChat(currentChat.id);
        if (newChat != null) {
            currentChat = newChat;
        }
        boolean anyVisible = false;
        if (ChatObject.canUserDoAdminAction(currentChat, ChatObject.ACTION_INVITE)) {
            inviteItem.setVisibility(View.VISIBLE);
            anyVisible = true;
        } else {
            inviteItem.setVisibility(View.GONE);
        }
        if (ChatObject.canManageCalls(currentChat)) {
            leaveItem.setVisibility(View.VISIBLE);
            anyVisible = true;
        } else {
            leaveItem.setVisibility(View.GONE);
        }
        if (ChatObject.canManageCalls(currentChat) && call.call.can_change_join_muted) {
            everyoneItem.setVisibility(View.VISIBLE);
            adminItem.setVisibility(View.VISIBLE);
            dividerItem.setVisibility(View.VISIBLE);
            anyVisible = true;
        } else {
            everyoneItem.setVisibility(View.GONE);
            adminItem.setVisibility(View.GONE);
            dividerItem.setVisibility(View.GONE);
        }
        otherItem.setVisibility(anyVisible ? View.VISIBLE : View.GONE);
        ((FrameLayout.LayoutParams) menuItemsContainer.getLayoutParams()).rightMargin = anyVisible ? 0 : AndroidUtilities.dp(6);
        actionBar.setTitleRightMargin(AndroidUtilities.dp(48) * (anyVisible ? 2 : 1));
    }

    protected void makeFocusable(EditTextBoldCursor editText, boolean showKeyboard) {
        if (!enterEventSent) {
            BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
            if (fragment instanceof ChatActivity) {
                boolean keyboardVisible = ((ChatActivity) fragment).needEnterText();
                enterEventSent = true;
                anyEnterEventSent = true;
                AndroidUtilities.runOnUIThread(() -> {
                    if (groupVoipInviteAlert != null) {
                        groupVoipInviteAlert.setFocusable(true);
                        editText.requestFocus();
                        if (showKeyboard) {
                            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
                        }
                    }
                }, keyboardVisible ? 200 : 0);
            } else {
                enterEventSent = true;
                anyEnterEventSent = true;
                groupVoipInviteAlert.setFocusable(true);
                editText.requestFocus();
                if (showKeyboard) {
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(editText));
                }
            }
        }
    }

    public static void create(LaunchActivity activity, AccountInstance account) {
        if (groupCallInstance != null || VoIPService.getSharedInstance() == null) {
            return;
        }
        ChatObject.Call call = VoIPService.getSharedInstance().groupCall;
        if (call == null) {
            return;
        }
        TLRPC.Chat chat = account.getMessagesController().getChat(call.chatId);
        if (chat == null) {
            return;
        }
        groupCallInstance = new GroupCallActivity(activity, account, call, chat);
        groupCallInstance.parentActivity = activity;
        groupCallInstance.show();
    }

    public GroupCallActivity(Context context, AccountInstance account, ChatObject.Call call, TLRPC.Chat chat) {
        super(context, false);
        this.accountInstance = account;
        this.call = call;
        this.currentChat = chat;
        this.currentAccount = account.getCurrentAccount();
        drawNavigationBar = true;
        scrollNavBar = true;
        navBarColorKey = null;
        //useLightNavBar = true;

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
            BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
            if (anyEnterEventSent) {
                if (fragment instanceof ChatActivity) {
                    ((ChatActivity) fragment).onEditTextDialogClose(true);
                }
            }
        });

        setDimBehindAlpha(75);

        oldParticipants.addAll(call.sortedParticipants);
        oldInvited.addAll(call.invitedUsers);

        selfDummyParticipant = new TLRPC.TL_groupCallParticipant();
        selfDummyParticipant.user_id = accountInstance.getUserConfig().getClientUserId();
        selfDummyParticipant.muted = true;
        selfDummyParticipant.can_self_unmute = true;
        selfDummyParticipant.date = accountInstance.getConnectionsManager().getCurrentTime();

        currentCallState = VoIPService.getSharedInstance().getCallState();

        VoIPService.audioLevelsCallback = (uids, levels, voice) -> {
            for (int a = 0; a < uids.length; a++) {
                TLRPC.TL_groupCallParticipant participant = call.participantsBySources.get(uids[a]);
                if (participant != null) {
                    ArrayList<TLRPC.TL_groupCallParticipant> array = delayedGroupCallUpdated ? oldParticipants : call.sortedParticipants;
                    int idx = array.indexOf(participant);
                    if (idx >= 0) {
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(idx + listAdapter.usersStartRow);
                        if (holder != null) {
                            GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                            cell.setAmplitude(levels[a] * 15.0f);
                            if (holder.itemView == scrimView) {
                                containerView.invalidate();
                            }
                        }
                    }
                }
            }
        };

        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.groupCallUpdated);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.needShowAlert);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        accountInstance.getNotificationCenter().addObserver(this, NotificationCenter.didLoadChatAdmins);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcMicAmplitudeEvent);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didEndCall);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

        bigMicDrawable = new RLottieDrawable(R.raw.voice_outlined, "" + R.raw.voice_outlined, AndroidUtilities.dp(28), AndroidUtilities.dp(38), true, null);

        containerView = new FrameLayout(context) {

            private boolean ignoreLayout = false;
            private RectF rect = new RectF();

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop() - AndroidUtilities.dp(14 + 231);

                LayoutParams layoutParams = (LayoutParams) listView.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(14);

                layoutParams = (LayoutParams) actionBarShadow.getLayoutParams();
                layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

                int contentSize = Math.max(AndroidUtilities.dp(64 + 50 + 58 * 2.5f), availableHeight / 5 * 3);
                int padding = Math.max(0, availableHeight - contentSize + AndroidUtilities.dp(8));
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                updateLayout(false);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - AndroidUtilities.dp(37) && actionBar.getAlpha() == 0.0f) {
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
            protected void onDraw(Canvas canvas) {
                int offset = AndroidUtilities.dp(74);
                float top = scrollOffsetY - offset;

                int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
                float rad = 1.0f;

                if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                    int willMoveUpTo = offset - backgroundPaddingTop - AndroidUtilities.dp(14);
                    float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() - top - backgroundPaddingTop) / willMoveUpTo);
                    int diff = (int) ((ActionBar.getCurrentActionBarHeight() - willMoveUpTo) * moveProgress);
                    top -= diff;
                    height += diff;
                    rad = 1.0f - moveProgress;
                }

                top += getPaddingTop();

                shadowDrawable.setBounds(0, (int) top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (rad != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * rad, AndroidUtilities.dp(12) * rad, Theme.dialogs_onlineCirclePaint);
                }

                int finalColor = Color.argb((int) (255 * actionBar.getAlpha()), (int) (Color.red(backgroundColor) * 0.8f), (int) (Color.green(backgroundColor) * 0.8f), (int) (Color.blue(backgroundColor) * 0.8f));
                Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, 0, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (scrimView != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), scrimPaint);
                    float listTop = listView.getY();

                    boolean groupedBackgroundWasDraw = false;

                    int count = listView.getChildCount();
                    for (int num = 0; num < count; num++) {
                        View child = listView.getChildAt(num);
                        if (child != scrimView) {
                            continue;
                        }

                        float viewClipLeft = Math.max(listView.getLeft(), listView.getLeft() + child.getX());
                        float viewClipTop = Math.max(listTop, listView.getTop() + child.getY());
                        float viewClipRight = Math.min(listView.getRight(), listView.getLeft() + child.getX() + child.getMeasuredWidth());
                        float viewClipBottom = Math.min(listView.getY() + listView.getMeasuredHeight(), listView.getY() + child.getY() + child.getMeasuredHeight());

                        if (viewClipTop < viewClipBottom) {
                            if (child.getAlpha() != 1f) {
                                canvas.saveLayerAlpha(viewClipLeft, viewClipTop, viewClipRight, viewClipBottom, (int) (255 * child.getAlpha()), Canvas.ALL_SAVE_FLAG);
                            } else {
                                canvas.save();
                            }

                            canvas.clipRect(viewClipLeft, viewClipTop, viewClipRight, viewClipBottom);
                            canvas.translate(listView.getLeft() + child.getX(), listView.getY() + child.getY());
                            rect.set(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), listViewBackgroundPaint);
                            child.draw(canvas);
                            canvas.restore();
                        }
                    }
                }
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.setKeepScreenOn(true);
        containerView.setClipChildren(false);

        listView = new RecyclerListView(context) {

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == scrimView) {
                    return false;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                float maxBottom = 0;
                float minTop = 0;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    View child = getChildAt(a);
                    ViewHolder holder = findContainingViewHolder(child);
                    if (holder.getItemViewType() == 3) {
                        continue;
                    }
                    maxBottom = Math.max(maxBottom, child.getY() + child.getMeasuredHeight());
                    if (a == 0) {
                        minTop = Math.max(0, child.getY());
                    } else {
                        minTop = Math.min(minTop, Math.max(0, child.getY()));
                    }
                }
                rect.set(0, minTop, getMeasuredWidth(), Math.min(getMeasuredHeight(), maxBottom));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), listViewBackgroundPaint);

                canvas.save();
                canvas.clipRect(0, 0, getMeasuredWidth(), getMeasuredHeight());
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        listView.setClipToPadding(false);
        listView.setClipChildren(false);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView.invalidate();
                updateLayout(true);
            }
        };
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (listView.getChildCount() <= 0) {
                    return;
                }
                if (!call.loadingMembers && !call.membersLoadEndReached && layoutManager.findLastVisibleItemPosition() > listAdapter.getItemCount() - 5) {
                    call.loadMembers(false);
                }
                updateLayout(true);
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(74);
                    float top = scrollOffsetY - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() && listView.canScrollVertically(1)) {
                        View child = listView.getChildAt(0);
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > 0) {
                            listView.smoothScrollBy(0, holder.itemView.getTop());
                        }
                    }
                }
                scrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING;
            }
        });

        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, 0, listView));
        layoutManager.setBind(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 14, 14, 14, 231));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setTopBottomSelectorRadius(13);
        listView.setSelectorDrawableColor(Theme.getColor(Theme.key_voipgroup_listSelector));
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) view;
                if (cell.isSelfUser()) {
                    return;
                }
                Bundle args = new Bundle();
                args.putInt("user_id", cell.getParticipant().user_id);
                parentActivity.presentFragment(new ProfileActivity(args));
                dismiss();
            } else if (view instanceof GroupCallInvitedCell) {
                GroupCallInvitedCell cell = (GroupCallInvitedCell) view;
                if (cell.getUser() == null) {
                    return;
                }
                Bundle args = new Bundle();
                args.putInt("user_id", cell.getUser().id);
                parentActivity.presentFragment(new ProfileActivity(args));
                dismiss();
            } else if (position == listAdapter.addMemberRow) {
                TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(currentChat.id);
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
                    public void inviteUser(int id) {
                        inviteUserToCall(id, true);
                    }

                    @Override
                    public void needOpenSearch(MotionEvent ev, EditTextBoldCursor editText) {
                        if (!enterEventSent) {
                            if (ev.getX() > editText.getLeft() && ev.getX() < editText.getRight()
                                    && ev.getY() > editText.getTop() && ev.getY() < editText.getBottom()) {
                                makeFocusable(editText, true);
                            } else {
                                makeFocusable(editText, false);
                            }
                        }
                    }
                });
                groupVoipInviteAlert.show();
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (view instanceof GroupCallUserCell) {
                updateItems();
                if (!ChatObject.canManageCalls(currentChat)) {
                    return false;
                }
                GroupCallUserCell cell = (GroupCallUserCell) view;
                return cell.clickMuteButton();
            }
            return false;
        });

        buttonsContainer = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int cw = AndroidUtilities.dp(122);
                int w = (getMeasuredWidth() - cw) / 2;
                int h = getMeasuredHeight();

                int x = (w - soundButton.getMeasuredWidth()) / 2;
                int y = (h - leaveButton.getMeasuredHeight()) / 2 - AndroidUtilities.dp(9);
                soundButton.layout(x, y, x + soundButton.getMeasuredWidth(), y + soundButton.getMeasuredHeight());

                x = getMeasuredWidth() - w + (w - leaveButton.getMeasuredWidth()) / 2;
                leaveButton.layout(x, y, x + leaveButton.getMeasuredWidth(), y + leaveButton.getMeasuredHeight());

                x = (getMeasuredWidth() - muteButton.getMeasuredWidth()) / 2;
                y = (h - muteButton.getMeasuredHeight()) / 2 - AndroidUtilities.dp(18);
                muteButton.layout(x, y, x + muteButton.getMeasuredWidth(), y + muteButton.getMeasuredHeight());

                for (int a = 0; a < 2; a++) {
                    x = (getMeasuredWidth() - muteLabel[a].getMeasuredWidth()) / 2;
                    y = h - AndroidUtilities.dp(35) - muteLabel[a].getMeasuredHeight();
                    muteLabel[a].layout(x, y, x + muteLabel[a].getMeasuredWidth(), y + muteLabel[a].getMeasuredHeight());

                    x = (getMeasuredWidth() - muteSubLabel[a].getMeasuredWidth()) / 2;
                    y = h - AndroidUtilities.dp(17) - muteSubLabel[a].getMeasuredHeight();
                    muteSubLabel[a].layout(x, y, x + muteSubLabel[a].getMeasuredWidth(), y + muteSubLabel[a].getMeasuredHeight());
                }
            }

            final OvershootInterpolator overshootInterpolator = new OvershootInterpolator(1.5f);
            int currentLightColor;

            @SuppressLint("DrawAllocation")
            @Override
            protected void dispatchDraw(Canvas canvas) {
                int offset = (getMeasuredWidth() - getMeasuredHeight()) / 2;

                long newTime = SystemClock.elapsedRealtime();
                long dt = newTime - lastUpdateTime;
                lastUpdateTime = newTime;
                if (dt > 20) {
                    dt = 17;
                }

                if (currentState != null) {
                    currentState.update(0, offset, getMeasuredHeight(), dt);
                }

                tinyWaveDrawable.minRadius = AndroidUtilities.dp(62);
                tinyWaveDrawable.maxRadius = AndroidUtilities.dp(62) + AndroidUtilities.dp(20) * BlobDrawable.FORM_SMALL_MAX;

                bigWaveDrawable.minRadius = AndroidUtilities.dp(65);
                bigWaveDrawable.maxRadius = AndroidUtilities.dp(65) + AndroidUtilities.dp(20) * BlobDrawable.FORM_BIG_MAX;

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
                    invalidate();
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
                        invalidate();
                    }

                    if (invalidateColors && currentState != null) {
                        invalidateColors = false;
                        int lightingColor;
                        int soundButtonColor;
                        int soundButtonColorChecked;
                        if (prevState != null) {
                            fillColors(prevState.currentState, colorsTmp);
                            int oldLight = colorsTmp[0];
                            int oldSound = colorsTmp[1];
                            int oldSound2 = colorsTmp[2];
                            fillColors(currentState.currentState, colorsTmp);
                            lightingColor = ColorUtils.blendARGB(oldLight, colorsTmp[0], switchProgress);
                            soundButtonColorChecked = ColorUtils.blendARGB(oldSound, colorsTmp[1], switchProgress);
                            soundButtonColor = ColorUtils.blendARGB(oldSound2, colorsTmp[2], switchProgress);
                        } else {
                            fillColors(currentState.currentState, colorsTmp);
                            lightingColor = colorsTmp[0];
                            soundButtonColorChecked = colorsTmp[1];
                            soundButtonColor = colorsTmp[2];
                        }
                        if (currentLightColor != lightingColor) {
                            radialGradient = new RadialGradient(0, 0, AndroidUtilities.dp(100), new int[]{ColorUtils.setAlphaComponent(lightingColor, 60), ColorUtils.setAlphaComponent(lightingColor, 0)}, null, Shader.TileMode.CLAMP);
                            radialPaint.setShader(radialGradient);
                            currentLightColor = lightingColor;
                        }

                        soundButton.setBackgroundColor(soundButtonColor, soundButtonColorChecked);
                    }

                    boolean showWaves = false;
                    boolean showLighting = false;
                    if (currentState != null) {
                        showWaves = currentState.currentState == MUTE_BUTTON_STATE_MUTE || currentState.currentState == MUTE_BUTTON_STATE_UNMUTE;
                        showLighting = currentState.currentState != MUTE_BUTTON_STATE_CONNECTING;
                    }

                    if (prevState != null && currentState != null && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                        showWavesProgress -= dt / 180f;
                        if (showWavesProgress < 0f) {
                            showWavesProgress = 0f;
                        }
                        invalidate();
                    } else {
                        if (showWaves && showWavesProgress != 1f) {
                            showWavesProgress += dt / 350f;
                            if (showWavesProgress > 1f) {
                                showWavesProgress = 1f;
                            }
                            invalidate();
                        } else if (!showWaves && showWavesProgress != 0) {
                            showWavesProgress -= dt / 350f;
                            if (showWavesProgress < 0f) {
                                showWavesProgress = 0f;
                            }
                            invalidate();
                        }
                    }

                    if (showLighting && showLightingProgress != 1f) {
                        showLightingProgress += dt / 350f;
                        if (showLightingProgress > 1f) {
                            showLightingProgress = 1f;
                        }
                        invalidate();
                    } else if (!showLighting && showLightingProgress != 0) {
                        showLightingProgress -= dt / 350f;
                        if (showLightingProgress < 0f) {
                            showLightingProgress = 0f;
                        }
                        invalidate();
                    }
                }

                float showWavesProgressInterpolated = overshootInterpolator.getInterpolation(GroupCallActivity.this.showWavesProgress);

                showWavesProgressInterpolated = 0.4f + 0.6f * showWavesProgressInterpolated;

                bigWaveDrawable.update(amplitude, 1f);
                tinyWaveDrawable.update(amplitude, 1f);

                if (prevState != null && currentState != null && (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING || prevState.currentState == MUTE_BUTTON_STATE_CONNECTING)) {
                    float progress;
                    if (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                        progress = switchProgress;
                        paint.setShader(prevState.shader);
                    } else {
                        progress = 1f - switchProgress;
                        paint.setShader(currentState.shader);
                    }

                    paintTmp.setColor(AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f));

                    int cx = muteButton.getLeft() + muteButton.getMeasuredWidth() / 2;
                    int cy = muteButton.getTop() + muteButton.getMeasuredHeight() / 2;
                    radialMatrix.setTranslate(cx, cy);
                    radialGradient.setLocalMatrix(radialMatrix);

                    paint.setAlpha(76);

                    float radius = AndroidUtilities.dp(52) / 2f;
                    canvas.drawCircle(leaveButton.getX() + leaveButton.getMeasuredWidth() / 2f, leaveButton.getY() + radius, radius, leaveBackgroundPaint);


                    canvas.save();

                    canvas.scale(BlobDrawable.GLOBAL_SCALE, BlobDrawable.GLOBAL_SCALE, cx, cy);

                    canvas.save();
                    float scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * 0.5f;
                    canvas.scale(scale * showLightingProgress, scale * showLightingProgress, cx, cy);

                    float scaleLight = 0.7f + BlobDrawable.LIGHT_GRADIENT_SIZE;
                    canvas.save();
                    canvas.scale(scaleLight, scaleLight, cx, cy);
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(160), radialPaint);
                    canvas.restore();

                    canvas.restore();
                    canvas.save();
                    scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude;
                    canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                    bigWaveDrawable.draw(cx, cy, canvas, paint);
                    canvas.restore();

                    canvas.save();
                    scale = BlobDrawable.SCALE_SMALL_MIN + BlobDrawable.SCALE_SMALL * amplitude;
                    canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                    tinyWaveDrawable.draw(cx, cy, canvas, paint);
                    canvas.restore();

                    paint.setAlpha(255);

                    if (canSwitchProgress) {
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(57), paint);
                        paint.setColor(Theme.getColor(Theme.key_voipgroup_connectingProgress));
                        if (progress != 0) {
                            paint.setAlpha((int) (255 * progress));
                            paint.setShader(null);
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(57), paint);
                        }
                    }
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(55) * progress, paintTmp);
                    if (!canSwitchProgress) {
                        radialProgressView.draw(canvas, cx, cy);
                    }
                    canvas.restore();
                    invalidate();
                } else {
                    for (int i = 0; i < 2; i++) {
                        float alpha;
                        float buttonRadius = AndroidUtilities.dp(57);
                        if (i == 0 && prevState != null) {
                            paint.setShader(prevState.shader);
                            alpha = 1f - switchProgress;
                            if (prevState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                                buttonRadius -= alpha * AndroidUtilities.dp(2);
                            }
                        } else if (i == 1) {
                            paint.setShader(currentState.shader);
                            alpha = switchProgress;
                            if (currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                                buttonRadius -= alpha * AndroidUtilities.dp(2);
                            }
                        } else {
                            continue;
                        }
                        if (paint.getShader() == null) {
                            paint.setColor(AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f));
                        }

                        int cx = muteButton.getLeft() + muteButton.getMeasuredWidth() / 2;
                        int cy = muteButton.getTop() + muteButton.getMeasuredHeight() / 2;
                        radialMatrix.setTranslate(cx, cy);
                        radialGradient.setLocalMatrix(radialMatrix);

                        paint.setAlpha((int) (76 * alpha));
                        if (i == 1) {
                            float radius = AndroidUtilities.dp(52) / 2f;
                            canvas.drawCircle(leaveButton.getX() + leaveButton.getMeasuredWidth() / 2, leaveButton.getY() + radius, radius, leaveBackgroundPaint);
                        }

                        canvas.save();
                        canvas.scale(BlobDrawable.GLOBAL_SCALE, BlobDrawable.GLOBAL_SCALE, cx, cy);

                        canvas.save();
                        float scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude * 0.5f;
                        canvas.scale(scale * showLightingProgress, scale * showLightingProgress, cx, cy);
                        if (i == 1) {
                            float scaleLight = 0.7f + BlobDrawable.LIGHT_GRADIENT_SIZE;
                            canvas.save();
                            canvas.scale(scaleLight, scaleLight, cx, cy);
                            canvas.drawCircle(cx, cy, AndroidUtilities.dp(160), radialPaint);
                            canvas.restore();
                        }
                        canvas.restore();
                        canvas.save();
                        scale = BlobDrawable.SCALE_BIG_MIN + BlobDrawable.SCALE_BIG * amplitude;
                        canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                        bigWaveDrawable.draw(cx, cy, canvas, paint);
                        canvas.restore();

                        canvas.save();
                        scale = BlobDrawable.SCALE_SMALL_MIN + BlobDrawable.SCALE_SMALL * amplitude;
                        canvas.scale(scale * showWavesProgressInterpolated, scale * showWavesProgressInterpolated, cx, cy);
                        tinyWaveDrawable.draw(cx, cy, canvas, paint);
                        canvas.restore();
                        if (i == 0) {
                            paint.setAlpha(255);
                        } else {
                            paint.setAlpha((int) (255 * alpha));
                        }
                        canvas.drawCircle(cx, cy, buttonRadius, paint);

                        canvas.restore();

                        if (i == 1 && currentState.currentState == MUTE_BUTTON_STATE_CONNECTING) {
                            radialProgressView.draw(canvas, cx, cy);
                        }
                    }
                    invalidate();
                }
                super.dispatchDraw(canvas);
            }

        };
        buttonsContainer.setWillNotDraw(false);
        containerView.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 231, Gravity.LEFT | Gravity.BOTTOM));

        int color = Theme.getColor(Theme.key_voipgroup_unmuteButton2);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        radialMatrix = new Matrix();
        radialGradient = new RadialGradient(0, 0, AndroidUtilities.dp(160), new int[]{Color.argb(50, r, g, b), Color.argb(0, r, g, b)}, null, Shader.TileMode.CLAMP);
        radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        radialPaint.setShader(radialGradient);

        tinyWaveDrawable = new BlobDrawable(9);
        bigWaveDrawable = new BlobDrawable(12);

        tinyWaveDrawable.minRadius = AndroidUtilities.dp(62);
        tinyWaveDrawable.maxRadius = AndroidUtilities.dp(72);
        tinyWaveDrawable.generateBlob();

        bigWaveDrawable.minRadius = AndroidUtilities.dp(65);
        bigWaveDrawable.maxRadius = AndroidUtilities.dp(75);
        bigWaveDrawable.generateBlob();

        tinyWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_unmuteButton), (int) (255 * WaveDrawable.CIRCLE_ALPHA_2)));
        bigWaveDrawable.paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_unmuteButton), (int) (255 * WaveDrawable.CIRCLE_ALPHA_1)));

        soundButton = new VoIPToggleButton(context);
        soundButton.setCheckable(true);
        soundButton.setTextSize(12);
        buttonsContainer.addView(soundButton, LayoutHelper.createFrame(68, 90));
        soundButton.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(getContext(), false);
        });

        leaveButton = new VoIPToggleButton(context);
        leaveButton.setDrawBackground(false);
        leaveButton.setTextSize(12);
        leaveButton.setData(R.drawable.calls_decline, 0xffffffff, Theme.getColor(Theme.key_voipgroup_leaveButton), 0.3f, false, LocaleController.getString("VoipGroupLeave", R.string.VoipGroupLeave), false, false);
        buttonsContainer.addView(leaveButton, LayoutHelper.createFrame(68, 80));
        leaveButton.setOnClickListener(v -> {
            updateItems();
            onLeaveClick(context, this::dismiss, false);
        });

        muteButton = new RLottieImageView(context) {

            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN && muteButtonState == MUTE_BUTTON_STATE_UNMUTE) {
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
                            muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
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
                    info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, LocaleController.getString("VoipMute", R.string.VoipMute)));
                }
            }
        };
        muteButton.setAnimation(bigMicDrawable);
        muteButton.setScaleType(ImageView.ScaleType.CENTER);
        buttonsContainer.addView(muteButton, LayoutHelper.createFrame(122, 122, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        muteButton.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() == null || muteButtonState == MUTE_BUTTON_STATE_CONNECTING) {
                return;
            }
            if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                AndroidUtilities.shakeView(muteLabel[0], 2, 0);
                AndroidUtilities.shakeView(muteSubLabel[0], 2, 0);
                try {
                    Vibrator vibrator = (Vibrator) parentActivity.getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null) {
                        vibrator.vibrate(200);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return;
            }
            if (muteButtonState == MUTE_BUTTON_STATE_UNMUTE) {
                updateMuteButton(MUTE_BUTTON_STATE_MUTE, true);
                VoIPService.getSharedInstance().setMicMute(false, false, true);
                muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } else {
                updateMuteButton(MUTE_BUTTON_STATE_UNMUTE, true);
                VoIPService.getSharedInstance().setMicMute(true, false, true);
                muteButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
        });

        radialProgressView = new RadialProgressView(context);
        radialProgressView.setSize(AndroidUtilities.dp(110));
        radialProgressView.setStrokeWidth(4);
        radialProgressView.setProgressColor(Theme.getColor(Theme.key_voipgroup_connectingProgress));
        //buttonsContainer.addView(radialProgressView, LayoutHelper.createFrame(126, 126, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        for (int a = 0; a < 2; a++) {
            muteLabel[a] = new TextView(context);
            muteLabel[a].setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            muteLabel[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            muteLabel[a].setGravity(Gravity.CENTER_HORIZONTAL);
            buttonsContainer.addView(muteLabel[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 26));

            muteSubLabel[a] = new TextView(context);
            muteSubLabel[a].setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            muteSubLabel[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            muteSubLabel[a].setGravity(Gravity.CENTER_HORIZONTAL);
            buttonsContainer.addView(muteSubLabel[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 10));
            if (a == 1) {
                muteLabel[a].setVisibility(View.INVISIBLE);
                muteSubLabel[a].setVisibility(View.INVISIBLE);
            }
        }

        actionBar = new ActionBar(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                containerView.invalidate();
            }
        };
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
                    dismiss();
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

                    builder.setTitle(LocaleController.getString("VoipGroupEndAlertTitle", R.string.VoipGroupEndAlertTitle));
                    builder.setMessage(LocaleController.getString("VoipGroupEndAlertText", R.string.VoipGroupEndAlertText));

                    builder.setPositiveButton(LocaleController.getString("VoipGroupEnd", R.string.VoipGroupEnd), (dialogInterface, i) -> {
                        if (VoIPService.getSharedInstance() != null) {
                            VoIPService.getSharedInstance().hangUp(1);
                        }
                        dismiss();
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();

                    dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
                    dialog.show();
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
                    }
                    dialog.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
                }
            }
        });

        actionBar.setAlpha(0.0f);
        actionBar.getBackButton().setScaleX(0.9f);
        actionBar.getBackButton().setScaleY(0.9f);
        actionBar.getBackButton().setTranslationX(-AndroidUtilities.dp(14));

        actionBar.getTitleTextView().setTranslationY(AndroidUtilities.dp(23));
        actionBar.getSubtitleTextView().setTranslationY(AndroidUtilities.dp(20));

        otherItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_voipgroup_actionBarItems));
        otherItem.setLongClickEnabled(false);
        otherItem.setIcon(R.drawable.ic_ab_other);
        otherItem.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        otherItem.setSubMenuOpenSide(2);
        otherItem.setDelegate(id -> actionBar.getActionBarMenuOnItemClick().onItemClick(id));
        otherItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_voipgroup_actionBarItemsSelector), 6));
        otherItem.setOnClickListener(v -> {
            updateItems();
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
            otherItem.toggleSubMenu();
        });
        otherItem.setPopupItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), false);
        otherItem.setPopupItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), true);

        pipItem = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_voipgroup_actionBarItems));
        pipItem.setLongClickEnabled(false);
        pipItem.setIcon(R.drawable.msg_voice_pip);
        pipItem.setContentDescription(LocaleController.getString("AccDescrPipMode", R.string.AccDescrPipMode));
        pipItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_voipgroup_actionBarItemsSelector), 6));
        pipItem.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(parentActivity)) {
                GroupCallPip.clearForce();
                dismiss();
            } else {
                AlertsCreator.createDrawOverlayGroupCallPermissionDialog(getContext()).show();
            }
        });

        titleTextView = new TextView(context);
        titleTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setGravity(Gravity.LEFT | Gravity.TOP);
        titleTextView.setText(LocaleController.getString("VoipGroupVoiceChat", R.string.VoipGroupVoiceChat));

        actionBarBackground = new View(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ActionBar.getCurrentActionBarHeight());
            }
        };
        actionBarBackground.setAlpha(0.0f);

        containerView.addView(actionBarBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 48, 0));
        containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

        menuItemsContainer = new LinearLayout(context);
        menuItemsContainer.setOrientation(LinearLayout.HORIZONTAL);
        menuItemsContainer.addView(pipItem, LayoutHelper.createLinear(48, 48));
        menuItemsContainer.addView(otherItem, LayoutHelper.createLinear(48, 48));
        containerView.addView(menuItemsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT));

        actionBarShadow = new View(context);
        actionBarShadow.setAlpha(0.0f);
        actionBarShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(actionBarShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1));

        for (int a = 0; a < 2; a++) {
            undoView[a] = new UndoView(context);
            undoView[a].setAdditionalTranslationY(AndroidUtilities.dp(10));
            if (Build.VERSION.SDK_INT >= 21) {
                undoView[a].setTranslationZ(AndroidUtilities.dp(5));
            }
            containerView.addView(undoView[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        }

        everyoneItem = otherItem.addSubItem(eveyone_can_speak_item, 0, LocaleController.getString("VoipGroupAllCanSpeak", R.string.VoipGroupAllCanSpeak), true);
        adminItem = otherItem.addSubItem(admin_can_speak_item, 0, LocaleController.getString("VoipGroupOnlyAdminsCanSpeak", R.string.VoipGroupOnlyAdminsCanSpeak), true);

        everyoneItem.setCheckColor(Theme.getColor(Theme.key_voipgroup_checkMenu));
        everyoneItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));
        adminItem.setCheckColor(Theme.getColor(Theme.key_voipgroup_checkMenu));
        adminItem.setColors(Theme.getColor(Theme.key_voipgroup_checkMenu), Theme.getColor(Theme.key_voipgroup_checkMenu));
        dividerItem = otherItem.addDivider(Theme.getColor(Theme.key_voipgroup_listViewBackground));

        inviteItem = otherItem.addSubItem(share_invite_link_item, R.drawable.msg_link, LocaleController.getString("VoipGroupShareInviteLink", R.string.VoipGroupShareInviteLink));
        leaveItem = otherItem.addSubItem(leave_item, R.drawable.msg_endcall, LocaleController.getString("VoipGroupEndChat", R.string.VoipGroupEndChat));
        otherItem.setPopupItemsSelectorColor(Theme.getColor(Theme.key_voipgroup_listSelector));

        leaveItem.setColors(Theme.getColor(Theme.key_voipgroup_leaveCallMenu), Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
        inviteItem.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));

        listAdapter.notifyDataSetChanged();
        oldCount = listAdapter.getItemCount();

        actionBar.setTitle(currentChat.title);
        actionBar.setSubtitle(LocaleController.formatPluralString("Participants", call.call.participants_count + (listAdapter.addSelfToCounter() ? 1 : 0)));
        actionBar.setTitleRightMargin(AndroidUtilities.dp(48) * 2);

        VoIPService.getSharedInstance().registerStateListener(this);
        updateItems();
        updateSpeakerPhoneIcon(false);
        updateState(false, false);
        setColorProgress(0.0f);

        leaveBackgroundPaint.setColor(Theme.getColor(Theme.key_voipgroup_leaveButton));
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().unregisterStateListener(this);
        }
        if (groupCallInstance == this) {
            groupCallInstance = null;
        }
        groupCallUiVisible = false;

        VoIPService.audioLevelsCallback = null;
        GroupCallPip.updateVisibility(getContext());
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

    private UndoView getUndoView() {
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

    private void setColorProgress(float progress) {
        colorProgress = progress;
        backgroundColor = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_actionBarUnscrolled), Theme.getColor(Theme.key_voipgroup_actionBar), progress, 1.0f);
        actionBarBackground.setBackgroundColor(backgroundColor);
        otherItem.redrawPopup(0xff232A33);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        navBarColor = backgroundColor;

        int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_listViewBackground), progress, 1.0f);
        dividerItem.setBackgroundColor(color);
        listViewBackgroundPaint.setColor(color);
        listView.setGlowColor(color);

        if (muteButtonState == MUTE_BUTTON_STATE_CONNECTING || muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
            muteButton.invalidate();
        }

        color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_leaveButton), Theme.getColor(Theme.key_voipgroup_leaveButtonScrolled), progress, 1.0f);
        leaveButton.setBackgroundColor(color, color);

        color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_lastSeenText), progress, 1.0f);
        int color2 = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_mutedIconUnscrolled), Theme.getColor(Theme.key_voipgroup_mutedIcon), progress, 1.0f);
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallTextCell) {
                GroupCallTextCell cell = (GroupCallTextCell) child;
                cell.setColors(color2, color);
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

    private void getLink(boolean copy) {
        TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(currentChat.id);
        String url;
        if (!TextUtils.isEmpty(currentChat.username)) {
            url = accountInstance.getMessagesController().linkPrefix + "/" + currentChat.username;
        } else {
            url = chatFull != null && chatFull.exported_invite instanceof TLRPC.TL_chatInviteExported ? chatFull.exported_invite.link : null;
        }
        if (TextUtils.isEmpty(url)) {
            TLRPC.TL_messages_exportChatInvite req = new TLRPC.TL_messages_exportChatInvite();
            req.peer = MessagesController.getInputPeer(currentChat);
            accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_chatInviteExported) {
                    TLRPC.ExportedChatInvite invite = (TLRPC.ExportedChatInvite) response;
                    if (chatFull != null) {
                        chatFull.exported_invite = invite;
                    }
                    openShareAlert(invite.link, copy);
                }
            }));
        } else {
            openShareAlert(url, copy);
        }
    }

    private void openShareAlert(String url, boolean copy) {
        if (copy) {
            AndroidUtilities.addToClipboard(url);
            getUndoView().showWithAction(0, UndoView.ACTION_VOIP_LINK_COPIED, null, null, null, null);
        } else {
            String message = LocaleController.formatString("VoipGroupInviteText", R.string.VoipGroupInviteText, url);
            boolean keyboardIsOpen = false;
            if (parentActivity != null) {
                BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
                if (fragment  instanceof ChatActivity) {
                    keyboardIsOpen = ((ChatActivity) fragment).needEnterText();
                    anyEnterEventSent = true;
                    enterEventSent = true;
                }
            }

            shareAlert = new ShareAlert(getContext(), null, message, false, url, false);
            shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
                @Override
                public boolean didCopy() {
                    getUndoView().showWithAction(0, UndoView.ACTION_VOIP_LINK_COPIED, null, null, null, null);
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

    private void inviteUserToCall(int id, boolean shouldAdd) {
        TLRPC.User user = accountInstance.getMessagesController().getUser(id);
        if (user == null) {
            return;
        }
        final AlertDialog[] progressDialog = new AlertDialog[]{new AlertDialog(getContext(), 3)};
        TLRPC.TL_phone_inviteToGroupCall req = new TLRPC.TL_phone_inviteToGroupCall();
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
                        applyCallParticipantUpdates();
                        if (groupVoipInviteAlert != null) {
                            groupVoipInviteAlert.dismiss();
                        }
                        try {
                            progressDialog[0].dismiss();
                        } catch (Throwable ignore) {

                        }
                        progressDialog[0] = null;
                        getUndoView().showWithAction(0, UndoView.ACTION_VOIP_INVITED, user, null, null, null);
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
                        processSelectedOption(id, 3);
                    } else {
                        BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
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

    private void updateLayout(boolean animated) {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset((int) (scrollOffsetY = listView.getPaddingTop()));
            containerView.invalidate();
            return;
        }
        RecyclerListView.Holder holder = null;
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
            if (holder != null) {
                if (holder.getAdapterPosition() == 0) {
                    break;
                } else {
                    holder = null;
                }
            }
        }
        float newOffset = holder != null ? Math.max(0, holder.itemView.getY()) : 0;
        boolean show = newOffset <= ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(14);

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
                    .translationX(show ? 0.0f : -AndroidUtilities.dp(14))
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();

            actionBar.getTitleTextView().animate()
                    .translationY(show ? 0.0f : AndroidUtilities.dp(23))
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();

            actionBar.getSubtitleTextView().animate()
                    .translationY(show ? 0.0f : AndroidUtilities.dp(20))
                    .setDuration(300)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();

            /*titleTextView.animate()
                    .scaleX(show ? 0.9f : 1.0f)
                    .scaleY(show ? 0.9f : 1.0f)
                    .alpha(show ? 0.0f : 1.0f)
                    .setDuration(140)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start();*/

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
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        newOffset += layoutParams.topMargin;
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset((int) ((scrollOffsetY = newOffset) - layoutParams.topMargin));

            int offset = AndroidUtilities.dp(74);
            float t = scrollOffsetY - offset;
            int diff;
            if (t + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight() * 2) {
                int willMoveUpTo = offset - backgroundPaddingTop - AndroidUtilities.dp(14) + ActionBar.getCurrentActionBarHeight();
                float moveProgress = Math.min(1.0f, (ActionBar.getCurrentActionBarHeight() * 2 - t - backgroundPaddingTop) / willMoveUpTo);
                diff = (int) (AndroidUtilities.dp(AndroidUtilities.isTablet() ? 17 : 13) * moveProgress);
                float newProgress = Math.min(1.0f, moveProgress);
                if (Math.abs(newProgress - colorProgress) > 0.0001f) {
                    setColorProgress(Math.min(1.0f, moveProgress));
                }
                titleTextView.setScaleX(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
                titleTextView.setScaleY(Math.max(0.9f, 1.0f - 0.1f * moveProgress * 1.2f));
                titleTextView.setAlpha(Math.max(0.0f, 1.0f - moveProgress * 1.2f));
            } else {
                diff = 0;
                titleTextView.setScaleX(1.0f);
                titleTextView.setScaleY(1.0f);
                titleTextView.setAlpha(1.0f);
                if (colorProgress > 0.0001f) {
                    setColorProgress(0.0f);
                }
            }

            menuItemsContainer.setTranslationY(Math.max(AndroidUtilities.dp(4), scrollOffsetY - AndroidUtilities.dp(53) - diff));
            titleTextView.setTranslationY(Math.max(AndroidUtilities.dp(4), scrollOffsetY - AndroidUtilities.dp(44) - diff));
            containerView.invalidate();
        }
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
        if (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING) {
            cancelMutePress();
            updateMuteButton(MUTE_BUTTON_STATE_CONNECTING, animated);
        } else {
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            TLRPC.TL_groupCallParticipant participant = call.participants.get(accountInstance.getUserConfig().getClientUserId());
            if (participant != null && !participant.can_self_unmute && participant.muted && !ChatObject.canManageCalls(currentChat)) {
                cancelMutePress();
                updateMuteButton(MUTE_BUTTON_STATE_MUTED_BY_ADMIN, animated);
                VoIPService.getSharedInstance().setMicMute(true, false, false);
            } else {
                boolean micMuted = VoIPService.getSharedInstance().isMicMute();
                if (selfUpdated && participant != null && participant.muted && !micMuted) {
                    cancelMutePress();
                    VoIPService.getSharedInstance().setMicMute(true, false, false);
                    micMuted = true;
                }
                if (micMuted) {
                    updateMuteButton(MUTE_BUTTON_STATE_UNMUTE, animated);
                } else {
                    updateMuteButton(MUTE_BUTTON_STATE_MUTE, animated);
                }
            }
        }
    }

    @Override
    public void onAudioSettingsChanged() {
        updateSpeakerPhoneIcon(true);
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallUserCell) {
                ((GroupCallUserCell) child).applyParticipantChanges(true);
            }
        }
    }

    private void updateSpeakerPhoneIcon(boolean animated) {
        if (soundButton == null) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }

        boolean bluetooth = service.isBluetoothOn() || service.isBluetoothWillOn();
        boolean checked = !bluetooth && service.isSpeakerphoneOn();

        if (bluetooth) {
            soundButton.setData(R.drawable.calls_bluetooth, Color.WHITE, 0, 0.1f, true, LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), false, animated);
        } else if (checked) {
            soundButton.setData(R.drawable.calls_speaker, Color.WHITE, 0, 0.3f, true, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
        } else {
            if (service.isHeadsetPlugged()) {
                soundButton.setData(R.drawable.calls_headphones, Color.WHITE, 0, 0.1f, true, LocaleController.getString("VoipAudioRoutingHeadset", R.string.VoipAudioRoutingHeadset), false, animated);
            } else {
                soundButton.setData(R.drawable.calls_speaker, Color.WHITE, 0, 0.1f, true, LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), false, animated);
            }
        }
        soundButton.setChecked(checked, animated);
    }

    private void updateMuteButton(int state, boolean animated) {
        if (muteButtonState == state && animated) {
            return;
        }
        if (muteButtonAnimator != null) {
            muteButtonAnimator.cancel();
            muteButtonAnimator = null;
        }

        String newText;
        String newSubtext;

        boolean changed;
        if (state == MUTE_BUTTON_STATE_UNMUTE) {
            newText = LocaleController.getString("VoipGroupUnmute", R.string.VoipGroupUnmute);
            newSubtext = LocaleController.getString("VoipHoldAndTalk", R.string.VoipHoldAndTalk);
            changed = bigMicDrawable.setCustomEndFrame(13);
        } else if (state == MUTE_BUTTON_STATE_MUTE) {
            newText = LocaleController.getString("VoipTapToMute", R.string.VoipTapToMute);
            newSubtext = "";
            changed = bigMicDrawable.setCustomEndFrame(24);
        } else {
            if (state == MUTE_BUTTON_STATE_CONNECTING) {
                newText = LocaleController.getString("Connecting", R.string.Connecting);
                newSubtext = "";
            } else {
                newText = LocaleController.getString("VoipMutedByAdmin", R.string.VoipMutedByAdmin);
                newSubtext = LocaleController.getString("VoipMutedByAdminInfo", R.string.VoipMutedByAdminInfo);
            }
            changed = bigMicDrawable.setCustomEndFrame(13);
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
                if (state == MUTE_BUTTON_STATE_MUTE) {
                    bigMicDrawable.setCurrentFrame(12);
                } else {
                    bigMicDrawable.setCurrentFrame(0);
                }
            }
            muteButton.playAnimation();
            muteLabel[1].setVisibility(View.VISIBLE);
            muteLabel[1].setAlpha(0.0f);
            muteLabel[1].setTranslationY(-AndroidUtilities.dp(5));
            muteLabel[1].setText(newText);
            muteSubLabel[1].setVisibility(View.VISIBLE);
            muteSubLabel[1].setAlpha(0.0f);
            muteSubLabel[1].setTranslationY(-AndroidUtilities.dp(5));
            muteSubLabel[1].setText(newSubtext);

            muteButtonAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
            muteButtonAnimator.addUpdateListener(animation -> {
                float v = (float) animation.getAnimatedValue();
                muteLabel[0].setAlpha(1.0f - v);
                muteLabel[0].setTranslationY(AndroidUtilities.dp(5) * v);
                muteSubLabel[0].setAlpha(1.0f - v);
                muteSubLabel[0].setTranslationY(AndroidUtilities.dp(5) * v);
                muteLabel[1].setAlpha(v);
                muteLabel[1].setTranslationY(AndroidUtilities.dp(-5 + 5 * v));
                muteSubLabel[1].setAlpha(v);
                muteSubLabel[1].setTranslationY(AndroidUtilities.dp(-5 + 5 * v));
            });
            muteButtonAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    muteButtonAnimator = null;
                    TextView temp = muteLabel[0];
                    muteLabel[0] = muteLabel[1];
                    muteLabel[1] = temp;
                    temp.setVisibility(View.INVISIBLE);
                    temp = muteSubLabel[0];
                    muteSubLabel[0] = muteSubLabel[1];
                    muteSubLabel[1] = temp;
                    temp.setVisibility(View.INVISIBLE);
                    for (int a = 0; a < 2; a++) {
                        muteLabel[a].setTranslationY(0);
                        muteSubLabel[a].setTranslationY(0);
                    }
                }
            });
            muteButtonAnimator.setDuration(180);
            muteButtonAnimator.start();
            muteButtonState = state;
        } else {
            muteButtonState = state;
            bigMicDrawable.setCurrentFrame(bigMicDrawable.getCustomEndFrame() - 1, false, true);
            muteLabel[0].setText(newText);
            muteSubLabel[0].setText(newSubtext);
        }
        updateMuteButtonState(animated);
    }

    private void fillColors(int state, int[] colorsToSet) {
        if (state == MUTE_BUTTON_STATE_UNMUTE) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_unmuteButton2);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_soundButtonActive), Theme.getColor(Theme.key_voipgroup_soundButtonActiveScrolled), colorProgress, 1.0f);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_soundButton);
        } else if (state == MUTE_BUTTON_STATE_MUTE) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_muteButton2);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_soundButtonActive2), Theme.getColor(Theme.key_voipgroup_soundButtonActive2Scrolled), colorProgress, 1.0f);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_soundButton2);
        } else if (state == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3);
            colorsToSet[1] = Theme.getColor(Theme.key_voipgroup_mutedByAdminMuteButton);
            colorsToSet[2] = Theme.getColor(Theme.key_voipgroup_mutedByAdminMuteButtonDisabled);
        } else {
            colorsToSet[0] = Theme.getColor(Theme.key_voipgroup_disabledButton);
            colorsToSet[1] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_disabledButtonActive), Theme.getColor(Theme.key_voipgroup_disabledButtonActiveScrolled), colorProgress, 1.0f);
            colorsToSet[2] = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_listViewBackgroundUnscrolled), Theme.getColor(Theme.key_voipgroup_disabledButton), colorProgress, 1.0f);
        }
    }

    private void updateMuteButtonState(boolean animated) {
        muteButton.invalidate();

        if (states[muteButtonState] == null) {
            states[muteButtonState] = new WeavingState(muteButtonState);
            if (muteButtonState == MUTE_BUTTON_STATE_CONNECTING) {
                states[muteButtonState].shader = null;
            } else {
                if (muteButtonState == MUTE_BUTTON_STATE_MUTED_BY_ADMIN) {
                    states[muteButtonState].shader = new LinearGradient(0,400, 400, 0, new int[]{Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient),Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient3), Theme.getColor(Theme.key_voipgroup_mutedByAdminGradient2)}, null, Shader.TileMode.CLAMP);
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

    private static void processOnLeave(ChatObject.Call call, boolean discard, Runnable onLeave) {
        if (VoIPService.getSharedInstance() != null) {
            VoIPService.getSharedInstance().hangUp(discard ? 1 : 0);
        }
        if (call != null) {
            int selfUserId = call.currentAccount.getUserConfig().clientUserId;
            TLRPC.TL_groupCallParticipant participant = call.participants.get(selfUserId);
            if (participant != null) {
                call.participants.delete(selfUserId);
                call.sortedParticipants.remove(participant);
                call.call.participants_count--;
            }
        }
        if (onLeave != null) {
            onLeave.run();
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didStartedCall);
    }

    public static void onLeaveClick(Context context, Runnable onLeave, boolean fromOverlayWindow) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        TLRPC.Chat currentChat = service.getChat();
        ChatObject.Call call = service.groupCall;
        if (!ChatObject.canManageCalls(currentChat)) {
            processOnLeave(call, false, onLeave);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(LocaleController.getString("VoipGroupLeaveAlertTitle", R.string.VoipGroupLeaveAlertTitle));
        builder.setMessage(LocaleController.getString("VoipGroupLeaveAlertText", R.string.VoipGroupLeaveAlertText));

        int currentAccount = service.getAccount();

        CheckBoxCell[] cells = new CheckBoxCell[1];

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        cells[0] = new CheckBoxCell(context, 1);
        cells[0].setBackgroundDrawable(Theme.getSelectorDrawable(false));
        if (fromOverlayWindow) {
            cells[0].setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        } else {
            cells[0].setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            CheckBoxSquare checkBox = (CheckBoxSquare) cells[0].getCheckBoxView();
            checkBox.setColors(Theme.key_voipgroup_mutedIcon, Theme.key_voipgroup_listeningText, Theme.key_voipgroup_nameText);
        }
        cells[0].setTag(0);
        cells[0].setText(LocaleController.getString("VoipGroupLeaveAlertEndChat", R.string.VoipGroupLeaveAlertEndChat), "", false, false);

        cells[0].setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
        linearLayout.addView(cells[0], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        cells[0].setOnClickListener(v -> {
            Integer num = (Integer) v.getTag();
            cells[num].setChecked(!cells[num].isChecked(), true);
        });

        builder.setCustomViewOffset(12);
        builder.setView(linearLayout);

        builder.setPositiveButton(LocaleController.getString("VoipGroupLeave", R.string.VoipGroupLeave), (dialogInterface, position) -> processOnLeave(call, cells[0].isChecked(), onLeave));
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
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
    private View scrimView;
    private int popupAnimationIndex = -1;
    private AnimatorSet scrimAnimatorSet;
    private ActionBarPopupWindow scrimPopupWindow;
    private ActionBarMenuSubItem[] scrimPopupWindowItems;

    private void processSelectedOption(int userId, int option) {
        TLRPC.User user = accountInstance.getMessagesController().getUser(userId);
        if (option == 0 || option == 2 || option == 3) {
            if (option == 0) {
                if (VoIPService.getSharedInstance() == null) {
                    return;
                }
                VoIPService.getSharedInstance().editCallMember(user, true);
                getUndoView().showWithAction(0, UndoView.ACTION_VOIP_MUTED, user, null, null, null);
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

            TextView messageTextView = new TextView(getContext());
            messageTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

            FrameLayout frameLayout = new FrameLayout(getContext());
            builder.setView(frameLayout);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(12));

            BackupImageView imageView = new BackupImageView(getContext());
            imageView.setRoundRadius(AndroidUtilities.dp(20));
            frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

            avatarDrawable.setInfo(user);
            imageView.setImage(ImageLocation.getForUser(user, false), "50_50", avatarDrawable, user);
            String name = UserObject.getFirstName(user);

            TextView textView = new TextView(getContext());
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_actionBarItems));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            if (option == 0) {
                textView.setText(LocaleController.getString("VoipGroupMuteMemberAlertTitle", R.string.VoipGroupMuteMemberAlertTitle));
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupMuteMemberAlertText", R.string.VoipGroupMuteMemberAlertText, name)));
            } else if (option == 2) {
                textView.setText(LocaleController.getString("VoipGroupRemoveMemberAlertTitle", R.string.VoipGroupRemoveMemberAlertTitle));
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupRemoveMemberAlertText", R.string.VoipGroupRemoveMemberAlertText, name)));
            } else {
                textView.setText(LocaleController.getString("VoipGroupAddMemberTitle", R.string.VoipGroupAddMemberTitle));
                messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("VoipGroupAddMemberText", R.string.VoipGroupAddMemberText, name, currentChat.title)));
            }

            frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

            if (option == 0) {
                builder.setPositiveButton(LocaleController.getString("VoipGroupMute", R.string.VoipGroupMute), (dialogInterface, i) -> {
                    if (VoIPService.getSharedInstance() == null) {
                        return;
                    }
                    VoIPService.getSharedInstance().editCallMember(user, true);
                    getUndoView().showWithAction(0, UndoView.ACTION_VOIP_MUTED, user, null, null, null);
                });
            } else if (option == 2) {
                builder.setPositiveButton(LocaleController.getString("VoipGroupUserRemove", R.string.VoipGroupUserRemove), (dialogInterface, i) -> {
                    accountInstance.getMessagesController().deleteUserFromChat(currentChat.id, user, null);
                    getUndoView().showWithAction(0, UndoView.ACTION_VOIP_REMOVED, user, null, null, null);
                });
            } else {
                builder.setPositiveButton(LocaleController.getString("VoipGroupAdd", R.string.VoipGroupAdd), (dialogInterface, i) -> {
                    BaseFragment fragment = parentActivity.getActionBarLayout().fragmentsStack.get(parentActivity.getActionBarLayout().fragmentsStack.size() - 1);
                    accountInstance.getMessagesController().addUserToChat(currentChat.id, user, 0, null, fragment, () -> inviteUserToCall(userId, false));
                });
            }
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            AlertDialog dialog = builder.create();
            dialog.setBackgroundColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));
            dialog.show();
            if (option == 2) {
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
                }
            }
        } else {
            VoIPService.getSharedInstance().editCallMember(user, false);
            getUndoView().showWithAction(0, UndoView.ACTION_VOIP_UNMUTED, user, null, null, null);
        }
    }

    private boolean showMenuForCell(GroupCallUserCell view) {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
            scrimPopupWindowItems = null;
            return false;
        }

        Rect rect = new Rect();

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext());
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
        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        popupLayout.setBackgroundDrawable(shadowDrawable);
        popupLayout.setBackgroundColor(backgroundColor);

        LinearLayout linearLayout = new LinearLayout(getContext());
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

        linearLayout.setMinimumWidth(AndroidUtilities.dp(200));
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TLRPC.TL_groupCallParticipant participant = view.getParticipant();
        ArrayList<String> items = new ArrayList<>(2);
        ArrayList<Integer> icons = new ArrayList<>(2);
        ArrayList<Integer> options = new ArrayList<>(2);
        boolean isAdmin = false;
        if (currentChat.megagroup) {
            isAdmin = accountInstance.getMessagesController().getAdminRank(currentChat.id, participant.user_id) != null;
        } else {
            TLRPC.ChatFull chatFull = accountInstance.getMessagesController().getChatFull(currentChat.id);
            if (chatFull != null) {
                for (int a = 0, N = chatFull.participants.participants.size(); a < N; a++) {
                    TLRPC.ChatParticipant chatParticipant = chatFull.participants.participants.get(a);
                    if (chatParticipant.user_id == participant.user_id) {
                        isAdmin = chatParticipant instanceof TLRPC.TL_chatParticipantAdmin || chatParticipant instanceof TLRPC.TL_chatParticipantCreator;
                        break;
                    }
                }
            }
        }
        if (!isAdmin || !participant.muted) {
            if (!participant.muted || participant.can_self_unmute) {
                items.add(LocaleController.getString("VoipGroupMute", R.string.VoipGroupMute));
                icons.add(R.drawable.msg_voice_muted);
                options.add(0);
            } else {
                items.add(LocaleController.getString("VoipGroupAllowToSpeak", R.string.VoipGroupAllowToSpeak));
                icons.add(R.drawable.msg_voice_unmuted);
                options.add(1);
            }
        }
        if (!isAdmin) {
            items.add(LocaleController.getString("VoipGroupUserRemove", R.string.VoipGroupUserRemove));
            icons.add(R.drawable.msg_block2);
            options.add(2);
        }
        if (options.isEmpty()) {
            return false;
        }

        scrimPopupWindowItems = new ActionBarMenuSubItem[items.size()];
        for (int a = 0, N = items.size(); a < N; a++) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getContext(), a == 0, a == N - 1);
            if (options.get(a) != 2) {
                cell.setColors(Theme.getColor(Theme.key_voipgroup_actionBarItems), Theme.getColor(Theme.key_voipgroup_actionBarItems));
            } else {
                cell.setColors(Theme.getColor(Theme.key_voipgroup_leaveCallMenu), Theme.getColor(Theme.key_voipgroup_leaveCallMenu));
            }
            cell.setSelectorColor(Theme.getColor(Theme.key_voipgroup_listSelector));
            cell.setTextAndIcon(items.get(a), icons.get(a));
            scrimPopupWindowItems[a] = cell;
            linearLayout.addView(cell);
            final int i = a;
            cell.setOnClickListener(v1 -> {
                if (i >= options.size()) {
                    return;
                }
                processSelectedOption(participant.user_id, options.get(i));
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            });
        }
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                scrimPopupWindow = null;
                scrimPopupWindowItems = null;
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
                        scrimView = null;
                        containerView.invalidate();
                        listView.invalidate();
                        if (delayedGroupCallUpdated) {
                            delayedGroupCallUpdated = false;
                            applyCallParticipantUpdates();
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
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        int popupX = AndroidUtilities.dp(14) + listView.getMeasuredWidth() + AndroidUtilities.dp(6) - popupLayout.getMeasuredWidth();
        int popupY = (int) (listView.getY() + view.getY() + view.getMeasuredHeight());

        scrimPopupWindow.showAtLocation(listView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        listView.stopScroll();
        layoutManager.setCanScrollVertically(false);
        scrimView = view;
        containerView.invalidate();
        listView.invalidate();
        if (scrimAnimatorSet != null) {
            scrimAnimatorSet.cancel();
        }
        scrimAnimatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0, 100));
        scrimAnimatorSet.playTogether(animators);
        scrimAnimatorSet.setDuration(150);
        scrimAnimatorSet.start();
        return true;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private int usersStartRow;
        private int usersEndRow;
        private int invitedStartRow;
        private int invitedEndRow;
        private int addMemberRow;
        private int selfUserRow;
        private int lastRow;
        private int rowsCount;

        public ListAdapter(Context context) {
            mContext = context;
        }

        public boolean addSelfToCounter() {
            if (selfUserRow < 0) {
                return false;
            }
            if (VoIPService.getSharedInstance() == null) {
                return false;
            }
            return !VoIPService.getSharedInstance().isJoined();
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        private void updateRows() {
            if (delayedGroupCallUpdated) {
                return;
            }
            rowsCount = 0;
            if (ChatObject.canWriteToChat(currentChat)) {
                addMemberRow = rowsCount++;
            } else {
                addMemberRow = -1;
            }
            if (call.participants.indexOfKey(selfDummyParticipant.user_id) < 0) {
                selfUserRow = rowsCount++;
            } else {
                selfUserRow = -1;
            }
            usersStartRow = rowsCount;
            rowsCount += call.sortedParticipants.size();
            usersEndRow = rowsCount;
            if (call.invitedUsers.isEmpty()) {
                invitedStartRow = -1;
                invitedEndRow = -1;
            } else {
                invitedStartRow = rowsCount;
                rowsCount += call.invitedUsers.size();
                invitedEndRow = rowsCount;
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
            if (type == 1) {
                GroupCallUserCell cell = (GroupCallUserCell) holder.itemView;
                String key = actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled;
                cell.setGrayIconColor(key, Theme.getColor(key));
                cell.setDrawDivider(holder.getAdapterPosition() != getItemCount() - 2);
            } else if (type == 2) {
                GroupCallInvitedCell cell = (GroupCallInvitedCell) holder.itemView;
                String key = actionBar.getTag() != null ? Theme.key_voipgroup_mutedIcon : Theme.key_voipgroup_mutedIconUnscrolled;
                cell.setGrayIconColor(key, Theme.getColor(key));
                cell.setDrawDivider(holder.getAdapterPosition() != getItemCount() - 2);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    GroupCallTextCell textCell = (GroupCallTextCell) holder.itemView;
                    int color = AndroidUtilities.getOffsetColor(Theme.getColor(Theme.key_voipgroup_lastSeenTextUnscrolled), Theme.getColor(Theme.key_voipgroup_lastSeenText), actionBar.getTag() != null ? 1.0f : 0.0f, 1.0f);
                    textCell.setColors(color, color);
                    textCell.setTextAndIcon(LocaleController.getString("VoipGroupInviteMember", R.string.VoipGroupInviteMember), R.drawable.actions_addmember2, true);
                    break;
                case 1:
                    GroupCallUserCell userCell = (GroupCallUserCell) holder.itemView;
                    TLRPC.TL_groupCallParticipant participant;
                    if (position == selfUserRow) {
                        participant = selfDummyParticipant;
                    } else {
                        int row = position - usersStartRow;
                        if (delayedGroupCallUpdated) {
                            if (row >= 0 && row < oldParticipants.size()) {
                                participant = oldParticipants.get(row);
                            } else {
                                participant = null;
                            }
                        } else {
                            if (row >= 0 && row < call.sortedParticipants.size()) {
                                participant = call.sortedParticipants.get(row);
                            } else {
                                participant = null;
                            }
                        }
                    }
                    if (participant != null) {
                        userCell.setData(accountInstance, participant, call);
                    }
                    break;
                case 2:
                    GroupCallInvitedCell invitedCell = (GroupCallInvitedCell) holder.itemView;
                    Integer uid;
                    int row = position - invitedStartRow;
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
                    if (uid != null) {
                        invitedCell.setData(currentAccount, uid);
                    }
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == 1) {
                GroupCallUserCell userCell = (GroupCallUserCell) holder.itemView;
                return !userCell.isSelfUser();
            } else if (type == 3) {
                return false;
            }
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GroupCallTextCell(mContext);
                    break;
                case 1:
                    view = new GroupCallUserCell(mContext) {
                        @Override
                        protected void onMuteClick(GroupCallUserCell cell) {
                            showMenuForCell(cell);
                        }
                    };
                    break;
                case 2:
                    view = new GroupCallInvitedCell(mContext);
                    break;
                case 3:
                default:
                    view = new View(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == lastRow) {
                return 3;
            }
            if (position == addMemberRow) {
                return 0;
            }
            if (position == selfUserRow || position >= usersStartRow && position < usersEndRow) {
                return 1;
            }
            return 2;
        }
    }

    private int oldAddMemberRow;
    private int oldSelfUserRow;
    private int oldUsersStartRow;
    private int oldUsersEndRow;
    private int oldInvitedStartRow;
    private int oldInvitedEndRow;

    public void setOldRows(int addMemberRow, int selfUserRow, int usersStartRow, int usersEndRow, int invitedStartRow, int invitedEndRow) {
        oldAddMemberRow = addMemberRow;
        oldSelfUserRow = selfUserRow;
        oldUsersStartRow = usersStartRow;
        oldUsersEndRow = usersEndRow;
        oldInvitedStartRow = invitedStartRow;
        oldInvitedEndRow = invitedEndRow;
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
            if (oldItemPosition == oldCount - 1 && newItemPosition == listAdapter.rowsCount - 1) {
                return true;
            } else if (oldItemPosition == oldCount - 1 || newItemPosition == listAdapter.rowsCount - 1) {
                return false;
            }
            if ((newItemPosition == listAdapter.selfUserRow || newItemPosition >= listAdapter.usersStartRow && newItemPosition < listAdapter.usersEndRow) &&
                    (oldItemPosition == oldSelfUserRow || oldItemPosition >= oldUsersStartRow && oldItemPosition < oldUsersEndRow)) {
                TLRPC.TL_groupCallParticipant oldItem;
                TLRPC.TL_groupCallParticipant newItem;
                if (oldItemPosition == oldSelfUserRow) {
                    oldItem = selfDummyParticipant;
                } else {
                    oldItem = oldParticipants.get(oldItemPosition - oldUsersStartRow);
                }
                if (newItemPosition == listAdapter.selfUserRow) {
                    newItem = selfDummyParticipant;
                } else {
                    newItem = call.sortedParticipants.get(newItemPosition - listAdapter.usersStartRow);
                }
                return oldItem.user_id == newItem.user_id;
            } else if (newItemPosition >= listAdapter.invitedStartRow && newItemPosition < listAdapter.invitedEndRow &&
                    oldItemPosition >= oldInvitedStartRow && oldItemPosition < oldInvitedEndRow) {
                Integer oldItem = oldInvited.get(oldItemPosition - oldInvitedStartRow);
                Integer newItem = call.invitedUsers.get(newItemPosition - listAdapter.invitedStartRow);
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
        boolean changed;

        private UpdateCallback(RecyclerView.Adapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onInserted(int position, int count) {
            changed = true;
            adapter.notifyItemRangeInserted(position, count);
        }

        @Override
        public void onRemoved(int position, int count) {
            changed = true;
            adapter.notifyItemRangeRemoved(position, count);
        }

        @Override
        public void onMoved(int fromPosition, int toPosition) {
            changed = true;
            adapter.notifyItemMoved(fromPosition, toPosition);
        }

        @Override
        public void onChanged(int position, int count, @Nullable Object payload) {
            adapter.notifyItemRangeChanged(position, count, payload);
        }
    }

    private void toggleAdminSpeak() {
        TLRPC.TL_phone_toggleGroupCallSettings req = new TLRPC.TL_phone_toggleGroupCallSettings();
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
}
