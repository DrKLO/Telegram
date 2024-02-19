package com.google.zxing;

import com.google.zxing.common.BitMatrix;

public final class BinaryBitmap {

  private final Binarizer binarizer;
  private BitMatrix matrix;

  public BinaryBitmap(Binarizer binarizer) {
    if (binarizer == null) {
      throw new IllegalArgumentException("Binarizer must be non-null.");
    }
    this.binarizer = binarizer;
  }

  public int getWidth() {
    return binarizer.getWidth();
  }

  public int getHeight() {
    return binarizer.getHeight();
  }

  public BitMatrix getBlackMatrix() throws NotFoundException {
    if (matrix == null) {
      matrix = binarizer.getBlackMatrix();
    }
    return matrix;
  }


  public BinaryBitmap crop(int left, int top, int width, int height) {
    LuminanceSource newSource = binarizer.getLuminanceSource().crop(left, top, width, height);
    return new BinaryBitmap(binarizer.createBinarizer(newSource));
  }


  @Override
  public String toString() {
    try {
      return getBlackMatrix().toString();
    } catch (NotFoundException e) {
      return "";
    }
  }

}
