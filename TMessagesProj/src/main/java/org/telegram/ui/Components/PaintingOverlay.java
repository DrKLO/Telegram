package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;
import org.telegram.ui.Components.Paint.Views.PaintTextOptionsView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PaintingOverlay extends FrameLayout {

    private Bitmap paintBitmap;
    private HashMap<View, VideoEditedInfo.MediaEntity> mediaEntityViews;
    private boolean ignoreLayout;
    private Drawable backgroundDrawable;

    public PaintingOverlay(Context context) {
        super(context);
    }

    public void setData(String paintPath, ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean startAfterSet) {
        setEntities(entities, isVideo, startAfterSet);
        if (paintPath != null) {
            paintBitmap = BitmapFactory.decodeFile(paintPath);
            setBackground(backgroundDrawable = new BitmapDrawable(paintBitmap));
        } else {
            paintBitmap = null;
            setBackground(backgroundDrawable = null);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayout = true;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        if (mediaEntityViews != null) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            for (int a = 0, N = getChildCount(); a < N; a++) {
                View child = getChildAt(a);
                VideoEditedInfo.MediaEntity entity = mediaEntityViews.get(child);
                if (entity == null) {
                    continue;
                }
                if (child instanceof EditTextOutline) {
                    child.measure(MeasureSpec.makeMeasureSpec(entity.viewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    float sc = entity.textViewWidth * width / entity.viewWidth;
                    child.setScaleX(entity.scale * sc);
                    child.setScaleY(entity.scale * sc);
                } else {
                    child.measure(MeasureSpec.makeMeasureSpec((int) (width * entity.width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * entity.height), MeasureSpec.EXACTLY));
                }
            }
        }
        ignoreLayout = false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (mediaEntityViews != null) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            for (int a = 0, N = getChildCount(); a < N; a++) {
                View child = getChildAt(a);
                VideoEditedInfo.MediaEntity entity = mediaEntityViews.get(child);
                if (entity == null) {
                    continue;
                }
                int x, y;
                if (child instanceof EditTextOutline) {
                    x = (int) (width * entity.textViewX) - child.getMeasuredWidth() / 2;
                    y = (int) (height * entity.textViewY) - child.getMeasuredHeight() / 2;
                } else {
                    x = (int) (width * entity.x);
                    y = (int) (height * entity.y);
                }
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            }
        }
    }

    public void reset() {
        paintBitmap = null;
        setBackground(backgroundDrawable = null);
        if (mediaEntityViews != null) {
            mediaEntityViews.clear();
        }
        removeAllViews();
    }

    public void showAll() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            getChildAt(a).setVisibility(VISIBLE);
        }
        setBackground(backgroundDrawable);
    }

    public void hideEntities() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            getChildAt(a).setVisibility(INVISIBLE);
        }
    }

    public void hideBitmap() {
        setBackground(null);
    }

    public void setEntities(ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean startAfterSet) {
        reset();
        mediaEntityViews = new HashMap<>();
        if (entities != null && !entities.isEmpty()) {
            for (int a = 0, N = entities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = entities.get(a);
                View child = null;
                if (entity.type == 0) {
                    BackupImageView imageView = new BackupImageView(getContext());
                    imageView.setLayerNum(8);
                    imageView.setAspectFit(true);
                    ImageReceiver imageReceiver = imageView.getImageReceiver();
                    if (isVideo) {
                        imageReceiver.setAllowDecodeSingleFrame(true);
                        imageReceiver.setAllowStartLottieAnimation(false);
                        if (startAfterSet) {
                            imageReceiver.setDelegate((imageReceiver1, set, thumb, memCache) -> {
                                if (set && !thumb) {
                                    RLottieDrawable drawable = imageReceiver1.getLottieAnimation();
                                    if (drawable != null) {
                                        drawable.start();
                                    }
                                }
                            });
                        }
                    }
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(entity.document.thumbs, 90);
                    imageReceiver.setImage(ImageLocation.getForDocument(entity.document), null, ImageLocation.getForDocument(thumb, entity.document), null, "webp", entity.parentObject, 1);
                    if ((entity.subType & 2) != 0) {
                        imageView.setScaleX(-1);
                    }
                    entity.view = child = imageView;
                } else if (entity.type == 1) {
                    EditTextOutline editText = new EditTextOutline(getContext()) {
                        @Override
                        public boolean dispatchTouchEvent(MotionEvent event) {
                            return false;
                        }

                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            return false;
                        }
                    };
                    editText.setBackgroundColor(Color.TRANSPARENT);
                    editText.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
                    editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, entity.fontSize);
                    editText.setTypeface(entity.textTypeface.getTypeface());
                    SpannableString text = new SpannableString(Emoji.replaceEmoji(entity.text, editText.getPaint().getFontMetricsInt(), (int) (editText.getTextSize() * .8f), false));
                    for (VideoEditedInfo.EmojiEntity e : entity.entities) {
                        text.setSpan(new AnimatedEmojiSpan(e.document_id, editText.getPaint().getFontMetricsInt()), e.offset, e.offset + e.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    editText.setText(text);
                    editText.setGravity(Gravity.CENTER);

                    int gravity;
                    switch (entity.textAlign) {
                        default:
                        case PaintTextOptionsView.ALIGN_LEFT:
                            gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                            break;
                        case PaintTextOptionsView.ALIGN_CENTER:
                            gravity = Gravity.CENTER;
                            break;
                        case PaintTextOptionsView.ALIGN_RIGHT:
                            gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                            break;
                    }

                    editText.setGravity(gravity);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        int textAlign;
                        switch (entity.textAlign) {
                            default:
                            case PaintTextOptionsView.ALIGN_LEFT:
                                textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_END : View.TEXT_ALIGNMENT_TEXT_START;
                                break;
                            case PaintTextOptionsView.ALIGN_CENTER:
                                textAlign = View.TEXT_ALIGNMENT_CENTER;
                                break;
                            case PaintTextOptionsView.ALIGN_RIGHT:
                                textAlign = LocaleController.isRTL ? View.TEXT_ALIGNMENT_TEXT_START : View.TEXT_ALIGNMENT_TEXT_END;
                                break;
                        }
                        editText.setTextAlignment(textAlign);
                    }
                    editText.setHorizontallyScrolling(false);
                    editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    editText.setFocusableInTouchMode(true);
                    editText.setEnabled(false);
                    editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    if (Build.VERSION.SDK_INT >= 23) {
                        editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                    }
                    editText.setShadowLayer(0, 0, 0, 0);
                    int textColor = entity.color;
                    if (entity.subType == 0) {
                        editText.setFrameColor(entity.color);
                        textColor = AndroidUtilities.computePerceivedBrightness(entity.color) >= .721f ? Color.BLACK : Color.WHITE;
                    } else if (entity.subType == 1) {
                        editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? 0x99000000 : 0x99ffffff);
                    } else if (entity.subType == 2) {
                        editText.setFrameColor(AndroidUtilities.computePerceivedBrightness(entity.color) >= .25f ? Color.BLACK : Color.WHITE);
                    } else {
                        editText.setFrameColor(0);
                    }
                    editText.setTextColor(textColor);
                    editText.setCursorColor(textColor);
                    editText.setHandlesColor(textColor);
                    editText.setHighlightColor(Theme.multAlpha(textColor, .4f));
                    entity.view = child = editText;
                }
                if (child != null) {
                    addView(child);
                    child.setRotation((float) (-entity.rotation / Math.PI * 180));
                    mediaEntityViews.put(child, entity);
                }
            }
        }
    }

    public void setBitmap(Bitmap bitmap) {
        setBackground(backgroundDrawable = new BitmapDrawable(paintBitmap = bitmap));
    }

    public Bitmap getBitmap() {
        return paintBitmap;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha((int) (255 * alpha));
        }
        final int count = getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);
            if (child != null && child.getParent() == this) {
                child.setAlpha(alpha);
            }
        }
    }

    public Bitmap getThumb() {
        float w = getMeasuredWidth();
        float h = getMeasuredHeight();
        float scale = Math.max(w / AndroidUtilities.dp(120), h / AndroidUtilities.dp(120));
        Bitmap bitmap = Bitmap.createBitmap((int) (w / scale), (int) (h / scale), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / scale, 1.0f / scale);
        draw(canvas);
        return bitmap;
    }
}
