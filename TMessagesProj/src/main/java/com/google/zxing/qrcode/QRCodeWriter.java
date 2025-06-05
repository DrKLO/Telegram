/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.qrcode;

import static org.telegram.messenger.AndroidUtilities.readRes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.GradientDrawable;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;

import java.util.Arrays;
import java.util.Map;

/**
 * This object renders a QR Code as a BitMatrix 2D array of greyscale values.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class QRCodeWriter {

  private static final int QUIET_ZONE_SIZE = 4;
  private ByteMatrix input;
  private float[] radii = new float[8];
  private int imageBloks;
  private int imageBlockX;
  public boolean includeSideQuads = true;
  private int sideQuadSize;

  private int imageSize;

  public Bitmap encode(String contents, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap) throws WriterException {
    return encode(contents, width, height, hints, bitmap, 1.0f, 0xffffffff, 0xff000000);
  }

  public Bitmap encode(String contents, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap, float radiusFactor, int backgroundColor, int color) throws WriterException {

    if (contents.isEmpty()) {
      throw new IllegalArgumentException("Found empty contents");
    }

    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' + height);
    }

    ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
    int quietZone = QUIET_ZONE_SIZE;
    if (hints != null) {
      if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
        errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints.get(EncodeHintType.ERROR_CORRECTION).toString());
      }
      if (hints.containsKey(EncodeHintType.MARGIN)) {
        quietZone = Integer.parseInt(hints.get(EncodeHintType.MARGIN).toString());
      }
    }

    QRCode code = Encoder.encode(contents, errorCorrectionLevel, hints);

    input = code.getMatrix();
    if (input == null) {
      throw new IllegalStateException();
    }
    int inputWidth = input.getWidth();
    int inputHeight = input.getHeight();

    for (int x = 0; x < inputWidth; x++) {
      if (has(x, 0)) {
        sideQuadSize++;
      } else {
        break;
      }
    }

    int qrWidth = inputWidth + (quietZone * 2);
    int qrHeight = inputHeight + (quietZone * 2);
    int outputWidth = Math.max(width, qrWidth);
    int outputHeight = Math.max(height, qrHeight);

    int multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight);

    int padding = 16;

    int size = multiple * inputWidth + padding * 2;
    if (bitmap == null || bitmap.getWidth() != size) {
      bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    }
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(backgroundColor);
    Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    blackPaint.setColor(color);

    GradientDrawable rect = new GradientDrawable();
    rect.setShape(GradientDrawable.RECTANGLE);
    rect.setCornerRadii(radii);

    imageBloks = Math.round((size - 32) / 4.65f / multiple);
    if (imageBloks % 2 != inputWidth % 2) {
      imageBloks++;
    }
    imageBlockX = (inputWidth - imageBloks) / 2;
    imageSize = imageBloks * multiple - 24;
    int imageX = (size - imageSize) / 2;
    if (includeSideQuads) {
      blackPaint.setColor(color);
      drawSideQuadsGradient(canvas, blackPaint, rect, sideQuadSize, multiple, padding, size, radiusFactor, radii, backgroundColor, color);
    }
    boolean isTransparentBackground = Color.alpha(backgroundColor) == 0;
    float r = multiple / 2.0f * radiusFactor;

    for (int y = 0, outputY = padding; y < inputHeight; y++, outputY += multiple) {
      for (int x = 0, outputX = padding; x < inputWidth; x++, outputX += multiple) {
        if (has(x, y)) {
          Arrays.fill(radii, r);
          if (has(x, y - 1)) {
            radii[0] = radii[1] = 0;
            radii[2] = radii[3] = 0;
          }
          if (has(x, y + 1)) {
            radii[6] = radii[7] = 0;
            radii[4] = radii[5] = 0;
          }
          if (has(x - 1, y)) {
            radii[0] = radii[1] = 0;
            radii[6] = radii[7] = 0;
          }
          if (has(x + 1, y)) {
            radii[2] = radii[3] = 0;
            radii[4] = radii[5] = 0;
          }
          rect.setColor(color);
          rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
          rect.draw(canvas);
        } else {
          boolean has = false;
          Arrays.fill(radii, 0);
          if (has(x - 1, y - 1) && has(x - 1, y) && has(x, y - 1)) {
            radii[0] = radii[1] = r;
            has = true;
          }
          if (has(x + 1, y - 1) && has(x + 1, y) && has(x, y - 1)) {
            radii[2] = radii[3] = r;
            has = true;
          }
          if (has(x - 1, y + 1) && has(x - 1, y) && has(x, y + 1)) {
            radii[6] = radii[7] = r;
            has = true;
          }
          if (has(x + 1, y + 1) && has(x + 1, y) && has(x, y + 1)) {
            radii[4] = radii[5] = r;
            has = true;
          }
          if (has && !isTransparentBackground) {
            canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
            rect.setColor(backgroundColor);
            rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
            rect.draw(canvas);
          }
        }
      }
    }

    Bitmap icon = SvgHelper.getBitmap(readRes(R.raw.qr_logo), imageSize, imageSize, false);
    canvas.drawBitmap(icon, imageX, imageX, null);
    icon.recycle();

    canvas.setBitmap(null);

    return bitmap;
  }

  public static void drawSideQuadsGradient(Canvas canvas, Paint blackPaint, GradientDrawable rect, float sideQuadSize, float multiple, int padding, float size, float radiusFactor, float[] radii, int backgroundColor, int color) {
    boolean isTransparentBackground = Color.alpha(backgroundColor) == 0;
    rect.setShape(GradientDrawable.RECTANGLE);
    rect.setCornerRadii(radii);
    Path clipPath = new Path();
    RectF rectF = new RectF();
    for (int a = 0; a < 3; a++) {
      float x, y;
      if (a == 0) {
        x = padding;
        y = padding;
      } else if (a == 1) {
        x = size - sideQuadSize * multiple - padding;
        y = padding;
      } else {
        x = padding;
        y = size - sideQuadSize * multiple - padding;
      }

      float r;
      if (isTransparentBackground) {
        rectF.set(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
        r = (sideQuadSize * multiple) / 4.0f * radiusFactor;
        clipPath.reset();
        clipPath.addRoundRect(rectF, r, r, Path.Direction.CW);
        clipPath.close();
        canvas.save();
        canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
      }
      r = (sideQuadSize * multiple) / 3.0f * radiusFactor;
      Arrays.fill(radii, r);
      rect.setColor(color);
      rect.setBounds((int) x, (int) y, (int) (x + sideQuadSize * multiple), (int) (y + sideQuadSize * multiple));
      rect.draw(canvas);
      canvas.drawRect(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple, blackPaint);
      if (isTransparentBackground) {
        canvas.restore();
      }

      if (!isTransparentBackground) {
        r = (sideQuadSize * multiple) / 4.0f * radiusFactor;
        Arrays.fill(radii, r);
        rect.setColor(backgroundColor);
        rect.setBounds((int) (x + multiple), (int) (y + multiple), (int) (x + (sideQuadSize - 1) * multiple), (int) (y + (sideQuadSize - 1) * multiple));
        rect.draw(canvas);
      }

      r = ((sideQuadSize - 2) * multiple) / 4.0f * radiusFactor;
      Arrays.fill(radii, r);
      rect.setColor(color);
      rect.setBounds((int) (x + multiple * 2), (int) (y + multiple * 2), (int) (x + (sideQuadSize - 2) * multiple), (int) (y + (sideQuadSize - 2) * multiple));
      rect.draw(canvas);
    }
  }

  public static void drawSideQuads(Canvas canvas, float xOffset, float yOffset, Paint blackPaint, float sideQuadSize, float multiple, int padding, float size, float radiusFactor, float[] radii, boolean isTransparentBackground) {
    Path clipPath = new Path();
    for (int a = 0; a < 3; a++) {
      float x, y;
      if (a == 0) {
        x = padding;
        y = padding;
      } else if (a == 1) {
        x = size - sideQuadSize * multiple - padding;
        y = padding;
      } else {
        x = padding;
        y = size - sideQuadSize * multiple - padding;
      }

      x += xOffset;
      y += yOffset;

      float r;
      if (isTransparentBackground) {
        AndroidUtilities.rectTmp.set(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
        r = (sideQuadSize * multiple) / 4.0f * radiusFactor;
        clipPath.reset();
        clipPath.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
        clipPath.close();
        canvas.save();
        canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
      }
      r = (sideQuadSize * multiple) / 3.0f * radiusFactor;
      AndroidUtilities.rectTmp.set(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple);
      canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, blackPaint);
      if (isTransparentBackground) {
        canvas.restore();
      }

      r = ((sideQuadSize - 2) * multiple) / 4.0f * radiusFactor;
      AndroidUtilities.rectTmp.set(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple);
      canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, blackPaint);
    }
  }

  private boolean has(int x, int y) {
    if (x >= imageBlockX && x < imageBlockX + imageBloks && y >= imageBlockX && y < imageBlockX + imageBloks) {
      return false;
    }
    if ((x < sideQuadSize || x >= input.getWidth() - sideQuadSize) && y < sideQuadSize) {
      return false;
    }
    if (x < sideQuadSize && y >= input.getHeight() - sideQuadSize) {
      return false;
    }
    return x >= 0 && y >= 0 && x < input.getWidth() && y < input.getHeight() && input.get(x, y) == 1;
  }

  public int getImageSize() {
    return imageSize;
  }

  public int getSideSize() {
    return sideQuadSize;
  }
}
