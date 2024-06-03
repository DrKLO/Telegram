package org.telegram.ui.Components.Paint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Views.PaintColorsListView;
import org.telegram.ui.Components.Paint.Views.PipettePickerView;
import org.telegram.ui.Components.ViewPagerFixed;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class ColorPickerBottomSheet extends BottomSheet {
    private final static int FROM_PICKER = 0,
            FROM_ALPHA = 1,
            FROM_INIT = 2,
            FROM_GRID = 3,
            FROM_SLIDER = 4,
            FROM_SLIDER_TEXT = 5;

    private final static int HORIZONTAL_SQUARES = 12;
    private final static int VERTICAL_SQUARES = 10;

    private ColorPickerView pickerView;
    private ImageView pipetteView;
    private ImageView doneView;
    private AlphaPickerView alphaPickerView;
    private android.graphics.Path path = new android.graphics.Path();
    private int mColor;
    private Consumer<Integer> colorListener;

    private PipetteDelegate pipetteDelegate;

    private boolean initialized;

    public ColorPickerBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);

        fixNavigationBar(0xff252525);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff252525, PorterDuff.Mode.MULTIPLY));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, AndroidUtilities.dp(16), 0, 0);

        pipetteView = new ImageView(context);
        pipetteView.setImageResource(R.drawable.picker);
        pipetteView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        pipetteView.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        pipetteView.setOnClickListener(v -> {
            if (pipetteDelegate.isPipetteVisible()) {
                return;
            }

            Bitmap drawingBitmap = AndroidUtilities.snapshotView(pipetteDelegate.getSnapshotDrawingView());

            Bitmap bitmap = Bitmap.createBitmap(drawingBitmap.getWidth(), drawingBitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            c.drawColor(Color.BLACK);

            pipetteDelegate.onDrawImageOverCanvas(bitmap, c);
            c.drawBitmap(drawingBitmap, 0, 0, null);
            drawingBitmap.recycle();
            PipettePickerView pipette = new PipettePickerView(context, bitmap) {
                @Override
                protected void onStartPipette() {
                    pipetteDelegate.onStartColorPipette();
                }

                @Override
                protected void onStopPipette() {
                    pipetteDelegate.onStopColorPipette();
                }
            };
            pipetteDelegate.getContainerView().addView(pipette, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            pipette.setColorListener(pipetteDelegate::onColorSelected);
            pipette.animateShow();
            dismiss();
        });
//        bottomLayout.addView(pipetteView, LayoutHelper.createLinear(28, 28, 0, 0, 16, 0));

        doneView = new ImageView(context);
        doneView.setImageResource(R.drawable.ic_ab_done);
        doneView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        doneView.setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
        doneView.setOnClickListener(v -> {
            dismiss();
        });

        alphaPickerView = new AlphaPickerView(context);
        alphaPickerView.setColor(Color.RED);

        pickerView = new ColorPickerView(context);
        linearLayout.addView(pickerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0));

        ScrollView scrollView = new ScrollView(context) {
            {
                setWillNotDraw(false);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);

                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) pickerView.getLayoutParams();
                params.height = (int) ((MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(24)) * ((float) VERTICAL_SQUARES / HORIZONTAL_SQUARES) + AndroidUtilities.dp(48 + 40));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                float y = linearLayout.getY() + AndroidUtilities.dp(1);
                int w = AndroidUtilities.dp(36);
                AndroidUtilities.rectTmp.set((getMeasuredWidth() - w) / 2f, y, (getMeasuredWidth() + w) / 2f, y + AndroidUtilities.dp(4));
                int color = 0xff5B5B5B;
                Theme.dialogs_onlineCirclePaint.setColor(color);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
            }
        };
        scrollView.addView(linearLayout);
        setCustomView(scrollView);
    }

    @Override
    public void show() {
        if (!pipetteDelegate.isPipetteAvailable()) {
            pipetteView.setVisibility(View.GONE);
        }
        super.show();
    }

    public ColorPickerBottomSheet setPipetteDelegate(PipetteDelegate pipetteDelegate) {
        this.pipetteDelegate = pipetteDelegate;
        return this;
    }

    public ColorPickerBottomSheet setColorListener(Consumer<Integer> colorListener) {
        this.colorListener = colorListener;
        return this;
    }

    public ColorPickerBottomSheet setColor(int color) {
        onSetColor(color, FROM_INIT);
        return this;
    }

    private void onSetColor(int color, int from) {
        if (!initialized) {
            if (from != FROM_INIT) {
                return;
            } else {
                initialized = true;
            }
        }

        if (from != FROM_SLIDER_TEXT) {
            View focus = pickerView.findFocus();
            if (focus != null) {
                focus.clearFocus();
                AndroidUtilities.hideKeyboard(focus);
            }
        }

        if (from != FROM_GRID) {
            pickerView.gridPickerView.setCurrentColor(color);
        }
        if (from != FROM_PICKER) {
            pickerView.gradientPickerView.setColor(color, from != FROM_ALPHA);
        }
        if (from != FROM_ALPHA) {
            alphaPickerView.setColor(color);
        }
        pickerView.slidersPickerView.invalidateColor();
    }

    @Override
    public void dismiss() {
        super.dismiss();

        if (colorListener != null) {
            colorListener.accept(mColor);
        }
    }

    private final class ColorPickerView extends LinearLayout {
        private GridPickerView gridPickerView;
        private GradientPickerView gradientPickerView;
        private SlidersPickerView slidersPickerView;
        private ViewPagerFixed.TabsView tabsView;

        public ColorPickerView(Context context) {
            super(context);
            setOrientation(VERTICAL);

            gridPickerView = new GridPickerView(context);
            gridPickerView.setCurrentColor(mColor);
            gradientPickerView = new GradientPickerView(context);
            slidersPickerView = new SlidersPickerView(context);

            ViewPagerFixed pager = new ViewPagerFixed(context, resourcesProvider) {
                @Override
                protected int tabMarginDp() {
                    return 0;
                }
            };
            pager.setAdapter(new ViewPagerFixed.Adapter() {
                @Override
                public int getItemCount() {
                    return 3;
                }

                @Override
                public String getItemTitle(int position) {
                    switch (position) {
                        default:
                        case 0:
                            return LocaleController.getString(R.string.PaintPaletteGrid).toUpperCase();
                        case 1:
                            return LocaleController.getString(R.string.PaintPaletteSpectrum).toUpperCase();
                        case 2:
                            return LocaleController.getString(R.string.PaintPaletteSliders).toUpperCase();
                    }
                }

                @Override
                public View createView(int viewType) {
                    switch (viewType) {
                        default:
                        case 0:
                            return gridPickerView;
                        case 1:
                            return gradientPickerView;
                        case 2:
                            return slidersPickerView;
                    }
                }

                @Override
                public int getItemViewType(int position) {
                    return position;
                }

                @Override
                public void bindView(View view, int position, int viewType) {}
            });

            addView(pager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
            addView(alphaPickerView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 12, 0, 12, 0));

            LinearLayout bottomLayout = new LinearLayout(context);
            bottomLayout.setOrientation(LinearLayout.HORIZONTAL);
            bottomLayout.setGravity(Gravity.CENTER_VERTICAL);

            bottomLayout.addView(pipetteView, LayoutHelper.createLinear(28, 28));
            bottomLayout.addView(tabsView = pager.createTabsView(false, 8), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 1f, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
            bottomLayout.addView(doneView, LayoutHelper.createLinear(28, 28));

            addView(bottomLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 14, 0, 14, 0));
        }
    }

    private final class GridPickerView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final int[] colors = {
                0xFF00A1D8,
                0xFF0060FD,
                0xFF4C1FB7,
                0xFF982BBC,
                0xFFB82C5D,
                0xFFFD3E12,
                0xFFFF6900,
                0xFFFDAB00,
                0xFFFCC700,
                0xFFFCFA43,
                0xFFD9EB37,
                0xFF76ba3f
        };

        private Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LongSparseArray<Float> selectors = new LongSparseArray<>();
        private long selected = Long.MIN_VALUE;
        private Path selectorPath = new Path();
        private float[] radii = new float[8];

        private Map<Long, Integer> colorMap = new HashMap<>();

        public GridPickerView(Context context) {
            super(context);

            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(3), AndroidUtilities.dp(14), AndroidUtilities.dp(3));

            selectorPaint.setColor(0xffffffff);
            selectorPaint.setStyle(Paint.Style.STROKE);
            selectorPaint.setStrokeCap(Paint.Cap.ROUND);
            selectorPaint.setStrokeJoin(Paint.Join.ROUND);

            for (int x = 0; x < HORIZONTAL_SQUARES; x++) {
                for (int y = 0; y < VERTICAL_SQUARES; y++) {
                    if (y == 0) {
                        colorMap.put((long) (x << 16) + y, ColorUtils.blendARGB(Color.WHITE, Color.BLACK, (float) x / (HORIZONTAL_SQUARES - 1)));
                    } else {
                        int color;
                        int centerY = VERTICAL_SQUARES / 2 + 1;
                        if (y < centerY) {
                            color = ColorUtils.blendARGB(colors[x], Color.BLACK, (centerY - y - 1) / (VERTICAL_SQUARES / 2f - 1) * 0.5f);
                        } else { // y >= centerY
                            color = ColorUtils.blendARGB(colors[x], Color.WHITE, 0.5f - (VERTICAL_SQUARES - y - 1) / (VERTICAL_SQUARES / 2f) * 0.5f);
                        }
                        colorMap.put((long) (x << 16) + y, color);
                    }
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    updatePosition(event);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    updatePosition(event);
                    break;
                case MotionEvent.ACTION_UP:
                    updatePosition(event);
                case MotionEvent.ACTION_CANCEL:
                     getParent().requestDisallowInterceptTouchEvent(false);
                     break;
            }
            return true;
        }

        public void setCurrentColor(int color) {
            for (Map.Entry<Long, Integer> e : colorMap.entrySet()) {
                if (e.getValue() == color) {
                    long p = e.getKey();
                    int x = (int) (p >> 16);
                    int y = (int) (p - (x << 16));
                    setCurrentColor(x, y);
                    return;
                }
            }

            selected = Long.MIN_VALUE;
            invalidate();
        }

        public void setCurrentColor(int x, int y) {
            selected = ((long) x << 16) + y;
            if (selectors.get(selected) == null) {
                selectors.put(selected, 0f);
            }
            invalidate();
        }

        private void updatePosition(MotionEvent e) {
            int xSize = (getWidth() - getPaddingLeft() - getPaddingRight()) / HORIZONTAL_SQUARES;
            int ySize = (getHeight() - getPaddingTop() - getPaddingBottom()) / VERTICAL_SQUARES;
            int x = (int) ((e.getX() - getPaddingLeft()) / xSize);
            int y = (int) (e.getY() / ySize);
            Integer clr = colorMap.get(((long) x << 16) + y);
            if (clr != null) {
                onSetColor(clr, FROM_GRID);
                setCurrentColor(x, y);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            canvas.save();
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(10), AndroidUtilities.dp(10), Path.Direction.CW);
            canvas.clipPath(path);

            float xSize = (float) (getWidth() - getPaddingLeft() - getPaddingRight()) / HORIZONTAL_SQUARES;
            float ySize = (float) (getHeight() - getPaddingTop() - getPaddingBottom()) / VERTICAL_SQUARES;

            for (int x = 0; x < HORIZONTAL_SQUARES; x++) {
                for (int y = 0; y < VERTICAL_SQUARES; y++) {
                    Integer clr = colorMap.get((long) (x << 16) + y);
                    if (clr == null) {
                        continue;
                    }
                    paint.setColor(clr);
                    AndroidUtilities.rectTmp.set(getPaddingLeft() + x * xSize, getPaddingTop() + y * ySize, getPaddingLeft() + (x + 1) * xSize, getPaddingTop() + (y + 1) * ySize);
                    canvas.drawRect(AndroidUtilities.rectTmp, paint);
                }
            }

            canvas.restore();

            for (int i = 0; i < selectors.size(); ++i) {
                long p = selectors.keyAt(i);
                float t = selectors.valueAt(i);

                if (selected == p) {
                    t = Math.min(1, t + 16f / 350f);
                } else {
                    t = Math.max(0, t - 16f / 150f);
                }

                int x = (int) (p >> 16);
                int y = (int) (p - (x << 16));

                Integer color = colorMap.get(p);
                if (color != null) {
                    selectorPaint.setColor(AndroidUtilities.computePerceivedBrightness(color) > 0.721f ? 0xff111111 : 0xffffffff);
                }
                selectorPaint.setStrokeWidth(CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t) * AndroidUtilities.dp(3));

                selectorPath.rewind();
                AndroidUtilities.rectTmp.set(getPaddingLeft() + x * xSize, getPaddingTop() + y * ySize, getPaddingLeft() + (x + 1) * xSize, getPaddingTop() + (y + 1) * ySize);
                radii[0] = radii[1] = x == 0 && y == 0 ? AndroidUtilities.dp(10) : 0; // top left
                radii[2] = radii[3] = x == HORIZONTAL_SQUARES - 1 && y == 0 ? AndroidUtilities.dp(10) : 0; // top right
                radii[4] = radii[5] = x == HORIZONTAL_SQUARES - 1 && y == VERTICAL_SQUARES - 1 ? AndroidUtilities.dp(10) : 0; // bottom right
                radii[6] = radii[7] = x == 0 && y == VERTICAL_SQUARES - 1 ? AndroidUtilities.dp(10) : 0; // bottom left
                selectorPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                canvas.drawPath(selectorPath, selectorPaint);

                if (t <= 0 && selected != p) {
                    selectors.removeAt(i);
                    i--;
                    invalidate();
                    continue;
                } else if (t < 1) {
                    invalidate();
                }

                selectors.setValueAt(i, t);
            }
        }
    }

    private final class GradientPickerView extends View {
        private Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint whiteBlackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float positionX, positionY;
        private Drawable shadowDrawable;

        private float[] hsv = new float[3];

        public GradientPickerView(Context context) {
            super(context);

            setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(3), AndroidUtilities.dp(14), AndroidUtilities.dp(3));

            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(3));
            shadowDrawable = ContextCompat.getDrawable(context, R.drawable.knob_shadow);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);

            gradientPaint.setShader(new LinearGradient(0, getPaddingTop(), 0, h - getPaddingBottom(), new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED}, null, android.graphics.Shader.TileMode.CLAMP));
            whiteBlackPaint.setShader(new LinearGradient(getPaddingLeft(), 0, w - getPaddingRight(), 0, new int[]{Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK}, new float[]{0.06f, 0.22f, 0.78f, 0.94f}, Shader.TileMode.MIRROR));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            AndroidUtilities.rectTmp.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), gradientPaint);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(8), AndroidUtilities.dp(8), whiteBlackPaint);

            float outlineRad = AndroidUtilities.dp(13);
            float rad2 = outlineRad - outlinePaint.getStrokeWidth() / 2;
            float min = AndroidUtilities.dp(16);
            int w = getWidth() - getPaddingLeft() - getPaddingRight();
            int h = getHeight() - getPaddingTop() - getPaddingBottom();
            float cx = getPaddingLeft() + MathUtils.clamp(positionX * w, min, w - min);
            float cy = getPaddingTop() + MathUtils.clamp(positionY * h, min, h - min);
            shadowDrawable.getPadding(AndroidUtilities.rectTmp2);
            shadowDrawable.setBounds(
                (int) (cx - outlineRad - AndroidUtilities.rectTmp2.left),
                (int) (cy - outlineRad - AndroidUtilities.rectTmp2.top),
                (int) (cx + outlineRad + AndroidUtilities.rectTmp2.bottom),
                (int) (cy + outlineRad + AndroidUtilities.rectTmp2.bottom)
            );
            shadowDrawable.draw(canvas);
            canvas.drawCircle(cx, cy, outlineRad, outlinePaint);
            PaintColorsListView.drawColorCircle(canvas, cx, cy, rad2, ColorUtils.setAlphaComponent(mColor, 0xFF));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    updatePosition(event);
                    break;
                case MotionEvent.ACTION_MOVE:
                    updatePosition(event);
                    break;
                case MotionEvent.ACTION_UP:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    updatePosition(event);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }

        private void updatePosition(MotionEvent e) {
            positionX = (e.getX() - getPaddingLeft()) / (getWidth() - getPaddingLeft() - getPaddingRight());
            positionY = (e.getY() - getPaddingTop()) / (getHeight() - getPaddingTop() - getPaddingBottom());

            hsv[0] = positionY * 360f;
            if (positionX <= 0.22f || positionX >= 0.78f) {
                hsv[1] = positionX <= 0.22f ? AndroidUtilities.lerp(1f, 0f, 1f - positionX / 0.22f) : AndroidUtilities.lerp(1f, 0f, (positionX - 0.78f) / (1f - 0.78f));
                hsv[2] = positionX <= 0.22f ? 1f : AndroidUtilities.lerp(1f, 0f, (positionX - 0.78f) / (1f - 0.78f));
            } else {
                hsv[1] = 1f;
                hsv[2] = 1f;
            }
            mColor = Color.HSVToColor(hsv);
            onSetColor(mColor, FROM_PICKER);
            invalidate();
        }

        public void setColor(int color, boolean updatePosition) {
            mColor = color;
            Color.colorToHSV(color, hsv);

            if (updatePosition) {
                positionX = 1f + hsv[1] * 0.5f - (hsv[2] <= 0.5f ? 1f - (0.78f + (1f - hsv[2]) * (1f - 0.78f)) : 1f - (1f - hsv[2]) * 0.22f);
                positionY = hsv[0] / 360f;
            }
            invalidate();
        }
    }

    private final class SlidersPickerView extends LinearLayout {
        private SliderCell red;
        private SliderCell green;
        private SliderCell blue;

        private EditText hexEdit;

        private boolean isInvalidatingColor;

        public SlidersPickerView(Context context) {
            super(context);

            setOrientation(VERTICAL);
            setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);

            red = new SliderCell(context);
            red.bind(ColorSliderView.MODE_RED);
            addView(red, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0, 16));

            green = new SliderCell(context);
            green.bind(ColorSliderView.MODE_GREEN);
            addView(green, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0, 16));

            blue = new SliderCell(context);
            blue.bind(ColorSliderView.MODE_BLUE);
            addView(blue, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0, 16));

            LinearLayout hexLayout = new LinearLayout(context);
            hexLayout.setOrientation(HORIZONTAL);
            hexLayout.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            addView(hexLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 64));

            TextView hexTitle = new TextView(context);
            hexTitle.setTextColor(0x99ffffff);
            hexTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            hexTitle.setText(LocaleController.getString(R.string.PaintPaletteSlidersHexColor).toUpperCase());
            hexTitle.setTypeface(AndroidUtilities.bold());
            hexLayout.addView(hexTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

            hexEdit = new EditTextBoldCursor(context);
            hexEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            hexEdit.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0x19ffffff));
            hexEdit.setPadding(0, 0, 0, 0);
            hexEdit.setTextColor(Color.WHITE);
            hexEdit.setGravity(Gravity.CENTER);
            hexEdit.setSingleLine();
            hexEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
            hexEdit.setImeActionLabel(LocaleController.getString(R.string.Done), EditorInfo.IME_ACTION_DONE);
            hexEdit.setTypeface(AndroidUtilities.bold());
            hexEdit.addTextChangedListener(new TextWatcher() {
                private Pattern pattern = Pattern.compile("^[0-9a-fA-F]*$");
                private CharSequence previous;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    previous = s.toString();
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isInvalidatingColor) {
                        return;
                    }
                    if (previous != null && s != null && !TextUtils.isEmpty(s) && !Objects.equals(previous.toString(), s.toString())) {
                        String str = s.toString();
                        if (str.length() > 8) {
                            hexEdit.setText(str.substring(2, 8).toUpperCase());
                            hexEdit.setSelection(8);
                            return;
                        }

                        if (!pattern.matcher(s).find()) {
                            return;
                        }
                        int color;
                        switch (str.length()) {
                            case 3:
                                color = (int) Long.parseLong("FF" + str.charAt(0) + str.charAt(0) + str.charAt(1) + str.charAt(1) + str.charAt(2) + str.charAt(2), 16);
                                break;
                            case 6:
                                color = 0xFF000000 + (int) Long.parseLong(str, 16);
                                break;
                            case 8:
                                color = (int) Long.parseLong(str, 16);
                                break;
                            default:
                                color = mColor;
                                break;
                        }

                        if (color == mColor) {
                            return;
                        }

                        onSetColor(color, FROM_SLIDER_TEXT);
                    }
                }
            });
            hexEdit.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && TextUtils.isEmpty(hexEdit.getText())) {
                    hexEdit.setText("0");
                }
            });
            hexEdit.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus();
                    AndroidUtilities.hideKeyboard(v);
                }
                return false;
            });
            hexLayout.addView(hexEdit, LayoutHelper.createLinear(72, 36));
        }

        public void invalidateColor() {
            isInvalidatingColor = true;

            red.invalidateColor();
            green.invalidateColor();
            blue.invalidateColor();

            if (!hexEdit.isFocused()) {
                int ss = hexEdit.getSelectionStart(), se = hexEdit.getSelectionEnd();
                StringBuilder str = new StringBuilder(Integer.toHexString(mColor));
                while (str.length() < 8) {
                    str.insert(0, "0");
                }
                hexEdit.setText(str.toString().toUpperCase().substring(2));
                hexEdit.setSelection(ss, se);
            }
            isInvalidatingColor = false;
        }
    }

    private final class SliderCell extends FrameLayout {
        private TextView titleView;
        private ColorSliderView sliderView;
        private EditText valueView;

        private int mode;

        private boolean isInvalidatingColor;

        public SliderCell(@NonNull Context context) {
            super(context);

            titleView = new TextView(context);
            titleView.setTextColor(0x99ffffff);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            titleView.setTypeface(AndroidUtilities.bold());
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 8, 0, 8, 0));

            sliderView = new ColorSliderView(context);
            addView(sliderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT, 0, 16, 78, 0));

            valueView = new EditTextBoldCursor(context);
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            valueView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), 0x19ffffff));
            valueView.setPadding(0, 0, 0, 0);
            valueView.setTextColor(Color.WHITE);
            valueView.setGravity(Gravity.CENTER);
            valueView.setSingleLine();
            valueView.setImeOptions(EditorInfo.IME_ACTION_DONE);
            valueView.setImeActionLabel(LocaleController.getString(R.string.Done), EditorInfo.IME_ACTION_DONE);
            valueView.setInputType(EditorInfo.TYPE_CLASS_NUMBER);
            valueView.setTypeface(AndroidUtilities.bold());
            valueView.addTextChangedListener(new TextWatcher() {
                private CharSequence previous;

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    previous = s.toString();
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (isInvalidatingColor) {
                        return;
                    }

                    if (previous != null && s != null && !TextUtils.isEmpty(s) && !Objects.equals(previous.toString(), s.toString())) {
                        int val = Integer.parseInt(s.toString());
                        val = MathUtils.clamp(val, 0, 255);

                        int color;
                        switch (mode) {
                            default:
                            case ColorSliderView.MODE_RED:
                                color = Color.argb(Color.alpha(mColor), val, Color.green(mColor), Color.blue(mColor));
                                break;
                            case ColorSliderView.MODE_GREEN:
                                color = Color.argb(Color.alpha(mColor), Color.red(mColor), val, Color.blue(mColor));
                                break;
                            case ColorSliderView.MODE_BLUE:
                                color = Color.argb(Color.alpha(mColor), Color.red(mColor), Color.green(mColor), val);
                                break;
                        }
                        onSetColor(color, FROM_SLIDER_TEXT);
                    }
                }
            });
            valueView.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && TextUtils.isEmpty(valueView.getText())) {
                    valueView.setText("0");
                }
            });
            valueView.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    v.clearFocus();
                    AndroidUtilities.hideKeyboard(v);
                }
                return false;
            });
            addView(valueView, LayoutHelper.createFrame(72, 36, Gravity.BOTTOM | Gravity.RIGHT));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52), MeasureSpec.EXACTLY));
        }

        public void bind(int mode) {
            this.mode = mode;
            sliderView.setMode(mode);
            switch (mode) {
                case ColorSliderView.MODE_RED:
                    titleView.setText(LocaleController.getString(R.string.PaintPaletteSlidersRed).toUpperCase());
                    break;
                case ColorSliderView.MODE_GREEN:
                    titleView.setText(LocaleController.getString(R.string.PaintPaletteSlidersGreen).toUpperCase());
                    break;
                case ColorSliderView.MODE_BLUE:
                    titleView.setText(LocaleController.getString(R.string.PaintPaletteSlidersBlue).toUpperCase());
                    break;
            }
            invalidateColor();
        }

        public void invalidateColor() {
            isInvalidatingColor = true;

            sliderView.invalidateColor();
            int ss = valueView.getSelectionStart(), se = valueView.getSelectionEnd();
            switch (mode) {
                case ColorSliderView.MODE_RED:
                    valueView.setText(String.valueOf(Color.red(mColor)));
                    break;
                case ColorSliderView.MODE_GREEN:
                    valueView.setText(String.valueOf(Color.green(mColor)));
                    break;
                case ColorSliderView.MODE_BLUE:
                    valueView.setText(String.valueOf(Color.blue(mColor)));
                    break;
            }
            valueView.setSelection(ss, se);

            isInvalidatingColor = false;
        }
    }

    private final class AlphaPickerView extends View {
        private Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float alpha;

        public AlphaPickerView(Context context) {
            super(context);
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(3));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            invalidateShader();
        }

        public void setColor(int mColor) {
            alpha = Color.alpha(mColor) / (float) 0xFF;
            invalidateShader();
            invalidate();
        }

        private void invalidateShader() {
            colorPaint.setShader(new LinearGradient(0, 0, getWidth(), 0, new int[]{Color.TRANSPARENT, mColor}, null, Shader.TileMode.CLAMP));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                case MotionEvent.ACTION_MOVE:
                    updatePosition(event.getX());
                    break;
                case MotionEvent.ACTION_UP:
                    updatePosition(event.getX());
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }

        private void updatePosition(float x) {
            float rad = AndroidUtilities.dp(6);
            float outlineRad = AndroidUtilities.dp(13);
            float rad2 = outlineRad - outlinePaint.getStrokeWidth() / 2;

            alpha = MathUtils.clamp((x - rad + rad2) / (getWidth() - rad * 2), 0, 1);
            onSetColor(ColorUtils.setAlphaComponent(mColor, (int) (alpha * 0xFF)), FROM_ALPHA);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float y = getHeight() / 2f;
            float rad = AndroidUtilities.dp(6);
            AndroidUtilities.rectTmp.set(rad, y - rad, getWidth() - rad, y + rad);
            canvas.save();
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);
            canvas.clipPath(path);
            PaintColorsListView.drawCheckerboard(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(6));
            canvas.restore();
            AndroidUtilities.rectTmp.set(rad, y - rad, getWidth() - rad, y + rad);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), colorPaint);

            float outlineRad = AndroidUtilities.dp(13);
            float rad2 = outlineRad - outlinePaint.getStrokeWidth() / 2;
            float cx = Math.max(rad + rad2, rad + (getWidth() - rad * 2) * alpha - rad2);
            canvas.drawCircle(cx, y, outlineRad, outlinePaint);
            PaintColorsListView.drawColorCircle(canvas, cx, y, rad2, ColorUtils.setAlphaComponent(mColor, (int) (alpha * 0xFF)));
        }
    }

    private final class ColorSliderView extends View {
        private Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final static int MODE_RED = 0, MODE_GREEN = 1, MODE_BLUE = 2;
        private int mode;

        private int filledColor;

        public ColorSliderView(Context context) {
            super(context);
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            outlinePaint.setStrokeWidth(AndroidUtilities.dp(3));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            invalidateShader();
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public void invalidateColor() {
            filledColor = ColorUtils.setAlphaComponent(mColor, 0xFF);
            invalidateShader();
            invalidate();
        }

        private void invalidateShader() {
            int from, to;
            switch (mode) {
                default:
                case MODE_RED:
                    from = Color.argb(0xFF, 0x00, Color.green(mColor), Color.blue(mColor));
                    to = Color.argb(0xFF, 0xFF, Color.green(mColor), Color.blue(mColor));
                    break;
                case MODE_GREEN:
                    from = Color.argb(0xFF, Color.red(mColor), 0x00, Color.blue(mColor));
                    to = Color.argb(0xFF, Color.red(mColor), 0xFF, Color.blue(mColor));
                    break;
                case MODE_BLUE:
                    from = Color.argb(0xFF, Color.red(mColor), Color.green(mColor), 0x00);
                    to = Color.argb(0xFF, Color.red(mColor), Color.green(mColor), 0xFF);
                    break;
            }
            colorPaint.setShader(new LinearGradient(0, 0, getWidth(), 0, new int[]{from, to}, null, Shader.TileMode.CLAMP));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                case MotionEvent.ACTION_MOVE:
                    updatePosition(event.getX());
                    break;
                case MotionEvent.ACTION_UP:
                    updatePosition(event.getX());
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }

        private void updatePosition(float x) {
            float rad = AndroidUtilities.dp(6);
            float outlineRad = AndroidUtilities.dp(13);
            float rad2 = outlineRad - outlinePaint.getStrokeWidth() / 2;

            float val = MathUtils.clamp((x - rad + rad2) / (getWidth() - rad * 2), 0, 1);

            int color;
            switch (mode) {
                default:
                case MODE_RED:
                    color = Color.argb(0xFF, (int) (val * 0xFF), Color.green(mColor), Color.blue(mColor));
                    break;
                case MODE_GREEN:
                    color = Color.argb(0xFF, Color.red(mColor), (int) (val * 0xFF), Color.blue(mColor));
                    break;
                case MODE_BLUE:
                    color = Color.argb(0xFF, Color.red(mColor), Color.green(mColor), (int) (val * 0xFF));
                    break;
            }

            onSetColor(ColorUtils.setAlphaComponent(color, Color.alpha(mColor)), FROM_SLIDER);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float y = getHeight() / 2f;
            float rad = AndroidUtilities.dp(6);
            AndroidUtilities.rectTmp.set(rad, y - rad, getWidth() - rad, y + rad);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(16), AndroidUtilities.dp(16), colorPaint);

            float val;
            switch (mode) {
                default:
                case MODE_RED:
                    val = Color.red(mColor) / (float) 0xFF;
                    break;
                case MODE_GREEN:
                    val = Color.green(mColor) / (float) 0xFF;
                    break;
                case MODE_BLUE:
                    val = Color.blue(mColor) / (float) 0xFF;
                    break;
            }

            float outlineRad = AndroidUtilities.dp(13);
            float rad2 = outlineRad - outlinePaint.getStrokeWidth() / 2;
            float cx = Math.max(rad + rad2, rad + (getWidth() - rad * 2) * val - rad2);
            canvas.drawCircle(cx, y, outlineRad, outlinePaint);
            PaintColorsListView.drawColorCircle(canvas, cx, y, rad2, filledColor);
        }
    }

    public interface PipetteDelegate {
        void onStartColorPipette();
        void onStopColorPipette();
        ViewGroup getContainerView();
        View getSnapshotDrawingView();
        void onDrawImageOverCanvas(Bitmap bitmap, Canvas canvas);
        boolean isPipetteVisible();
        boolean isPipetteAvailable();
        void onColorSelected(int color);
    }
}
