package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.voip.VoIPButtonsLayout;
import org.telegram.ui.Components.voip.VoIPToggleButton;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;

public class GroupCallPipAlertView extends LinearLayout implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {

    public static final int POSITION_LEFT = 0;
    public static final int POSITION_RIGHT = 1;
    public static final int POSITION_BOTTOM = 2;
    public static final int POSITION_TOP = 3;

    FrameLayout groupInfoContainer;
    TextView titleView;
    TextView subtitleView;

    VoIPToggleButton soundButton;
    VoIPToggleButton muteButton;
    VoIPToggleButton leaveButton;

    BackupImageView avatarImageView;

    RectF rectF = new RectF();

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    LinearGradient linearGradient;
    private int position;

    float cx;
    float cy;
    private boolean invalidateGradient = true;
    int currentAccount;
    private boolean mutedByAdmin;

    public GroupCallPipAlertView(Context context, int account) {
        super(context);
        setOrientation(VERTICAL);
        currentAccount = account;

        paint.setAlpha((int) (255 * 0.92f));

        groupInfoContainer = new FrameLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    VoIPService service = VoIPService.getSharedInstance();
                    if (service != null && ChatObject.isChannelOrGiga(service.getChat())) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, getString(R.string.VoipChannelOpenVoiceChat)));
                    } else {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, getString(R.string.VoipGroupOpenVoiceChat)));
                    }
                }
            }
        };
        groupInfoContainer.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(22));
        groupInfoContainer.addView(avatarImageView, LayoutHelper.createFrame(44, 44));
        groupInfoContainer.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        groupInfoContainer.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                Intent intent = new Intent(getContext(), LaunchActivity.class).setAction("voip_chat");
                intent.putExtra("currentAccount", VoIPService.getSharedInstance().getAccount());
                getContext().startActivity(intent);
            }
        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTypeface(AndroidUtilities.bold());
        linearLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.6f)));

        linearLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        groupInfoContainer.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 55, 0, 0, 0));

        addView(groupInfoContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 10 ,10 ,10));

        soundButton = new VoIPToggleButton(context, 44f);
        soundButton.setTextSize(12);
        soundButton.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() == null) {
                return;
            }
            VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(getContext(), Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context));
        });
        soundButton.setCheckable(true);
        soundButton.setBackgroundColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.15f)), ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)));

        muteButton = new VoIPToggleButton(context, 44f);
        muteButton.setTextSize(12);
        muteButton.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() != null) {
                if (VoIPService.getSharedInstance().mutedByAdmin()) {
                    muteButton.shakeView();
                    try {
                        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null) {
                            vibrator.vibrate(200);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    VoIPService.getSharedInstance().setMicMute(!VoIPService.getSharedInstance().isMicMute(), false, true);
                }
            }
        });

        leaveButton = new VoIPToggleButton(context, 44f);
        leaveButton.setTextSize(12);
        leaveButton.setData(R.drawable.calls_decline, 0xffffffff, 0xFFCE4A4A, 0.3f, false, getString(R.string.VoipGroupLeave), false, false);
        leaveButton.setOnClickListener(v -> GroupCallActivity.onLeaveClick(getContext(), () -> GroupCallPip.updateVisibility(context), Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)));

        VoIPButtonsLayout buttonsContainer = new VoIPButtonsLayout(context);
        buttonsContainer.setChildSize(68);
        buttonsContainer.setUseStartPadding(false);
        buttonsContainer.addView(soundButton, LayoutHelper.createFrame(68, 63));
        buttonsContainer.addView(muteButton, LayoutHelper.createFrame(68, 63));
        buttonsContainer.addView(leaveButton, LayoutHelper.createFrame(68, 63));
        setWillNotDraw(false);

        addView(buttonsContainer,  LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6, 0));
    }

    float muteProgress;
    float mutedByAdminProgress;

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        boolean isMute = VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute() || mutedByAdmin;
        if (isMute && muteProgress != 1f) {
            muteProgress += 16f / 150f;
            if (muteProgress >= 1f) {
                muteProgress = 1f;
            }
            invalidateGradient = true;
            invalidate();
        } else if (!isMute && muteProgress != 0){
            muteProgress -= 16f / 150f;
            if (muteProgress < 0f) {
                muteProgress = 0f;
            }
            invalidateGradient = true;
            invalidate();
        }

        if (mutedByAdmin && mutedByAdminProgress != 1f) {
            mutedByAdminProgress += 16f / 150f;
            if (mutedByAdminProgress >= 1f) {
                mutedByAdminProgress = 1f;
            }
            invalidateGradient = true;
            invalidate();
        } else if (!mutedByAdmin && mutedByAdminProgress != 0){
            mutedByAdminProgress -= 16f / 150f;
            if (mutedByAdminProgress < 0f) {
                mutedByAdminProgress = 0f;
            }
            invalidateGradient = true;
            invalidate();
        }
        if (invalidateGradient) {
            int color1 = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_overlayAlertGradientMuted), Theme.getColor(Theme.key_voipgroup_overlayAlertGradientUnmuted), (1f - muteProgress));
            int color2 = ColorUtils.blendARGB(Theme.getColor(Theme.key_voipgroup_overlayAlertGradientMuted2), Theme.getColor(Theme.key_voipgroup_overlayAlertGradientUnmuted2), (1f - muteProgress));

            color1 = ColorUtils.blendARGB(color1, Theme.getColor(Theme.key_voipgroup_overlayAlertMutedByAdmin), mutedByAdminProgress);
            color2 = ColorUtils.blendARGB(color2, Theme.getColor(Theme.key_voipgroup_overlayAlertMutedByAdmin2), mutedByAdminProgress);

            invalidateGradient = false;
            if (position == POSITION_LEFT) {
                linearGradient = new LinearGradient(-AndroidUtilities.dp(60), cy - getTranslationY(), getMeasuredWidth(), getMeasuredHeight() / 2f, new int[]{color1, color2}, null, Shader.TileMode.CLAMP);
            } else if (position == POSITION_RIGHT) {
                linearGradient = new LinearGradient(0, getMeasuredHeight() / 2f, getMeasuredWidth() + AndroidUtilities.dp(60), cy - getTranslationY(), new int[]{color2, color1}, null, Shader.TileMode.CLAMP);
            } else if (position == POSITION_BOTTOM) {
                linearGradient = new LinearGradient(cx - getTranslationX(), -AndroidUtilities.dp(60), getMeasuredWidth() / 2f, getMeasuredHeight(), new int[]{color1, color2}, null, Shader.TileMode.CLAMP);
            } else {
                linearGradient = new LinearGradient(getMeasuredWidth() / 2f, 0, cx - getTranslationX(), getMeasuredHeight() +  + AndroidUtilities.dp(60), new int[]{color2, color1}, null, Shader.TileMode.CLAMP);
            }
        }
        rectF.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        paint.setShader(linearGradient);
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
        float x, y;
        if (position == POSITION_LEFT) {
            y = cy - getTranslationY();
            x = 0;
        } else if (position == POSITION_RIGHT) {
            y = cy - getTranslationY();
            x = getMeasuredWidth();
        } else if (position == POSITION_BOTTOM) {
            x = cx - getTranslationX();
            y = 0;
        } else {
            x = cx - getTranslationX();
            y = getMeasuredHeight();
        }
        setPivotX(x);
        setPivotY(y);

        canvas.save();
        if (position == POSITION_LEFT) {
            canvas.clipRect(x - AndroidUtilities.dp(15), y - AndroidUtilities.dp(15), x, y + AndroidUtilities.dp(15));
            canvas.translate(AndroidUtilities.dp(3), 0);
            canvas.rotate(45, x, y);
        } else if (position == POSITION_RIGHT) {
            canvas.clipRect(x, y - AndroidUtilities.dp(15), x + AndroidUtilities.dp(15), y + AndroidUtilities.dp(15));
            canvas.translate(-AndroidUtilities.dp(3), 0);
            canvas.rotate(45, x, y);
        } else if (position == POSITION_BOTTOM) {
            canvas.clipRect(x - AndroidUtilities.dp(15) , y - AndroidUtilities.dp(15), x + AndroidUtilities.dp(15), y);
            canvas.rotate(45, x, y);
            canvas.translate(0, AndroidUtilities.dp(3));
        } else {
            canvas.clipRect(x - AndroidUtilities.dp(15) , y, x + AndroidUtilities.dp(15), y + AndroidUtilities.dp(15));
            canvas.rotate(45, x, y);
            canvas.translate(0, -AndroidUtilities.dp(3));
        }

        rectF.set(x - AndroidUtilities.dp(10), y - AndroidUtilities.dp(10), x + AndroidUtilities.dp(10), y + AndroidUtilities.dp(10));

        canvas.drawRoundRect(rectF, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
        canvas.restore();

        super.onDraw(canvas);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(230), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.groupCall != null) {
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            final TLRPC.Chat chat = service.getChat();
            avatarDrawable.setColor(
                Theme.getColor(Theme.keys_avatar_background[AvatarDrawable.getColorIndex(chat != null ? chat.id : 0)]),
                Theme.getColor(Theme.keys_avatar_background2[AvatarDrawable.getColorIndex(chat != null ? chat.id : 0)])
            );
            avatarDrawable.setInfo(currentAccount, chat);
            if (chat != null) {
                avatarImageView.setImage(ImageLocation.getForLocal(chat.photo.photo_small), "50_50", avatarDrawable, null);
            }

            String titleStr;
            if (service.isConference() && service.groupCall != null) {
                if (service.groupCall.sortedParticipants.size() == 1) {
                    titleStr = getString(R.string.ConferenceChat);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < Math.min(3, service.groupCall.sortedParticipants.size()); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        final long did = DialogObject.getPeerDialogId(service.groupCall.sortedParticipants.get(i).peer);
                        sb.append(DialogObject.getShortName(did));
                    }
                    if (service.groupCall.sortedParticipants.size() > 3) {
                        sb.append(" ");
                        sb.append(LocaleController.formatPluralString("AndOther", service.groupCall.sortedParticipants.size() - 3));
                    }
                    titleStr = sb.toString();
                }
            } else if (!TextUtils.isEmpty(service.groupCall.call.title)) {
                titleStr = service.groupCall.call.title;
            } else if (chat != null) {
                titleStr = chat.title;
            } else {
                titleStr = "";
            }
            if (titleStr != null) {
                titleStr = titleStr.replace("\n", " ").replaceAll(" +", " ").trim();
            }
            titleView.setText(titleStr);

            updateMembersCount();
            service.registerStateListener(this);

            if (VoIPService.getSharedInstance() != null) {
                mutedByAdmin = VoIPService.getSharedInstance().mutedByAdmin();
            }
            mutedByAdminProgress = mutedByAdmin ? 1f : 0;
            boolean isMute = VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().isMicMute() || mutedByAdmin;
            muteProgress = isMute ? 1f : 0f;
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this,NotificationCenter.groupCallUpdated);
        updateButtons(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }

        NotificationCenter.getInstance(currentAccount).removeObserver(this,NotificationCenter.groupCallUpdated);
    }

    private void updateMembersCount() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.groupCall != null) {
            int currentCallState = service.getCallState();
            if (!service.isSwitchingStream() && (currentCallState == VoIPService.STATE_WAIT_INIT || currentCallState == VoIPService.STATE_WAIT_INIT_ACK || currentCallState == VoIPService.STATE_CREATING || currentCallState == VoIPService.STATE_RECONNECTING)) {
                subtitleView.setText(getString("VoipGroupConnecting", R.string. VoipGroupConnecting));
            } else {
                subtitleView.setText(LocaleController.formatPluralString(service.groupCall.call.rtmp_stream ? "ViewersWatching" : "Participants", service.groupCall.call.participants_count));
            }
        }
    }

    private void updateButtons(boolean animated) {
        if (soundButton == null || muteButton == null) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }

        boolean bluetooth = service.isBluetoothOn();
        boolean checked = !bluetooth && service.isSpeakerphoneOn();
        soundButton.setChecked(checked, animated);

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

        if (service.mutedByAdmin()) {
            muteButton.setData(R.drawable.calls_unmute, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)), 0.1f, true, getString(R.string.VoipMutedByAdminShort), true, animated);
        } else {
            muteButton.setData(R.drawable.calls_unmute, Color.WHITE, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * (service.isMicMute() ? 0.3f : 0.15f))), 0.1f, true, service.isMicMute() ? getString(R.string.VoipUnmute) : getString(R.string.VoipMute), service.isMicMute(), animated);
        }
        invalidate();
    }

    @Override
    public void onAudioSettingsChanged() {
        updateButtons(true);
    }

    @Override
    public void onStateChanged(int state) {
        updateMembersCount();
    }

    public void setPosition(int position, float cx, float cy) {
        this.position = position;
        this.cx = cx;
        this.cy = cy;
        invalidate();
        invalidateGradient = true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupCallUpdated) {
            updateMembersCount();
            if (VoIPService.getSharedInstance() != null) {
                boolean mutedByAdminNew = VoIPService.getSharedInstance().mutedByAdmin();
                if (mutedByAdminNew != mutedByAdmin) {
                    mutedByAdmin = mutedByAdminNew;
                    invalidate();
                }
            }
        }
    }
}
