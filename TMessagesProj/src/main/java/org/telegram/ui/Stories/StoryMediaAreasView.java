package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineHeightSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Views.LocationMarker;
import org.telegram.ui.Components.Paint.Views.WeatherView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.EmojiAnimationsOverlay;
import org.telegram.ui.LocationActivity;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.Weather;

import java.util.ArrayList;

public class StoryMediaAreasView extends FrameLayout implements View.OnClickListener {

    private AreaView lastSelectedArea = null;
    private AreaView selectedArea = null;
    private HintView2 hintView = null;

    private final FrameLayout hintsContainer;
    private boolean malicious;

    Matrix matrix = new Matrix();
    float[] point = new float[2];

    private View parentView;
    private Theme.ResourcesProvider resourcesProvider;

    public StoryMediaAreasView(Context context, View parentView, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.parentView = parentView;
        this.resourcesProvider = resourcesProvider;
        parentHighlightAlpha = new AnimatedFloat(parentView, 0, 120, new LinearInterpolator());
        parentHighlightScaleAlpha = new AnimatedFloat(parentView, 0, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        setClipChildren(false);
        addView(hintsContainer = new FrameLayout(context));

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public static ArrayList<TL_stories.MediaArea> getMediaAreasFor(StoryEntry entry) {
        if (entry == null || entry.mediaEntities == null) {
            return null;
        }
        ArrayList<TL_stories.MediaArea> areas = new ArrayList<>();
        for (int i = 0; i < entry.mediaEntities.size(); i++) {
            if (entry.mediaEntities.get(i).mediaArea instanceof TL_stories.TL_mediaAreaSuggestedReaction) {
                areas.add(entry.mediaEntities.get(i).mediaArea);
            }
        }
        return areas;
    }

    protected void onHintVisible(boolean hintVisible) {

    }

    protected void presentFragment(BaseFragment fragment) {

    }

    private ArrayList<TL_stories.MediaArea> lastMediaAreas;

    public void set(TL_stories.StoryItem storyItem, EmojiAnimationsOverlay animationsOverlay) {
        ArrayList<TL_stories.MediaArea> mediaAreas = storyItem != null ? storyItem.media_areas : null;
        set(storyItem, mediaAreas, animationsOverlay);
    }

    public void set(TL_stories.StoryItem storyItem, ArrayList<TL_stories.MediaArea> mediaAreas, EmojiAnimationsOverlay animationsOverlay) {
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
        parentHighlightScaleAlpha.set(0, true);
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
            TL_stories.MediaArea mediaArea = mediaAreas.get(i);
            if (mediaArea != null && mediaArea.coordinates != null) {
                View areaView;
                if (mediaArea instanceof TL_stories.TL_mediaAreaSuggestedReaction) {
                    StoryReactionWidgetView storyReactionWidgetView = new StoryReactionWidgetView(getContext(), this, (TL_stories.TL_mediaAreaSuggestedReaction) mediaArea, animationsOverlay);
                    areaView = storyReactionWidgetView;
                    if (storyItem != null) {
                        storyReactionWidgetView.setViews(storyItem.views, false);
                    }
                    ScaleStateListAnimator.apply(areaView);
                } else if (mediaArea instanceof TL_stories.TL_mediaAreaWeather) {
                    TL_stories.TL_mediaAreaWeather weather = (TL_stories.TL_mediaAreaWeather) mediaArea;

                    Weather.State state = new Weather.State();
                    state.emoji = weather.emoji;
                    state.temperature = (float) weather.temperature_c;

                    LocationMarker marker = new LocationMarker(getContext(), LocationMarker.VARIANT_WEATHER, AndroidUtilities.density, 0);
                    marker.setMaxWidth(AndroidUtilities.displaySize.x);
                    marker.setIsVideo(true);
                    marker.setCodeEmoji(UserConfig.selectedAccount, state.getEmoji());
                    marker.setText(state.getTemperature());
                    marker.setType(3, weather.color);

                    areaView = new FitViewWidget(getContext(), marker, mediaArea);
                } else {
                    areaView = new AreaView(getContext(), parentView, mediaArea);
                }
                areaView.setOnClickListener(this);
                addView(areaView);

                totalArea += (mediaArea.coordinates.w / 100f * W) * (mediaArea.coordinates.h / 100f * H);
            }
        }
        malicious = false; // totalArea > W * H * .33f;

        hintsContainer.bringToFront();
    }

    public static int rgbaToArgb(int rgba) {
        int red = (rgba >>> 24) & 0xFF;
        int green = (rgba >>> 16) & 0xFF;
        int blue = (rgba >>> 8) & 0xFF;
        int alpha = rgba & 0xFF;

        return (alpha << 24) | (red << 16) | (green << 8) | blue;
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
            } else if (view instanceof FitViewWidget) {
                FitViewWidget child = (FitViewWidget) getChildAt(i);
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

        if (v instanceof StoryReactionWidgetView) {
            showEffect((StoryReactionWidgetView) v);
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

            if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaChannelPost) {
                Bundle args = new Bundle();
                args.putLong("chat_id", ((TL_stories.TL_mediaAreaChannelPost) selectedArea.mediaArea).channel_id);
                args.putInt("message_id", ((TL_stories.TL_mediaAreaChannelPost) selectedArea.mediaArea).msg_id);
                presentFragment(new ChatActivity(args));

                selectedArea = null;
                invalidate();
                return;
            }


            if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaUrl) {
                Browser.openUrl(getContext(), ((TL_stories.TL_mediaAreaUrl) selectedArea.mediaArea).url);
                selectedArea = null;
                invalidate();
                return;
            }

            LocationActivity fragment = new LocationActivity(3) {
                @Override
                protected boolean disablePermissionCheck() {
                    return true;
                }
            };
            fragment.fromStories = true;
            fragment.searchStories(selectedArea.mediaArea);
            fragment.setResourceProvider(resourcesProvider);
            TLRPC.TL_message message = new TLRPC.TL_message();
            if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaVenue) {
                TL_stories.TL_mediaAreaVenue areaVenue = (TL_stories.TL_mediaAreaVenue) selectedArea.mediaArea;
                TLRPC.TL_messageMediaVenue media = new TLRPC.TL_messageMediaVenue();
                media.venue_id = areaVenue.venue_id;
                media.venue_type = areaVenue.venue_type;
                media.title = areaVenue.title;
                media.address = areaVenue.address;
                media.provider = areaVenue.provider;
                media.geo = areaVenue.geo;
                message.media = media;
            } else if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaGeoPoint) {
                fragment.setInitialMaxZoom(true);
                TL_stories.TL_mediaAreaGeoPoint areaGeo = (TL_stories.TL_mediaAreaGeoPoint) selectedArea.mediaArea;
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

        selectedArea = lastSelectedArea = (AreaView) v;
        invalidate();
        if (hintView != null) {
            hintView.hide();
            hintView = null;
        }

        final HintView2 thisHint = hintView = new HintView2(getContext())
            .setSelectorColor(0x28ffffff)
            .setJointPx(0, selectedArea.getTranslationX() - dp(8))
            .setDuration(5000);

        boolean multiline = false;
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaChannelPost) {
            text.append(LocaleController.getString(R.string.StoryViewMessage));
        } else if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaUrl) {
            thisHint.setMultilineText(multiline = true);
            text.append(LocaleController.getString(R.string.StoryOpenLink));
            text.append("\n");
            TL_stories.TL_mediaAreaUrl area = (TL_stories.TL_mediaAreaUrl) selectedArea.mediaArea;
            int start = text.length();
            text.append(TextUtils.ellipsize(area.url, hintView.getTextPaint(), AndroidUtilities.displaySize.x * .6f, TextUtils.TruncateAt.END));
            text.setSpan(new RelativeSizeSpan(0.85f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(Theme.multAlpha(Color.WHITE, 0.6f)), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new LineHeightSpan() {
                @Override
                public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
                    fm.ascent -= dp(2);
                    fm.top -= dp(2);
                }
            }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            thisHint.setInnerPadding(11, 7, 11, 7);
        } else {
            text.append(LocaleController.getString(R.string.StoryViewLocation));
        }
        SpannableString arrowRight = new SpannableString(">");
        ColoredImageSpan imageSpan = new ColoredImageSpan(R.drawable.photos_arrow);
        imageSpan.translate(dp(multiline ? 1 : 2), dp(multiline ? 0 : 1));
        arrowRight.setSpan(imageSpan, 0, arrowRight.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableString arrowLeft = new SpannableString("<");
        imageSpan = new ColoredImageSpan(R.drawable.attach_arrow_right);
        imageSpan.translate(dp(multiline ? -1 : -2), dp(multiline ? 0 : 1));
        imageSpan.setScale(-1, 1);
        arrowLeft.setSpan(imageSpan, 0, arrowLeft.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceCharSequence(">", text, AndroidUtilities.isRTL(text) ? arrowLeft : arrowRight);

        thisHint.setText(text);
        thisHint.setOnHiddenListener(() -> {
            hintsContainer.removeView(thisHint);
            if (thisHint == hintView) {
                selectedArea = null;
                invalidate();
                onHintVisible(false);
            }
        });
        final int h = multiline ? 100 : 50;
        boolean top = selectedArea.getTranslationY() - dp(h) < AndroidUtilities.dp(100);
        thisHint.setDirection(top ? HintView2.DIRECTION_TOP : HintView2.DIRECTION_BOTTOM);
        if (selectedArea.mediaArea instanceof TL_stories.TL_mediaAreaChannelPost && (
            top ?
                selectedArea.getTranslationY() + selectedArea.getMeasuredHeight() / 2f > getMeasuredHeight() - dp(120) :
                selectedArea.getTranslationY() - selectedArea.getMeasuredHeight() / 2f - dp(h) < dp(120)
        )) {
            thisHint.setTranslationY(selectedArea.getTranslationY() - selectedArea.getMeasuredHeight() / 3f);
        } else if (top) {
            thisHint.setTranslationY(selectedArea.getTranslationY() + selectedArea.getMeasuredHeight() / 2f);
        } else {
            thisHint.setTranslationY(selectedArea.getTranslationY() - selectedArea.getMeasuredHeight() / 2f - dp(h));
        }
        thisHint.setOnClickListener(view -> onClick(selectedArea));
        thisHint.setPadding(dp(8), dp(8), dp(8), dp(8));
        hintsContainer.addView(thisHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, h));
        thisHint.show();
        onHintVisible(true);
    }

    public void showEffect(StoryReactionWidgetView v) {

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
            } else if (view instanceof FitViewWidget) {
                FitViewWidget child = (FitViewWidget) view;
                int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
                child.layout(-w / 2, -h / 2, w / 2, h / 2);
                child.setTranslationX((float) (child.mediaArea.coordinates.x / 100 * getMeasuredWidth()));
                child.setTranslationY((float) (child.mediaArea.coordinates.y / 100 * getMeasuredHeight()));
                child.setRotation((float) child.mediaArea.coordinates.rotation);
            }
        }
    }


    private final Rect rect = new Rect();
    private final RectF rectF = new RectF();
    private final Paint cutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        cutPaint.setColor(0xffffffff);
    }
    public final AnimatedFloat parentHighlightAlpha;
    public final AnimatedFloat parentHighlightScaleAlpha;

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == hintsContainer) {
            drawHighlight(canvas);
        } else if (child instanceof AreaView) {
            canvas.save();
            canvas.translate(child.getLeft(), child.getTop());
            canvas.concat(child.getMatrix());
            ((AreaView) child).customDraw(canvas);
            canvas.restore();
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private final Path clipPath = new Path();
    private final float[] radii = new float[8];

    private void drawHighlight(Canvas canvas) {
        float parentAlpha = parentHighlightAlpha.set(selectedArea != null && selectedArea.supportsBounds && !selectedArea.scaleOnTap);
        boolean scale = selectedArea != null && selectedArea.scaleOnTap;
        float parentScale = parentHighlightScaleAlpha.set(scale);
        if (parentAlpha > 0) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            canvas.drawColor(Theme.multAlpha(0x18000000, parentAlpha));
            for (int i = 0; i < getChildCount(); ++i) {
                View child2 = getChildAt(i);
                if (child2 != hintsContainer) {
                    AreaView areaView = (AreaView) child2;
                    float alpha = areaView.highlightAlpha.set(child2 == selectedArea && selectedArea.supportsBounds);
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
        if ((scale || parentScale > 0) && lastSelectedArea != null) {
            if (parentBitmap == null) {
                parentBitmap = getPlayingBitmap();
            }
            if (parentBitmap != null) {
                canvas.drawColor(Theme.multAlpha(0x30000000, parentScale));
                canvas.save();
                clipPath.rewind();
                rectF.set(lastSelectedArea.getX(), lastSelectedArea.getY(), lastSelectedArea.getX() + lastSelectedArea.getMeasuredWidth(), lastSelectedArea.getY() + lastSelectedArea.getMeasuredHeight());
                final float s = AndroidUtilities.lerp(1.0f, 1.05f * (lastSelectedArea.bounceOnTap ? lastSelectedArea.bounce.getScale(.05f) : 1f), parentScale);
                canvas.scale(s, s, rectF.centerX(), rectF.centerY());
                canvas.rotate(lastSelectedArea.getRotation(), rectF.centerX(), rectF.centerY());
                final float r = (lastSelectedArea.mediaArea.coordinates.flags & 1) != 0 ?
                    (float) (lastSelectedArea.mediaArea.coordinates.radius / 100.0 * getWidth()) :
                    .2f * lastSelectedArea.getMeasuredHeight();
                clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
                canvas.clipPath(clipPath);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                rect.set(0, 0, parentBitmap.getWidth(), parentBitmap.getHeight());
                canvas.rotate(-lastSelectedArea.getRotation(), rectF.centerX(), rectF.centerY());
                canvas.drawBitmap(parentBitmap, rect, AndroidUtilities.rectTmp, null);
                canvas.restore();

                canvas.save();
                canvas.translate(lastSelectedArea.getX(), lastSelectedArea.getY());
                canvas.rotate(lastSelectedArea.getRotation(), lastSelectedArea.getPivotX(), lastSelectedArea.getPivotY());
                canvas.scale(lastSelectedArea.getScaleX() * s, lastSelectedArea.getScaleY() * s, lastSelectedArea.getPivotX(), lastSelectedArea.getPivotY());
                lastSelectedArea.drawAbove(canvas);
                canvas.restore();
            }
        } else if (parentBitmap != null) {
            parentBitmap.recycle();
            parentBitmap = null;
        }
        invalidate();
    }

    private Bitmap parentBitmap;
    protected Bitmap getPlayingBitmap() {
        return null;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (parentBitmap != null) {
            parentBitmap.recycle();
            parentBitmap = null;
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

    public boolean hasSelectedForScale() {
        return selectedArea != null && (selectedArea.scaleOnTap || selectedArea.supportsBounds);
    }

    // returns true when widget that is drawn above the story (f.ex. reaction) is at these coordinates
    // used to detect that back gesture safety measure should not occur
    public boolean hasAreaAboveAt(float x, float y) {
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof StoryReactionWidgetView) {
                if (rotatedRectContainsPoint(
                        child.getTranslationX(),
                        child.getTranslationY(),
                        child.getMeasuredWidth(),
                        child.getMeasuredHeight(),
                        child.getRotation(),
                        x, y
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean rotatedRectContainsPoint(float rcx, float rcy, float rw, float rh, float rr, float x, float y) {
        float xt = x - rcx;
        float yt = y - rcy;

        double rad = Math.toRadians(-rr);
        float xr = (float) (xt * Math.cos(rad) - yt * Math.sin(rad));
        float yr = (float) (xt * Math.sin(rad) + yt * Math.cos(rad));

        return xr >= -rw/2 && xr <= rw/2 && yr >= -rh/2 && yr <= rh/2;
    }

    public void onStoryItemUpdated(TL_stories.StoryItem storyItem, boolean animated) {
        if (storyItem == null) {
            return;
        }
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof StoryReactionWidgetView) {
                StoryReactionWidgetView storyReactionWidgetView = (StoryReactionWidgetView) getChildAt(i);
                storyReactionWidgetView.setViews(storyItem.views, animated);
            }
        }
    }

    public boolean hasClickableViews(float x, float y) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == hintsContainer) {
                continue;
            }
            if (!(child instanceof StoryReactionWidgetView)) {
                continue;
            }
            child.getMatrix().invert(matrix);
            point[0] = x;
            point[1] = y;
            matrix.mapPoints(point);
            if (point[0] >= child.getLeft() && point[0] <= child.getRight() &&
                point[1] >= child.getTop() && point[1] <= child.getBottom()) {
                return true;
            }
        }
        return false;
    }

    public static class AreaView extends View {

        public final AnimatedFloat highlightAlpha;

        public final TL_stories.MediaArea mediaArea;

        public AreaView(Context context, View parent, TL_stories.MediaArea mediaArea) {
            super(context);
            this.mediaArea = mediaArea;
            supportsBounds = mediaArea instanceof TL_stories.TL_mediaAreaGeoPoint || mediaArea instanceof TL_stories.TL_mediaAreaVenue || mediaArea instanceof TL_stories.TL_mediaAreaUrl;
            supportsShining = mediaArea instanceof TL_stories.TL_mediaAreaGeoPoint || mediaArea instanceof TL_stories.TL_mediaAreaVenue;
            final boolean hasRadius = mediaArea instanceof TL_stories.TL_mediaAreaGeoPoint || mediaArea instanceof TL_stories.TL_mediaAreaVenue || (mediaArea.coordinates.flags & 1) != 0;
            scaleOnTap = hasRadius;
            ripple = hasRadius;
            bounceOnTap = hasRadius;
            highlightAlpha = new AnimatedFloat(parent, 0, 120, new LinearInterpolator());
            strokeGradientPaint.setStyle(Paint.Style.STROKE);
            rippleDrawable.setCallback(this);
        }

        private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokeGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient gradient, strokeGradient;
        private final Matrix gradientMatrix = new Matrix();
        private final Drawable rippleDrawable = Theme.createSelectorDrawable(0x45FFFFFF, Theme.RIPPLE_MASK_ALL);
        public final ButtonBounce bounce = new ButtonBounce(this);

        private boolean supportsBounds = false;
        private boolean supportsShining = false;
        private boolean scaleOnTap;
        private boolean bounceOnTap;
        private boolean ripple;
        private boolean shining = false;
        private long startTime;

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (getParent() instanceof View) {
                    bounce.setAdditionalInvalidate(((View) getParent())::invalidate);
                }
                bounce.setPressed(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    rippleDrawable.setHotspot(event.getX(), event.getY());
                }
                rippleDrawable.setState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled});
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                bounce.setPressed(false);
                rippleDrawable.setState(new int[]{});
            }
            return super.dispatchTouchEvent(event) || true;
        }

        public void customDraw(Canvas canvas) {

        }

        public void drawAbove(Canvas canvas) {
            if (!ripple) {
                return;
            }
            final float r = getInnerRadius();

            AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            rippleDrawable.setBounds(0, 0, getWidth(), getHeight());
            rippleDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == rippleDrawable || super.verifyDrawable(who);
        }

        public float getInnerRadius() {
            if (getParent() instanceof View && mediaArea != null && mediaArea.coordinates != null) {
                return (mediaArea.coordinates.flags & 1) != 0 ?
                        (float) (mediaArea.coordinates.radius / 100.0 * ((View) getParent()).getWidth() / getScaleX()) :
                        .2f * getMeasuredHeight();
            } else {
                return .2f * getMeasuredHeight();
            }
        }

        private final Path clipPath = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            final float r = getInnerRadius();
            drawAbove(canvas);

            if (supportsShining && shining && gradient != null) {
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
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, gradientPaint);

                strokeGradient.setLocalMatrix(gradientMatrix);
                strokeGradientPaint.setShader(strokeGradient);
                final float sw = AndroidUtilities.dpf2(1.5f);
                strokeGradientPaint.setStrokeWidth(sw);
                AndroidUtilities.rectTmp.inset(sw / 2f, sw / 2f);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, r - sw / 2f, r - sw / 2f, strokeGradientPaint);

                invalidate();
            }
        }

        private final Runnable shineRunnable = this::shineInternal;

        public void shine() {
            if (!supportsShining) {
                return;
            }
            AndroidUtilities.cancelRunOnUIThread(shineRunnable);
            AndroidUtilities.runOnUIThread(shineRunnable, 400L);
        }

        private void shineInternal() {
            if (!supportsShining) {
                return;
            }
            shining = true;
            startTime = System.currentTimeMillis();
            gradient = new LinearGradient(0, 0, 40, 0, new int[] { 0x00ffffff, 0x2dffffff, 0x2dffffff, 0x00ffffff }, new float[] { 0, .4f, .6f, 1f }, Shader.TileMode.CLAMP );
            strokeGradient = new LinearGradient(0, 0, 40, 0, new int[] { 0x00ffffff, 0x20ffffff, 0x20ffffff, 0x00ffffff }, new float[] { 0, .4f, .6f, 1f }, Shader.TileMode.CLAMP );
            invalidate();
        }
    }

    public static class FitViewWidget extends FrameLayout {

        public final TL_stories.MediaArea mediaArea;
        public final View child;

        public FitViewWidget(Context context, View widget, TL_stories.MediaArea mediaArea) {
            super(context);
            this.mediaArea = mediaArea;
            addView(this.child = widget);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

            this.child.measure(widthMeasureSpec, heightMeasureSpec);

            final int childw = this.child.getMeasuredWidth() - this.child.getPaddingLeft() - this.child.getPaddingRight();
            final int childh = this.child.getMeasuredHeight() - this.child.getPaddingTop() - this.child.getPaddingBottom();
            this.child.setPivotX(this.child.getPaddingLeft() + childw / 2f);
            this.child.setPivotY(this.child.getPaddingTop() + childh / 2f);

            final int w = MeasureSpec.getSize(widthMeasureSpec);
            final int h = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(w, h);

            float scale = Math.min(
                (float) w / childw,
                (float) h / childh
            );
            this.child.setTranslationX(w / 2f - (childw / 2f + this.child.getPaddingLeft()));
            this.child.setTranslationY(h / 2f - (childh / 2f + this.child.getPaddingTop()));
            this.child.setScaleX(scale);
            this.child.setScaleY(scale);
        }
    }
}
