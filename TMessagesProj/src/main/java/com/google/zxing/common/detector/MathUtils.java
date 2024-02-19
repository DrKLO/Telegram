package com.google.zxing.common.detector;

public final class MathUtils {

  private MathUtils() {
  }

  /**
   * Ends up being a bit faster than {@link Math#round(float)}. This merely rounds its
   * argument to the nearest int, where x.5 rounds up to x+1. Semantics of this shortcut
   * differ slightly from {@link Math#round(float)} in that half rounds down for negative
   * values. -2.5 rounds to -3, not -2. For purposes here it makes no difference.
   *
   * @param d real value to round
   * @return nearest {@code int}
   */
  public static int round(float d) {
    return (int) (d + (d < 0.0f ? -0.5f : 0.5f));
  }

  /**
   * @param aX point A x coordinate
   * @param aY point A y coordinate
   * @param bX point B x coordinate
   * @param bY point B y coordinate
   * @return Euclidean distance between points A and B
   */
  public static float distance(float aX, float aY, float bX, float bY) {
    double xDiff = aX - bX;
    double yDiff = aY - bY;
    return (float) Math.sqrt(xDiff * xDiff + yDiff * yDiff);
  }

  /**
   * @param aX point A x coordinate
   * @param aY point A y coordinate
   * @param bX point B x coordinate
   * @param bY point B y coordinate
   * @return Euclidean distance between points A and B
   */
  public static float distance(int aX, int aY, int bX, int bY) {
    double xDiff = aX - bX;
    double yDiff = aY - bY;
    return (float) Math.sqrt(xDiff * xDiff + yDiff * yDiff);
  }

  /**
   * @param array values to sum
   * @return sum of values in array
   */
  public static int sum(int[] array) {
    int count = 0;
    for (int a : array) {
      count += a;
    }
    return count;
  }

}
