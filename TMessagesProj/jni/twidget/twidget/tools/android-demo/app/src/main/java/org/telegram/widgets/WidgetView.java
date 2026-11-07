package org.telegram.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.widgetsandbox.litehtml.LitehtmlBackend;
import org.telegram.widgetsandbox.litehtml.LitehtmlNative;

import java.util.ArrayList;

/**
 * Shows one widget: the document's last frame, kept in a bitmap that each paint only touches where it changed, the
 * compositor's layers above it at their tracks' matrices (sampled every frame while one moves, the core asleep),
 * and an EditText over the text control being edited. Touches go to the renderer in CSS px.
 */
@SuppressLint("ViewConstructor")
public class WidgetView extends FrameLayout {

    private static final long GESTURE_DECISION_TIMEOUT_MS = 150;

    /** A compositor layer as this view plays it (src/compositor.h). */
    static final class Layer {
        long id;
        long contentVersion;
        long parent;
        final RectF bounds = new RectF();  // CSS px, document coordinates
        Bitmap bitmap;                     // the content, rastered at density × raster scale
        boolean hasClip;
        final RectF clip = new RectF();
        final float[] clipRadii = new float[8];
        double start, duration;
        boolean repeat;
        float[] keys = new float[0];       // t, a, b, c, d, e, f, opacity per key

        void recycle() {
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }
        }
    }

    private final WidgetRenderer renderer;
    private final LitehtmlBackend backend;
    private final float density;

    private Bitmap document;
    private int contentHeightPx;
    private ArrayList<Layer> layers = new ArrayList<>();
    private final Paint layerPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Matrix own = new Matrix();
    private final float[] values = new float[9];
    private final float[] sample = new float[7];
    private final LongSparseArray<Placed> placed = new LongSparseArray<>();

    private EditText editor;
    private long editorGeneration;
    private boolean updatingEditor;

    private boolean decided;
    private final Runnable gestureTimeout = () -> {
        if (!decided) {
            releaseGesture();
        }
    };

    /** Where a layer shows: its matrix after its parents', their opacity and clips, in CSS px. */
    private static final class Placed {
        final Matrix world = new Matrix();
        float opacity = 1f;
        Path clip;
    }

    WidgetView(Context context, WidgetRenderer renderer, LitehtmlBackend backend, float density) {
        super(context);
        this.renderer = renderer;
        this.backend = backend;
        this.density = density;
        setWillNotDraw(false);
    }

    // ── document ──

    /** Paints `list` (CSS px) into the kept frame: only `damage` when the frame is still the right size. */
    void paintDocument(JSONArray list, JSONArray damage, int widthCss, int heightCss) {
        int w = Math.max(1, (int) Math.ceil(widthCss * density)), h = Math.max(1, (int) Math.ceil(heightCss * density));
        if (document == null || document.getWidth() != w || document.getHeight() != h) {
            if (document != null) {
                document.recycle();
            }
            document = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            damage = null;
        }
        Canvas canvas = new Canvas(document);
        if (damage == null) {
            document.eraseColor(0);
        }
        canvas.scale(density, density);
        backend.paint(canvas, list, density, damage);
        if (h != contentHeightPx) {
            contentHeightPx = h;
            requestLayout();
        }
        invalidate();
    }

    // ── layers ──

    Layer makeLayer(JSONObject source, Layer old, JSONArray content) {
        Layer layer = old != null ? old : new Layer();
        layer.id = source.optLong("id");
        layer.contentVersion = source.optLong("cv");
        layer.parent = source.optLong("p");
        JSONArray b = source.optJSONArray("b");
        if (b != null) {
            float x = (float) b.optDouble(0), y = (float) b.optDouble(1);
            layer.bounds.set(x, y, x + (float) b.optDouble(2), y + (float) b.optDouble(3));
        }
        JSONArray l = source.optJSONArray("l");
        layer.hasClip = l != null;
        if (l != null) {
            float x = (float) l.optDouble(0), y = (float) l.optDouble(1);
            layer.clip.set(x, y, x + (float) l.optDouble(2), y + (float) l.optDouble(3));
            JSONArray q = source.optJSONArray("q");
            for (int i = 0; i < 8; i++) {
                layer.clipRadii[i] = q == null ? 0f : (float) q.optDouble(i);
            }
        }
        layer.start = source.optDouble("ts");
        layer.duration = source.optDouble("td");
        layer.repeat = source.optInt("tr") != 0;
        JSONArray k = source.optJSONArray("k");
        layer.keys = new float[k == null ? 0 : k.length()];
        for (int i = 0; i < layer.keys.length; i++) {
            layer.keys[i] = (float) k.optDouble(i);
        }
        if (old == null && content != null) {
            float scale = density * (float) source.optDouble("rs", 1.0);
            int w = (int) Math.ceil(layer.bounds.width() * scale), h = (int) Math.ceil(layer.bounds.height() * scale);
            if (w > 0 && h > 0) {
                layer.bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(layer.bitmap);
                canvas.scale(scale, scale);
                canvas.translate(-layer.bounds.left, -layer.bounds.top);
                backend.paint(canvas, content, scale, null);
            }
        }
        return layer;
    }

    void setLayers(ArrayList<Layer> layers) {
        this.layers = layers;
        invalidate();
    }

    /** The track's matrix and opacity `elapsed` seconds in, into `out` (a, b, c, d, e, f, opacity). */
    private static void sampleTrack(Layer layer, double elapsed, float[] out) {
        float[] k = layer.keys;
        int count = k.length / 8;
        if (count == 0) {
            out[0] = 1; out[1] = 0; out[2] = 0; out[3] = 1; out[4] = 0; out[5] = 0; out[6] = 1;
            return;
        }
        int from = 0, to = 0;
        float u = 0;
        if (count > 1 && elapsed > 0 && layer.duration > 0) {
            double p = layer.repeat ? (elapsed % layer.duration) / layer.duration : elapsed / layer.duration;
            if (p >= 1) {
                from = to = count - 1;
            } else {
                while (from + 2 < count && k[(from + 1) * 8] < p) {
                    from++;
                }
                to = from + 1;
                float ta = k[from * 8], tb = k[to * 8];
                u = tb > ta ? (float) Math.max(0, Math.min(1, (p - ta) / (tb - ta))) : 0f;
            }
        }
        for (int i = 0; i < 7; i++) {
            float a = k[from * 8 + 1 + i], b = k[to * 8 + 1 + i];
            out[i] = a + (b - a) * u;
        }
    }

    // ── drawing ──

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), contentHeightPx);
        measureChildren(widthMeasureSpec, MeasureSpec.makeMeasureSpec(contentHeightPx, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw && oldw != 0) {
            renderer.setWidth(w / density);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (document != null) {
            canvas.drawBitmap(document, 0, 0, null);
        }
        if (layers.isEmpty()) {
            return;
        }
        // Each layer where its track has it now, inside its parent's matrix, opacity and clip. A parent comes
        // before its children.
        double now = LitehtmlNative.clock();
        boolean running = false;
        placed.clear();
        canvas.save();
        canvas.scale(density, density);
        for (Layer layer : layers) {
            Placed up = layer.parent != 0 ? placed.get(layer.parent) : null;
            if (layer.parent != 0 && up == null) {
                continue;
            }
            sampleTrack(layer, now - layer.start, sample);
            running |= layer.keys.length > 8 && layer.duration > 0 && (layer.repeat || now - layer.start < layer.duration);
            Placed self = new Placed();
            values[0] = sample[0];
            values[1] = sample[2];
            values[2] = sample[4];
            values[3] = sample[1];
            values[4] = sample[3];
            values[5] = sample[5];
            values[6] = 0;
            values[7] = 0;
            values[8] = 1;
            own.setValues(values);
            self.world.set(own);
            if (up != null) {
                self.world.postConcat(up.world);
            }
            self.opacity = (up != null ? up.opacity : 1f) * sample[6];
            self.clip = up != null ? up.clip : null;
            if (layer.hasClip) {
                Path clip = new Path();
                clip.addRoundRect(layer.clip, layer.clipRadii, Path.Direction.CW);
                if (up != null) {
                    clip.transform(up.world);
                }
                if (self.clip != null) {
                    clip.op(self.clip, Path.Op.INTERSECT);
                }
                self.clip = clip;
            }
            placed.put(layer.id, self);
            if (layer.bitmap == null || self.opacity <= 0) {
                continue;
            }
            canvas.save();
            if (self.clip != null) {
                canvas.clipPath(self.clip);
            }
            canvas.concat(self.world);
            layerPaint.setAlpha((int) (Math.max(0, Math.min(1, self.opacity)) * 255));
            canvas.drawBitmap(layer.bitmap, null, layer.bounds, layerPaint);
            canvas.restore();
        }
        canvas.restore();
        if (running) {
            postInvalidateOnAnimation();
        }
    }

    // ── touches ──

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int masked = event.getActionMasked();
        int action;
        switch (masked) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                action = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                action = 1;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                action = 2;
                break;
            case MotionEvent.ACTION_CANCEL:
                action = 3;
                break;
            default:
                return false;
        }
        if (masked == MotionEvent.ACTION_DOWN) {
            // The feed scrolls unless the widget keeps the gesture: it has a moment to say so.
            decided = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            removeCallbacks(gestureTimeout);
            postDelayed(gestureTimeout, GESTURE_DECISION_TIMEOUT_MS);
        } else if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(gestureTimeout);
        }
        if (action == 1 || action == 3) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                renderer.touch(action, event.getX(i) / density, event.getY(i) / density, event.getPointerId(i), i == 0);
            }
        } else {
            int i = event.getActionIndex();
            renderer.touch(action, event.getX(i) / density, event.getY(i) / density, event.getPointerId(i), i == 0);
        }
        return true;
    }

    private void releaseGesture() {
        decided = true;
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    // ── the editor ──

    /** Covers the control being edited (Renderer::EditorSpec as JSON) with an EditText; null removes it. */
    void showEditor(JSONObject spec) {
        if (spec == null) {
            if (editor != null) {
                InputMethodManager input = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input != null) {
                    input.hideSoftInputFromWindow(editor.getWindowToken(), 0);
                }
                EditText gone = editor;
                editor = null;
                removeView(gone);
            }
            return;
        }
        editorGeneration = spec.optLong("generation");
        boolean multiline = spec.optBoolean("multiline");
        if (editor == null) {
            editor = new EditText(getContext());
            editor.setBackground(null);
            editor.setPadding(0, 0, 0, 0);
            editor.setIncludeFontPadding(false);
            editor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (!updatingEditor) {
                        renderer.edit(editorGeneration, s.toString());
                    }
                }
            });
            // The EditText losing the keyboard ends the editing: change, blur and focusout in the widget.
            editor.setOnFocusChangeListener((View v, boolean hasFocus) -> {
                if (!hasFocus && v == editor) {
                    renderer.endEditing();
                }
            });
            addView(editor);
        }
        JSONArray frame = spec.optJSONArray("frame");
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            (int) Math.ceil(frame.optDouble(2) * density), (int) Math.ceil(frame.optDouble(3) * density));
        params.leftMargin = (int) Math.floor(frame.optDouble(0) * density);
        params.topMargin = (int) Math.floor(frame.optDouble(1) * density);
        editor.setLayoutParams(params);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) spec.optDouble("fontSize", 16) * density);
        int weight = spec.optInt("fontWeight", 400);
        boolean italic = spec.optBoolean("italic");
        if (Build.VERSION.SDK_INT >= 28) {
            editor.setTypeface(Typeface.create(Typeface.SANS_SERIF, Math.max(1, Math.min(1000, weight)), italic));
        } else {
            editor.setTypeface(Typeface.create(Typeface.SANS_SERIF, (weight >= 600 ? Typeface.BOLD : 0) | (italic ? Typeface.ITALIC : 0)));
        }
        editor.setTextColor((int) spec.optLong("color"));
        int align = spec.optInt("align");
        editor.setGravity((multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL)
            | (align == 1 ? Gravity.CENTER_HORIZONTAL : align == 2 ? Gravity.END : Gravity.START));
        editor.setHint(spec.optString("placeholder"));
        editor.setEnabled(!spec.optBoolean("readOnly"));
        int maxLength = spec.optInt("maxLength", -1);
        editor.setFilters(maxLength >= 0 ? new InputFilter[]{new InputFilter.LengthFilter(maxLength)} : new InputFilter[0]);
        editor.setInputType(inputType(spec.optString("inputType"), multiline));
        editor.setImeOptions(multiline ? EditorInfo.IME_ACTION_NONE : EditorInfo.IME_ACTION_DONE);
        String text = spec.optString("text");
        if (!text.equals(editor.getText().toString())) {
            updatingEditor = true;
            editor.setText(text);
            editor.setSelection(editor.length());
            updatingEditor = false;
        }
        int start = spec.optInt("selectionStart", -1), end = spec.optInt("selectionEnd", -1);
        if (start >= 0 && end >= start && end <= editor.length()) {
            editor.setSelection(start, end);
        }
        if (!editor.hasFocus()) {
            editor.requestFocus();
            InputMethodManager input = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null) {
                input.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    private static int inputType(String type, boolean multiline) {
        if (multiline) {
            return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        }
        switch (type) {
            case "number":
                return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
            case "email":
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            case "url":
                return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
            case "tel":
                return InputType.TYPE_CLASS_PHONE;
            default:
                return InputType.TYPE_CLASS_TEXT;
        }
    }

    void release() {
        if (document != null) {
            document.recycle();
            document = null;
        }
        layers = new ArrayList<>();
    }
}
