package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Xfermode;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import com.google.android.exoplayer2.extractor.CeaUtil;
import com.google.zxing.qrcode.decoder.Mode;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.EmojiAnimationsOverlay;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;

public class StoryMediaAreasView extends FrameLayout implements View.OnClickListener {

    private AreaView selectedArea = null;
    private HintView2 hintView = null;

    private final FrameLayout hintsContainer;
    private boolean malicious;

    Matrix matrix = new Matrix();
    float[] point = new float[2];

    private Theme.ResourcesProvider resourcesProvider;

    public StoryMediaAreasView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);
        addView(hintsContainer = new FrameLayout(context));
    }

    protected void onHintVisible(boolean hintVisible) {

    }

    protected void presentFragment(BaseFragment fragment) {

    }

    private ArrayList<TLRPC.MediaArea> lastMediaAreas;

    public void set(ArrayList<TLRPC.MediaArea> mediaAreas) {
        if (mediaAreas == lastMediaAreas && (mediaAreas == null || lastMediaAreas == null || mediaAreas.size() == lastMediaAreas.size())) {
            return;
        }

        if (hintView != null) {
            hintView.hide();
            hintView = null;
        }

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child != hintsContainer) {
                removeView(child);
                i--;
            }
        }
        selectedArea = null;
        invalidate();
        onHintVisible(false);
        malicious = false;

        lastMediaAreas = mediaAreas;
        if (mediaAreas == null) {
            return;
        }

        shined = false;

        final float W = 1080, H = 1920;

        double totalArea = 0;
        for (int i = 0; i < mediaAreas.size(); ++i) {
            TLRPC.MediaArea mediaArea = mediaAreas.get(i);
            if (mediaArea != null && mediaArea.coordinates != null) {
                AreaView areaView = new AreaView(getContext(), this, mediaArea);
                areaView.setOnClickListener(this);
                addView(areaView);

                totalArea += (mediaArea.coordinates.w / 100f * W) * (mediaArea.coordinates.h / 100f * H);
            }
        }
        malicious = totalArea > W * H * .33f;

        hintsContainer.bringToFront();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        for (int i = 0; i < getChildCount(); ++i) {
            View view = getChildAt(i);
            if (view == hintsContainer) {
                hintsContainer.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                );
            } else if (view instanceof AreaView) {
                AreaView child = (AreaView) getChildAt(i);
                child.measure(
                    MeasureSpec.makeMeasureSpec((int) Math.ceil(child.mediaArea.coordinates.w / 100 * w), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((int) Math.ceil(child.mediaArea.coordinates.h / 100 * h), MeasureSpec.EXACTLY)
                );
            }
        }
        setMeasuredDimension(w, h);
    }

    @Override
    public void onClick(View v) {
        if (!(v instanceof AreaView)) {
            return;
        }

        if (selectedArea == v) {
            AndroidUtilities.runOnUIThread(() -> {
                if (hintView != null) {
                    hintView.hide();
                    hintView = null;
                }
                onHintVisible(false);
            }, 200);

            LocationActivity fragment = new LocationActivity(3) {
                @Override
                protected boolean disablePermissionCheck() {
                    return true;
                }
            };
            fragment.setResourceProvider(resourcesProvider);
            TLRPC.TL_message message = new TLRPC.TL_message();
            if (selectedArea.mediaArea instanceof TLRPC.TL_mediaAreaVenue) {
                TLRPC.TL_mediaAreaVenue areaVenue = (TLRPC.TL_mediaAreaVenue) selectedArea.mediaArea;
                TLRPC.TL_messageMediaVenue media = new TLRPC.TL_messageMediaVenue();
                media.venue_id = areaVenue.venue_id;
                media.venue_type = areaVenue.venue_type;
                media.title = areaVenue.title;
                media.address = areaVenue.address;
                media.provider = areaVenue.provider;
                media.geo = areaVenue.geo;
                message.media = media;
            } else if (selectedArea.mediaArea instanceof TLRPC.TL_mediaAreaGeoPoint) {
                fragment.setInitialMaxZoom(true);
                TLRPC.TL_mediaAreaGeoPoint areaGeo = (TLRPC.TL_mediaAreaGeoPoint) selectedArea.mediaArea;
                TLRPC.TL_messageMediaGeo media = new TLRPC.TL_messageMediaGeo();
                media.geo = areaGeo.geo;
                message.media = media;
            } else {
                selectedArea = null;
                invalidate();
                return;
            }
            fragment.setSharingAllowed(false);
            fragment.setMessageObject(new MessageObject(UserConfig.selectedAccount, message, false, false));
            presentFragment(fragment);
            selectedArea = null;
            invalidate();
            return;
        }

        if (selectedArea != null && malicious) {
            onClickAway();
            return;
        }

        selectedArea = (AreaView) v;
        invalidate();
        if (hintView != null) {
            hintView.hide();
            hintView = null;
        }

        boolean top = selectedArea.getTranslationY() < AndroidUtilities.dp(100);

        SpannableStringBuilder text = new SpannableStringBuilder(LocaleController.getString("StoryViewLocation", R.string.StoryViewLocation));
        SpannableString arrowRight = new SpannableString(">");
        ColoredImageSpan imageSpan = new ColoredImageSpan(R.drawable.photos_arrow);
        imageSpan.translate(dp(2), dp(1));
        arrowRight.setSpan(imageSpan, 0, arrowRight.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString arrowLeft = new SpannableString("<");
        imageSpan = new ColoredImageSpan(R.drawable.attach_arrow_right);
        imageSpan.translate(dp(-2), dp(1));
        imageSpan.setScale(-1, 1);
        arrowLeft.setSpan(imageSpan, 0, arrowLeft.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceCharSequence(">", text, arrowRight);
        AndroidUtilities.replaceCharSequence("<", text, arrowLeft);

        final HintView2 thisHint = hintView = new HintView2(getContext(), top ? HintView2.DIRECTION_TOP : HintView2.DIRECTION_BOTTOM)
            .setText(text)
            .setSelectorColor(0x28ffffff)
            .setJointPx(0, selectedArea.getTranslationX() - dp(8))
            .setDuration(5000);
        thisHint.setOnHiddenListener(() -> {
            hintsContainer.removeView(thisHint);
            if (thisHint == hintView) {
                selectedArea = null;
                invalidate();
                onHintVisible(false);
            }
        });
        if (top) {
            hintView.setTranslationY(selectedArea.getTranslationY() + selectedArea.getMeasuredHeight() / 2f);
        } else {
            hintView.setTranslationY(selectedArea.getTranslationY() - selectedArea.getMeasuredHeight() / 2f - dp(50));
        }
        hintView.setOnClickListener(view -> onClick(selectedArea));
        hintView.setPadding(dp(8), dp(8), dp(8), dp(8));
        hintsContainer.addView(hintView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50));
        hintView.show();
        onHintVisible(true);
    }

    public void closeHint() {
        if (hintView != null) {
            hintView.hide();
            hintView = null;
        }
        selectedArea = null;
        invalidate();
        onHintVisible(false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getChildCount() == 0 || hintView == null || !hintView.shown()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            onClickAway();
        }
        super.onTouchEvent(event);
        return true;
    }

    private void onClickAway() {
        if (hintView != null) {
            hintView.hide();
            hintView = null;
        }
        selectedArea = null;
        invalidate();
        onHintVisible(false);

        if (malicious) {
            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child != hintsContainer) {
                    child.setClickable(false);
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); ++i) {
            View view = getChildAt(i);
            if (view == hintsContainer) {
                view.layout(0, 0, right - left, bottom - top);
            } else if (view instanceof AreaView) {
                AreaView child = (AreaView) view;
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                child.layout(-w / 2, -h / 2, w / 2, h / 2);
                child.setTranslationX((float) (child.mediaArea.coordinates.x / 100 * getMeasuredWidth()));
                child.setTranslationY((float) (child.mediaArea.coordinates.y / 100 * getMeasuredHeight()));
                child.setRotation((float) child.mediaArea.coordinates.rotation);
            }
        }
    }


    private final RectF rectF = new RectF();
    private final Paint cutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        cutPaint.setColor(0xffffffff);
    }
    public final AnimatedFloat parentHighlightAlpha = new AnimatedFloat(this, 0, 120, new LinearInterpolator());

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == hintsContainer) {
            drawHighlight(canvas);
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void drawHighlight(Canvas canvas) {
        float parentAlpha = parentHighlightAlpha.set(selectedArea != null);
        if (parentAlpha > 0) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            canvas.drawColor(Theme.multAlpha(0x18000000, parentAlpha));
            for (int i = 0; i < getChildCount(); ++i) {
                View child2 = getChildAt(i);
                if (child2 != hintsContainer) {
                    AreaView areaView = (AreaView) child2;
                    float alpha = areaView.highlightAlpha.set(child2 == selectedArea);
                    if (alpha > 0) {
                        canvas.save();
                        rectF.set(child2.getX(), child2.getY(), child2.getX() + child2.getMeasuredWidth(), child2.getY() + child2.getMeasuredHeight());
                        canvas.rotate(child2.getRotation(), rectF.centerX(), rectF.centerY());
                        cutPaint.setAlpha((int) (0xFF * alpha));
                        canvas.drawRoundRect(rectF, rectF.height() * .2f, rectF.height() * .2f, cutPaint);
                        canvas.restore();
                    }
                }
            }
            canvas.restore();
        }
    }

    private boolean shined = false;
    public void shine() {
        if (shined) {
            return;
        }
        shined = true;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof AreaView) {
                ((AreaView) child).shine();
            }
        }
    }

    public boolean hasSelected() {
        return selectedArea != null;
    }

    public static class AreaView extends View {

        public final AnimatedFloat highlightAlpha;

        public final TLRPC.MediaArea mediaArea;

        public AreaView(Context context, View parent, TLRPC.MediaArea mediaArea) {
            super(context);
            this.mediaArea = mediaArea;
            highlightAlpha = new AnimatedFloat(parent, 0, 120, new LinearInterpolator());
            strokeGradientPaint.setStyle(Paint.Style.STROKE);
        }

        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokeGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient gradient, strokeGradient;
        private final Matrix gradientMatrix = new Matrix();

        private boolean shining = false;
        private long startTime;

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (shining && gradient != null) {
                float w = getMeasuredWidth() * .7f;
                float t = (System.currentTimeMillis() - startTime) / 600f;
                float tx = t * (getMeasuredWidth() + w) - w;

                if (t >= 1) {
                    shining = false;
                    return;
                }

                gradientMatrix.reset();
                gradientMatrix.postScale(w / 40, 1);
                gradientMatrix.postTranslate(tx, 0);
                gradient.setLocalMatrix(gradientMatrix);
                gradientPaint.setShader(gradient);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, .2f * getMeasuredHeight(), .2f * getMeasuredHeight(), gradientPaint);

                strokeGradient.setLocalMatrix(gradientMatrix);
                strokeGradientPaint.setShader(strokeGradient);
                final float sw = AndroidUtilities.dpf2(1.5f);
                strokeGradientPaint.setStrokeWidth(sw);
                AndroidUtilities.rectTmp.inset(sw / 2f, sw / 2f);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, .2f * getMeasuredHeight() - sw / 2f, .2f * getMeasuredHeight() - sw / 2f, strokeGradientPaint);

                invalidate();
            }
        }

        private final Runnable shineRunnable = this::shineInternal;

        public void shine() {
            AndroidUtilities.cancelRunOnUIThread(shineRunnable);
            AndroidUtilities.runOnUIThread(shineRunnable, 400L);
        }

        private void shineInternal() {
            shining = true;
            startTime = System.currentTimeMillis();
            gradient = new LinearGradient(0, 0, 40, 0, new int[] { 0x00ffffff, 0x2dffffff, 0x2dffffff, 0x00ffffff }, new float[] { 0, .4f, .6f, 1f }, Shader.TileMode.CLAMP );
            strokeGradient = new LinearGradient(0, 0, 40, 0, new int[] { 0x00ffffff, 0x20ffffff, 0x20ffffff, 0x00ffffff }, new float[] { 0, .4f, .6f, 1f }, Shader.TileMode.CLAMP );
            invalidate();
        }
    }
}
