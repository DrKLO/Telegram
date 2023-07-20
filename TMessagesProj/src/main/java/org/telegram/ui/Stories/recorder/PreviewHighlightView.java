package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.PeerStoriesView;
import org.telegram.ui.Stories.StoryCaptionView;

public class PreviewHighlightView extends FrameLayout {

    private int currentAccount;
    private int storiesCount = 1;

    private final FrameLayout top;
    private final FrameLayout bottom;

    private final StoryCaptionView storyCaptionView;

    public PreviewHighlightView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;

        TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();

        top = new FrameLayout(getContext()) {
            private RectF rectF = new RectF();
            private Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);

                barPaint.setColor(0xffffffff);
                float w = (getWidth() - dpf2(5) * 2 - dpf2(2 * (storiesCount - 1))) / storiesCount;
                float x = dpf2(5);
                for (int i = 0; i < storiesCount; ++i) {
                    rectF.set(x, dpf2(8), x + w, dpf2(8 + 2));
                    barPaint.setAlpha(i < storiesCount - 1 ? 0xff : 0x55 + 0x30);
                    canvas.drawRoundRect(rectF, dpf2(1), dpf2(1), barPaint);
                    x += w + dpf2(2);
                }
            }
        };
        PeerStoriesView.PeerHeaderView headerView = new PeerStoriesView.PeerHeaderView(getContext(), null);
        headerView.backupImageView.getAvatarDrawable().setInfo(me);
        headerView.backupImageView.setForUserOrChat(me, headerView.backupImageView.getAvatarDrawable());
        headerView.titleView.setText(UserObject.getUserName(me));
        headerView.setSubtitle(LocaleController.getString("RightNow", R.string.RightNow), false);
        top.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 17, 0, 0));

        ImageView closeIconView = new ImageView(context);
        closeIconView.setImageDrawable(getContext().getResources().getDrawable(R.drawable.ic_close_white).mutate());
        closeIconView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        top.addView(closeIconView, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.TOP, 12, 15, 12, 0));
        addView(top, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        bottom = new FrameLayout(getContext());
        storyCaptionView = new StoryCaptionView(getContext(), resourcesProvider);
        storyCaptionView.disableTouches = true;
        storyCaptionView.setTranslationY(dp(8));
        bottom.addView(storyCaptionView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 64));

//        ImageView shareButton = new ImageView(context);
//        shareButton.setImageDrawable(getContext().getResources().getDrawable(R.drawable.msg_share_filled));
//        int padding = AndroidUtilities.dp(8);
//        shareButton.setPadding(padding, padding, padding, padding);
//        shareButton.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(20), ColorUtils.setAlphaComponent(Color.BLACK, 76)));
//        bottom.addView(shareButton, LayoutHelper.createFrame(40, 40, Gravity.BOTTOM | Gravity.RIGHT, 10, 10, 10, 64));

        ImageView shareButton = new ImageView(context);
        shareButton.setImageResource(R.drawable.msg_share);
        shareButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        bottom.addView(shareButton, LayoutHelper.createFrame(28, 28, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 12, 16));

        FrameLayout editLayout = new FrameLayout(context);
        editLayout.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(22), ColorUtils.setAlphaComponent(Color.BLACK, 122)));

        TextView editText = new TextView(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(1694498815);
        editText.setText(LocaleController.getString("ReplyPrivately", R.string.ReplyPrivately));
        editLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 24, 0, 24, 0));

//        ImageView likeButton = new ImageView(context);
//        likeButton.setImageResource(R.drawable.msg_input_like);
//        likeButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
//        editLayout.addView(likeButton, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 103, 0));

        ImageView attachButton = new ImageView(context);
        attachButton.setImageResource(R.drawable.input_attach);
        attachButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
        editLayout.addView(attachButton, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 9, 0));

//        ImageView micButton = new ImageView(context);
//        micButton.setImageResource(R.drawable.input_mic);
//        micButton.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));
//        editLayout.addView(micButton, LayoutHelper.createFrame(28, 28, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 9, 0));

        bottom.addView(editLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 9, 8, 55, 8));

        addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        top.setAlpha(0f);
        bottom.setAlpha(0f);
    }

    public void updateCount() {
        storiesCount = MessagesController.getInstance(currentAccount).getStoriesController().getSelfStoriesCount() + 1;
        top.invalidate();
    }

    public void updateCaption(CharSequence caption) {
        caption = AnimatedEmojiSpan.cloneSpans(caption);
        storyCaptionView.captionTextview.setText(caption);
    }

    private boolean shownTop = false, shownBottom = false;

    public void show(boolean topOrBottom, boolean show, View fadeView) {
        if (topOrBottom) {
            if (shownTop == show)
                return;
            shownTop = show;
        } else {
            if (shownBottom == show)
                return;
            shownBottom = show;
        }

        View view = topOrBottom ? top : bottom;

        view.clearAnimation();
        view.animate().alpha(show ? (topOrBottom ? .5f : .2f) : 0f).start();

        if (fadeView != null) {
            fadeView.clearAnimation();
            fadeView.animate().alpha(show ? 0f : 1f).start();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}
