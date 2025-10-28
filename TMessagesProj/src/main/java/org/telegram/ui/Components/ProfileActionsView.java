package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProfileActionsView extends View {

    private final List<Action> actions = new ArrayList<>();
    private final Paint paint = new Paint();
    private final Paint shaderPaint = new Paint();
    private final TextPaint textPaint = new TextPaint();
    private final Rect textBounds = new Rect();

    public boolean isAnimatingCallAction = false;
    public boolean isOpeningLayout = true;
    public float clipHeight = -1;
    private final Path clipAvatarPath = new Path();
    private final Path clipPath = new Path();
    private ProfileActivity.AvatarImageView avatarView;
    private float renderNodeScale;
    private float renderNodeTranslateY;
    private RenderNode renderNode;

    private int activeCount = 0;
    private final int targetHeight;
    private boolean ignoreRect = false;
    private float currentHeight = 0;

    private OnActionClickListener onActionClickListener = null;

    private final Set<Integer> allAvailableActions = new HashSet<>();

    public int mode = MODE_MY_PROFILE;
    public static final int MODE_USER = 0;
    public static final int MODE_CHANNEL = 1;
    public static final int MODE_BOT = 2;
    public static final int MODE_GROUP = 3;
    public static final int MODE_FORUM = 4;
    public static final int MODE_TOPIC = 5;
    public static final int MODE_MY_PROFILE = 6;

    public static final int KEY_MESSAGE = 0;
    public static final int KEY_NOTIFICATION = 1;
    public static final int KEY_DISCUSS = 2;
    public static final int KEY_GIFT = 3;
    public static final int KEY_SHARE = 4;
    public static final int KEY_CALL = 5;
    public static final int KEY_VIDEO = 6;
    public static final int KEY_JOIN = 7;
    public static final int KEY_REPORT = 8;
    public static final int KEY_LEAVE = 9;
    public static final int KEY_VOICE_CHAT = 10;
    public static final int KEY_STREAM = 11;
    public static final int KEY_STORY = 12;
    public static final int KEY_STOP = 13;
    public static final int KEY_SET_PHOTO = 14;
    public static final int KEY_EDIT_USERNAME = 15;
    public static final int KEY_EDIT_INFO = 16;

    private boolean isApplying;
    private boolean isNotificationsEnabled;

    private Action callAction = null;
    private Action firstAction, lastAction;
    final float xpadding;
    final float ypadding;
    final float top;
    final float textPadding;

    private int color = 0;
    private boolean hasColorById;
    private RadialGradient radialGradient;
    private final Matrix matrix = new Matrix();

    public boolean myProfile;

    public ProfileActionsView(Context context, int targetHeight) {
        super(context);

        paint.setColor(Color.BLACK);
        paint.setAlpha(40);

        xpadding = dpf2(14);
        ypadding = dpf2(12);
        top = dpf2(8);
        textPadding = dpf2(4);

        this.targetHeight = (int) (targetHeight - ypadding - top);

        textPaint.setTextSize(dpf2(11));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setColor(Color.WHITE);
        setBackgroundColor(0);
    }

    public void drawingBlur(boolean drawing) {
        if (ignoreRect != drawing || renderNode != null) {
            ignoreRect = drawing;
            renderNode = null;
            avatarView = null;
            invalidate();
        }
    }

    public void drawingBlur(RenderNode renderNode, ProfileActivity.AvatarImageView avatarView, float scale, float dy) {
        this.ignoreRect = false;
        this.renderNode = renderNode;
        this.avatarView = avatarView;
        this.renderNodeScale = scale;
        this.renderNodeTranslateY = dy;
        invalidate();
    }

    public void setOnActionClickListener(OnActionClickListener onActionClickListener) {
        this.onActionClickListener = onActionClickListener;
    }

    public void setActionsColor(int color, boolean hasColorById) {
        if (radialGradient == null || this.color != color || this.hasColorById != hasColorById) {
            this.color = color;
            this.hasColorById = hasColorById;
            createColorShader();
        }
    }

    private void createColorShader() {
        if (color == 0) return;
        if (!hasColorById) {
            paint.setColor(color);
//            paint.setAlpha(40);
            return;
        }
        int w = getMeasuredWidth();
        if (w <= 0) return;

        float betweenPadding = xpadding / 2f;
        float width = (w - betweenPadding * (activeCount - 1) - xpadding * 2f) / activeCount;

        this.radialGradient = new RadialGradient(
                width / 2f,
                targetHeight / 2f,
                hasColorById ? width * 0.65f : 1f,
                Theme.multAlpha(color, 0.8f),
                color,
                Shader.TileMode.CLAMP
        );
        shaderPaint.setShader(radialGradient);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.makeMeasureSpec((int) (targetHeight + top + ypadding), MeasureSpec.EXACTLY)
        );
    }

    public void updatePosition(float y, float newHeight) {
        currentHeight = newHeight;
        setTranslationY(y);
        invalidate();
    }

    private float getItemWidth() {
        int w = getMeasuredWidth();
        float betweenPadding = xpadding / 2f;
        return (w - betweenPadding * (activeCount - 1) - xpadding * 2f) / activeCount;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (clipHeight >= 0f) {
            final float bottom = clipHeight - getY();
            if (bottom <= 0) {
                return;
            }
            canvas.clipRect(0f, 0f, getMeasuredWidth(), bottom);
        }

        float height = Math.max(0f, currentHeight - ypadding - top);

        if (height <= 0f) {
            return;
        }

        final float betweenPadding = xpadding / 2f;
        final float width = getItemWidth();
        float left = xpadding;
        float r = getRoundRadius();

        if (renderNode != null) {
            clipPath.rewind();
        }

        Action newFirstAction = null, newLastAction = null;
        int c = actions.size();
        for (int i = 0; i < c; i++) {
            Action action = actions.get(i);
            if (action.isDeleted) continue;

            if (!action.isDeleting) {
                action.rect.set(left, top, left + width, top + height);
                left += width + betweenPadding;

                if (newFirstAction == null) {
                    newFirstAction = action;
                }
                newLastAction = action;
            }

            action.updatePosition();
            if (renderNode != null) {
                AndroidUtilities.rectTmp.set(action.rect);
                AndroidUtilities.rectTmp.inset(
                    action.rect.width() / 2.0f * (1.0f - action.getScale()),
                    action.rect.height() / 2.0f * (1.0f - action.getScale())
                );
                clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CCW);
            }
        }
        firstAction = newFirstAction;
        lastAction = newLastAction;

        float fraction = Utilities.clamp01(height / targetHeight);
        float alphaFraction1 = Utilities.clamp01((fraction - 0.2f) / 0.8f);
        if (alphaFraction1 <= 0f) {
            return;
        }

        if (!ignoreRect) {
            for (int i = 0; i < c; i++) {
                Action action = actions.get(i);
                if (!action.isDeleted) {
                    AndroidUtilities.rectTmp.set(action.rect);
                    AndroidUtilities.rectTmp.inset(
                        action.rect.width() / 2.0f * (1.0f - action.getScale()),
                        action.rect.height() / 2.0f * (1.0f - action.getScale())
                    );
                    int wasAlpha = paint.getAlpha();
                    int newAlpha = (int) (action.getAlpha() * alphaFraction1 * wasAlpha);
                    paint.setAlpha((int) (newAlpha * (radialGradient != null ? 0.1f : 1f)));
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, paint);
                    if (radialGradient != null) {
                        int wasAlpha2 = shaderPaint.getAlpha();
                        shaderPaint.setAlpha((int) (action.getAlpha() * alphaFraction1 * wasAlpha2));
                        matrix.setTranslate(AndroidUtilities.rectTmp.left, AndroidUtilities.rectTmp.top);
                        radialGradient.setLocalMatrix(matrix);
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, shaderPaint);
                        shaderPaint.setAlpha(wasAlpha2);
                    }
                    paint.setAlpha(wasAlpha);
                }
            }
        }

        drawRenderNode(canvas);

        float alphaFraction2 = Utilities.clamp01((fraction - 0.4f) / 0.6f);
        if (alphaFraction2 > 0f) {
            for (int i = 0; i < c; i++) {
                drawAction(canvas, actions.get(i), fraction, alphaFraction2);
            }
        }
    }

    private void drawRenderNode(Canvas canvas) {
        if (renderNode == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !renderNode.hasDisplayList() || !canvas.isHardwareAccelerated()) {
            return;
        }

        canvas.save();
        if (avatarView != null) {
            View v = (View) avatarView.getParent();
            float vl = v.getX();
            float vt = v.getY() - getTranslationY();
            float vw = v.getWidth() * v.getScaleX();
            float vh = v.getHeight() * v.getScaleY();

            clipAvatarPath.rewind();
            clipAvatarPath.addRoundRect(
                    vl,
                    vt,
                    vl + vw,
                    vt + vh,
                    avatarView.getRoundRadiusForExpand() * v.getScaleX(),
                    avatarView.getRoundRadiusForExpand() * v.getScaleY(),
                    Path.Direction.CCW
            );
            canvas.clipPath(clipAvatarPath);
        }

        canvas.clipPath(clipPath);
        canvas.translate(0f, renderNodeTranslateY);
        canvas.scale(renderNodeScale, renderNodeScale);
        canvas.drawRenderNode(renderNode);

        canvas.restore();
    }

    public void stopLoading(int key) {
        stopLoading(find(key));
    }

    private void stopLoading(Action a) {
        if (a != null && a.isLoading) {
            a.isLoading = false;
            invalidate();
        }
    }

    private void updateBounds(Action action) {
        final float cx = action.rect.centerX();
        final float cy = action.rect.centerY();

        final float drawableSize = dp(mode == MODE_MY_PROFILE ? 28 : 24);
        final float drawableR = drawableSize * 0.5f;

        action.text.setMaxWidth(action.rect.width() - dp(2));
        action.textScale = action.text.getLineCount() >= 3 ? 0.75f : action.text.getLineCount() >= 2 ? 0.85f : 1.0f;
        final float drawableTop = Math.max(0, (targetHeight - action.text.getHeight() * action.textScale) / 3f);

        action.drawable.setBounds(
            (int) (cx - drawableR),
            (int) (drawableTop),
            (int) (cx + drawableR),
            (int) (drawableTop + drawableSize)
        );
    }

    private void drawAction(Canvas canvas, Action action, float fraction, float alpha) {
        if (action == null || action.drawable == null || action.isDeleted) {
            return;
        }
        canvas.save();
        alpha *= action.getAlpha();
        final float cx = action.rect.centerX();
        final float cy = action.rect.centerY();
        fraction *= action.getScale();
        canvas.scale(fraction, fraction, cx, cy);
        canvas.clipRect(action.rect);

        updateBounds(action);

        final float textY = action.drawable.getBounds().bottom + action.drawable.getBounds().top - action.text.getHeight() * action.textScale / 2.0f - dp(2);

        canvas.save();
        canvas.scale(action.textScale, action.textScale, cx, textY + action.text.getHeight() * action.textScale / 2.0f);
        action.text.draw(canvas, cx - action.text.getWidth() / 2f, textY, 0xFFFFFFFF, alpha);
        canvas.restore();

        if (action.iconTranslationY != 0) {
            canvas.translate(0, action.iconTranslationY);
        }

        if (action.iconScale != 1f) {
            canvas.scale(
                    action.iconScale,
                    action.iconScale,
                    action.drawable.getBounds().centerX(),
                    action.drawable.getBounds().centerY()
            );
        }
        if (!isAnimatingCallAction || action.key != KEY_CALL) {
            action.drawable.setAlpha((int) (0xFF * alpha));
            action.drawable.draw(canvas);
        }

        canvas.restore();
        drawLoading(canvas, action, alpha);
    }

    private void drawLoading(Canvas canvas, Action action, float alpha) {
        if (action.stopDelay > 0 && System.currentTimeMillis() > action.stopDelay + action.startTime) {
            action.isLoading = false;
        }

        if (action.isLoading) {
            if (action.loadingDrawable == null) {
                action.loadingDrawable = new LoadingDrawable();
                action.loadingDrawable.setCallback(this);
                action.loadingDrawable.setColors(
                        Theme.multAlpha(Color.WHITE, .1f),
                        Theme.multAlpha(Color.WHITE, .3f),
                        Theme.multAlpha(Color.WHITE, .35f),
                        Theme.multAlpha(Color.WHITE, .8f)
                );
                action.loadingDrawable.setAppearByGradient(true);
                action.loadingDrawable.strokePaint.setStrokeWidth(dpf2(1.25f));
            } else if (action.loadingDrawable.isDisappeared() || action.loadingDrawable.isDisappearing()) {
                action.loadingDrawable.reset();
                action.loadingDrawable.resetDisappear();
            }
        } else if (action.loadingDrawable != null && !action.loadingDrawable.isDisappearing() && !action.loadingDrawable.isDisappeared()) {
            action.loadingDrawable.disappear();
        }

        if (action.loadingDrawable != null) {
            action.loadingDrawable.setBounds(action.rect);
            action.loadingDrawable.setRadiiDp(8);
            action.loadingDrawable.setAlpha((int) (0xFF * alpha));
            action.loadingDrawable.draw(canvas);
        }
    }

    public float getRoundRadius() {
        return dp(10);
    }

    private Action hit = null;
    private float downX, downY;
    private long downTime;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentHeight < dp(8)) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();
        int eventAction = event.getAction();

        if (eventAction == MotionEvent.ACTION_DOWN) {
            hit = null;
            int c = actions.size();
            for (int i = 0; i < c; i++) {
                Action a = actions.get(i);
                if (!a.isDeleting && a.rect.contains(x, y)) {
                    hit = a;
                    downX = x;
                    downY = y;
                    downTime = System.currentTimeMillis();
                    hit.bounce.setPressed(true);
//                    try {
//                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
//                    } catch (Exception ignore) {}
                    break;
                }
            }
        } else if (eventAction == MotionEvent.ACTION_MOVE) {
            if (hit != null) {
                if (Math.abs(x - downX) > 20 || Math.abs(y - downY) > 20) {
                    hit.bounce.setPressed(false);
                    hit = null;
                }
            }
        } else if (eventAction == MotionEvent.ACTION_UP || eventAction == MotionEvent.ACTION_CANCEL) {
            if (hit != null) {
                hit.bounce.setPressed(false);
                if (eventAction == MotionEvent.ACTION_UP && hit.rect.contains(x, y)) {
                    if (System.currentTimeMillis() - downTime > 250) {
                        try {
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                        } catch (Exception ignore) {
                        }
                    }
                    if (hit.supportsLoading && !hit.isLoading) {
                        hit.isLoading = true;
                        invalidate();
                    }
                    if (hit.supportsAnimate != 0) {
                        hit.updateDrawable(true, hit.supportsAnimate);
                    }
                    hit.startTime = System.currentTimeMillis();
                    final Action finalHit = hit;
                    if (onActionClickListener != null) {
                        if (finalHit.callDelay == 0) {
                            onActionClickListener.onClick(hit.key, hit.rect.left, hit.rect.top);
                        } else {
                            postDelayed(() -> {
                                onActionClickListener.onClick(finalHit.key, finalHit.rect.left, finalHit.rect.top);
                            }, finalHit.callDelay);
                        }
                    }
                }
                hit = null;
                return true;
            }
        }
        return hit != null;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || who instanceof LoadingDrawable;
    }

    public void beginApplyingActions() {
        isApplying = true;
    }

    public void commitActions() {
        if (isApplying) {
            isApplying = false;
            applyVisibleActions();
        }
    }

    public void set(int key, boolean enabled) {
        boolean changed;
        if (enabled) {
            changed = allAvailableActions.add(key);
        } else {
            changed = allAvailableActions.remove(key);
        }
        if (changed) {
            applyVisibleActions();
        }
    }

    public void setNotifications(boolean enabled) {
        final boolean animated = isNotificationsEnabled != enabled;
        isNotificationsEnabled = enabled;
        Action notificationAction = find(KEY_NOTIFICATION);
        if (notificationAction != null) {
            updateNotification(notificationAction, animated);
            invalidate();
        } else {
            allAvailableActions.add(KEY_NOTIFICATION);
            applyVisibleActions();
        }
    }

    public void addCameraAction(RLottieDrawable drawable) {
        Action action = new Action();
        drawable.setMasterParent(this);
        drawable.setCurrentFrame(0);
        drawable.start();

        action.iconTranslationY = -dp(2);
        action.drawable = drawable;
        action.key = KEY_SET_PHOTO;
        action.setText(getString(R.string.ProfileActionsEditPhoto));
        action.iconScale = 1.5f;
        actions.add(action);
        activeCount = actions.size();
    }

    public void addEditUsernameAction() {
        Action action = new Action();
        int resId = R.raw.profile_username;
        RLottieDrawable drawable = new RLottieDrawable(
            resId,
            String.valueOf(resId),
            dp(56),
            dp(56),
            false, null
        );
        drawable.setMasterParent(this);
        action.drawable = drawable;
        action.text = null;
        action.key = KEY_EDIT_USERNAME;
        drawable.setCurrentFrame(14);
        drawable.multiplySpeed(0.4f);
        drawable.start();
        action.setText(getString(R.string.ProfileActionsEditUsername));
        actions.add(action);
    }

    public void addEditInfoAction() {
        Action action = new Action();
        int resId = R.raw.profile_edit;
        RLottieDrawable drawable = new RLottieDrawable(
                resId,
                String.valueOf(resId),
                dp(56),
                dp(56),
                false, null
        );
        drawable.setMasterParent(this);
        drawable.multiplySpeed(1.4f);
        action.drawable = drawable;
        action.text = null;
        action.key = KEY_EDIT_INFO;
        action.iconTranslationY = -dp(2);
        drawable.start();
        action.setText(getString(R.string.ProfileActionsEditInfo));
        actions.add(action);
    }

    public void startAnimatedActions() {
        if (mode == MODE_MY_PROFILE) {
            int c = actions.size();
            for (int i = 0; i < c; i++) {
                Action a = actions.get(i);
                if (a.drawable instanceof RLottieDrawable) {
                    RLottieDrawable drawable = (RLottieDrawable) a.drawable;
                    if (a.key == KEY_EDIT_USERNAME) {
                        drawable.setCurrentFrame(14);
                    } else {
                        drawable.setCurrentFrame(0);
                    }
                    drawable.start();
                }
            }
        }
    }

    public boolean supportsEditInfo() {
        return mode == MODE_MY_PROFILE;
    }

    public void startCameraAnimation() {
        Action camera = find(KEY_SET_PHOTO);
        if (camera != null && camera.drawable != null) {
            ((RLottieDrawable) camera.drawable).start();
        }
    }

    public boolean canHaveJoinAction() {
        return mode == MODE_CHANNEL || mode == MODE_GROUP;
    }

    private void updateNotification(Action notificationAction, boolean animated) {
        if (animated) {
            if (isNotificationsEnabled) {
                notificationAction.update(true, R.raw.profile_unmuting, R.string.ProfileButtonMute);
            } else {
                notificationAction.update(true, R.raw.profile_muting, R.string.ProfileButtonUnmute);
            }
        } else {
            if (isNotificationsEnabled) {
                notificationAction.update(false, R.drawable.mute, R.string.ProfileButtonMute);
            } else {
                notificationAction.update(false, R.drawable.unmute, R.string.ProfileButtonUnmute);
            }
        }
    }

    private void applyVisibleActions() {
        if (isApplying) return;
        if (mode == MODE_MY_PROFILE) {
            activeCount = actions.size();
            invalidate();
            return;
        }

        List<Action> out = new ArrayList<>();
        boolean join = hasJoin();

        switch (mode) {
            case MODE_USER:
                insertIfAvailable(out, KEY_MESSAGE);
                insertIfAvailable(out, KEY_NOTIFICATION);
                insertIfAvailable(out, KEY_CALL);
                insertIfAvailable(out, KEY_VIDEO);
                insertIfNotAvailable(out, KEY_GIFT, KEY_VIDEO);
                break;
            case MODE_TOPIC:
                insertIfAvailable(out, KEY_MESSAGE);
                insertIfAvailable(out, KEY_NOTIFICATION);
                break;
            case MODE_CHANNEL:
                if (join) {
                    insertIfAvailable(out, KEY_JOIN);
                } else {
                    insertIfAvailable(out, KEY_VOICE_CHAT);
                    insertIfNotAvailable(out, KEY_STREAM, KEY_VOICE_CHAT);
                }
                insertIfAvailable(out, KEY_NOTIFICATION);
                if (!join) {
                    insertIfAvailable(out, KEY_DISCUSS);
                    insertIfNotAvailable2(out, KEY_GIFT, KEY_DISCUSS, KEY_STORY);
                }
                insertIfNotAvailable(out, KEY_SHARE, KEY_STORY);
                if (join) {
                    out.add(getOrCreate(KEY_REPORT));
                } else {
                    insertIfAvailable(out, KEY_STORY);
                    insertIfNotAvailable(out, KEY_LEAVE, KEY_STORY);
                }
                break;
            case MODE_GROUP:
            case MODE_FORUM:
                if (join) {
                    insertIfAvailable(out, KEY_JOIN);
                } else {
                    insertIfAvailable(out, KEY_MESSAGE);
                }
                insertIfAvailable(out, KEY_NOTIFICATION);
                if (join) {
                    out.add(getOrCreate(KEY_REPORT));
                } else {
                    insertIfAvailable(out, KEY_VOICE_CHAT);
                    insertIfNotAvailable(out, KEY_STREAM, KEY_VOICE_CHAT);
                    insertIfAvailable(out, KEY_LEAVE);
                }
                break;
            case MODE_BOT:
                insertIfAvailable(out, KEY_MESSAGE);
                insertIfAvailable(out, KEY_NOTIFICATION);
                insertIfAvailable(out, KEY_SHARE);
                out.add(getOrCreate(KEY_STOP));
                break;
        }

        AndroidUtilities.runOnUIThread(() -> {
            int oldCount = activeCount;
            activeCount = out.size();

            if (oldCount != activeCount && radialGradient != null) {
                createColorShader();
            }

            int c = actions.size();
            for (int i = 0; i < c; i++) {
                Action a = actions.get(i);
                if (a.isDeleting && !a.isDeleted) {
                    out.add(a);
                } else if (find(out, a.key) == null) {
                    a.delete();
                    out.add(a);
                }
            }

            actions.clear();
            actions.addAll(out);
            invalidate();
        });
    }

    private void insertIfAvailable(List<Action> list, int key) {
        if (allAvailableActions.contains(key)) {
            list.add(getOrCreate(key));
        }
    }

    private void insertIfNotAvailable(List<Action> list, int key, int notAvailable) {
        if (allAvailableActions.contains(key) &&
                !allAvailableActions.contains(notAvailable)) {
            list.add(getOrCreate(key));
        }
    }

    private void insertIfNotAvailable2(List<Action> list, int key, int notAvailable1, int notAvailable2) {
        if (allAvailableActions.contains(key) &&
                !allAvailableActions.contains(notAvailable1) &&
                !allAvailableActions.contains(notAvailable2)) {
            list.add(getOrCreate(key));
        }
    }

    private boolean hasJoin() {
        return allAvailableActions.contains(KEY_JOIN) &&
                !allAvailableActions.contains(KEY_LEAVE);
    }

    private Action getOrCreate(int key) {
        Action newAction = find(key);
        if (newAction != null) {
            if (key == KEY_NOTIFICATION) {
                updateNotification(newAction, false);
            }
            return newAction;
        }

        switch (key) {
            case KEY_MESSAGE:
                newAction = new Action(R.drawable.message, R.string.ProfileActionsMessage);
                break;
            case KEY_DISCUSS:
                newAction = new Action(R.drawable.message, R.string.ProfileActionsDiscuss);
                break;
            case KEY_GIFT:
                newAction = new Action(R.drawable.gift, R.string.ProfileActionsGift);
                newAction.supportsLoading = true;
                newAction.stopDelay = 200;
                break;
            case KEY_SHARE:
                newAction = new Action(R.drawable.action_share, R.string.ProfileActionsShare);
                break;
            case KEY_CALL:
                newAction = new Action(R.drawable.call, R.string.ProfileActionsCall);
                callAction = newAction;
                newAction.supportsLoading = true;
                newAction.stopDelay = 500;
                break;
            case KEY_VIDEO:
                newAction = new Action(R.drawable.video, R.string.ProfileActionsVideo);
                newAction.supportsLoading = true;
                newAction.stopDelay = 500;
                break;
            case KEY_JOIN:
                newAction = new Action(R.drawable.join, R.string.ProfileActionsJoin);
                newAction.supportsLoading = true;
                newAction.callDelay = 300;
                break;
            case KEY_REPORT:
                newAction = new Action(R.drawable.report, R.string.ProfileActionsReport);
                newAction.supportsLoading = true;
                newAction.stopDelay = 500;
                break;
            case KEY_LEAVE:
                newAction = new Action(R.drawable.leave, R.string.ProfileActionsLeave);
                newAction.supportsLoading = true;
                newAction.supportsAnimate = R.raw.profile_leave;
                newAction.stopDelay = 300;
                break;
            case KEY_VOICE_CHAT:
                newAction = new Action(R.drawable.live_stream, R.string.ProfileActionsVoiceChat);
                newAction.supportsLoading = true;
                newAction.supportsAnimate = R.raw.profile_voicechat;
                newAction.stopDelay = 500;
                break;
            case KEY_STREAM:
                newAction = new Action(R.drawable.live_stream, R.string.ProfileActionsLiveStream);
                newAction.supportsLoading = true;
                newAction.supportsAnimate = R.raw.profile_voicechat;
                newAction.stopDelay = 500;
                break;
            case KEY_STORY:
                newAction = new Action(R.drawable.story, R.string.ProfileActionsAddStory);
                break;
            case KEY_STOP:
                newAction = new Action(R.drawable.block, R.string.ProfileActionsStop);
                newAction.supportsLoading = true;
                newAction.stopDelay = 300;
                break;
            case KEY_NOTIFICATION:
                newAction = new Action();
                updateNotification(newAction, false);
                break;
        }

        if (newAction != null) {
            newAction.key = key;
        }
        return newAction;
    }

    private Action find(int key) {
        return find(actions, key);
    }

    private Action find(List<Action> actions, int key) {
        int c = actions.size();
        for (int i = 0; i < c; i++) {
            Action a = actions.get(i);
            if (!a.isDeleting && a.key == key) {
                return a;
            }
        }
        return null;
    }

    public boolean hasCall() {
        return allAvailableActions.contains(KEY_CALL) && callAction != null;
    }

    private boolean callAnimationStateLoaded = false;
    private float callBackwardAnimateFromX = -1;
    private float callBackwardAnimateFromY = -1;

    public void applyCallTransition(
            View callView,
            boolean isOpen,
            float maxBottom,
            float fraction
    ) {
        if (callView == null || getMeasuredWidth() <= 0f) {
            return;
        }

        float callAnimateFromX = callView.getLeft();
        float callAnimateFromY = callView.getTop();
        if (isOpen) {
            int c = actions.size();
            final float betweenPadding = xpadding / 2f;
            final float width = getItemWidth();
            float left = xpadding;

            for (int i = 0; i < c; i++) {
                Action action = actions.get(i);
                if (action.isDeleted) continue;
                if (action.key == KEY_CALL) {
                    callAction.rect.set(left, top, left + width, top + targetHeight);
                    break;
                }
                left += width + betweenPadding;
            }

            updateBounds(callAction);

            float callAnimateEndY = maxBottom - targetHeight - ypadding - top
                    + callAction.drawable.getBounds().centerY()
                    - callView.getMeasuredHeight() / 2f
                    - callAnimateFromY;
            float callAnimateEndX = callAction.drawable.getBounds().centerX()
                    - callView.getMeasuredWidth() / 2f
                    - callAnimateFromX;

            callView.setTranslationX(AndroidUtilities.lerp(0f, callAnimateEndX, fraction));
            callView.setTranslationY(AndroidUtilities.lerp(0f, callAnimateEndY, fraction));
        } else {
            if (!callAnimationStateLoaded) {
                callAnimationStateLoaded = true;
                callBackwardAnimateFromY = getTranslationY()
                        + callAction.drawable.getBounds().centerY()
                        - callView.getMeasuredHeight() / 2f
                        - callAnimateFromY;

                callBackwardAnimateFromX = callAction.drawable.getBounds().centerX()
                        - callView.getMeasuredWidth() / 2f
                        - callAnimateFromX;
            }

            callView.setTranslationX(AndroidUtilities.lerp(0f, callBackwardAnimateFromX, fraction));
            callView.setTranslationY(AndroidUtilities.lerp(0f, callBackwardAnimateFromY, fraction));
        }

        if (callView.getVisibility() != View.VISIBLE) {
            callView.setVisibility(View.VISIBLE);
        }
    }

    public interface OnActionClickListener {
        void onClick(int key, float x, float y);
    }

    private class Action {
        int key;

        private final ButtonBounce bounce = new ButtonBounce(ProfileActionsView.this);
        final RectF prevRect = new RectF();
        final RectF rect = new RectF();

        private final AnimatedFloat positionFraction = new AnimatedFloat(ProfileActionsView.this, 0, 250, CubicBezierInterpolator.DEFAULT);
        private final RectF to = new RectF();
        private final RectF from = new RectF();

        private Drawable drawable;
        private Text text;
        private float textScale = 1.0f;

        public void setText(CharSequence cs) {
            this.text = new Text(cs, 11, AndroidUtilities.bold())
                .multiline(3)
                .align(Layout.Alignment.ALIGN_CENTER);
        }

        boolean isOpening = false;
        boolean isDeleting = false;
        boolean isDeleted = false;

        int iconTranslationY = 0;
        float iconScale = 1f;

        LoadingDrawable loadingDrawable;
        boolean isLoading;
        boolean supportsLoading;
        int supportsAnimate;
        int callDelay = 0;
        long startTime;
        int stopDelay;

        public Action() {
        }

        public Action(int drawableRes, int textRes) {
            this(false, drawableRes, textRes);
        }

        public Action(boolean animated, int drawableRes, int textRes) {
            update(animated, drawableRes, textRes);
        }

        public float getAlpha() {
            if (isDeleting) {
                return 1f - positionFraction.set(1f);
            } else if (isOpening) {
                return positionFraction.set(1f);
            } else {
                return 1f;
            }
        }

        public void delete() {
            if (loadingDrawable != null) {
                loadingDrawable.disappear();
                supportsLoading = false;
                isLoading = false;
            }
            isDeleting = true;

            boolean isFirstItem = prevRect.left - 1 <= xpadding;
            boolean isLastItem = prevRect.right + 1 >= getMeasuredWidth() - xpadding;

            if (isFirstItem && isLastItem) {
                isFirstItem = isLastItem = false;
            }
            from.set(prevRect);
            to.set(prevRect);

            if (isFirstItem) {
                to.right = to.left;
            } else if (isLastItem) {
                to.left = to.right;
            } else if ((key == KEY_GIFT || key == KEY_DISCUSS) && mode == MODE_CHANNEL) {
                to.left = to.right;
            } else {
                to.left = to.right = to.centerX();
            }
            positionFraction.set(0f, true);
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        public void updateDrawable(boolean animated, int drawableRes) {
            if (animated) {
                RLottieDrawable drawable = new RLottieDrawable(
                    drawableRes,
                    String.valueOf(drawableRes),
                    dp(56),
                    dp(56),
                    false, null
                );
                drawable.setMasterParent(ProfileActionsView.this);
                drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                drawable.start();
                this.drawable = drawable;
            } else {
                this.drawable = getResources().getDrawable(drawableRes).mutate();
            }
        }

        @SuppressLint("UseCompatLoadingForDrawables")
        public void update(boolean animated, int drawableRes, int textRes) {
            updateDrawable(animated, drawableRes);
            this.setText(getString(textRes));
        }

        public void updatePosition() {
            if (isDeleting) {
                animatePosition();
                return;
            }

            if (isOpeningLayout) {
                isOpening = false;
                prevRect.set(rect);
                from.set(rect);
                to.set(rect);
                positionFraction.set(1f, true);
                return;
            }

            if (to.isEmpty()) {
                isOpening = true;
                to.set(rect);
                from.set(rect);

                boolean fromRight = rect.left - 1 <= xpadding;
                boolean fromLeft = rect.right + 1 >= getMeasuredWidth() - xpadding;

                if ((fromRight && fromLeft) ||
                        (firstAction != null && firstAction.key == key) ||
                        (lastAction != null && lastAction.key == key)) {
                    fromRight = fromLeft = false;
                }

                if ((key == KEY_CALL || key == KEY_VIDEO) && mode == MODE_USER) {
                    fromLeft = false;
                    fromRight = true;
                } else if ((key == KEY_GIFT || key == KEY_DISCUSS) && mode == MODE_CHANNEL) {
                    fromLeft = false;
                    fromRight = true;
                } else if (fromRight && firstAction != null && !firstAction.isDeleting) {
                    fromLeft = true;
                    fromRight = false;
                } else if (fromLeft && lastAction != null && !lastAction.isDeleting) {
                    fromLeft = false;
                    fromRight = true;
                }

                if (fromRight) {
                    from.left = from.right;
                } else if (fromLeft) {
                    from.right = from.left;
                } else {
                    from.left = from.right = to.centerX();
                }

                positionFraction.set(0f, true);
            }

            if (!rect.equals(to)) {
                from.set(prevRect);
                to.set(rect);
                positionFraction.set(0f, true);
            }

            animatePosition();
            prevRect.set(rect);
        }

        private void animatePosition() {
            float fraction = positionFraction.set(1f);
            if (fraction != 1f) {
                rect.left = AndroidUtilities.lerp(from.left, to.left, fraction);
                rect.right = AndroidUtilities.lerp(from.right, to.right, fraction);
            } else {
                isOpening = false;
                if (isDeleting) {
                    isDeleted = true;
                }
            }
        }

        public float getScale() {
            return bounce.getScale(0.04f);
        }
    }
}
