package org.telegram.ui;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.VoIPController;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BetterRatingView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.RLottieImageView;

public class VoIPFeedbackActivity extends Activity {
    Runnable onDismiss;
    int duration;
    boolean isVideo;
    long callID;
    long accessHash;
    int account;
    boolean userInitiative;
    long chatID;

    boolean initializedParams = false;

    public VoIPFeedbackActivity() {
        super();
    }

    protected void initParams() {

        onDismiss = this::finish;
        isVideo = getIntent().getBooleanExtra("call_video", false);
        callID = getIntent().getLongExtra("call_id", 0);
        accessHash = getIntent().getLongExtra("call_access_hash", 0);
        account = getIntent().getIntExtra("account", 0);
        duration = getIntent().getIntExtra("call_duration", 0);
        userInitiative = getIntent().getBooleanExtra("user_initiative", false);
        chatID = getIntent().getLongExtra("chat_id",0);
        initializedParams = true;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        overridePendingTransition(0, 0);
        initParams();

        VOIPFeedBackView responseView = new VOIPFeedBackView(this, onDismiss, duration, isVideo, callID,chatID, accessHash, account, userInitiative);

        setContentView(responseView.createView());

    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }


    private class VOIPFeedBackView extends BaseFragment {

        Runnable onDismiss;
        boolean isVideo;
        long callID;
        long accessHash;
        int account;
        Context context;
        boolean userInitiative;
        int duration;

        long chatID;

        Paint overlayPaint = new Paint();
        Paint overlayBottomPaint = new Paint();


        public VOIPFeedBackView(Context context, Runnable onDismiss, int duration, boolean isVideo, final long callID,final long chatID, final long accessHash, final int account, final boolean userInitiative) {

            this.context = context;
            this.onDismiss = onDismiss;
            this.isVideo = isVideo;
            this.callID = callID;
            this.chatID = chatID;
            this.accessHash = accessHash;
            this.account = account;
            this.userInitiative = userInitiative;
            this.duration = duration;
        }


        public View createView() {


            FrameLayout frameLayout = new FrameLayout(this.context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    super.dispatchDraw(canvas);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight(), overlayPaint);
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                        canvas.drawRect(0, getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight(), overlayBottomPaint);
                    }
                }
            };

            frameLayout.setClipToPadding(false);
            frameLayout.setClipChildren(false);
            frameLayout.setPadding(10, 10, 10, 10);
            MotionBackgroundDrawable drawable = new MotionBackgroundDrawable(0xFF08B0A3, 0xFF17AAE4, 0xFF3B7AF1, 0xFF4576E9, false);
            frameLayout.setBackground(drawable);

            ImageView backIcon = new ImageView(context);
            backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
            backIcon.setImageResource(R.drawable.msg_calls_minimize);
            backIcon.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            backIcon.setContentDescription(LocaleController.getString("Back", R.string.Back));
            backIcon.setOnClickListener(listener -> {
                finish();
            });

            frameLayout.addView(backIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));

            BackupImageView roundedIcon = new BackupImageView(context);

            roundedIcon.setRoundRadius(AndroidUtilities.dp(140/2));
            roundedIcon.setImageDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(140/2),Color.BLACK));

            ImageView phoneIcon = new ImageView(context);
            phoneIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
            phoneIcon.setImageResource(R.drawable.calls_decline);
            phoneIcon.setAdjustViewBounds(true);
            phoneIcon.setMaxHeight(30);
            phoneIcon.setMaxWidth(30);
            phoneIcon.setPadding(0,0,AndroidUtilities.dp(1),0);

            TextView callEnded = new TextView(context);
            callEnded.setTextSize(AndroidUtilities.dp(17));
            callEnded.setText("Call ended");
            callEnded.setTextColor(Color.WHITE);

            LinearLayout centerView = new LinearLayout(context);

            centerView.setOrientation(LinearLayout.VERTICAL);


            centerView.addView(roundedIcon,LayoutHelper.createLinear(AndroidUtilities.dp(140/2), AndroidUtilities.dp(140/2),Gravity.CENTER,0,0,0,AndroidUtilities.dp(5)));

            centerView.addView(callEnded,LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,LayoutHelper.WRAP_CONTENT,Gravity.TOP|Gravity.CENTER,10,10,10,10));

            ///centerView.addView(roundedIcon);

            TextView timerView = new TextView(context);
            timerView.setText(formatTime(duration));
            timerView.setPadding(0,AndroidUtilities.dp(5),0,0);
            timerView.setTextColor(Color.WHITE);


            LinearLayout phoneView = new LinearLayout(context);
            phoneView.setOrientation(LinearLayout.HORIZONTAL);
            phoneView.setGravity(LinearLayout.HORIZONTAL);


            TLRPC.User calledUser = getMessagesController().getUser((chatID));


            if (calledUser != null){
                if (calledUser.photo == null) {
                    AvatarDrawable userDp = new AvatarDrawable(calledUser);
                    roundedIcon.setImageDrawable(userDp);
                } else {
                    roundedIcon.setImage(ImageLocation.getForUserOrChat(calledUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(145), 0x7F_FF_FF_FF), calledUser);
                }
            }


            phoneView.addView(phoneIcon,LayoutHelper.createLinear(50,30,Gravity.RIGHT,0,0,0,0,0));
            phoneView.addView(timerView,LayoutHelper.createLinear(50,30,Gravity.RIGHT,0,0,0,0,0));


            centerView.addView(phoneView,LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,LayoutHelper.WRAP_CONTENT,Gravity.CENTER));

            LinearLayout alertView = new LinearLayout(context);
            alertView.setOrientation(LinearLayout.VERTICAL);

            alertView.setBackgroundColor(0x0F_00_00_00);
            int pad = AndroidUtilities.dp(24);
            alertView.setPadding(pad, pad, pad, pad);
            alertView.setBackground(Theme.createRoundRectDrawable(40, 0x1F_00_00_00));

            TextView text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text.setGravity(Gravity.CENTER);
            text.setText(LocaleController.getString("VoipRateCallAlert", R.string.VoipRateCallAlert));


            TextView alertTitle = new TextView(context);
            alertTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            alertTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            alertTitle.setGravity(Gravity.TOP | Gravity.CENTER);
            alertTitle.setPadding(0, 10, 0, 10);
            alertTitle.setText("Rate this call");
            alertTitle.setTypeface(Typeface.DEFAULT_BOLD);

            alertView.addView(alertTitle);
            alertView.addView(text);


            BetterRatingView bar = new BetterRatingView(context);

            alertView.addView(bar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            final LinearLayout problemsWrap = new LinearLayout(context);
            problemsWrap.setOrientation(LinearLayout.VERTICAL);

            View.OnClickListener problemCheckboxClickListener = v -> {
                CheckBoxCell check = (CheckBoxCell) v;
                check.setChecked(!check.isChecked(), true);
            };

            RLottieImageView animation = new RLottieImageView(context);
            animation.setAnimation(R.raw.animated_sticker, 350, 500);
            animation.setAutoRepeat(true);
            animation.setVisibility(View.GONE);

            bar.setOnRatingChangeListener(l->{
                final TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
                req.rating = bar.getRating();

                animation.setVisibility(View.VISIBLE);
                Log.e("Telegram","Number of l "+l);
                for (int i=0;i<l;i++){
                    bar.images[i].playAnimation();
                }
                animation.playAnimation();

                req.peer = new TLRPC.TL_inputPhoneCall();
                req.peer.access_hash = accessHash;
                req.peer.id = callID;
                req.user_initiative = userInitiative;
                req.rating = l;

                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_updates) {
                        TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                        MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                    }
                });
            });
            final String[] problems = {isVideo ? "distorted_video" : null, isVideo ? "pixelated_video" : null, "echo", "noise", "interruptions", "distorted_speech", "silent_local", "silent_remote", "dropped"};
            for (int i = 0; i < problems.length; i++) {
                if (problems[i] == null) {
                    continue;
                }
                CheckBoxCell check = new CheckBoxCell(context, 1);
                check.setClipToPadding(false);
                check.setTag(problems[i]);
                String label = null;
                switch (i) {
                    case 0:
                        label = LocaleController.getString("RateCallVideoDistorted", R.string.RateCallVideoDistorted);
                        break;
                    case 1:
                        label = LocaleController.getString("RateCallVideoPixelated", R.string.RateCallVideoPixelated);
                        break;
                    case 2:
                        label = LocaleController.getString("RateCallEcho", R.string.RateCallEcho);
                    case 3:
                        label = LocaleController.getString("RateCallNoise", R.string.RateCallNoise);
                        break;
                    case 4:
                        label = LocaleController.getString("RateCallInterruptions", R.string.RateCallInterruptions);
                        break;
                    case 5:
                        label = LocaleController.getString("RateCallDistorted", R.string.RateCallDistorted);
                        break;
                    case 6:
                        label = LocaleController.getString("RateCallSilentLocal", R.string.RateCallSilentLocal);
                        break;
                    case 7:
                        label = LocaleController.getString("RateCallSilentRemote", R.string.RateCallSilentRemote);
                        break;
                    case 8:
                        label = LocaleController.getString("RateCallDropped", R.string.RateCallDropped);
                        break;
                }
                check.setText(label, null, false, false);
                check.setOnClickListener(problemCheckboxClickListener);
                check.setTag(problems[i]);
                problemsWrap.addView(check);
            }
            alertView.addView(problemsWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8, 0, -8, 0));
            problemsWrap.setVisibility(View.GONE);

            final EditTextBoldCursor commentBox = new EditTextBoldCursor(context);
            commentBox.setHint(LocaleController.getString("VoipFeedbackCommentHint", R.string.VoipFeedbackCommentHint));
            commentBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            commentBox.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            commentBox.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
            commentBox.setBackground(null);
            commentBox.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_dialogTextRed2));
            commentBox.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            commentBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            commentBox.setVisibility(View.GONE);
            alertView.addView(commentBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 8, 8, 0));

            final boolean[] includeLogs = {true};
            final CheckBoxCell checkbox = new CheckBoxCell(context, 1);
            View.OnClickListener checkClickListener = v -> {
                includeLogs[0] = !includeLogs[0];
                checkbox.setChecked(includeLogs[0], true);
            };
            checkbox.setText(LocaleController.getString("CallReportIncludeLogs", R.string.CallReportIncludeLogs), null, true, false);
            checkbox.setClipToPadding(false);
            checkbox.setOnClickListener(checkClickListener);
            alertView.addView(checkbox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -8, 0, -8, 0));

            final TextView logsText = new TextView(context);
            logsText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            logsText.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
            logsText.setText(LocaleController.getString("CallReportLogsExplain", R.string.CallReportLogsExplain));
            logsText.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            logsText.setOnClickListener(checkClickListener);
            alertView.addView(logsText);

            checkbox.setVisibility(View.GONE);
            logsText.setVisibility(View.GONE);

            Button closeBtn = new Button(context);

            closeBtn.setPadding(0, 10, 0, 10);
            closeBtn.setBackground(Theme.createRoundRectDrawable(20, Color.WHITE));
            closeBtn.setTextColor(0xFF_00_00_FF);
            closeBtn.setText("Close");

            closeBtn.setOnClickListener(listener -> {
                finish();
            });


           centerView.addView(alertView, LayoutHelper.createLinear(300, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 0));

           // centerView.setVisibility(View.VISIBLE);
            frameLayout.addView(centerView,LayoutHelper.createFrame(300,LayoutHelper.WRAP_CONTENT,Gravity.CENTER,0,0,0,AndroidUtilities.dp(40)));

            frameLayout.addView(closeBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 30, 0, 30, AndroidUtilities.dp(30)));

            frameLayout.addView(animation,LayoutHelper.createFrame(200,300,Gravity.CENTER,0,AndroidUtilities.dp(30),0,0));

            return frameLayout;

        }

        private String formatTime(int duration) {
            String formattedTime = "";
            if (duration > 3600) {
                // get number of hours
                formattedTime += String.format("%02d",duration / 3600) + ":";
            }
            if (duration > 60) {
                // minutes
                formattedTime += String.format("%02d",(duration % 3600) / 60) + ":";
            } else {
                formattedTime += "00:";
            }
            formattedTime += String.format("%02d",duration % 60);

            return formattedTime;

        }
    }
}
