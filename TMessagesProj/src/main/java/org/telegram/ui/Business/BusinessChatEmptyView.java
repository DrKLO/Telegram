package org.telegram.ui.Business;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.StarParticlesView;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.Locale;

public class BusinessChatEmptyView extends LinearLayout {

    private TextView titleView;
    private TextView descriptionView, descriptionView2;

    public RLottieImageView imageView;
    private final Theme.ResourcesProvider resourcesProvider;

    private class DotTextView extends TextView {
        public DotTextView(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (getPaddingLeft() > 0) {
                canvas.drawCircle((getPaddingLeft() - dp(2.5f)) / 2f, dp(10), dp(2.5f), getPaint());
            }
            super.dispatchDraw(canvas);
        }
    }

    public BusinessChatEmptyView(Context context, int chatMode, long type, long topic_id, String quickReplyName, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        setOrientation(VERTICAL);
        this.resourcesProvider = resourcesProvider;

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        titleView.setGravity(Gravity.CENTER);

        descriptionView = new DotTextView(context);
        descriptionView.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        descriptionView.setGravity(Gravity.CENTER);
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descriptionView.setGravity(Gravity.CENTER_HORIZONTAL);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
//        imageView.setAnimation(R.raw.large_message_lock, 80, 80);
//        imageView.setOnClickListener(v -> {
//            imageView.setProgress(0);
//            imageView.playAnimation();
//        });
//        imageView.playAnimation();

        int descriptionViewMargin = 12;
        descriptionView.setMaxWidth(dp(160));
        if (QuickRepliesController.GREETING.equalsIgnoreCase(quickReplyName)) {
            imageView.setImageResource(R.drawable.large_greeting);
            titleView.setText(LocaleController.getString(R.string.BusinessGreetingIntroTitle));
            descriptionViewMargin = 22;
            descriptionView.setText(LocaleController.getString(R.string.BusinessGreetingIntro));
            descriptionView.setMaxWidth(Math.min(dp(160), HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint())));
        } else if (QuickRepliesController.AWAY.equalsIgnoreCase(quickReplyName)) {
            imageView.setImageResource(R.drawable.large_away);
            titleView.setText(LocaleController.getString(R.string.BusinessAwayIntroTitle));
            descriptionViewMargin = 22;
            descriptionView.setText(LocaleController.getString(R.string.BusinessAwayIntro));
            descriptionView.setMaxWidth(Math.min(dp(160), HintView2.cutInFancyHalf(descriptionView.getText(), descriptionView.getPaint())));
        } else if (chatMode == ChatActivity.MODE_QUICK_REPLIES) {
            imageView.setImageResource(R.drawable.large_quickreplies);

            QuickRepliesController.QuickReply reply = QuickRepliesController.getInstance(UserConfig.selectedAccount).findReply(topic_id);
            String replyName = reply == null ? quickReplyName : reply.name;

            titleView.setText(LocaleController.getString(R.string.BusinessRepliesIntroTitle));
            descriptionView.setMaxWidth(dp(208));
            descriptionView.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            descriptionView.setGravity(Gravity.LEFT);
            descriptionView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.BusinessRepliesIntro1, replyName)));
            descriptionView.setPadding(dp(28), 0, 0, 0);

            descriptionView2 = new DotTextView(context);
            descriptionView2.setMaxWidth(dp(208));
            descriptionView2.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            descriptionView2.setGravity(Gravity.LEFT);
            descriptionView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            descriptionView2.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.BusinessRepliesIntro2)));
            descriptionView2.setPadding(dp(28), 0, 0, 0);
        }

        addView(imageView, LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 17, 20, 9));
        addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 20, 0, 20, 9));
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, descriptionViewMargin, 0, descriptionViewMargin, descriptionView2 != null ? 9 : 19));
        if (descriptionView2 != null) {
            addView(descriptionView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 12, 0, 12, 19));
        }

        updateColors();
    }

    private void updateColors() {
        titleView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        descriptionView.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        if (descriptionView2 != null) {
            descriptionView2.setTextColor(getThemedColor(Theme.key_chat_serviceText));
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
