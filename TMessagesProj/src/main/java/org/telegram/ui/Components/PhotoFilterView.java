/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.BubbleActivity;
import org.telegram.ui.Cells.PhotoEditRadioCell;
import org.telegram.ui.Cells.PhotoEditToolCell;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

@SuppressLint("NewApi")
public class PhotoFilterView extends FrameLayout implements FilterShaders.FilterShadersDelegate, StoryRecorder.Touchable {

    private final static int curveGranularity = 100;
    private final static int curveDataStep = 2;

    private boolean showOriginal;

    private int enhanceTool;
    private int exposureTool;
    private int contrastTool;
    private int saturationTool;
    private int warmthTool;
    private int fadeTool;
    private int softenSkinTool;
    private int highlightsTool;
    private int shadowsTool;
    private int vignetteTool;
    private int grainTool;
    private int sharpenTool;
    private int tintShadowsTool;
    private int tintHighlightsTool;
    private int rowsCount;

    private float enhanceValue; //0 100
    private float exposureValue; //-100 100
    private float contrastValue; //-100 100
    private float warmthValue; //-100 100
    private float saturationValue; //-100 100
    private float fadeValue; // 0 100
    private float softenSkinValue; // 0 100
    private int tintShadowsColor; //0 0xffffffff
    private int tintHighlightsColor; //0 0xffffffff
    private float highlightsValue; //-100 100
    private float shadowsValue; //-100 100
    private float vignetteValue; //0 100
    private float grainValue; //0 100
    private int blurType; //0 none, 1 radial, 2 linear
    private float sharpenValue; //0 100
    private boolean filtersEmpty;
    private CurvesToolValue curvesToolValue;
    private float blurExcludeSize;
    private Point blurExcludePoint;
    private float blurExcludeBlurSize;
    private float blurAngle;

    private MediaController.SavedFilterState lastState;

    private FrameLayout toolsView;
    private TextView doneTextView;
    private TextView cancelTextView;
    private TextureView textureView;
    private boolean ownsTextureView;
    private boolean ownLayout;
    private FilterGLThread eglThread;
    private RecyclerListView recyclerListView;
    private FrameLayout blurLayout;
    private PhotoFilterBlurControl blurControl;
    private PhotoFilterCurvesControl curvesControl;
    private TextView blurOffButton;
    private TextView blurRadialButton;
    private TextView blurLinearButton;
    private FrameLayout curveLayout;
    private RadioButton[] curveRadioButton = new RadioButton[4];
    private PaintingOverlay paintingOverlay;
    private boolean isMirrored;

    private boolean inBubbleMode;

    private int selectedTool;

    private ImageView tuneItem;
    private ImageView blurItem;
    private ImageView curveItem;

    private Bitmap bitmapToEdit;
    private Bitmap bitmapMask;
    private final Rect maskRect = new Rect();
    private final Matrix maskMatrix = new Matrix();
    private final Paint maskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private int orientation;
    private final Theme.ResourcesProvider resourcesProvider;

    public static class CurvesValue {

        public float blacksLevel = 0.0f;
        public float shadowsLevel = 25.0f;
        public float midtonesLevel = 50.0f;
        public float highlightsLevel = 75.0f;
        public float whitesLevel = 100.0f;

        public float previousBlacksLevel = 0.0f;
        public float previousShadowsLevel = 25.0f;
        public float previousMidtonesLevel = 50.0f;
        public float previousHighlightsLevel = 75.0f;
        public float previousWhitesLevel = 100.0f;

        public float[] cachedDataPoints;

        public float[] getDataPoints() {
            if (cachedDataPoints == null) {
                interpolateCurve();
            }
            return cachedDataPoints;
        }

        public void saveValues() {
            previousBlacksLevel = blacksLevel;
            previousShadowsLevel = shadowsLevel;
            previousMidtonesLevel = midtonesLevel;
            previousHighlightsLevel = highlightsLevel;
            previousWhitesLevel = whitesLevel;
        }

        public void restoreValues() {
            blacksLevel = previousBlacksLevel;
            shadowsLevel = previousShadowsLevel;
            midtonesLevel = previousMidtonesLevel;
            highlightsLevel = previousHighlightsLevel;
            whitesLevel = previousWhitesLevel;
            interpolateCurve();
        }

        public float[] interpolateCurve() {
            float[] points = new float[] {
                    -0.001f, blacksLevel / 100.0f,
                    0.0f, blacksLevel / 100.0f,
                    0.25f, shadowsLevel / 100.0f,
                    0.5f, midtonesLevel / 100.0f,
                    0.75f, highlightsLevel / 100.0f,
                    1f, whitesLevel / 100.0f,
                    1.001f, whitesLevel / 100.0f
            };

            ArrayList<Float> dataPoints = new ArrayList<>(100);
            ArrayList<Float> interpolatedPoints = new ArrayList<>(100);

            interpolatedPoints.add(points[0]);
            interpolatedPoints.add(points[1]);

            for (int index = 1; index < points.length / 2 - 2; index++) {
                float point0x = points[(index - 1) * 2];
                float point0y = points[(index - 1) * 2 + 1];
                float point1x = points[(index) * 2];
                float point1y = points[(index) * 2 + 1];
                float point2x = points[(index + 1) * 2];
                float point2y = points[(index + 1) * 2 + 1];
                float point3x = points[(index + 2) * 2];
                float point3y = points[(index + 2) * 2 + 1];


                for (int i = 1; i < curveGranularity; i++) {
                    float t = (float) i * (1.0f / (float) curveGranularity);
                    float tt = t * t;
                    float ttt = tt * t;

                    float pix = 0.5f * (2 * point1x + (point2x - point0x) * t + (2 * point0x - 5 * point1x + 4 * point2x - point3x) * tt + (3 * point1x - point0x - 3 * point2x + point3x) * ttt);
                    float piy = 0.5f * (2 * point1y + (point2y - point0y) * t + (2 * point0y - 5 * point1y + 4 * point2y - point3y) * tt + (3 * point1y - point0y - 3 * point2y + point3y) * ttt);

                    piy = Math.max(0, Math.min(1, piy));

                    if (pix > point0x) {
                        interpolatedPoints.add(pix);
                        interpolatedPoints.add(piy);
                    }

                    if ((i - 1) % curveDataStep == 0) {
                        dataPoints.add(piy);
                    }
                }
                interpolatedPoints.add(point2x);
                interpolatedPoints.add(point2y);
            }
            interpolatedPoints.add(points[12]);
            interpolatedPoints.add(points[13]);

            cachedDataPoints = new float[dataPoints.size()];
            for (int a = 0; a < cachedDataPoints.length; a++) {
                cachedDataPoints[a] = dataPoints.get(a);
            }
            float[] retValue = new float[interpolatedPoints.size()];
            for (int a = 0; a < retValue.length; a++) {
                retValue[a] = interpolatedPoints.get(a);
            }
            return retValue;
        }

        public boolean isDefault() {
            return Math.abs(blacksLevel - 0) < 0.00001 && Math.abs(shadowsLevel - 25) < 0.00001 && Math.abs(midtonesLevel - 50) < 0.00001 && Math.abs(highlightsLevel - 75) < 0.00001 && Math.abs(whitesLevel - 100) < 0.00001;
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeFloat(blacksLevel);
            stream.writeFloat(shadowsLevel);
            stream.writeFloat(midtonesLevel);
            stream.writeFloat(highlightsLevel);
            stream.writeFloat(whitesLevel);
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            blacksLevel = previousBlacksLevel = stream.readFloat(exception);
            shadowsLevel = previousShadowsLevel = stream.readFloat(exception);
            midtonesLevel = previousMidtonesLevel = stream.readFloat(exception);
            highlightsLevel = previousHighlightsLevel = stream.readFloat(exception);
            whitesLevel = previousWhitesLevel = stream.readFloat(exception);
        }
    }

    public static class CurvesToolValue {

        public CurvesValue luminanceCurve = new CurvesValue();
        public CurvesValue redCurve = new CurvesValue();
        public CurvesValue greenCurve = new CurvesValue();
        public CurvesValue blueCurve = new CurvesValue();
        public ByteBuffer curveBuffer;

        public int activeType;

        public final static int CurvesTypeLuminance = 0;
        public final static int CurvesTypeRed = 1;
        public final static int CurvesTypeGreen = 2;
        public final static int CurvesTypeBlue = 3;

        public CurvesToolValue() {
            curveBuffer = ByteBuffer.allocateDirect(200 * 4);
            curveBuffer.order(ByteOrder.LITTLE_ENDIAN);
        }

        public void fillBuffer() {
            curveBuffer.position(0);
            float[] luminanceCurveData = luminanceCurve.getDataPoints();
            float[] redCurveData = redCurve.getDataPoints();
            float[] greenCurveData = greenCurve.getDataPoints();
            float[] blueCurveData = blueCurve.getDataPoints();
            for (int a = 0; a < 200; a++) {
                curveBuffer.put((byte) (redCurveData[a] * 255));
                curveBuffer.put((byte) (greenCurveData[a] * 255));
                curveBuffer.put((byte) (blueCurveData[a] * 255));
                curveBuffer.put((byte) (luminanceCurveData[a] * 255));
            }
            curveBuffer.position(0);
        }

        public boolean shouldBeSkipped() {
            return luminanceCurve.isDefault() && redCurve.isDefault() && greenCurve.isDefault() && blueCurve.isDefault();
        }

        public void serializeToStream(AbstractSerializedData stream) {
            luminanceCurve.serializeToStream(stream);
            redCurve.serializeToStream(stream);
            greenCurve.serializeToStream(stream);
            blueCurve.serializeToStream(stream);
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            luminanceCurve.readParams(stream, exception);
            redCurve.readParams(stream, exception);
            greenCurve.readParams(stream, exception);
            blueCurve.readParams(stream, exception);
        }
    }

    public PhotoFilterView(Context context, VideoEditTextureView videoTextureView, Bitmap bitmap, int rotation, MediaController.SavedFilterState state, PaintingOverlay overlay, int hasFaces, boolean mirror, boolean ownLayout, BlurringShader.BlurManager blurManager, Theme.ResourcesProvider resourcesProvider) {
        this(context, videoTextureView, bitmap, null, rotation, state, overlay, hasFaces, mirror, ownLayout, blurManager, resourcesProvider);
    }

    public PhotoFilterView(Context context, VideoEditTextureView videoTextureView, Bitmap bitmap, Bitmap mask, int rotation, MediaController.SavedFilterState state, PaintingOverlay overlay, int hasFaces, boolean mirror, boolean ownLayout, BlurringShader.BlurManager blurManager, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.ownLayout = ownLayout;
        this.resourcesProvider = resourcesProvider;

        inBubbleMode = context instanceof BubbleActivity;
        paintingOverlay = overlay;
        isMirrored = mirror;

        rowsCount = 0;
        if (hasFaces == 1) {
            softenSkinTool = rowsCount++;
        } else if (hasFaces == 0) {
            softenSkinTool = -1;
        }
        enhanceTool = rowsCount++;
        exposureTool = rowsCount++;
        contrastTool = rowsCount++;
        saturationTool = rowsCount++;
        warmthTool = rowsCount++;
        fadeTool = rowsCount++;
        highlightsTool = rowsCount++;
        shadowsTool = rowsCount++;
        vignetteTool = rowsCount++;
        if (hasFaces == 2) {
            softenSkinTool = rowsCount++;
        }
        if (videoTextureView == null) {
            grainTool = rowsCount++;
        } else {
            grainTool = -1;
        }
        sharpenTool = rowsCount++;
        tintShadowsTool = rowsCount++;
        tintHighlightsTool = rowsCount++;

        if (state != null) {
            enhanceValue = state.enhanceValue;
            softenSkinValue = state.softenSkinValue;
            exposureValue = state.exposureValue;
            contrastValue = state.contrastValue;
            warmthValue = state.warmthValue;
            saturationValue = state.saturationValue;
            fadeValue = state.fadeValue;
            tintShadowsColor = state.tintShadowsColor;
            tintHighlightsColor = state.tintHighlightsColor;
            highlightsValue = state.highlightsValue;
            shadowsValue = state.shadowsValue;
            vignetteValue = state.vignetteValue;
            grainValue = state.grainValue;
            blurType = state.blurType;
            sharpenValue = state.sharpenValue;
            curvesToolValue = state.curvesToolValue;
            blurExcludeSize = state.blurExcludeSize;
            blurExcludePoint = state.blurExcludePoint;
            blurExcludeBlurSize = state.blurExcludeBlurSize;
            filtersEmpty = state.isEmpty();
            blurAngle = state.blurAngle;
            lastState = state;
        } else {
            curvesToolValue = new CurvesToolValue();
            blurExcludeSize = 0.35f;
            blurExcludePoint = new Point(0.5f, 0.5f);
            blurExcludeBlurSize = 0.15f;
            blurAngle = (float) Math.PI / 2.0f;
            filtersEmpty = true;
        }
        bitmapToEdit = bitmap;
        bitmapMask = mask;
        orientation = rotation;

        if (videoTextureView != null) {
            textureView = videoTextureView;
            videoTextureView.setDelegate(thread -> {
                eglThread = thread;
                eglThread.setFilterGLThreadDelegate(PhotoFilterView.this);
            });
        } else {
            ownsTextureView = true;
            textureView = new TextureView(context) {
                @Override
                public void setTransform(@Nullable Matrix transform) {
                    super.setTransform(transform);
                    if (eglThread != null) {
                        eglThread.updateUiBlurTransform(transform, getWidth(), getHeight());
                    }
                }
            };
            if (ownLayout) {
                addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
            }
            textureView.setVisibility(INVISIBLE);
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    if (eglThread == null && surface != null) {
                        eglThread = new FilterGLThread(surface, bitmapToEdit, orientation, isMirrored, null, ownLayout, blurManager, width, height);
                        if (!ownLayout) {
                            eglThread.updateUiBlurGradient(gradientTop, gradientBottom);
                            eglThread.updateUiBlurTransform(textureView.getTransform(null), textureView.getWidth(), textureView.getHeight());
                        }
                        eglThread.setFilterGLThreadDelegate(PhotoFilterView.this);
                        eglThread.setSurfaceTextureSize(width, height);
                        eglThread.requestRender(true, true, false);
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                    if (eglThread != null) {
                        eglThread.setSurfaceTextureSize(width, height);
                        eglThread.requestRender(false, true, false);
                        eglThread.postRunnable(() -> {
                            if (eglThread != null) {
                                eglThread.requestRender(false, true, false);
                            }
                        });
                    }
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    if (eglThread != null) {
                        eglThread.shutdown();
                        eglThread = null;
                    }
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });
        }

        blurControl = new PhotoFilterBlurControl(context);
        blurControl.setVisibility(INVISIBLE);
        if (ownLayout) {
            addView(blurControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        }
        blurControl.setDelegate((centerPoint, falloff, size, angle) -> {
            blurExcludeSize = size;
            blurExcludePoint = centerPoint;
            blurExcludeBlurSize = falloff;
            blurAngle = angle;
            if (eglThread != null) {
                eglThread.requestRender(false);
            }
        });

        curvesControl = new PhotoFilterCurvesControl(context, curvesToolValue);
        curvesControl.setDelegate(() -> {
            updateFiltersEmpty();
            if (eglThread != null) {
                eglThread.requestRender(false);
            }
        });
        curvesControl.setVisibility(INVISIBLE);
        if (ownLayout) {
            addView(curvesControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
        }

        toolsView = new FrameLayout(context);
        addView(toolsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 186 + (!ownLayout ? 40 : 0), Gravity.LEFT | Gravity.BOTTOM));

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xff000000);
        toolsView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));

        cancelTextView = new TextView(context);
        cancelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelTextView.setTextColor(0xffffffff);
        cancelTextView.setGravity(Gravity.CENTER);
        cancelTextView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        cancelTextView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        cancelTextView.setText(LocaleController.getString(R.string.Cancel).toUpperCase());
        cancelTextView.setTypeface(AndroidUtilities.bold());
        frameLayout.addView(cancelTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        doneTextView = new TextView(context);
        doneTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneTextView.setTextColor(getThemedColor(Theme.key_chat_editMediaButton));
        doneTextView.setGravity(Gravity.CENTER);
        doneTextView.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_PICKER_SELECTOR_COLOR, 0));
        doneTextView.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        doneTextView.setText(LocaleController.getString(R.string.Done).toUpperCase());
        doneTextView.setTypeface(AndroidUtilities.bold());
        frameLayout.addView(doneTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        LinearLayout linearLayout = new LinearLayout(context);
        frameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));

        tuneItem = new ImageView(context);
        tuneItem.setScaleType(ImageView.ScaleType.CENTER);
        tuneItem.setImageResource(R.drawable.msg_photo_settings);
        tuneItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
        tuneItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(tuneItem, LayoutHelper.createLinear(56, 48));
        tuneItem.setOnClickListener(v -> {
            selectedTool = 0;
            tuneItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            blurItem.setColorFilter(null);
            curveItem.setColorFilter(null);
            switchMode();
        });

        blurItem = new ImageView(context);
        blurItem.setScaleType(ImageView.ScaleType.CENTER);
        blurItem.setImageResource(R.drawable.msg_photo_blur);
        blurItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(blurItem, LayoutHelper.createLinear(56, 48));
        blurItem.setOnClickListener(v -> {
            selectedTool = 1;
            tuneItem.setColorFilter(null);
            blurItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            curveItem.setColorFilter(null);
            switchMode();
        });
        if (videoTextureView != null) {
            blurItem.setVisibility(GONE);
        }

        curveItem = new ImageView(context);
        curveItem.setScaleType(ImageView.ScaleType.CENTER);
        curveItem.setImageResource(R.drawable.msg_photo_curve);
        curveItem.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        linearLayout.addView(curveItem, LayoutHelper.createLinear(56, 48));
        curveItem.setOnClickListener(v -> {
            selectedTool = 2;
            tuneItem.setColorFilter(null);
            blurItem.setColorFilter(null);
            curveItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            switchMode();
        });

        recyclerListView = new RecyclerListViewWithShadows(context);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        recyclerListView.setAdapter(new ToolsAdapter(context));
        toolsView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120 + (!ownLayout ? 60 : 0), Gravity.LEFT | Gravity.TOP));

        curveLayout = new FrameLayout(context);
        curveLayout.setVisibility(INVISIBLE);
        toolsView.addView(curveLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 78, Gravity.CENTER_HORIZONTAL, 0, 40 + (!ownLayout ? 40 : 0), 0, 0));

        LinearLayout curveTextViewContainer = new LinearLayout(context);
        curveTextViewContainer.setOrientation(LinearLayout.HORIZONTAL);
        curveLayout.addView(curveTextViewContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        for (int a = 0; a < 4; a++) {
            FrameLayout frameLayout1 = new FrameLayout(context);
            frameLayout1.setTag(a);

            curveRadioButton[a] = new RadioButton(context);
            curveRadioButton[a].setSize(AndroidUtilities.dp(20));
            frameLayout1.addView(curveRadioButton[a], LayoutHelper.createFrame(30, 30, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            TextView curveTextView = new TextView(context);
            curveTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            curveTextView.setGravity(Gravity.CENTER_VERTICAL);
            if (a == 0) {
                String str = LocaleController.getString(R.string.CurvesAll);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xffffffff);
                curveRadioButton[a].setColor(0xffffffff, 0xffffffff);
            } else if (a == 1) {
                String str = LocaleController.getString(R.string.CurvesRed);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xffe64d4d);
                curveRadioButton[a].setColor(0xffe64d4d, 0xffe64d4d);
            } else if (a == 2) {
                String str = LocaleController.getString(R.string.CurvesGreen);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xff5abb5f);
                curveRadioButton[a].setColor(0xff5abb5f, 0xff5abb5f);
            } else if (a == 3) {
                String str = LocaleController.getString(R.string.CurvesBlue);
                curveTextView.setText(str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase());
                curveTextView.setTextColor(0xff3dadee);
                curveRadioButton[a].setColor(0xff3dadee, 0xff3dadee);
            }
            frameLayout1.addView(curveTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 38, 0, 0));

            curveTextViewContainer.addView(frameLayout1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, a == 0 ? 0 : 30, 0, 0, 0));
            frameLayout1.setOnClickListener(v -> {
                int num = (Integer) v.getTag();
                curvesToolValue.activeType = num;
                for (int a1 = 0; a1 < 4; a1++) {
                    curveRadioButton[a1].setChecked(a1 == num, true);
                }
                curvesControl.invalidate();
            });
        }

        blurLayout = new FrameLayout(context);
        blurLayout.setVisibility(INVISIBLE);
        toolsView.addView(blurLayout, LayoutHelper.createFrame(280, 60, Gravity.CENTER_HORIZONTAL, 0, 40 + (!ownLayout ? 40 : 0), 0, 0));

        blurOffButton = new TextView(context);
        blurOffButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurOffButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurOffButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurOffButton.setText(LocaleController.getString(R.string.BlurOff));
        blurLayout.addView(blurOffButton, LayoutHelper.createFrame(80, 60));
        blurOffButton.setOnClickListener(v -> {
            blurType = 0;
            updateSelectedBlurType();
            blurControl.setVisibility(INVISIBLE);
            if (eglThread != null) {
                eglThread.requestRender(false);
            }
        });

        blurRadialButton = new TextView(context);
        blurRadialButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurRadialButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurRadialButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurRadialButton.setText(LocaleController.getString(R.string.BlurRadial));
        blurLayout.addView(blurRadialButton, LayoutHelper.createFrame(80, 80, Gravity.LEFT | Gravity.TOP, 100, 0, 0, 0));
        blurRadialButton.setOnClickListener(v -> {
            blurType = 1;
            updateSelectedBlurType();
            blurControl.setVisibility(VISIBLE);
            blurControl.setType(1);
            if (eglThread != null) {
                eglThread.requestRender(false);
            }
        });

        blurLinearButton = new TextView(context);
        blurLinearButton.setCompoundDrawablePadding(AndroidUtilities.dp(2));
        blurLinearButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        blurLinearButton.setGravity(Gravity.CENTER_HORIZONTAL);
        blurLinearButton.setText(LocaleController.getString(R.string.BlurLinear));
        blurLayout.addView(blurLinearButton, LayoutHelper.createFrame(80, 80, Gravity.LEFT | Gravity.TOP, 200, 0, 0, 0));
        blurLinearButton.setOnClickListener(v -> {
            blurType = 2;
            updateSelectedBlurType();
            blurControl.setVisibility(VISIBLE);
            blurControl.setType(0);
            if (eglThread != null) {
                eglThread.requestRender(false);
            }
        });

        updateSelectedBlurType();

        if (Build.VERSION.SDK_INT >= 21 && !inBubbleMode && ownLayout) {
            if (ownsTextureView) {
                ((LayoutParams) textureView.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
            }
            ((LayoutParams) curvesControl.getLayoutParams()).topMargin = AndroidUtilities.statusBarHeight;
        }
    }

    public void updateColors() {
        if (doneTextView != null) {
            doneTextView.setTextColor(getThemedColor(Theme.key_chat_editMediaButton));
        }
        if (tuneItem != null && tuneItem.getColorFilter() != null) {
            tuneItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
        }
        if (blurItem != null && blurItem.getColorFilter() != null) {
            blurItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
        }
        if (curveItem != null && curveItem.getColorFilter() != null) {
            curveItem.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
        }
        updateSelectedBlurType();
    }

    private void updateSelectedBlurType() {
        if (blurType == 0) {
            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.msg_blur_off).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurOffButton.setTextColor(getThemedColor(Theme.key_chat_editMediaButton));

            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);

            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 1) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);

            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.msg_blur_radial).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurRadialButton.setTextColor(getThemedColor(Theme.key_chat_editMediaButton));

            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_linear, 0, 0);
            blurLinearButton.setTextColor(0xffffffff);
        } else if (blurType == 2) {
            blurOffButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_off, 0, 0);
            blurOffButton.setTextColor(0xffffffff);

            blurRadialButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.msg_blur_radial, 0, 0);
            blurRadialButton.setTextColor(0xffffffff);

            Drawable drawable = blurOffButton.getContext().getResources().getDrawable(R.drawable.msg_blur_linear).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_editMediaButton), PorterDuff.Mode.MULTIPLY));
            blurLinearButton.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null);
            blurLinearButton.setTextColor(getThemedColor(Theme.key_chat_editMediaButton));
        }
        updateFiltersEmpty();
    }

    public MediaController.SavedFilterState getSavedFilterState() {
        MediaController.SavedFilterState state = new MediaController.SavedFilterState();
        state.enhanceValue = enhanceValue;
        state.exposureValue = exposureValue;
        state.contrastValue = contrastValue;
        state.warmthValue = warmthValue;
        state.saturationValue = saturationValue;
        state.fadeValue = fadeValue;
        state.softenSkinValue = softenSkinValue;
        state.tintShadowsColor = tintShadowsColor;
        state.tintHighlightsColor = tintHighlightsColor;
        state.highlightsValue = highlightsValue;
        state.shadowsValue = shadowsValue;
        state.vignetteValue = vignetteValue;
        state.grainValue = grainValue;
        state.blurType = blurType;
        state.sharpenValue = sharpenValue;
        state.curvesToolValue = curvesToolValue;
        state.blurExcludeSize = blurExcludeSize;
        state.blurExcludePoint = blurExcludePoint;
        state.blurExcludeBlurSize = blurExcludeBlurSize;
        state.blurAngle = blurAngle;
        return lastState = state;
    }

    public boolean hasChanges() {
        if (lastState != null) {
            return enhanceValue != lastState.enhanceValue ||
                    contrastValue != lastState.contrastValue ||
                    highlightsValue != lastState.highlightsValue ||
                    exposureValue != lastState.exposureValue ||
                    warmthValue != lastState.warmthValue ||
                    saturationValue != lastState.saturationValue ||
                    vignetteValue != lastState.vignetteValue ||
                    shadowsValue != lastState.shadowsValue ||
                    grainValue != lastState.grainValue ||
                    sharpenValue != lastState.sharpenValue ||
                    fadeValue != lastState.fadeValue ||
                    softenSkinValue != lastState.softenSkinValue ||
                    tintHighlightsColor != lastState.tintHighlightsColor ||
                    tintShadowsColor != lastState.tintShadowsColor ||
                    !curvesToolValue.shouldBeSkipped();
        } else {
            return enhanceValue != 0 || contrastValue != 0 || highlightsValue != 0 || exposureValue != 0 || warmthValue != 0 || saturationValue != 0 || vignetteValue != 0 ||
                    shadowsValue != 0 || grainValue != 0 || sharpenValue != 0 || fadeValue != 0 || softenSkinValue != 0 || tintHighlightsColor != 0 || tintShadowsColor != 0 || !curvesToolValue.shouldBeSkipped();
        }
    }

    private static class RecyclerListViewWithShadows extends RecyclerListView  {
        private final Paint topPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bottomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private boolean top, bottom;
        private AnimatedFloat topAlpha = new AnimatedFloat(this);
        private AnimatedFloat bottomAlpha = new AnimatedFloat(this);

        public RecyclerListViewWithShadows(Context context) {
            super(context);
            topPaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(8), new int[] {0xff000000, 0x00000000}, new float[] {0, 1}, Shader.TileMode.CLAMP));
            bottomPaint.setShader(new LinearGradient(0, 0, 0, AndroidUtilities.dp(8), new int[] {0x00000000, 0xff000000}, new float[] {0, 1}, Shader.TileMode.CLAMP));
        }

        @Override
        public void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            final float topAlpha = this.topAlpha.set(top ? 1 : 0);
            topPaint.setAlpha((int) (0xFF * topAlpha));
            canvas.drawRect(0, 0, getWidth(), AndroidUtilities.dp(8), topPaint);

            final float bottomAlpha = this.bottomAlpha.set(bottom ? 1 : 0);
            bottomPaint.setAlpha((int) (0xFF * bottomAlpha));
            canvas.save();
            canvas.translate(0, getHeight() - AndroidUtilities.dp(8));
            canvas.drawRect(0, 0, getWidth(), AndroidUtilities.dp(8), bottomPaint);
            canvas.restore();
        }

        @Override
        public void onScrolled(int dx, int dy) {
            super.onScrolled(dx, dy);
            updateAlphas();
        }

        private void updateAlphas() {
            final boolean top = canScrollVertically(-1);
            final boolean bottom = canScrollVertically(1);
            if (top != this.top || bottom != this.bottom) {
                this.top = top;
                this.bottom = bottom;
                invalidate();
            }
        }
    }

    public boolean onTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (textureView instanceof VideoEditTextureView) {
                if (((VideoEditTextureView) textureView).containsPoint(event.getX(), event.getY())) {
                    setShowOriginal(true);
                }
            } else {
                if (event.getX() >= textureView.getX() && event.getY() >= textureView.getY() && event.getX() <= textureView.getX() + textureView.getWidth() && event.getY() <= textureView.getY() + textureView.getHeight()) {
                    setShowOriginal(true);
                }
            }
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            setShowOriginal(false);
        }
        return true;
    }

    private void setShowOriginal(boolean value) {
        if (showOriginal == value) {
            return;
        }
        showOriginal = value;
        if (eglThread != null) {
            eglThread.requestRender(false);
        }
    }

    private void updateFiltersEmpty() {
        filtersEmpty =
            Math.abs(enhanceValue) < 0.1f &&
            Math.abs(softenSkinValue) < 0.1f &&
            Math.abs(exposureValue) < 0.1f &&
            Math.abs(contrastValue) < 0.1f &&
            Math.abs(warmthValue) < 0.1f &&
            Math.abs(saturationValue) < 0.1f &&
            Math.abs(fadeValue) < 0.1f &&
            tintShadowsColor == 0 &&
            tintHighlightsColor == 0 &&
            Math.abs(highlightsValue) < 0.1f &&
            Math.abs(shadowsValue) < 0.1f &&
            Math.abs(vignetteValue) < 0.1f &&
            Math.abs(grainValue) < 0.1f &&
            blurType == 0 &&
            Math.abs(sharpenValue) < 0.1f &&
            curvesToolValue.shouldBeSkipped();
    }

    public void switchMode() {
        if (selectedTool == 0) {
            blurControl.setVisibility(INVISIBLE);
            blurLayout.setVisibility(INVISIBLE);
            curveLayout.setVisibility(INVISIBLE);
            curvesControl.setVisibility(INVISIBLE);

            recyclerListView.setVisibility(VISIBLE);
        } else if (selectedTool == 1) {
            recyclerListView.setVisibility(INVISIBLE);
            curveLayout.setVisibility(INVISIBLE);
            curvesControl.setVisibility(INVISIBLE);

            blurLayout.setVisibility(VISIBLE);
            if (blurType != 0) {
                blurControl.setVisibility(VISIBLE);
            }
            updateSelectedBlurType();
        } else if (selectedTool == 2) {
            recyclerListView.setVisibility(INVISIBLE);
            blurLayout.setVisibility(INVISIBLE);
            blurControl.setVisibility(INVISIBLE);

            curveLayout.setVisibility(VISIBLE);
            curvesControl.setVisibility(VISIBLE);
            curvesToolValue.activeType = 0;
            for (int a = 0; a < 4; a++) {
                curveRadioButton[a].setChecked(a == 0, false);
            }
        }
    }

    public void shutdown() {
        if (ownsTextureView) {
            if (eglThread != null) {
                eglThread.shutdown();
                eglThread = null;
            }
            textureView.setVisibility(GONE);
        } else if (textureView instanceof VideoEditTextureView) {
            VideoEditTextureView videoEditTextureView = (VideoEditTextureView) textureView;
            if (lastState == null) {
                videoEditTextureView.setDelegate(null);
            } else if (eglThread != null) {
                eglThread.setFilterGLThreadDelegate(FilterShaders.getFilterShadersDelegate(lastState));
            }
        }
    }

    public TextureView getMyTextureView() {
        if (ownsTextureView && !ownLayout) {
            return textureView;
        }
        return null;
    }

    public void init() {
        textureView.setVisibility(VISIBLE);
    }

    public Bitmap getBitmap() {
        return eglThread != null ? eglThread.getTexture() : null;
    }

    private void fixLayout(int viewWidth, int viewHeight) {
        if (!ownLayout) {
            return;
        }
        viewWidth -= AndroidUtilities.dp(28);
        viewHeight -= AndroidUtilities.dp(14 + 140 + 60) + (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0);

        float bitmapW;
        float bitmapH;
        if (bitmapToEdit != null) {
            if (orientation % 360 == 90 || orientation % 360 == 270) {
                bitmapW = bitmapToEdit.getHeight();
                bitmapH = bitmapToEdit.getWidth();
            } else {
                bitmapW = bitmapToEdit.getWidth();
                bitmapH = bitmapToEdit.getHeight();
            }
        } else {
            bitmapW = textureView.getWidth();
            bitmapH = textureView.getHeight();
        }
        float scaleX = viewWidth / bitmapW;
        float scaleY = viewHeight / bitmapH;
        if (scaleX > scaleY) {
            bitmapH = viewHeight;
            bitmapW = (int) Math.ceil(bitmapW * scaleY);
        } else {
            bitmapW = viewWidth;
            bitmapH = (int) Math.ceil(bitmapH * scaleX);
        }

        int bitmapX = (int) Math.ceil((viewWidth - bitmapW) / 2 + AndroidUtilities.dp(14));
        int bitmapY = (int) Math.ceil((viewHeight - bitmapH) / 2 + AndroidUtilities.dp(14) + (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0));

        int width = (int) bitmapW;
        int height = (int) bitmapH;
        if (ownsTextureView) {
            LayoutParams layoutParams = (LayoutParams) textureView.getLayoutParams();
            layoutParams.leftMargin = bitmapX;
            layoutParams.topMargin = bitmapY;
            layoutParams.width = width;
            layoutParams.height = height;
        }

        curvesControl.setActualArea(bitmapX, bitmapY - (Build.VERSION.SDK_INT >= 21 && !inBubbleMode ? AndroidUtilities.statusBarHeight : 0), width, height);

        blurControl.setActualAreaSize(width, height);
        LayoutParams layoutParams;
        layoutParams = (LayoutParams) blurControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(38);

        layoutParams = (LayoutParams) curvesControl.getLayoutParams();
        layoutParams.height = viewHeight + AndroidUtilities.dp(28);

        if (AndroidUtilities.isTablet()) {
            int total = AndroidUtilities.dp(86) * 10;
            layoutParams = (FrameLayout.LayoutParams) recyclerListView.getLayoutParams();
            if (total < viewWidth) {
                layoutParams.width = total;
                layoutParams.leftMargin = (viewWidth - total) / 2;
            } else {
                layoutParams.width = LayoutHelper.MATCH_PARENT;
                layoutParams.leftMargin = 0;
            }
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (paintingOverlay != null && child == textureView) {
            canvas.save();
            canvas.translate(textureView.getLeft(), textureView.getTop());
            if (bitmapMask != null && textureView.getVisibility() == View.VISIBLE) {
                maskRect.set(0, 0, textureView.getMeasuredWidth(), textureView.getMeasuredHeight());
                if (orientation != 0) {
                    maskMatrix.reset();
                    maskMatrix.postRotate(orientation, bitmapMask.getWidth() / 2f, bitmapMask.getHeight() / 2f);
                    float dxy = (bitmapMask.getHeight() - bitmapMask.getWidth()) / 2f;
                    maskMatrix.postTranslate(dxy, -dxy);
                    maskMatrix.postScale(maskRect.width() / (float) bitmapMask.getHeight(), maskRect.height() / (float) bitmapMask.getWidth());
                    canvas.drawBitmap(bitmapMask, maskMatrix, maskPaint);
                } else {
                    canvas.drawBitmap(bitmapMask, null, maskRect, maskPaint);
                }
            }
            float scale = textureView.getMeasuredWidth() / (float) paintingOverlay.getMeasuredWidth();
            canvas.scale(scale, scale);
            paintingOverlay.draw(canvas);
            canvas.restore();
        }
        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        fixLayout(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public float getShadowsValue() {
        return (shadowsValue * 0.55f + 100.0f) / 100.0f;
    }

    @Override
    public float getHighlightsValue() {
        return (highlightsValue * 0.75f + 100.0f) / 100.0f;
    }

    @Override
    public float getEnhanceValue() {
        return (enhanceValue / 100.0f);
    }

    @Override
    public float getExposureValue() {
        return (exposureValue / 100.0f);
    }

    @Override
    public float getContrastValue() {
        return (contrastValue / 100.0f) * 0.3f + 1;
    }

    @Override
    public float getWarmthValue() {
        return warmthValue / 100.0f;
    }

    @Override
    public float getVignetteValue() {
        return vignetteValue / 100.0f;
    }

    @Override
    public float getSharpenValue() {
        return 0.11f + sharpenValue / 100.0f * 0.6f;
    }

    @Override
    public float getGrainValue() {
        return grainValue / 100.0f * 0.04f;
    }

    @Override
    public float getFadeValue() {
        return fadeValue / 100.0f;
    }

    @Override
    public float getSoftenSkinValue() {
        return softenSkinValue / 100.0f;
    }

    @Override
    public float getTintHighlightsIntensityValue() {
        float tintHighlightsIntensity = 50.0f;
        return tintHighlightsColor == 0 ? 0 : tintHighlightsIntensity / 100.0f;
    }

    @Override
    public float getTintShadowsIntensityValue() {
        float tintShadowsIntensity = 50.0f;
        return tintShadowsColor == 0 ? 0 : tintShadowsIntensity / 100.0f;
    }

    @Override
    public float getSaturationValue() {
        float parameterValue = (saturationValue / 100.0f);
        if (parameterValue > 0) {
            parameterValue *= 1.05f;
        }
        return parameterValue + 1;
    }

    @Override
    public int getTintHighlightsColor() {
        return tintHighlightsColor;
    }

    @Override
    public int getTintShadowsColor() {
        return tintShadowsColor;
    }

    @Override
    public int getBlurType() {
        return blurType;
    }

    @Override
    public float getBlurExcludeSize() {
        return blurExcludeSize;
    }

    @Override
    public float getBlurExcludeBlurSize() {
        return blurExcludeBlurSize;
    }

    @Override
    public float getBlurAngle() {
        return blurAngle;
    }

    @Override
    public Point getBlurExcludePoint() {
        return blurExcludePoint;
    }

    @Override
    public boolean shouldShowOriginal() {
        return showOriginal || filtersEmpty;
    }

    @Override
    public boolean shouldDrawCurvesPass() {
        return !curvesToolValue.shouldBeSkipped();
    }

    @Override
    public ByteBuffer fillAndGetCurveBuffer() {
        curvesToolValue.fillBuffer();
        return curvesToolValue.curveBuffer;
    }

    public FrameLayout getToolsView() {
        return toolsView;
    }

    public PhotoFilterCurvesControl getCurveControl() {
        return curvesControl;
    }

    public PhotoFilterBlurControl getBlurControl() {
        return blurControl;
    }

    public TextView getDoneTextView() {
        return doneTextView;
    }

    public TextView getCancelTextView() {
        return cancelTextView;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setEnhanceValue(float value) {
        enhanceValue = value * 100f;
        updateFiltersEmpty();
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            View child = recyclerListView.getChildAt(i);
            if (child instanceof PhotoEditToolCell && recyclerListView.getChildAdapterPosition(child) == enhanceTool) {
                ((PhotoEditToolCell) child).setIconAndTextAndValue(LocaleController.getString(R.string.Enhance), enhanceValue, 0, 100);
                break;
            }
        }
        if (eglThread != null) {
            eglThread.requestRender(true);
        }
    }

    public class ToolsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ToolsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowsCount;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View view;
            if (i == 0) {
                PhotoEditToolCell cell = new PhotoEditToolCell(mContext, resourcesProvider);
                view = cell;
                cell.setSeekBarDelegate((i1, progress) -> {
                    if (i1 == enhanceTool) {
                        enhanceValue = progress;
                    } else if (i1 == highlightsTool) {
                        highlightsValue = progress;
                    } else if (i1 == contrastTool) {
                        contrastValue = progress;
                    } else if (i1 == exposureTool) {
                        exposureValue = progress;
                    } else if (i1 == warmthTool) {
                        warmthValue = progress;
                    } else if (i1 == saturationTool) {
                        saturationValue = progress;
                    } else if (i1 == vignetteTool) {
                        vignetteValue = progress;
                    } else if (i1 == shadowsTool) {
                        shadowsValue = progress;
                    } else if (i1 == grainTool) {
                        grainValue = progress;
                    } else if (i1 == sharpenTool) {
                        sharpenValue = progress;
                    } else if (i1 == fadeTool) {
                        fadeValue = progress;
                    } else if (i1 == softenSkinTool) {
                        softenSkinValue = progress;
                    }
                    if (eglThread != null) {
                        eglThread.requestRender(true);
                    }
                    updateFiltersEmpty();
                });
            } else {
                view = new PhotoEditRadioCell(mContext);
                view.setOnClickListener(v -> {
                    PhotoEditRadioCell cell = (PhotoEditRadioCell) v;
                    Integer row = (Integer) cell.getTag();
                    if (row == tintShadowsTool) {
                        tintShadowsColor = cell.getCurrentColor();
                    } else {
                        tintHighlightsColor = cell.getCurrentColor();
                    }
                    if (eglThread != null) {
                        eglThread.requestRender(false);
                    }
                    updateFiltersEmpty();
                });
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
            switch (holder.getItemViewType()) {
                case 0: {
                    PhotoEditToolCell cell = (PhotoEditToolCell) holder.itemView;
                    cell.setTag(i);
                    if (i == enhanceTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Enhance), enhanceValue, 0, 100);
                    } else if (i == highlightsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Highlights), highlightsValue, -100, 100);
                    } else if (i == contrastTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Contrast), contrastValue, -100, 100);
                    } else if (i == exposureTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Exposure), exposureValue, -100, 100);
                    } else if (i == warmthTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Warmth), warmthValue, -100, 100);
                    } else if (i == saturationTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Saturation), saturationValue, -100, 100);
                    } else if (i == vignetteTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Vignette), vignetteValue, 0, 100);
                    } else if (i == shadowsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Shadows), shadowsValue, -100, 100);
                    } else if (i == grainTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Grain), grainValue, 0, 100);
                    } else if (i == sharpenTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Sharpen), sharpenValue, 0, 100);
                    } else if (i == fadeTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.Fade), fadeValue, 0, 100);
                    } else if (i == softenSkinTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.SoftenSkin), softenSkinValue, 0, 100);
                    }
                    break;
                }
                case 1: {
                    PhotoEditRadioCell cell = (PhotoEditRadioCell) holder.itemView;
                    cell.setTag(i);
                    if (i == tintShadowsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.TintShadows), 0, tintShadowsColor);
                    } else if (i == tintHighlightsTool) {
                        cell.setIconAndTextAndValue(LocaleController.getString(R.string.TintHighlights), 0, tintHighlightsColor);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == tintShadowsTool || position == tintHighlightsTool) {
                return 1;
            }
            return 0;
        }
    }

    public static class EnhanceView extends View {

        private TextPaint topTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private TextPaint bottomTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        private StaticLayout topText;
        private float topTextWidth, topTextLeft;

        private StaticLayout bottomText;
        private float bottomTextWidth, bottomTextLeft;

        private boolean shown;
        private AnimatedFloat showT = new AnimatedFloat(this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean allowTouch;
        private PhotoFilterView filterView;
        private Runnable requestFilterView;

        public EnhanceView(Context context, Runnable requestFilterView) {
            super(context);
            this.requestFilterView = requestFilterView;
        }

        public void setFilterView(PhotoFilterView filterView) {
            this.filterView = filterView;
        }

        public void setAllowTouch(boolean allow) {
            this.allowTouch = allow;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec)
            );

            topTextPaint.setColor(0xffffffff);
            topTextPaint.setShadowLayer(AndroidUtilities.dp(8), 0, 0, 0x30000000);
            topTextPaint.setTextSize(AndroidUtilities.dp(34));
            bottomTextPaint.setColor(0xffffffff);
            bottomTextPaint.setShadowLayer(AndroidUtilities.dp(12), 0, 0, 0x30000000);
            bottomTextPaint.setTextSize(AndroidUtilities.dp(58));

            if (topText == null) {
                topText = new StaticLayout(LocaleController.getString(R.string.Enhance), topTextPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                topTextWidth = topText.getLineCount() > 0 ? topText.getLineWidth(0) : 0;
                topTextLeft = topText.getLineCount() > 0 ? topText.getLineLeft(0) : 0;
            }
        }

        private void updateBottomText() {
            float value = filterView == null ? 0 : filterView.getEnhanceValue();
            bottomText = new StaticLayout("" + Math.round(value * 100), bottomTextPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            bottomTextWidth = bottomText.getLineCount() > 0 ? bottomText.getLineWidth(0) : 0;
            bottomTextLeft = bottomText.getLineCount() > 0 ? bottomText.getLineLeft(0) : 0;
            invalidate();
        }

        private boolean tracking;
        private long downTime;
        private float lastTouchX;
        private float lastTouchY;
        private float lastVibrateValue;

        private Runnable hide = () -> {
            shown = false;
            invalidate();
        };

        public boolean onTouch(MotionEvent event) {
            if (allowTouch && event.getPointerCount() == 1) {
                final int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    tracking = false;
                    downTime = System.currentTimeMillis();
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    if (filterView != null) {
                        lastVibrateValue = filterView.getEnhanceValue();
                    }
                    return true;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    final float x = event.getX(), y = event.getY();
                    if (!tracking) {
                        if (System.currentTimeMillis() - downTime <= ViewConfiguration.getLongPressTimeout() &&
                            Math.abs(lastTouchY - y) < Math.abs(lastTouchX - x) &&
                            Math.abs(lastTouchX - x) > AndroidUtilities.touchSlop
                        ) {
                            tracking = true;

                            AndroidUtilities.cancelRunOnUIThread(hide);
                            shown = true;
                            invalidate();
                        }
                    }

                    if (tracking) {
                        float dx = x - lastTouchX;
                        if (filterView == null) {
                            requestFilterView.run();
                        }
                        if (filterView == null) {
                            tracking = false;
                            return false;
                        }
                        final float fullDistance = AndroidUtilities.displaySize.x * .8f;
                        final float value = filterView.getEnhanceValue();
                        final float newValue = Utilities.clamp(value + dx / fullDistance, 1, 0);
                        int newValueInt = Math.round(newValue * 100), lastValueInt = Math.round(value * 100), lastVibrateValueInt = Math.round(lastVibrateValue * 100);
                        if (newValueInt != lastValueInt && (newValueInt == 100 || newValueInt == 0)) {
                            try {
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                            } catch (Exception ignore) {}
                            lastVibrateValue = newValue;
                        } else if (Math.abs(newValueInt - lastVibrateValueInt) > (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_HIGH ? 5 : 10)) {
                            AndroidUtilities.vibrateCursor(this);
                            lastVibrateValue = newValue;
                        }
                        filterView.setEnhanceValue(newValue);
                        updateBottomText();
                    }

                    lastTouchX = x;
                    lastTouchY = y;
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    tracking = false;
                    downTime = -1;
                    if (filterView != null) {
                        lastVibrateValue = filterView.getEnhanceValue();
                    }
                    AndroidUtilities.runOnUIThread(hide, 600);
                    return false;
                }
            } else {
                if (shown) {
                    shown = false;
                    invalidate();
                }
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float alpha = showT.set(shown);

            if (alpha > 0 && topText != null && bottomText != null) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);

                canvas.save();
                canvas.translate((getWidth() - topTextWidth) / 2f - topTextLeft, getHeight() * .22f);
                topText.draw(canvas);
                canvas.restore();

                canvas.save();
                canvas.translate((getWidth() - bottomTextWidth) / 2f - bottomTextLeft, getHeight() * .22f + AndroidUtilities.dp(60));
                bottomText.draw(canvas);
                canvas.restore();

                canvas.restore();
            }
        }
    }

    public Bitmap getUiBlurBitmap() {
        if (eglThread == null) {
            return null;
        }
        return eglThread.getUiBlurBitmap();
    }

    private int gradientTop, gradientBottom;
    public void updateUiBlurGradient(int top, int bottom) {
        if (eglThread != null) {
            eglThread.updateUiBlurGradient(top, bottom);
        } else {
            gradientTop = top;
            gradientBottom = bottom;
        }
    }
}
