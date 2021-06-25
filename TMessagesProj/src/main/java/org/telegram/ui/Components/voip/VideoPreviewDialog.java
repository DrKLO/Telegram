package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.GroupCallActivity;
import org.webrtc.RendererCommon;

public abstract class VideoPreviewDialog extends FrameLayout {

    VoIPTextureView textureView;
    Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean isDismissed;
    float outProgress;
    FrameLayout container;

    View negativeButton;
    View positiveButton;

    private final LinearLayout buttonsLayout;
    private final ActionBar actionBar;
    private final RLottieImageView flipIconView;
    private final RLottieImageView micIconView;

    int flipIconEndFrame;
    private final TextView subtitle;

    public boolean micEnabled;

    CellFlickerDrawable drawable = new CellFlickerDrawable();

    public VideoPreviewDialog(@NonNull Context context, RecyclerListView listView, RecyclerListView fullscreenListView) {
        super(context);
        backgroundPaint.setColor(Theme.getColor(Theme.key_voipgroup_dialogBackground));

        actionBar = new ActionBar(context);

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setItemsColor(Theme.getColor(Theme.key_voipgroup_actionBarItems), false);
        actionBar.setTitle(LocaleController.getString("CallVideoPreviewTitle", R.string.CallVideoPreviewTitle));
        actionBar.setOccupyStatusBar(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    dismiss(false);
                }
                super.onItemClick(id);
            }
        });

        container = new FrameLayout(context);
        container.setClipChildren(false);

        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        container.addView(actionBar);

        textureView = new VoIPTextureView(context, false, false);
        textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        textureView.setRoundCorners(AndroidUtilities.dp(8));
        if (VoIPService.getSharedInstance() != null) {
            textureView.renderer.setMirror(VoIPService.getSharedInstance().isFrontFaceCamera());
        }
        textureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        textureView.clipToTexture = true;
        textureView.renderer.setAlpha(0);
        textureView.renderer.setRotateTextureWitchScreen(true);
        textureView.renderer.setUseCameraRotation(true);

        subtitle = new TextView(context);
        subtitle.setTextColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_nameText), (int) (255 * 0.4f)));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitle.setText(LocaleController.getString("VideoPreviewDesrciption", R.string.VideoPreviewDesrciption));
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        container.addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 24, 0, 24, 108));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView negative = new TextView(getContext()) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setAlpha(enabled ? 1.0f : 0.5f);
            }

            @Override
            public void setTextColor(int color) {
                super.setTextColor(color);
                setBackgroundDrawable(Theme.getRoundRectSelectorDrawable(color));
            }
        };
        negative.setMinWidth(AndroidUtilities.dp(64));
        negative.setTag(Dialog.BUTTON_POSITIVE);
        negative.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        negative.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        negative.setGravity(Gravity.CENTER);
        negative.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        negative.setText(LocaleController.getString("Cancel", R.string.Cancel));
        negative.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Theme.getColor(Theme.key_voipgroup_listViewBackground), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_nameText), (int) (255 * 0.3f))));
        negative.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));

        negativeButton = negative;

        TextView positive = new TextView(getContext()) {

            Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                Shader gradient = new LinearGradient(0, 0, getMeasuredWidth(), 0, new int[]{Theme.getColor(Theme.key_voipgroup_unmuteButton), Theme.getColor(Theme.key_voipgroup_unmuteButton2)}, null, Shader.TileMode.CLAMP);
                gradientPaint.setShader(gradient);
            }


            @Override
            protected void onDraw(Canvas canvas) {
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), gradientPaint);
                super.onDraw(canvas);
            }
        };
        positive.setMinWidth(AndroidUtilities.dp(64));
        positive.setTag(Dialog.BUTTON_POSITIVE);
        positive.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        positive.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        positive.setGravity(Gravity.CENTER);
        positive.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        positive.setText(LocaleController.getString("ShareVideo", R.string.ShareVideo));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            positive.setForeground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), Color.TRANSPARENT, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_nameText), (int) (255 * 0.3f))));
        }
        positive.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        positiveButton = positive;

        buttonsLayout.addView(negative, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1f, 0, 4, 0, 4, 0));
        buttonsLayout.addView(positive, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 1f, 0, 4, 0, 4, 0));

        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        container.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        if (VoIPService.getSharedInstance() != null) {
            textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
                @Override
                public void onFirstFrameRendered() {
                    textureView.animate().alpha(1f).setDuration(250);
                }

                @Override
                public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

                }
            });
            VoIPService.getSharedInstance().setLocalSink(textureView.renderer, false);
        }

        negative.setOnClickListener(view -> {
            dismiss(false);
        });
        positive.setOnClickListener(view -> {
            if (isDismissed) {
                return;
            }
            dismiss(true);
        });

        setAlpha(0);
        setTranslationX(AndroidUtilities.dp(32));
        animate().alpha(1f).translationX(0).setDuration(150).start();

        flipIconView = new RLottieImageView(context);
        flipIconView.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        flipIconView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(48), ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f))));
        RLottieDrawable flipIcon = new RLottieDrawable(R.raw.camera_flip, "" + R.raw.camera_flip, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
        flipIconView.setAnimation(flipIcon);
        flipIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        flipIconView.setOnClickListener(v -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().switchCamera();
                if (flipIconEndFrame == 18) {
                    flipIcon.setCustomEndFrame(flipIconEndFrame = 39);
                    flipIcon.start();
                } else {
                    flipIcon.setCurrentFrame(0, false);
                    flipIcon.setCustomEndFrame(flipIconEndFrame = 18);
                    flipIcon.start();
                }
            }
        });

        addView(flipIconView, LayoutHelper.createFrame(48, 48));


        micIconView = new RLottieImageView(context);
        micIconView.setPadding(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(9));
        micIconView.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(48), ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f))));
        RLottieDrawable micIcon = new RLottieDrawable(R.raw.voice_mini, "" + R.raw.voice_mini, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
        micIconView.setAnimation(micIcon);
        micIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        micEnabled = true;
        micIcon.setCurrentFrame(micEnabled ? 69 : 36);
        micIconView.setOnClickListener(v -> {
            micEnabled = !micEnabled;
            if (micEnabled) {
                micIcon.setCurrentFrame(36);
                micIcon.setCustomEndFrame(69);
            } else {
                micIcon.setCurrentFrame(69);
                micIcon.setCustomEndFrame(99);
            }
            micIcon.start();
        });
        addView(micIconView, LayoutHelper.createFrame(48, 48));

        setWillNotDraw(false);
    }

    public void dismiss(boolean apply) {
        if (isDismissed) {
            return;
        }
        isDismissed = true;
        animate().alpha(0f).translationX(AndroidUtilities.dp(32)).setDuration(150).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (getParent() != null) {
                    ((ViewGroup) getParent()).removeView(VideoPreviewDialog.this);
                }
                onDismiss(apply);
            }
        });
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = textureView.getRight() - AndroidUtilities.dp(48 + 12) - textureView.currentClipHorizontal;
        float y = textureView.getBottom() - AndroidUtilities.dp(48 + 12) - textureView.currentClipVertical;
        flipIconView.setTranslationX(x);
        flipIconView.setTranslationY(y);
        flipIconView.setScaleX(textureView.getScaleX());
        flipIconView.setScaleY(textureView.getScaleY());
        flipIconView.setPivotX(getMeasuredWidth() / 2f - x);
        flipIconView.setPivotY(getMeasuredHeight() / 2f - y);
        flipIconView.setAlpha(textureView.renderer.getAlpha() * (1f - outProgress));


        x = textureView.getLeft() + AndroidUtilities.dp(12) + textureView.currentClipHorizontal;
        micIconView.setTranslationX(x);
        micIconView.setTranslationY(y);
        micIconView.setScaleX(textureView.getScaleX());
        micIconView.setScaleY(textureView.getScaleY());
        micIconView.setPivotX(getMeasuredWidth() / 2f - x);
        micIconView.setPivotY(getMeasuredHeight() / 2f - y);
        micIconView.setAlpha(textureView.renderer.getAlpha() * (1f - outProgress));

        canvas.drawColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_voipgroup_actionBar), (int) (255 * (1f - outProgress))));
        if (isDismissed || textureView.renderer.getAlpha() != 1f) {
            invalidate();
        }

        if (!textureView.renderer.isFirstFrameRendered() && textureView.renderer.getAlpha() != 1f) {
            MarginLayoutParams layoutParams = (MarginLayoutParams) textureView.getLayoutParams();
            AndroidUtilities.rectTmp.set(layoutParams.leftMargin, layoutParams.topMargin, getMeasuredWidth() - layoutParams.rightMargin, getMeasuredHeight() - layoutParams.bottomMargin);
            float k = !GroupCallActivity.isLandscapeMode ? 9f / 16f : 16f / 9f;

            if (AndroidUtilities.rectTmp.width() / AndroidUtilities.rectTmp.height() > k) {
                float padding = (AndroidUtilities.rectTmp.width() - AndroidUtilities.rectTmp.height() * k) / 2f;
                AndroidUtilities.rectTmp.left += padding;
                AndroidUtilities.rectTmp.right -= padding;
            } else {
                float padding = (AndroidUtilities.rectTmp.height() - AndroidUtilities.rectTmp.width() * k) / 2f;
                AndroidUtilities.rectTmp.top += padding;
                AndroidUtilities.rectTmp.bottom -= padding;
            }

            drawable.setParentWidth(getMeasuredWidth());
            drawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(8));
            invalidate();
        }

        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    protected void onDismiss(boolean apply) {

    }

    boolean ignoreLayout = false;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean isLandscape = MeasureSpec.getSize(widthMeasureSpec) > MeasureSpec.getSize(heightMeasureSpec);
        ignoreLayout = true;

        if (isLandscape) {
            actionBar.setTitle(null);
            MarginLayoutParams marginLayoutParams = (MarginLayoutParams) textureView.getLayoutParams();
            marginLayoutParams.topMargin = AndroidUtilities.dp(8);
            marginLayoutParams.bottomMargin = AndroidUtilities.dp(76);
            marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = AndroidUtilities.dp(48);
            negativeButton.setVisibility(View.VISIBLE);
            subtitle.setVisibility(View.GONE);

            marginLayoutParams = (MarginLayoutParams) buttonsLayout.getLayoutParams();
            marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = AndroidUtilities.dp(80);
            marginLayoutParams.bottomMargin = AndroidUtilities.dp(16);

        } else {
            MarginLayoutParams marginLayoutParams = (MarginLayoutParams) textureView.getLayoutParams();
            actionBar.setTitle(LocaleController.getString("CallVideoPreviewTitle", R.string.CallVideoPreviewTitle));
            marginLayoutParams.topMargin = ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(8);
            marginLayoutParams.bottomMargin = AndroidUtilities.dp(168);
            marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = AndroidUtilities.dp(12);
            negativeButton.setVisibility(View.GONE);
            subtitle.setVisibility(View.VISIBLE);

            marginLayoutParams = (MarginLayoutParams) buttonsLayout.getLayoutParams();
            marginLayoutParams.rightMargin = marginLayoutParams.leftMargin = marginLayoutParams.bottomMargin = AndroidUtilities.dp(16);
        }
        ignoreLayout = false;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    public int getBackgroundColor() {
        int color = Theme.getColor(Theme.key_voipgroup_actionBar);
        color = ColorUtils.setAlphaComponent(color, (int) (255 * (getAlpha() * (1f - outProgress))));
        return color;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (getParent() != null) {
            ((View) getParent()).invalidate();
        }
    }

    public void update() {
        if (VoIPService.getSharedInstance() != null) {
            textureView.renderer.setMirror(VoIPService.getSharedInstance().isFrontFaceCamera());
        }
    }
}
