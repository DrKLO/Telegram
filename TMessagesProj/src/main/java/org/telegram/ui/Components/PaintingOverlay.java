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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;

import java.util.ArrayList;

public class PaintingOverlay extends FrameLayout {

    private Bitmap paintBitmap;
    private ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
    private boolean ignoreLayout;
    private Drawable backgroundDrawable;

    public PaintingOverlay(Context context) {
        super(context);
    }

    public void setData(String paintPath, ArrayList<VideoEditedInfo.MediaEntity> entities, boolean isVideo, boolean startAfterSet) {
        if (paintPath != null) {
            paintBitmap = BitmapFactory.decodeFile(paintPath);
            setBackground(backgroundDrawable = new BitmapDrawable(paintBitmap));
        } else {
            paintBitmap = null;
            setBackground(backgroundDrawable = null);
        }
        setEntities(entities, isVideo, startAfterSet);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayout = true;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        if (mediaEntities != null) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.view == null) {
                    continue;
                }
                if (entity.view instanceof EditTextOutline) {
                    entity.view.measure(MeasureSpec.makeMeasureSpec(entity.viewWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    float sc = entity.textViewWidth * width / entity.viewWidth;
                    entity.view.setScaleX(entity.scale * sc);
                    entity.view.setScaleY(entity.scale * sc);
                } else {
                    entity.view.measure(MeasureSpec.makeMeasureSpec((int) (width * entity.width), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * entity.height), MeasureSpec.EXACTLY));
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
        if (mediaEntities != null) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.view == null) {
                    continue;
                }
                int x;
                int y;
                if (entity.view instanceof EditTextOutline) {
                    x = (int) (width * entity.textViewX) - entity.view.getMeasuredWidth() / 2;
                    y = (int) (height * entity.textViewY) - entity.view.getMeasuredHeight() / 2;
                } else {
                    x = (int) (width * entity.x);
                    y = (int) (height * entity.y);
                }
                entity.view.layout(x, y, x + entity.view.getMeasuredWidth(), y + entity.view.getMeasuredHeight());
            }
        }
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
        mediaEntities = entities;
        removeAllViews();
        if (entities != null && !entities.isEmpty()) {
            for (int a = 0, N = mediaEntities.size(); a < N; a++) {
                VideoEditedInfo.MediaEntity entity = mediaEntities.get(a);
                if (entity.type == 0) {
                    BackupImageView imageView = new BackupImageView(getContext());
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
                    entity.view = imageView;
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
                    editText.setText(entity.text);
                    editText.setTypeface(null, Typeface.BOLD);
                    editText.setGravity(Gravity.CENTER);
                    editText.setHorizontallyScrolling(false);
                    editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
                    editText.setFocusableInTouchMode(true);
                    editText.setEnabled(false);
                    editText.setInputType(editText.getInputType() | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    if (Build.VERSION.SDK_INT >= 23) {
                        editText.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
                    }
                    if ((entity.subType & 1) != 0) {
                        editText.setTextColor(0xffffffff);
                        editText.setStrokeColor(entity.color);
                        editText.setFrameColor(0);
                        editText.setShadowLayer(0, 0, 0, 0);
                    } else if ((entity.subType & 4) != 0) {
                        editText.setTextColor(0xff000000);
                        editText.setStrokeColor(0);
                        editText.setFrameColor(entity.color);
                        editText.setShadowLayer(0, 0, 0, 0);
                    } else {
                        editText.setTextColor(entity.color);
                        editText.setStrokeColor(0);
                        editText.setFrameColor(0);
                        editText.setShadowLayer(5, 0, 1, 0x66000000);
                    }
                    entity.view = editText;
                }
                addView(entity.view);
                entity.view.setRotation((float) (-entity.rotation / Math.PI * 180));
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
