/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.opengl.Matrix;
import android.util.Pair;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;

/** Utility functions for working with matrices, vertices, and polygons. */
/* package */ final class MatrixUtils {
  /**
   * Contains the normal vectors of the clipping planes in homogeneous coordinates which
   * conveniently also double as origin vectors and parameters of the normal form of the planes ax +
   * by + cz = d.
   */
  private static final float[][] NDC_CUBE =
      new float[][] {
        new float[] {1, 0, 0, 1},
        new float[] {-1, 0, 0, 1},
        new float[] {0, 1, 0, 1},
        new float[] {0, -1, 0, 1},
        new float[] {0, 0, 1, 1},
        new float[] {0, 0, -1, 1}
      };

  /**
   * Returns a 4x4, column-major {@link Matrix} float array, from an input {@link
   * android.graphics.Matrix}.
   *
   * <p>This is useful for converting to the 4x4 column-major format commonly used in OpenGL.
   */
  public static float[] getGlMatrixArray(android.graphics.Matrix matrix) {
    float[] matrix3x3Array = new float[9];
    matrix.getValues(matrix3x3Array);
    float[] matrix4x4Array = getMatrix4x4Array(matrix3x3Array);

    // Transpose from row-major to column-major representations.
    float[] transposedMatrix4x4Array = new float[16];
    Matrix.transposeM(
        transposedMatrix4x4Array, /* mTransOffset= */ 0, matrix4x4Array, /* mOffset= */ 0);

    return transposedMatrix4x4Array;
  }

  /**
   * Returns a 4x4 matrix array containing the 3x3 matrix array's contents.
   *
   * <p>The 3x3 matrix array is expected to be in 2 dimensions, and the 4x4 matrix array is expected
   * to be in 3 dimensions. The output will have the third row/column's values be an identity
   * matrix's values, so that vertex transformations using this matrix will not affect the z axis.
   * <br>
   * Input format: [a, b, c, d, e, f, g, h, i] <br>
   * Output format: [a, b, 0, c, d, e, 0, f, 0, 0, 1, 0, g, h, 0, i]
   */
  private static float[] getMatrix4x4Array(float[] matrix3x3Array) {
    float[] matrix4x4Array = new float[16];
    matrix4x4Array[10] = 1;
    for (int inputRow = 0; inputRow < 3; inputRow++) {
      for (int inputColumn = 0; inputColumn < 3; inputColumn++) {
        int outputRow = (inputRow == 2) ? 3 : inputRow;
        int outputColumn = (inputColumn == 2) ? 3 : inputColumn;
        matrix4x4Array[outputRow * 4 + outputColumn] = matrix3x3Array[inputRow * 3 + inputColumn];
      }
    }
    return matrix4x4Array;
  }

  /**
   * Clips a convex polygon to normalized device coordinates (-1 to 1 on x, y, and z axes).
   *
   * <p>The input and output vertices are given in homogeneous coordinates (x,y,z,1) where the last
   * element must always be 1. To convert a general vector in homogeneous coordinates (xw,yw,zw,w)
   * to this form, simply divide all elements by w.
   *
   * @param polygonVertices The vertices in counter-clockwise order as 4 element vectors of
   *     homogeneous coordinates.
   * @return The vertices of the clipped polygon, in counter-clockwise order, or an empty list if
   *     the polygon doesn't intersect with the NDC range.
   */
  public static ImmutableList<float[]> clipConvexPolygonToNdcRange(
      ImmutableList<float[]> polygonVertices) {
    checkArgument(polygonVertices.size() >= 3, "A polygon must have at least 3 vertices.");

    // This is a 3D generalization of the Sutherland-Hodgman algorithm
    // https://en.wikipedia.org/wiki/Sutherland%E2%80%93Hodgman_algorithm
    // using a convex clipping volume (the NDC cube) instead of a convex clipping polygon to clip a
    // given subject polygon.
    // For this algorithm, the subject polygon doesn't necessarily need to be convex. But since we
    // require that it is convex, we can assume that the clipped result is a single connected
    // convex polygon.
    ImmutableList.Builder<float[]> outputVertices =
        new ImmutableList.Builder<float[]>().addAll(polygonVertices);
    for (float[] clippingPlane : NDC_CUBE) {
      ImmutableList<float[]> inputVertices = outputVertices.build();
      outputVertices = new ImmutableList.Builder<>();

      for (int i = 0; i < inputVertices.size(); i++) {
        float[] currentVertex = inputVertices.get(i);
        float[] previousVertex =
            inputVertices.get((inputVertices.size() + i - 1) % inputVertices.size());
        if (isInsideClippingHalfSpace(currentVertex, clippingPlane)) {
          if (!isInsideClippingHalfSpace(previousVertex, clippingPlane)) {
            float[] intersectionPoint =
                computeIntersectionPoint(
                    clippingPlane, clippingPlane, previousVertex, currentVertex);
            if (!Arrays.equals(currentVertex, intersectionPoint)) {
              outputVertices.add(intersectionPoint);
            }
          }
          outputVertices.add(currentVertex);
        } else if (isInsideClippingHalfSpace(previousVertex, clippingPlane)) {
          float[] intersection =
              computeIntersectionPoint(clippingPlane, clippingPlane, previousVertex, currentVertex);
          if (!Arrays.equals(previousVertex, intersection)) {
            outputVertices.add(intersection);
          }
        }
      }
    }

    return outputVertices.build();
  }

  /**
   * Returns whether the given point is inside the half-space bounded by the clipping plane and
   * facing away from its normal vector.
   *
   * <p>The clipping plane has the form ax + by + cz = d.
   *
   * @param point A point in homogeneous coordinates (x,y,z,1).
   * @param clippingPlane The parameters (a,b,c,d) of the plane's normal form.
   * @return Whether the point is on the inside of the plane.
   */
  private static boolean isInsideClippingHalfSpace(float[] point, float[] clippingPlane) {
    checkArgument(clippingPlane.length == 4, "Expecting 4 plane parameters");

    return clippingPlane[0] * point[0] + clippingPlane[1] * point[1] + clippingPlane[2] * point[2]
        <= clippingPlane[3];
  }

  /**
   * Returns the intersection point of the given line and plane.
   *
   * <p>This method may only be called if such an intersection exists.
   *
   * <p>The plane has the form ax + by + cz = d.
   *
   * <p>The points are given in homogeneous coordinates (x,y,z,1).
   *
   * @param planePoint A point on the plane.
   * @param planeParameters The parameters of the plane's normal form.
   * @param linePoint1 A point on the line.
   * @param linePoint2 Another point on the line.
   * @return The point of intersection.
   */
  private static float[] computeIntersectionPoint(
      float[] planePoint, float[] planeParameters, float[] linePoint1, float[] linePoint2) {
    checkArgument(planeParameters.length == 4, "Expecting 4 plane parameters");

    // See https://en.wikipedia.org/wiki/Line%E2%80%93plane_intersection#Algebraic_form for the
    // derivation of this solution formula.
    float lineEquationParameter =
        ((planePoint[0] - linePoint1[0]) * planeParameters[0]
                + (planePoint[1] - linePoint1[1]) * planeParameters[1]
                + (planePoint[2] - linePoint1[2]) * planeParameters[2])
            / ((linePoint2[0] - linePoint1[0]) * planeParameters[0]
                + (linePoint2[1] - linePoint1[1]) * planeParameters[1]
                + (linePoint2[2] - linePoint1[2]) * planeParameters[2]);
    float x = linePoint1[0] + (linePoint2[0] - linePoint1[0]) * lineEquationParameter;
    float y = linePoint1[1] + (linePoint2[1] - linePoint1[1]) * lineEquationParameter;
    float z = linePoint1[2] + (linePoint2[2] - linePoint1[2]) * lineEquationParameter;
    return new float[] {x, y, z, 1};
  }

  /**
   * Applies a transformation matrix to each point.
   *
   * @param transformationMatrix The 4x4 transformation matrix.
   * @param points The points as 4 element vectors of homogeneous coordinates (x,y,z,1).
   * @return The transformed points as 4 element vectors of homogeneous coordinates (x,y,z,1).
   */
  public static ImmutableList<float[]> transformPoints(
      float[] transformationMatrix, ImmutableList<float[]> points) {
    ImmutableList.Builder<float[]> transformedPoints = new ImmutableList.Builder<>();
    for (int i = 0; i < points.size(); i++) {
      float[] transformedPoint = new float[4];
      Matrix.multiplyMV(
          transformedPoint,
          /* resultVecOffset= */ 0,
          transformationMatrix,
          /* lhsMatOffset= */ 0,
          points.get(i),
          /* rhsVecOffset= */ 0);
      // Multiplication result is in homogeneous coordinates (xw,yw,zw,w) with any w. Divide by w
      // to get (x,y,z,1).
      transformedPoint[0] /= transformedPoint[3];
      transformedPoint[1] /= transformedPoint[3];
      transformedPoint[2] /= transformedPoint[3];
      transformedPoint[3] = 1;
      transformedPoints.add(transformedPoint);
    }
    return transformedPoints.build();
  }

  /**
   * Returns the output frame size after applying the given list of {@link GlMatrixTransformation
   * GlMatrixTransformations} to an input frame with the given size.
   */
  public static Pair<Integer, Integer> configureAndGetOutputSize(
      int inputWidth,
      int inputHeight,
      ImmutableList<GlMatrixTransformation> matrixTransformations) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    Pair<Integer, Integer> outputSize = Pair.create(inputWidth, inputHeight);
    for (int i = 0; i < matrixTransformations.size(); i++) {
      outputSize = matrixTransformations.get(i).configure(outputSize.first, outputSize.second);
    }

    return outputSize;
  }

  /** Class only contains static methods. */
  private MatrixUtils() {}
}
