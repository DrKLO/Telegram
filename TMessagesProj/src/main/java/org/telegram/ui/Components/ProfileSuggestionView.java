package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.Utilities.clamp01;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RenderNode;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

public class ProfileSuggestionView extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout layout;
    private final LinearLayout textLayout;
    public final ImageView closeView;
    private final TextView titleView;
    private final TextView subtitleView;

    public ProfileSuggestionView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.resourcesProvider = resourcesProvider;

        layout = new FrameLayout(context);
        layout.setPadding(dp(14), dp(10), dp(14), dp(10));
        layout.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.multAlpha(Color.BLACK, 0.175f)));
        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 12, 13, 12, 13));
        ScaleStateListAnimator.apply(layout, .025f, 1.2f);

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 26, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setText(Emoji.replaceEmoji(getString(R.string.PasskeyPopupTitle), titleView.getPaint().getFontMetricsInt(), false));
        NotificationCenter.listenEmojiLoading(titleView);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.multAlpha(0xFFFFFFFF, 0.75f));
        subtitleView.setSingleLine();
        subtitleView.setLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setText(getString(R.string.PasskeyPopupText));
        NotificationCenter.listenEmojiLoading(subtitleView);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        closeView = new ImageView(context);
        closeView.setImageResource(R.drawable.ic_layer_close);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Color.WHITE, 0.85f), PorterDuff.Mode.SRC_IN));
        layout.addView(closeView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        ScaleStateListAnimator.apply(closeView);

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(88), MeasureSpec.EXACTLY)
        );
    }

    public float clipHeight = -1;
    private final Path clipAvatarPath = new Path();
    private final Path clipPath = new Path();

    public boolean isOpeningLayout = true;
    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        final float shown = clamp01(ilerp(newHeight, 0, getHeight()));
        layout.setAlpha(shown);
        layout.setScaleX(lerp(0.95f, 1.0f, shown));
        layout.setScaleY(lerp(0.95f, 1.0f, shown));
        setTranslationY(y);
        invalidate();
    }

    private ProfileActivity.AvatarImageView avatarView;
    private float renderNodeScale;
    private float renderNodeTranslateY;
    private RenderNode renderNode;

    private int activeCount = 0;
    private boolean ignoreRect = false;
    private float currentHeight = 0;

    public void drawingBlur(boolean drawing) {
//        if (ignoreRect != drawing || renderNode != null) {
//            ignoreRect = drawing;
//            renderNode = null;
//            avatarView = null;
//            invalidate();
//        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
//        this.ignoreRect = false;
//        this.renderNode = renderNode;
//        this.avatarView = avatarView;
//        this.renderNodeScale = scale;
//        this.renderNodeTranslateY = dy;
//        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (clipHeight >= 0f || currentHeight < getHeight()) {
            final float bottom = Math.min(currentHeight, clipHeight >= 0f ? clipHeight - getY() : getHeight());
            if (ev.getY() > bottom)
                return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        if (clipHeight >= 0f || currentHeight < getHeight()) {
            final float bottom = Math.min(currentHeight, clipHeight >= 0f ? clipHeight - getY() : getHeight());
            if (bottom <= 0) {
                return;
            }
            canvas.clipRect(0f, 0f, getMeasuredWidth(), bottom);
        }

//        if (renderNode != null) {
//            clipPath.rewind();
//            final float r = dp(10);
//            AndroidUtilities.rectTmp.set(layout.getX(), layout.getY(), layout.getX() + layout.getWidth(), layout.getY() + layout.getHeight());
//            clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CCW);
//        }
//
//        drawRenderNode(canvas);

        super.dispatchDraw(canvas);

        canvas.restore();
    }

//    private void drawRenderNode(Canvas canvas) {
//        if (renderNode == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !renderNode.hasDisplayList() || !canvas.isHardwareAccelerated()) {
//            return;
//        }
//
//        canvas.save();
//        if (avatarView != null) {
//            View v = (View) avatarView.getParent();
//            float vl = v.getX();
//            float vt = v.getY() - getTranslationY();
//            float vw = v.getWidth() * v.getScaleX();
//            float vh = v.getHeight() * v.getScaleY();
//
//            clipAvatarPath.rewind();
//            clipAvatarPath.addRoundRect(
//                vl,
//                vt,
//                vl + vw,
//                vt + vh,
//                avatarView.getRoundRadiusForExpand() * v.getScaleX(),
//                avatarView.getRoundRadiusForExpand() * v.getScaleY(),
//                Path.Direction.CCW
//            );
//            canvas.clipPath(clipAvatarPath);
//        }
//
//        canvas.clipPath(clipPath);
//        canvas.translate(0f, renderNodeTranslateY);
//        canvas.scale(renderNodeScale, renderNodeScale);
//        canvas.drawRenderNode(renderNode);
//
//        canvas.restore();
//    }
}
