package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

public class ChatReplyContainer extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    public Layout[] layouts = new Layout[2];

    public ChatReplyContainer(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        for (int i = 0; i < layouts.length; ++i) {
            layouts[i] = new Layout(context, resourcesProvider);
            addView(layouts[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        }
        layouts[0].setVisibility(View.VISIBLE);
        layouts[1].setVisibility(View.GONE);
    }

    public Layout current() {
        return layouts[0];
    }

    public void switchLayouts() {
        switchLayouts(true);
    }
    public void switchLayouts(boolean animated) {
        final Layout tmp = layouts[0];
        layouts[0] = layouts[1];
        layouts[1] = tmp;

        if (animated) {
            layouts[0].active = true;
            layouts[0].setVisibility(View.VISIBLE);
            layouts[0].setScaleX(0.8f);
            layouts[0].setScaleY(0.8f);
            layouts[0].setAlpha(0.0f);
            layouts[0].setTranslationY(dp(20));
            layouts[0].animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .translationY(0)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();

            final Layout outgoing = layouts[1];
            layouts[1].active = false;
            layouts[1].setVisibility(View.VISIBLE);
            layouts[1].animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .alpha(0.0f)
                .translationY(-dp(20))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                })
                .start();
        } else {
            layouts[1].setVisibility(View.GONE);
            layouts[1].active = false;

            layouts[0].setVisibility(View.VISIBLE);
            layouts[0].setScaleX(1.0f);
            layouts[0].setScaleY(1.0f);
            layouts[0].setAlpha(1.0f);
            layouts[0].setTranslationY(0);
            layouts[0].active = true;
        }
    }

    public class Layout extends FrameLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;

        public ImageView icon;
        public SimpleTextView name;
        public SimpleTextView obj;
        public SimpleTextView objHint;
        public BackupImageView image;

        public boolean hasSpoiler;
        public boolean active;

        public Layout(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            addView(icon, LayoutHelper.createFrame(52, 46, Gravity.TOP | Gravity.LEFT));

            name = new SimpleTextView(context);
            name.setTextSize(14);
            name.setTypeface(AndroidUtilities.bold());
            addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

            obj = new SimpleTextView(context);
            obj.setTextSize(14);
            NotificationCenter.listenEmojiLoading(obj);
            addView(obj, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 24, 0, 0));

            objHint = new SimpleTextView(context);
            objHint.setTextSize(14);
            objHint.setText(LocaleController.getString(R.string.TapForForwardingOptions));
            objHint.setAlpha(0f);
            addView(objHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.TOP | Gravity.LEFT, 52, 24, 0, 0));

            SpoilerEffect replySpoilerEffect = new SpoilerEffect();
            image = new BackupImageView(context) {
                Path path = new Path();

                @Override
                public void draw(Canvas canvas) {
                    super.draw(canvas);

                    if (hasSpoiler) {
                        path.rewind();
                        AndroidUtilities.rectTmp.set(imageReceiver.getImageX(), imageReceiver.getImageY(), imageReceiver.getImageX2(), imageReceiver.getImageY2());
                        path.addRoundRect(AndroidUtilities.rectTmp, dp(2), dp(2), Path.Direction.CW);

                        canvas.save();
                        canvas.clipPath(path);

                        int sColor = Color.WHITE;
                        replySpoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f)));
                        replySpoilerEffect.setBounds((int) imageReceiver.getImageX(), (int) imageReceiver.getImageY(), (int) imageReceiver.getImageX2(), (int) imageReceiver.getImageY2());
                        replySpoilerEffect.draw(canvas);
                        invalidate();

                        canvas.restore();
                    }
                }
            };
            image.setRoundRadius(dp(6));
            addView(image, LayoutHelper.createFrame(34, 34, Gravity.TOP | Gravity.LEFT, 52, 6, 0, 0));

            updateColors();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (!active) return false;
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public void updateColors() {
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            name.setTextColor(Theme.getColor(Theme.key_chat_replyPanelName, resourcesProvider));
            obj.setTextColor(Theme.getColor(Theme.key_glass_defaultText, resourcesProvider));
            obj.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
            objHint.setTextColor(Theme.getColor(Theme.key_glass_defaultText, resourcesProvider));
        }
    }

}
