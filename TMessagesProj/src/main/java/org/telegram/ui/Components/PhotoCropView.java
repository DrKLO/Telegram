/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

public class PhotoCropView extends FrameLayout {

    public interface PhotoCropViewDelegate {
        void needMoveImageTo(float x, float y, float s, boolean animated);
        Bitmap getBitmap();
    }

    private boolean freeformCrop = true;
    private Paint rectPaint;
    private Paint circlePaint;
    private Paint halfPaint;
    private Paint shadowPaint;
    private float rectSizeX = 600;
    private float rectSizeY = 600;
    private int draggingState = 0;
    private int orientation;
    private float oldX = 0, oldY = 0;
    private int bitmapWidth = 1, bitmapHeight = 1, bitmapX, bitmapY;
    private float rectX = -1, rectY = -1;
    private float bitmapGlobalScale = 1;
    private float bitmapGlobalX = 0;
    private float bitmapGlobalY = 0;
    private PhotoCropViewDelegate delegate;
    private Bitmap bitmapToEdit;

    private RectF animationStartValues;
    private RectF animationEndValues;
    private Runnable animationRunnable;

    public PhotoCropView(Context context) {
        super(context);

        rectPaint = new Paint();
        rectPaint.setColor(0xb2ffffff);
        rectPaint.setStrokeWidth(AndroidUtilities.dp(2));
        rectPaint.setStyle(Paint.Style.STROKE);
        circlePaint = new Paint();
        circlePaint.setColor(0xffffffff);
        halfPaint = new Paint();
        halfPaint.setColor(0x7f000000);
        shadowPaint = new Paint();
        shadowPaint.setColor(0x1a000000);
        setWillNotDraw(false);
    }

    public void setBitmap(Bitmap bitmap, int rotation, boolean freeform) {
        bitmapToEdit = bitmap;
        rectSizeX = 600;
        rectSizeY = 600;
        draggingState = 0;
        oldX = 0;
        oldY = 0;
        bitmapWidth = 1;
        bitmapHeight = 1;
        rectX = -1;
        rectY = -1;
        freeformCrop = freeform;
        orientation = rotation;
        requestLayout();
    }

    public void setOrientation(int rotation) {
        orientation = rotation;
        rectX = -1;
        rectY = -1;
        rectSizeX = 600;
        rectSizeY = 600;
        delegate.needMoveImageTo(0, 0, 1, false);
        requestLayout();

        /*float bitmapScaledWidth = bitmapWidth * bitmapGlobalScale;
        float bitmapScaledHeight = bitmapHeight * bitmapGlobalScale;
        float bitmapStartX = (getWidth() - AndroidUtilities.dp(28) - bitmapScaledWidth) / 2 + bitmapGlobalX + AndroidUtilities.dp(14);
        float bitmapStartY = (getHeight() - AndroidUtilities.dp(28) - bitmapScaledHeight) / 2 + bitmapGlobalY + AndroidUtilities.dp(14);

        float percSizeX = rectSizeX / bitmapScaledWidth;
        float percSizeY = rectSizeY / bitmapScaledHeight;
        float percX = (rectX - bitmapStartX) / bitmapScaledWidth + percSizeX;
        float percY = (rectY - bitmapStartY) / bitmapScaledHeight;

        int width;
        int height;
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            width = bitmapToEdit.getHeight();
            height = bitmapToEdit.getWidth();
        } else {
            width = bitmapToEdit.getWidth();
            height = bitmapToEdit.getHeight();
        }

        int x = (int) (percX * width);
        int y = (int) (percY * height);
        int sizeX = (int) (percSizeX * width);
        int sizeY = (int) (percSizeY * height);
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + sizeX > width) {
            sizeX = width - x;
        }
        if (y + sizeY > height) {
            sizeY = height - y;
        }

        double cx = (x + sizeX) - width / 2.0f;
        double cy = y - height / 2.0f;
        double newX = cx * Math.cos(-Math.PI / 2) - cy * Math.sin(-Math.PI / 2) + height / 2.0f;
        double newY = cx * Math.sin(-Math.PI / 2) + cy * Math.cos(-Math.PI / 2) + width / 2.0f;
        int temp = sizeX;
        sizeY = sizeY;
        sizeY = temp;*/



        /*int temp = bitmapWidth;
        orientation = rotation;
        bitmapWidth = bitmapHeight;
        bitmapHeight = temp;
        bitmapScaledWidth = bitmapWidth * bitmapGlobalScale;
        bitmapScaledHeight = bitmapHeight * bitmapGlobalScale;

        rectX = (float) (newX * bitmapScaledWidth);
        rectY = (float) (newX * bitmapScaledHeight);
        float temp2 = rectSizeX;
        rectSizeX = rectSizeY;
        rectSizeY = temp2;

        moveToFill(false);
        invalidate();*/

        /*float temp = rectX;
        rectX = rectY;
        rectY = temp;
        temp = rectSizeX;
        rectSizeX = rectSizeY;
        rectSizeY = temp;
        int temp2 = bitmapWidth;*/
        //requestLayout();

        /*
        bitmapWidth = bitmapHeight;
        bitmapHeight = temp2;*/

        /*float bitmapScaledWidth = bitmapWidth * bitmapGlobalScale;
        float bitmapScaledHeight = bitmapHeight * bitmapGlobalScale;
        float bitmapStartX = (getWidth() - AndroidUtilities.dp(28) - bitmapScaledWidth) / 2 + bitmapGlobalX + AndroidUtilities.dp(14);
        float bitmapStartY = (getHeight() - AndroidUtilities.dp(28) - bitmapScaledHeight) / 2 + bitmapGlobalY + AndroidUtilities.dp(14);

        float percX = (rectX - bitmapStartX) / bitmapScaledWidth;
        float percY = (rectY - bitmapStartY) / bitmapScaledHeight;
        float percSizeX = rectSizeX / bitmapScaledWidth;
        float percSizeY = rectSizeY / bitmapScaledHeight;

        rectX = percY

        int width;
        int height;
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            width = bitmapToEdit.getHeight();
            height = bitmapToEdit.getWidth();
        } else {
            width = bitmapToEdit.getWidth();
            height = bitmapToEdit.getHeight();
        }

        int x = (int) (percX * width);
        int y = (int) (percY * height);
        int sizeX = (int) (percSizeX * width);
        int sizeY = (int) (percSizeY * height);
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + sizeX > width) {
            sizeX = width - x;
        }
        if (y + sizeY > height) {
            sizeY = height - y;
        }*/
        //moveToFill(false);
    }

    public boolean onTouch(MotionEvent motionEvent) {
        if (motionEvent == null) {
            draggingState = 0;
            return false;
        }
        float x = motionEvent.getX();
        float y = motionEvent.getY();
        int cornerSide = AndroidUtilities.dp(20);
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                draggingState = 1;
            } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                draggingState = 2;
            } else if (rectX - cornerSide < x && rectX + cornerSide > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                draggingState = 3;
            } else if (rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                draggingState = 4;
            } else {
                if (freeformCrop) {
                    if (rectX + cornerSide < x && rectX - cornerSide + rectSizeX > x && rectY - cornerSide < y && rectY + cornerSide > y) {
                        draggingState = 5;
                    } else if (rectY + cornerSide < y && rectY - cornerSide + rectSizeY > y && rectX - cornerSide + rectSizeX < x && rectX + cornerSide + rectSizeX > x) {
                        draggingState = 6;
                    } else if (rectY + cornerSide < y && rectY - cornerSide + rectSizeY > y && rectX - cornerSide < x && rectX + cornerSide > x) {
                        draggingState = 7;
                    } else if (rectX + cornerSide < x && rectX - cornerSide + rectSizeX > x && rectY - cornerSide + rectSizeY < y && rectY + cornerSide + rectSizeY > y) {
                        draggingState = 8;
                    }
                } else {
                    draggingState = 0;
                }
            }
            if (draggingState != 0) {
                cancelAnimationRunnable();
                PhotoCropView.this.requestDisallowInterceptTouchEvent(true);
            }
            oldX = x;
            oldY = y;
        } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            if (draggingState != 0) {
                draggingState = 0;
                startAnimationRunnable();
                return true;
            }
        } else if (motionEvent.getAction() == MotionEvent.ACTION_MOVE && draggingState != 0) {
            float diffX = x - oldX;
            float diffY = y - oldY;
            float bitmapScaledWidth = bitmapWidth * bitmapGlobalScale;
            float bitmapScaledHeight = bitmapHeight * bitmapGlobalScale;
            float bitmapStartX = (getWidth() - AndroidUtilities.dp(28) - bitmapScaledWidth) / 2 + bitmapGlobalX + AndroidUtilities.dp(14);
            float bitmapStartY = (getHeight() - AndroidUtilities.dp(28) - bitmapScaledHeight) / 2 + bitmapGlobalY + AndroidUtilities.dp(14);
            float bitmapEndX = bitmapStartX + bitmapScaledWidth;
            float bitmapEndY = bitmapStartY + bitmapScaledHeight;

            float minSide = AndroidUtilities.getPixelsInCM(0.9f, true);

            if (draggingState == 1 || draggingState == 5) {
                if (draggingState != 5) {
                    if (rectSizeX - diffX < minSide) {
                        diffX = rectSizeX - minSide;
                    }
                    if (rectX + diffX < bitmapX) {
                        diffX = bitmapX - rectX;
                    }
                    if (rectX + diffX < bitmapStartX) {
                        bitmapGlobalX -= bitmapStartX - rectX - diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                }
                if (!freeformCrop) {
                    if (rectY + diffX < bitmapY) {
                        diffX = bitmapY - rectY;
                    }
                    if (rectY + diffX < bitmapStartY) {
                        bitmapGlobalY -= bitmapStartY - rectY - diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    rectX += diffX;
                    rectY += diffX;
                    rectSizeX -= diffX;
                    rectSizeY -= diffX;
                } else {
                    if (rectSizeY - diffY < minSide) {
                        diffY = rectSizeY - minSide;
                    }
                    if (rectY + diffY < bitmapY) {
                        diffY = bitmapY - rectY;
                    }
                    if (rectY + diffY < bitmapStartY) {
                        bitmapGlobalY -= bitmapStartY - rectY - diffY;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    if (draggingState != 5) {
                        rectX += diffX;
                        rectSizeX -= diffX;
                    }
                    rectY += diffY;
                    rectSizeY -= diffY;
                }
            } else if (draggingState == 2 || draggingState == 6) {
                if (rectSizeX + diffX < minSide) {
                    diffX = -(rectSizeX - minSide);
                }
                if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                    diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                }
                if (rectX + rectSizeX + diffX > bitmapEndX) {
                    bitmapGlobalX -= bitmapEndX - rectX - rectSizeX - diffX;
                    delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                }
                if (!freeformCrop) {
                    if (rectY - diffX < bitmapY) {
                        diffX = rectY - bitmapY;
                    }
                    if (rectY - diffX < bitmapStartY) {
                        bitmapGlobalY -= bitmapStartY - rectY + diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    rectY -= diffX;
                    rectSizeX += diffX;
                    rectSizeY += diffX;
                } else {
                    if (draggingState != 6) {
                        if (rectSizeY - diffY < minSide) {
                            diffY = rectSizeY - minSide;
                        }
                        if (rectY + diffY < bitmapY) {
                            diffY = bitmapY - rectY;
                        }
                        if (rectY + diffY < bitmapStartY) {
                            bitmapGlobalY -= bitmapStartY - rectY - diffY;
                            delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                        }
                        rectY += diffY;
                        rectSizeY -= diffY;
                    }
                    rectSizeX += diffX;
                }
            } else if (draggingState == 3 || draggingState == 7) {
                if (rectSizeX - diffX < minSide) {
                    diffX = rectSizeX - minSide;
                }
                if (rectX + diffX < bitmapX) {
                    diffX = bitmapX - rectX;
                }
                if (rectX + diffX < bitmapStartX) {
                    bitmapGlobalX -= bitmapStartX - rectX - diffX;
                    delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                }
                if (!freeformCrop) {
                    if (rectY + rectSizeX - diffX > bitmapY + bitmapHeight) {
                        diffX = rectY + rectSizeX - bitmapY - bitmapHeight;
                    }
                    if (rectY + rectSizeX - diffX > bitmapEndY) {
                        bitmapGlobalY -= bitmapEndY - rectY - rectSizeX + diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    rectX += diffX;
                    rectSizeX -= diffX;
                    rectSizeY -= diffX;
                } else {
                    if (draggingState != 7) {
                        if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                            diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                        }
                        if (rectY + rectSizeY + diffY > bitmapEndY) {
                            bitmapGlobalY -= bitmapEndY - rectY - rectSizeY - diffY;
                            delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                        }
                        rectSizeY += diffY;
                        if (rectSizeY < minSide) {
                            rectSizeY = minSide;
                        }
                    }
                    rectX += diffX;
                    rectSizeX -= diffX;
                }
            } else if (draggingState == 4 || draggingState == 8) {
                if (draggingState != 8) {
                    if (rectX + rectSizeX + diffX > bitmapX + bitmapWidth) {
                        diffX = bitmapX + bitmapWidth - rectX - rectSizeX;
                    }
                    if (rectX + rectSizeX + diffX > bitmapEndX) {
                        bitmapGlobalX -= bitmapEndX - rectX - rectSizeX - diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                }
                if (!freeformCrop) {
                    if (rectY + rectSizeX + diffX > bitmapY + bitmapHeight) {
                        diffX = bitmapY + bitmapHeight - rectY - rectSizeX;
                    }
                    if (rectY + rectSizeX + diffX > bitmapEndY) {
                        bitmapGlobalY -= bitmapEndY - rectY - rectSizeX - diffX;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    rectSizeX += diffX;
                    rectSizeY += diffX;
                } else {
                    if (rectY + rectSizeY + diffY > bitmapY + bitmapHeight) {
                        diffY = bitmapY + bitmapHeight - rectY - rectSizeY;
                    }
                    if (rectY + rectSizeY + diffY > bitmapEndY) {
                        bitmapGlobalY -= bitmapEndY - rectY - rectSizeY - diffY;
                        delegate.needMoveImageTo(bitmapGlobalX, bitmapGlobalY, bitmapGlobalScale, false);
                    }
                    if (draggingState != 8) {
                        rectSizeX += diffX;
                    }
                    rectSizeY += diffY;
                }
                if (rectSizeX < minSide) {
                    rectSizeX = minSide;
                }
                if (rectSizeY < minSide) {
                    rectSizeY = minSide;
                }
            }

            oldX = x;
            oldY = y;
            invalidate();
        }
        return draggingState != 0;
    }

    public float getRectX() {
        return rectX - AndroidUtilities.dp(14);
    }

    public float getRectY() {
        return rectY - AndroidUtilities.dp(14);
    }

    public float getRectSizeX() {
        return rectSizeX;
    }

    public float getRectSizeY() {
        return rectSizeY;
    }

    public float getBitmapX() {
        return bitmapX - AndroidUtilities.dp(14);
    }

    public float getBitmapY() {
        return bitmapY - AndroidUtilities.dp(14);
    }

    public float getLimitX() {
        return rectX - ((int) Math.max(0, Math.ceil((getWidth() - AndroidUtilities.dp(28) - bitmapWidth * bitmapGlobalScale) / 2)) + AndroidUtilities.dp(14));
    }

    public float getLimitY() {
        return rectY - ((int) Math.max(0, Math.ceil((getHeight() - AndroidUtilities.dp(28) - bitmapHeight * bitmapGlobalScale) / 2)) + AndroidUtilities.dp(14));
    }

    public float getLimitWidth() {
        return getWidth() - AndroidUtilities.dp(14) - rectX - (int) Math.max(0, Math.ceil((getWidth() - AndroidUtilities.dp(28) - bitmapWidth * bitmapGlobalScale) / 2)) - rectSizeX;
    }

    public float getLimitHeight() {
        return getHeight() - AndroidUtilities.dp(14) - rectY - (int) Math.max(0, Math.ceil((getHeight() - AndroidUtilities.dp(28) - bitmapHeight * bitmapGlobalScale) / 2)) - rectSizeY;
    }

    private Bitmap createBitmap(int x, int y, int w, int h) {
        Bitmap newBimap = delegate.getBitmap();
        if (newBimap != null) {
            bitmapToEdit = newBimap;
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        Matrix matrix = new Matrix();
        matrix.setTranslate(-bitmapToEdit.getWidth() / 2, -bitmapToEdit.getHeight() / 2);
        matrix.postRotate(orientation);
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            matrix.postTranslate(bitmapToEdit.getHeight() / 2 - x, bitmapToEdit.getWidth() / 2 - y);
        } else {
            matrix.postTranslate(bitmapToEdit.getWidth() / 2 - x, bitmapToEdit.getHeight() / 2 - y);
        }
        canvas.drawBitmap(bitmapToEdit, matrix, paint);
        try {
            canvas.setBitmap(null);
        } catch (Exception e) {
            //don't promt, this will crash on 2.x
        }

        return bitmap;
    }

    public Bitmap getBitmap() {
        Bitmap newBimap = delegate.getBitmap();
        if (newBimap != null) {
            bitmapToEdit = newBimap;
        }

        float bitmapScaledWidth = bitmapWidth * bitmapGlobalScale;
        float bitmapScaledHeight = bitmapHeight * bitmapGlobalScale;
        float bitmapStartX = (getWidth() - AndroidUtilities.dp(28) - bitmapScaledWidth) / 2 + bitmapGlobalX + AndroidUtilities.dp(14);
        float bitmapStartY = (getHeight() - AndroidUtilities.dp(28) - bitmapScaledHeight) / 2 + bitmapGlobalY + AndroidUtilities.dp(14);

        float percX = (rectX - bitmapStartX) / bitmapScaledWidth;
        float percY = (rectY - bitmapStartY) / bitmapScaledHeight;
        float percSizeX = rectSizeX / bitmapScaledWidth;
        float percSizeY = rectSizeY / bitmapScaledHeight;

        int width;
        int height;
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            width = bitmapToEdit.getHeight();
            height = bitmapToEdit.getWidth();
        } else {
            width = bitmapToEdit.getWidth();
            height = bitmapToEdit.getHeight();
        }

        int x = (int) (percX * width);
        int y = (int) (percY * height);
        int sizeX = (int) (percSizeX * width);
        int sizeY = (int) (percSizeY * height);
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + sizeX > width) {
            sizeX = width - x;
        }
        if (y + sizeY > height) {
            sizeY = height - y;
        }
        try {
            return createBitmap(x, y, sizeX, sizeY);
        } catch (Throwable e) {
            FileLog.e("tmessags", e);
            System.gc();
            try {
                return createBitmap(x, y, sizeX, sizeY);
            } catch (Throwable e2) {
                FileLog.e("tmessages", e2);
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), rectY, halfPaint);
        canvas.drawRect(0, rectY, rectX, rectY + rectSizeY, halfPaint);
        canvas.drawRect(rectX + rectSizeX, rectY, getWidth(), rectY + rectSizeY, halfPaint);
        canvas.drawRect(0, rectY + rectSizeY, getWidth(), getHeight(), halfPaint);

        int side = AndroidUtilities.dp(1);
        canvas.drawRect(rectX - side * 2, rectY - side * 2, rectX - side * 2 + AndroidUtilities.dp(20), rectY, circlePaint);
        canvas.drawRect(rectX - side * 2, rectY - side * 2, rectX, rectY - side * 2 + AndroidUtilities.dp(20), circlePaint);

        canvas.drawRect(rectX + rectSizeX + side * 2 - AndroidUtilities.dp(20), rectY - side * 2, rectX + rectSizeX + side * 2, rectY, circlePaint);
        canvas.drawRect(rectX + rectSizeX, rectY - side * 2, rectX + rectSizeX + side * 2, rectY - side * 2 + AndroidUtilities.dp(20), circlePaint);

        canvas.drawRect(rectX - side * 2, rectY + rectSizeY + side * 2 - AndroidUtilities.dp(20), rectX, rectY + rectSizeY + side * 2, circlePaint);
        canvas.drawRect(rectX - side * 2, rectY + rectSizeY, rectX - side * 2 + AndroidUtilities.dp(20), rectY + rectSizeY + side * 2, circlePaint);

        canvas.drawRect(rectX + rectSizeX + side * 2 - AndroidUtilities.dp(20), rectY + rectSizeY, rectX + rectSizeX + side * 2, rectY + rectSizeY + side * 2, circlePaint);
        canvas.drawRect(rectX + rectSizeX, rectY + rectSizeY + side * 2 - AndroidUtilities.dp(20), rectX + rectSizeX + side * 2, rectY + rectSizeY + side * 2, circlePaint);

        for (int a = 1; a < 3; a++) {
            canvas.drawRect(rectX + rectSizeX / 3 * a - side, rectY, rectX + side * 2 + rectSizeX / 3 * a, rectY + rectSizeY, shadowPaint);
            canvas.drawRect(rectX, rectY + rectSizeY / 3 * a - side, rectX + rectSizeX, rectY + rectSizeY / 3 * a + side * 2, shadowPaint);
        }

        for (int a = 1; a < 3; a++) {
            canvas.drawRect(rectX + rectSizeX / 3 * a, rectY, rectX + side + rectSizeX / 3 * a, rectY + rectSizeY, circlePaint);
            canvas.drawRect(rectX, rectY + rectSizeY / 3 * a, rectX + rectSizeX, rectY + rectSizeY / 3 * a + side, circlePaint);
        }

        canvas.drawRect(rectX, rectY, rectX + rectSizeX, rectY + rectSizeY, rectPaint);
    }

    public void setBitmapParams(float scale, float x, float y) {
        bitmapGlobalScale = scale;
        bitmapGlobalX = x;
        bitmapGlobalY = y;
    }

    public void startAnimationRunnable() {
        if (animationRunnable != null) {
            return;
        }
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable == this) {
                    animationRunnable = null;
                    moveToFill(true);
                }
            }
        };
        AndroidUtilities.runOnUIThread(animationRunnable, 1500);
    }

    public void cancelAnimationRunnable() {
        if (animationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
            animationRunnable = null;
            animationStartValues = null;
            animationEndValues = null;
        }
    }

    public void setAnimationProgress(float animationProgress) {
        if (animationStartValues != null) {
            if (animationProgress == 1) {
                rectX = animationEndValues.left;
                rectY = animationEndValues.top;
                rectSizeX = animationEndValues.right;
                rectSizeY = animationEndValues.bottom;
                animationStartValues = null;
                animationEndValues = null;
            } else {
                rectX = animationStartValues.left + (animationEndValues.left - animationStartValues.left) * animationProgress;
                rectY = animationStartValues.top + (animationEndValues.top - animationStartValues.top) * animationProgress;
                rectSizeX = animationStartValues.right + (animationEndValues.right - animationStartValues.right) * animationProgress;
                rectSizeY = animationStartValues.bottom + (animationEndValues.bottom - animationStartValues.bottom) * animationProgress;
            }
            invalidate();
        }
    }

    public void moveToFill(boolean animated) {
        float scaleToX = bitmapWidth / rectSizeX;
        float scaleToY = bitmapHeight / rectSizeY;
        float scaleTo = scaleToX > scaleToY ? scaleToY : scaleToX;
        if (scaleTo > 1 && scaleTo * bitmapGlobalScale > 3) {
            scaleTo = 3 / bitmapGlobalScale;
        } else if (scaleTo < 1 && scaleTo * bitmapGlobalScale < 1) {
            scaleTo = 1 / bitmapGlobalScale;
        }
        float newSizeX = rectSizeX * scaleTo;
        float newSizeY = rectSizeY * scaleTo;
        float newX = (getWidth() - AndroidUtilities.dp(28) - newSizeX) / 2 + AndroidUtilities.dp(14);
        float newY = (getHeight() - AndroidUtilities.dp(28) - newSizeY) / 2 + AndroidUtilities.dp(14);
        animationStartValues = new RectF(rectX, rectY, rectSizeX, rectSizeY);
        animationEndValues = new RectF(newX, newY, newSizeX, newSizeY);

        float newBitmapGlobalX = newX + getWidth() / 2 * (scaleTo - 1) + (bitmapGlobalX - rectX) * scaleTo;
        float newBitmapGlobalY = newY + getHeight() / 2 * (scaleTo - 1) + (bitmapGlobalY - rectY) * scaleTo;

        delegate.needMoveImageTo(newBitmapGlobalX, newBitmapGlobalY, bitmapGlobalScale * scaleTo, animated);
    }

    public void setDelegate(PhotoCropViewDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        Bitmap newBimap = delegate.getBitmap();
        if (newBimap != null) {
            bitmapToEdit = newBimap;
        }

        if (bitmapToEdit == null) {
            return;
        }

        int viewWidth = getWidth() - AndroidUtilities.dp(28);
        int viewHeight = getHeight() - AndroidUtilities.dp(28);

        float bitmapW;
        float bitmapH;
        if (orientation % 360 == 90 || orientation % 360 == 270) {
            bitmapW = bitmapToEdit.getHeight();
            bitmapH = bitmapToEdit.getWidth();
        } else {
            bitmapW = bitmapToEdit.getWidth();
            bitmapH = bitmapToEdit.getHeight();
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

        float percX = (rectX - bitmapX) / bitmapWidth;
        float percY = (rectY - bitmapY) / bitmapHeight;
        float percSizeX = rectSizeX / bitmapWidth;
        float percSizeY = rectSizeY / bitmapHeight;
        bitmapWidth = (int) bitmapW;
        bitmapHeight = (int) bitmapH;

        bitmapX = (int) Math.ceil((viewWidth - bitmapWidth) / 2 + AndroidUtilities.dp(14));
        bitmapY = (int) Math.ceil((viewHeight - bitmapHeight) / 2 + AndroidUtilities.dp(14));

        if (rectX == -1 && rectY == -1) {
            if (freeformCrop) {
                rectY = bitmapY;
                rectX = bitmapX;
                rectSizeX = bitmapWidth;
                rectSizeY = bitmapHeight;
            } else {
                if (bitmapWidth > bitmapHeight) {
                    rectY = bitmapY;
                    rectX = (viewWidth - bitmapHeight) / 2 + AndroidUtilities.dp(14);
                    rectSizeX = bitmapHeight;
                    rectSizeY = bitmapHeight;
                } else {
                    rectX = bitmapX;
                    rectY = (viewHeight - bitmapWidth) / 2 + AndroidUtilities.dp(14);
                    rectSizeX = bitmapWidth;
                    rectSizeY = bitmapWidth;
                }
            }
        } else {
            rectX = percX * bitmapWidth + bitmapX;
            rectY = percY * bitmapHeight + bitmapY;
            rectSizeX = percSizeX * bitmapWidth;
            rectSizeY = percSizeY * bitmapHeight;
        }
    }
}
