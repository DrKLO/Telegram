package org.telegram.ui.Components.quickforward;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.List;

public class QuickShareSelectorDrawable extends Drawable implements Animator.AnimatorListener {
    public static class Sizes {
        public static int PADDING_H = 9;
        public static int PADDING_V = 7;
        public static int AVATAR_RADIUS = 21;
        public static int AVATAR = AVATAR_RADIUS * 2;
        public static int GAP = 11;
        public static int BUBBLE_HEIGHT = AVATAR + PADDING_V * 2;
        public static int CLOSE_AVATAR_JUMP_HEIGHT = 15;
        public static int TEXT_PADDING_INTERNAL = 8;
        public static int TEXT_PADDING_EXTERNAL = 16;
        public static int BLUR_RADIUS = 10;
        public static int TEXT_BLUR_RADIUS = 4;
    }

    private static final RectF tmpRectF = new RectF();
    private static final Rect tmpRect = new Rect();
    private static final int[] tmpCords = new int[2];

    public int getBubbleWidth () {
        return dp((Sizes.AVATAR + Sizes.GAP) * avatarCells.length - Sizes.GAP + Sizes.PADDING_H * 2);
    }

    public final QuickShareSelectorOverlayLayout parent;

    private final Runnable onFinish;
    private final Paint paintBubbleBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix shaderMatrix = new Matrix();
    private final Path path = new Path();
    private LinearGradient linearGradient;

    private final RectF bubbleStart = new RectF();
    private final RectF buttonCurrent = new RectF();
    private final RectF bubbleCurrent = new RectF();
    private final RectF ballLeft = new RectF();
    private final RectF ballRight = new RectF();

    private final QuickShareAvatarCell[] avatarCells;
    private final Drawable shadowDrawable;
    public final ChatMessageCell cell;
    public final MessageObject messageObject;
    public final String key;

    private float bubbleOffset;
    private float openProgress = 0f;
    private float closeProgress = 0f;

    private Bitmap globalBlurBitmap;
    private BitmapShader bitmapShader;
    private Matrix bitmapMatrix = new Matrix();
    private Paint globalBlurBitmapPaint;

    private boolean openAnimationCompleted = false;
    private boolean closeAnimationCompleted = false;
    private boolean closeAnimationStarted = false;
    private BlurVisibilityDrawable closeAnimationDrawable;

    private boolean ballsAllowed = true;
    private int selectedIndex = -1;
    private boolean isReady;
    private boolean isDestroyed;

    private int offsetX, offsetY;

    public QuickShareSelectorDrawable(
            QuickShareSelectorOverlayLayout parent,
            ChatMessageCell cell,
            List<Long> dialogs,
            String key,
            Runnable onFinish
    ) {
        this.onFinish = onFinish;
        this.parent = parent;
        this.cell = cell;
        this.messageObject = cell.getMessageObject();
        this.key = key;

        avatarCells = new QuickShareAvatarCell[Math.min(5, dialogs.size())];

        for (int i = 0; i < avatarCells.length; i++) {
            avatarCells[i] = new QuickShareAvatarCell(this, dialogs.get(i));
        }

        paintBubbleBg.setStyle(Paint.Style.FILL);
        shadowDrawable = ContextCompat.getDrawable(parent.getContext(), R.drawable.reactions_bubble_shadow).mutate();

        cell.setHideSideButtonByQuickShare(true);

        parent.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);

        updateColors();

        openAnimation.setInterpolator(Interpolators.LINEAR_INTERPOLATOR);
        openAnimation.addListener(this);

        closeAnimation.setInterpolator(Interpolators.LINEAR_INTERPOLATOR);
        closeAnimation.addListener(this);

        AndroidUtilities.makeGlobalBlurBitmap((b) -> {
            if (isDestroyed) {
                globalBlurBitmap.recycle();
                return;
            }
            globalBlurBitmap = b;
            bitmapShader = new BitmapShader(globalBlurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            globalBlurBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            globalBlurBitmapPaint.setShader(bitmapShader);

            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .08f : +1.25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? +.02f : -.15f);
            globalBlurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));

            bitmapMatrix.reset();
            bitmapMatrix.setScale(15, 15);
            bitmapShader.setLocalMatrix(bitmapMatrix);
        }, 15);
    }

    public boolean applyBlurBitmapShader (float x, float y) {
        return globalBlurBitmapPaint != null;
    }

    public Paint getBlurBitmapPaint () {
        return globalBlurBitmapPaint;
    }

    private void calculateCords () {
        final int x1, y1, x2, y2;
        cell.getLocationInWindow(tmpCords);
        x1 = tmpCords[0];
        y1 = tmpCords[1];

        parent.getLocationInWindow(tmpCords);
        x2 = tmpCords[0];
        y2 = tmpCords[1];

        offsetX = x1 - x2;
        offsetY = y1 - y2;

        final float r = dp(16);
        final float buttonCx = offsetX + cell.getSideButtonStartX() + r;
        final float buttonCy = offsetY + cell.getSideButtonStartY() + r;

        bubbleStart.set(buttonCx - r, buttonCy - r, buttonCx + r, buttonCy + r);

        float padding = dp(16);
        if (bubbleStart.right + dp(48) + padding > parent.getMeasuredWidth()) {
            bubbleOffset = Math.max(0, parent.getMeasuredWidth() - padding - bubbleStart.right);
        } else if (bubbleStart.right + dp(48) - getBubbleWidth() - padding < 0) {
            float targetRight = padding + getBubbleWidth();
            bubbleOffset = Math.max(0, targetRight - bubbleStart.right );
        } else {
            bubbleOffset = dp(48);
        }
    }

    private void prepare () {
        calculateCords();

        openAnimation.start();
        isReady = true;
    }

    public boolean isDestroyed() {
        return isDestroyed;
    }

    public void onTouchMoveEvent (float touchX, float touchY) {
        if (!openAnimationCompleted) {
            return;
        }

        final float x = touchX - bubbleCurrent.left + offsetX;
        final float y = touchY - bubbleCurrent.top + offsetY;
        final int indexH = (int) Math.floor((x - ((dp(Sizes.PADDING_H) - dp(Sizes.GAP) / 2f))) / dp(Sizes.AVATAR + Sizes.GAP));
        final int index = (-dp(21 + Sizes.TEXT_PADDING_EXTERNAL)) < y && y < bubbleCurrent.height() ?
            MathUtils.clamp(indexH, 0, avatarCells.length - 1) : -1;

        setIndex(index);
    }

    public void close(Bulletin bulletin) {
        if (bulletin == null) {
            selectedIndex = -1;
            closeImpl();
            return;
        }

        final Bulletin.Layout layout = bulletin.getLayout();
        if (!(layout instanceof Bulletin.LottieLayout)) {
            closeImpl();
            return;
        }

        this.bulletinLayout = (Bulletin.LottieLayout) layout;
        this.bulletinLayout.imageView.setVisibility(View.INVISIBLE);

        final ViewTreeObserver observer = bulletinLayout.getViewTreeObserver();
        ViewTreeObserver.OnPreDrawListener onPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                initBulletin();
                closeImpl();
                return true;
            }
        };

        observer.addOnPreDrawListener(onPreDrawListener);
    }

    private void closeImpl () {
        closeAnimation.start();
        closeAnimationStarted = true;

        if (openAnimationCompleted && !isDestroyed) {
            closeAnimationDrawable = new BlurVisibilityDrawable(this::drawBubble);
            closeAnimationDrawable.render((int) bubbleCurrent.width(), (int) (bubbleCurrent.height() + dp(30)), dp(Sizes.BLUR_RADIUS), 4);
        }

        invalidateSelf();
    }

    public long getSelectedDialogId() {
        if (selectedIndex != -1) {
            return avatarCells[selectedIndex].dialogId;
        }

        return 0;
    }

    public void destroy () {
        cell.setHideSideButtonByQuickShare(false);
        if (!isDestroyed) {
            isDestroyed = true;
            if (globalBlurBitmap != null) {
                globalBlurBitmap.recycle();
            }
            if (closeAnimationDrawable != null) {
                closeAnimationDrawable.recycle();
            }
            for (QuickShareAvatarCell avatarCell : avatarCells) {
                avatarCell.recycle();
            }
        }
    }

    private Bulletin.LottieLayout bulletinLayout;
    private float bulletinImageCx, bulletinImageCy;

    private void initBulletin () {
        if (bulletinLayout == null) {
            return;
        }

        final float x1, y1, x2, y2;
        bulletinLayout.getLocationInWindow(tmpCords);
        x1 = tmpCords[0];
        y1 = tmpCords[1] - bulletinLayout.getTranslationY() + (bulletinLayout.top ? bulletinLayout.getTopOffset() : -bulletinLayout.getBottomOffset());

        parent.getLocationInWindow(tmpCords);
        x2 = tmpCords[0];
        y2 = tmpCords[1];

        bulletinImageCx = x1 - x2 + bulletinLayout.imageView.getLeft() + bulletinLayout.imageView.getMeasuredWidth() / 2f;
        bulletinImageCy = y1 - y2 + bulletinLayout.imageView.getTop() + bulletinLayout.imageView.getMeasuredHeight() / 2f;
    }

    private void drawBubble (Canvas canvas, int alpha) {
        canvas.save();
        canvas.translate(
                -bubbleCurrent.left,
                -bubbleCurrent.top + dp(30)
        );
        draw(canvas, alpha, true);
        canvas.restore();
    }


    public boolean isActive () {
        return !closeAnimationStarted;
    }




    @Override
    public void draw(@NonNull Canvas canvas) {
        draw(canvas, 255, false);
    }

    public void draw(@NonNull Canvas canvas, int alphaValue, boolean renderToBlurBuffer) {
        if (!isReady) {
            prepare();
        }

        if (closeAnimationDrawable != null && !renderToBlurBuffer) {
            closeAnimationDrawable.setBounds(
                (int) bubbleCurrent.left,
                (int) (bubbleCurrent.top - dp(30)),
                (int) bubbleCurrent.right,
                (int) bubbleCurrent.bottom
            );

            closeAnimationDrawable.setAlpha((int) ((1f - Interpolators.closeAlpha.getInterpolation(closeProgress)) * 255));
            closeAnimationDrawable.draw(canvas);

            if (selectedIndex != -1) {
                float alpha = 1 - Interpolators.closeAvatarAlpha.getInterpolation(closeProgress);
                float position = Interpolators.closeAvatarPosition.getInterpolation(closeProgress);

                final int offset = selectedIndex - 2;

                final float x1 = bubbleCurrent.centerX() + dp((Sizes.AVATAR + Sizes.GAP)) * offset;
                final float y1 = bubbleCurrent.centerY();

                final float x2 = bulletinImageCx;
                final float y2 = bulletinImageCy;

                final float x3 = (x1 + x2) / 2;
                final float y3 = bulletinLayout != null && bulletinLayout.top ?
                    Math.max(y1, y2) + dp(Sizes.CLOSE_AVATAR_JUMP_HEIGHT):
                    Math.min(y1, y2) - dp(Sizes.CLOSE_AVATAR_JUMP_HEIGHT);

                final float x = fromTo(x1, x2, position);
                final float y = findParabola(x1, y1, x2, y2, x3, y3, position);
                final float r = fromTo(dp(Sizes.AVATAR) / 2f + dp(2), dp(12), position);

                avatarCells[selectedIndex].drawBlurredAvatar(canvas, x, y, r, alpha);
            }

            return;
        }


        /* Apply paints */


        final float alpha = !renderToBlurBuffer ? (1f - closeProgress) : alphaValue / 255f;
        final float ms = fromTo(0.3f, 0.075f, Interpolators.bgScale.getInterpolation(openProgress));

        shaderMatrix.reset();
        shaderMatrix.setScale(ms, ms);
        shaderMatrix.postTranslate(0, bubbleCurrent.bottom);
        linearGradient.setLocalMatrix(shaderMatrix);
        paintBubbleBg.setAlpha((int) (Interpolators.bgOpacity.getInterpolation(openProgress) * 255 * alpha));

        tmpRectF.set(bubbleCurrent);
        tmpRectF.inset(-dp(8), -dp(8));
        tmpRectF.round(tmpRect);
        shadowDrawable.setAlpha((int) (alpha * 255));
        shadowDrawable.setBounds(tmpRect);
        shadowDrawable.draw(canvas);

        if (!openAnimationCompleted) {
            final float i = Interpolators.buttonRotationUp.getInterpolation(openProgress)
                    - Interpolators.buttonRotationDown.getInterpolation(openProgress);
            float degrees = i * -40;

            canvas.save();
            canvas.translate(buttonCurrent.left, buttonCurrent.top);
            canvas.rotate(degrees, dp(16), dp(16));
            canvas.translate(-cell.getSideButtonStartX(), -cell.getSideButtonStartY());
            cell.drawSideButton(canvas, true);
            canvas.restore();
        }

        if (ballsAllowed && !openAnimationCompleted) {
            canvas.drawPath(path, paintBubbleBg);
        } else {
            final float r1 = Math.min(bubbleCurrent.width(), bubbleCurrent.height()) / 2f;
            final float r2 = Math.min(buttonCurrent.width(), buttonCurrent.height()) / 2f;
            canvas.drawRoundRect(bubbleCurrent, r1, r1, paintBubbleBg);
            if (!openAnimationCompleted) {
                canvas.drawRoundRect(buttonCurrent, r2, r2, paintBubbleBg);
            }
        }

        final float radiusOffset = dp(2) * Interpolators.avatarOvershootCancel.getInterpolation(openProgress);
        final float radius1 = dp(Sizes.AVATAR + 2) * Interpolators.avatar1.getInterpolation(openProgress) / 2f - radiusOffset;
        final float radius2 = dp(Sizes.AVATAR + 2) * Interpolators.avatar2.getInterpolation(openProgress) / 2f - radiusOffset;
        final float radius3 = dp(Sizes.AVATAR + 2) * Interpolators.avatar3.getInterpolation(openProgress) / 2f - radiusOffset;

        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < avatarCells.length; i++) {
                if (k == 0 && i == selectedIndex || k == 1 && i != selectedIndex) {
                    continue;
                }

                final float offset = i - (avatarCells.length / 2f - 0.5f);
                final float radius;
                if (i == 2) {
                    radius = radius1;
                } else if (i == 1 || i == 3) {
                    radius = radius2;
                } else {
                    radius = radius3;
                }

                float cx = bubbleCurrent.centerX() + dp(Sizes.AVATAR + Sizes.GAP) * offset;
                float cy = bubbleCurrent.centerY();
                avatarCells[i].draw(canvas,
                    dp(Sizes.TEXT_PADDING_EXTERNAL),
                    parent.getMeasuredWidth() - dp(Sizes.TEXT_PADDING_EXTERNAL),
                    bubbleCurrent.left,
                    bubbleCurrent.right,
                    cx, cy, radius, alpha,
                    i == selectedIndex && closeAnimationStarted
                );
            }
        }
    }

    private void updateColors () {
        final int color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, cell.getResourcesProvider());

        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelShadow), PorterDuff.Mode.MULTIPLY));
        linearGradient = new LinearGradient(
                0, 0, 0, dp(100),
                new int[]{color, color & 0x00FFFFFF},
                new float[]{0f, 1f},
                Shader.TileMode.CLAMP
        );
        paintBubbleBg.setShader(linearGradient);
    }

    private void calculateOpeningAnimationPositions () {
        final float overshoot = 1f - Interpolators.overshootCancel.getInterpolation(openProgress);
        final float buttonJump = (Interpolators.buttonJumpUp.getInterpolation(openProgress) - Interpolators.buttonJumpDown.getInterpolation(openProgress)) * dp(13);
        buttonCurrent.set(bubbleStart);
        buttonCurrent.offset(0, -buttonJump);

        final float bubbleHalfHeight = fromTo(buttonCurrent.height(), dp(Sizes.BUBBLE_HEIGHT) + dp(2) * overshoot,
                Interpolators.heightExpansion.getInterpolation(openProgress)) / 2f;

        final float bubbleWidth = Math.max(fromTo(buttonCurrent.width(), getBubbleWidth() + dp(10) * overshoot,
                Interpolators.widthExpansion.getInterpolation(openProgress)), bubbleHalfHeight * 2f);

        final float bubbleRight = bubbleStart.centerX() + bubbleHalfHeight
                + Math.min(dp(-12) + bubbleOffset, (bubbleWidth - Math.max(buttonCurrent.width(), bubbleHalfHeight * 2f)) / 2f)
                * Interpolators.widthExpansion.getInterpolation(openProgress);

        final float bubbleCX = bubbleRight - bubbleWidth / 2f;
        final float bubbleCY = bubbleStart.bottom - bubbleHalfHeight - 1f - (dp(53 - 15) + dp(6) * overshoot) *
                Interpolators.bubbleY.getInterpolation(openProgress);

        bubbleCurrent.left = bubbleRight - bubbleWidth;
        bubbleCurrent.top = bubbleCY - bubbleHalfHeight;
        bubbleCurrent.right = bubbleRight;
        bubbleCurrent.bottom = bubbleCY + bubbleHalfHeight;

        if (ballsAllowed && !openAnimationCompleted) {
            final float radSg = fromTo(dp(5f), dp(3f), Interpolators.ballsRadius.getInterpolation(openProgress));
            float ncy = bubbleCurrent.bottom + radSg;
            float dx = (float) findOtherLeg(buttonCurrent.width() / 2f + radSg, Math.abs(ncy - buttonCurrent.centerY()));
            float ncx = buttonCurrent.centerX() - dx;

            boolean ballLeftOnCircle = ncx < bubbleCurrent.left + bubbleCurrent.height() / 2f;
            if (ballLeftOnCircle) {
                PointF f = findIntersectionWithGravity(
                        buttonCurrent.centerX(), buttonCurrent.centerY(), buttonCurrent.height() / 2f + radSg,
                        bubbleCurrent.left + bubbleCurrent.height() / 2f, bubbleCurrent.centerY(), bubbleCurrent.height() / 2f + radSg, true
                );
                if (f != null) {
                    ncx = f.x;
                    ncy = f.y;
                } else {
                    ballsAllowed = false;
                }
            }
            ballLeft.set(ncx - radSg, ncy - radSg, ncx + radSg, ncy + radSg);

            ncy = bubbleCurrent.bottom + radSg;
            ncx = buttonCurrent.centerX() + dx;
            boolean ballRightOnCircle = ncx > bubbleCurrent.right - bubbleCurrent.height() / 2f;
            if (ballRightOnCircle) {
                PointF f = findIntersectionWithGravity(
                        buttonCurrent.centerX(), buttonCurrent.centerY(), buttonCurrent.height() / 2f + radSg,
                        bubbleCurrent.right - bubbleCurrent.height() / 2f, bubbleCurrent.centerY(), bubbleCurrent.height() / 2f + radSg, false
                );
                if (f != null) {
                    ncx = f.x;
                    ncy = f.y;
                } else {
                    ballsAllowed = false;
                }
            }

            ballRight.set(ncx - radSg, ncy - radSg, ncx + radSg, ncy + radSg);

            float bdx = Math.abs(ballLeft.centerX() - ballRight.centerX());
            float bdy = Math.abs(ballLeft.centerY() - ballRight.centerY());

            boolean ballsConnected = Math.sqrt(bdx * bdx + bdy * bdy) <= (ballLeft.width() + ballRight.width()) / 2;
            if (ballsConnected && ballsAllowed) {
                ballsAllowed = false;
            }

            if (ballsAllowed) {
                buildPath(path, buttonCurrent, bubbleCurrent, ballLeft, ballRight, ballLeftOnCircle, ballRightOnCircle);
            }
        }
    }

    private void onOpenAnimationEnd () {
        cell.setHideSideButtonByQuickShare(false);
        openAnimationCompleted = true;
        invalidateSelf();

        if (closeAnimationCompleted) {
            onFinish.run();
        }
    }

    private void onCloseAnimationEnd () {
        closeAnimationCompleted = true;
        invalidateSelf();

        if (bulletinLayout != null) {
            bulletinLayout.imageView.setVisibility(View.VISIBLE);
        }

        if (openAnimationCompleted) {
            onFinish.run();
        }
    }

    private void setIndex (int index) {
        if (selectedIndex == index) {
            return;
        }

        parent.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        selectedIndex = index;
        for (int i = 0; i < avatarCells.length; i++) {
            avatarCells[i].setSelected(index == i, true);
            avatarCells[i].setFullVisible(index == i || index == -1, true);
        }
    }

    private void buildPath(
            Path path,
            RectF buttonCurrent,
            RectF bubbleCurrent,
            RectF ballLeft,
            RectF ballRight,
            boolean ballLeftOnCircle,
            boolean ballRightOnCircle
    ) {
        path.reset();

        final float br = calculateAngle(
                buttonCurrent.centerX(),
                buttonCurrent.centerY(),
                ballRight.centerX(),
                ballRight.centerY()
        );

        final float bl = calculateAngle(
                buttonCurrent.centerX(),
                buttonCurrent.centerY(),
                ballLeft.centerX(),
                ballLeft.centerY()
        );

        arcTo(path, buttonCurrent, br, bl, false);
        final float bl2 = ballLeftOnCircle ? calculateAngle(
                ballLeft.centerX(),
                ballLeft.centerY(),
                bubbleCurrent.left + bubbleCurrent.height() / 2f,
                bubbleCurrent.centerY()
        ) : -90;

        arcTo(path, ballLeft, reverseAngle(bl), bl2, true, true);
        if (!ballLeftOnCircle) {
            path.lineTo(bubbleCurrent.left + bubbleCurrent.height() / 2f, bubbleCurrent.bottom);
        }
        tmpRectF.set(bubbleCurrent.left, bubbleCurrent.top, bubbleCurrent.left + bubbleCurrent.height(), bubbleCurrent.bottom);
        arcTo(path, tmpRectF,reverseAngle(bl2), -90, false);

        path.lineTo(bubbleCurrent.right - bubbleCurrent.height() / 2f, bubbleCurrent.top);

        final float br2 = ballRightOnCircle ? calculateAngle(
                ballRight.centerX(),
                ballRight.centerY(),
                bubbleCurrent.right - bubbleCurrent.height() / 2f,
                bubbleCurrent.centerY()
        ) : -90;

        tmpRectF.set(bubbleCurrent.right - bubbleCurrent.height(), bubbleCurrent.top, bubbleCurrent.right, bubbleCurrent.bottom);
        arcTo(path, tmpRectF, -90, reverseAngle(br2), false);
        if (!ballRightOnCircle) {
            path.lineTo(ballRight.centerX(), bubbleCurrent.bottom);
        }
        arcTo(path, ballRight, br2, reverseAngle(br), true, true);

        path.close();
    }

    private void arcTo(Path path, RectF rectF, float startAngle, float endAngle, boolean clockwise) {
        arcTo(path, rectF, startAngle, endAngle, clockwise, false);
    }

    private void arcTo(Path path, RectF rectF, float startAngle, float endAngle, boolean clockwise, boolean strictMode) {
        float sweepAngle = endAngle - startAngle;

        if (clockwise) {
            if (sweepAngle > 0) {
                sweepAngle -= 360;
            }
        } else {
            if (sweepAngle < 0) {
                sweepAngle += 360;
            }
        }

        if (Math.abs(sweepAngle) > 270 && strictMode) {
            ballsAllowed = false;
        }

        path.arcTo(rectF, startAngle, sweepAngle);
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    private static float fromTo (float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static float reverseAngle (float angle) {
        if (angle <= 0) {
            return angle + 180;
        } else {
            return angle - 180;
        }
    }

    private static double findOtherLeg(double hypotenuse, double leg) {
        if (hypotenuse <= leg) {
            return 0;
        }

        return Math.sqrt(hypotenuse * hypotenuse - leg * leg);
    }

    public static PointF findIntersectionWithGravity(float x1, float y1, float radius1,
                                                     float x2, float y2, float radius2, boolean left) {
        float d = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));

        if (d > radius1 + radius2 || d < Math.abs(radius1 - radius2)) {
            return null;
        }

        float a = (radius1 * radius1 - radius2 * radius2 + d * d) / (2 * d);
        float h = (float) Math.sqrt(radius1 * radius1 - a * a);

        float px = x1 + a * (x2 - x1) / d;
        float py = y1 + a * (y2 - y1) / d;

        float intersection1X = px + h * (y2 - y1) / d;
        float intersection1Y = py - h * (x2 - x1) / d;

        float intersection2X = px - h * (y2 - y1) / d;
        float intersection2Y = py + h * (x2 - x1) / d;

        if (intersection1X != intersection2X) {
            if (intersection1X < intersection2X) {
                if (left) {
                    return new PointF(intersection1X, intersection1Y);
                } else {
                    return new PointF(intersection2X, intersection2Y);
                }
            }
        }


        if (intersection1Y > intersection2Y) {
            return new PointF(intersection1X, intersection1Y);
        } else {
            return new PointF(intersection2X, intersection2Y);
        }
    }

    private static float calculateAngle(float x1, float y1, float x2, float y2) {
        float deltaY = y2 - y1;
        float deltaX = x2 - x1;
        float angleRadians = (float) Math.atan2(deltaY, deltaX);
        return (float) Math.toDegrees(angleRadians);
    }

    private static Interpolator interpolator (Interpolator interpolator, int startMs, int endMs, int durationMs) {
        return interpolator (interpolator, startMs, endMs, durationMs, false);
    }

    private static Interpolator interpolator (Interpolator interpolator, int startMs, int endMs, int durationMs, boolean reverse) {
        final float start = startMs / (float) durationMs;
        final float end = endMs / (float) durationMs;

        return input -> {
            if (reverse) {
                final float value = MathUtils.clamp((input - start) / (end - start), 0, 1);
                return 1f - interpolator.getInterpolation(1f - value);
            }

            final float value = MathUtils.clamp((input - start) / (end - start), 0, 1);
            return interpolator.getInterpolation(value);
        };
    }


    private static float findParabola(
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,

            float progress
    ) {
        double c2 = calculateC2(x1, y1, x2, y2, x3, y3);
        double c1 = calculateC1(x1, y1, x2, y2, c2);
        double c0 = calculateC0(x1, y1, c1, c2);

        float x = fromTo(x1, x2, progress);
        double y = c2 * x * x + c1 * x + c0;

        return (float) y;
    }

    private static double calculateC2(double x0, double y0, double x1, double y1, double x2, double y2) {
        double numerator = y2 - ((x2 - x0) * (y1 - y0) / (x1 - x0)) - y0;
        double denominator = x2 * x2 - x1 * x2 + x1 * x0 - x0 * x2;

        if (denominator == 0) {
            return 0;
        }

        return numerator / denominator;
    }

    private static double calculateC1(double x0, double y0, double x1, double y1, double c2) {
        double numerator = y1 - c2 * (x1 * x1 - x0 * x0) - y0;
        double denominator = x1 - x0;

        if (denominator == 0) {
            return 0;
        }

        return numerator / denominator;
    }

    private static double calculateC0(double x0, double y0, double c1, double c2) {
        return y0 - c2 * (x0 * x0) - c1 * x0;
    }




    /* Animations */

    private static final int OPEN_DURATION = 560;
    private static final int CLOSE_DURATION = 240;

    public static class Interpolators {
        public static final Interpolator DECELERATE_INTERPOLATOR = new DecelerateInterpolator();
        public static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

        public static final Interpolator closeAlpha = interpolator(new DecelerateInterpolator(), 0, CLOSE_DURATION, CLOSE_DURATION);
        public static final Interpolator closeAvatarPosition = interpolator(LINEAR_INTERPOLATOR, 0, CLOSE_DURATION, CLOSE_DURATION);
        public static final Interpolator closeAvatarAlpha = interpolator(new DecelerateInterpolator(), CLOSE_DURATION * 11 / 12, CLOSE_DURATION, CLOSE_DURATION);

        public static final Interpolator buttonRotationUp = interpolator(new CubicBezierInterpolator(.7f,-0.6f,.4f,1), 0, 200, OPEN_DURATION);
        public static final Interpolator buttonRotationDown = interpolator(new CubicBezierInterpolator(.7f,-0.6f,.4f,1), 200, 400, OPEN_DURATION, true);
        public static final Interpolator buttonJumpUp = interpolator(new DecelerateInterpolator(), 0, 150, OPEN_DURATION);
        public static final Interpolator buttonJumpDown = interpolator(new DecelerateInterpolator(), 210, 425, OPEN_DURATION);
        public static final Interpolator bgOpacity = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 0, 320, OPEN_DURATION);
        public static final Interpolator bgScale = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 40, 320, OPEN_DURATION);
        public static final Interpolator heightExpansion = interpolator(new DecelerateInterpolator(), 0, 250, OPEN_DURATION);
        public static final Interpolator widthExpansion = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 0, 460, OPEN_DURATION);
        public static final Interpolator bubbleY = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 0, 325, OPEN_DURATION);
        public static final Interpolator ballsRadius = interpolator(new DecelerateInterpolator(), 150, 250, OPEN_DURATION);
        public static final Interpolator overshootCancel = interpolator(new DecelerateInterpolator(), 200, 480, OPEN_DURATION);
        public static final Interpolator avatar1 = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 60, 320, OPEN_DURATION);
        public static final Interpolator avatar2 = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 90, 380, OPEN_DURATION);
        public static final Interpolator avatar3 = interpolator(CubicBezierInterpolator.EASE_OUT_QUINT, 110, 440, OPEN_DURATION);
        public static final Interpolator avatarOvershootCancel = interpolator(new DecelerateInterpolator(), 200, 460, OPEN_DURATION);
    }

    private final static Property<QuickShareSelectorDrawable, Float> OPEN_FACTOR = new AnimationProperties.FloatProperty<QuickShareSelectorDrawable>("openFactor") {
        @Override
        public Float get(QuickShareSelectorDrawable d) {
            return d.openProgress;
        }

        @Override
        public void setValue(QuickShareSelectorDrawable d, float value) {
            d.openProgress = value;
            d.calculateOpeningAnimationPositions();
            d.invalidateSelf();
        }
    };

    private final ObjectAnimator openAnimation = ObjectAnimator.ofFloat(this, OPEN_FACTOR, 1)
            .setDuration((long) (OPEN_DURATION));

    private final static Property<QuickShareSelectorDrawable, Float> CLOSE_FACTOR = new AnimationProperties.FloatProperty<QuickShareSelectorDrawable>("openFactor") {
        @Override
        public Float get(QuickShareSelectorDrawable d) {
            return d.closeProgress;
        }

        @Override
        public void setValue(QuickShareSelectorDrawable d, float value) {
            d.closeProgress = value;
            d.invalidateSelf();
        }
    };

    private final ObjectAnimator closeAnimation = ObjectAnimator.ofFloat(this, CLOSE_FACTOR, 1)
            .setDuration(CLOSE_DURATION);

    @Override
    public void onAnimationStart(@NonNull Animator animation) {

    }

    @Override
    public void onAnimationEnd(@NonNull Animator animation) {
        if (animation == openAnimation) {
            onOpenAnimationEnd();
        } else if (animation == closeAnimation) {
            onCloseAnimationEnd();
        }
    }

    @Override
    public void onAnimationCancel(@NonNull Animator animation) {

    }

    @Override
    public void onAnimationRepeat(@NonNull Animator animation) {

    }
}
