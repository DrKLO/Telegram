/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/util/threshold_curve.h"

#include <memory>

#include "test/gtest.h"

// A threshold curve divides 2D space into three domains - below, on and above
// the threshold curve.
// The curve is defined by two points. Those points, P1 and P2, are ordered so
// that (P1.x <= P2.x && P1.y >= P2.y).
// The part of the curve which is between the two points is hereon referred
// to as the "segment".
// A "ray" extends from P1 directly upwards into infinity; that's the "vertical
// ray". Likewise, a "horizontal ray" extends from P2 directly rightwards.
//
//  ^   |                         //
//  |   | vertical ray            //
//  |   |                         //
//  |   |                         //
//  | P1|                         //
//  |    \                        //
//  |     \ segment               //
//  |      \                      //
//  |       \    horizontal ray   //
//  |     P2 ------------------   //
//  *---------------------------> //

namespace webrtc {

namespace {
enum RelativePosition { kBelow, kOn, kAbove };

void CheckRelativePosition(const ThresholdCurve& curve,
                           ThresholdCurve::Point point,
                           RelativePosition pos) {
  RTC_CHECK(pos == kBelow || pos == kOn || pos == kAbove);

  EXPECT_EQ(pos == kBelow, curve.IsBelowCurve(point));
  EXPECT_EQ(pos == kAbove, curve.IsAboveCurve(point));
}
}  // namespace

// Test that the curve correctly reports the below/above position of points,
// when the curve is a "normal" one - P1 and P2 are different in both their
// X and Y values.
TEST(ThresholdCurveTest, PointPositionToCommonCurve) {
  // The points (P1-P2) define the curve.           //
  // All other points are above/below/on the curve. //
  //                                                //
  //  ^                                             //
  //  |     |                                       //
  //  |  A  F    J  R   V                           //
  //  |     |                                       //
  //  |  B  P1   K  S   W                           //
  //  |      \                                      //
  //  |       \                                     //
  //  |        \ L                                  //
  //  |         \                                   //
  //  |  C  G    M  T   X                           //
  //  |           \                                 //
  //  |          N \                                //
  //  |             \                               //
  //  |  D  H    O  P2--Y----------------           //
  //  |  E  I    Q  U   Z                           //
  //  *---------------------------------->          //
  constexpr ThresholdCurve::Point p1{1000, 2000};
  constexpr ThresholdCurve::Point p2{2000, 1000};

  RTC_CHECK_GT((p1.x + p2.x) / 2, p1.x);
  RTC_CHECK_LT((p1.x + p2.x) / 2, p2.x);
  RTC_CHECK_LT((p1.y + p2.y) / 2, p1.y);
  RTC_CHECK_GT((p1.y + p2.y) / 2, p2.y);

  const ThresholdCurve curve(p1, p2);

  {
    // All cases where the point lies to the left of P1.
    constexpr float x = p1.x - 1;
    CheckRelativePosition(curve, {x, p1.y + 1}, kBelow);           // A
    CheckRelativePosition(curve, {x, p1.y + 0}, kBelow);           // B
    CheckRelativePosition(curve, {x, (p1.y + p2.y) / 2}, kBelow);  // C
    CheckRelativePosition(curve, {x, p2.y + 0}, kBelow);           // D
    CheckRelativePosition(curve, {x, p2.y - 1}, kBelow);           // E
  }

  {
    // All cases where the point has the same x-value as P1.
    constexpr float x = p1.x;
    CheckRelativePosition(curve, {x, p1.y + 1}, kOn);              // F
    CheckRelativePosition(curve, {x, p1.y + 0}, kOn);              // P1
    CheckRelativePosition(curve, {x, (p1.y + p2.y) / 2}, kBelow);  // G
    CheckRelativePosition(curve, {x, p2.y + 0}, kBelow);           // H
    CheckRelativePosition(curve, {x, p2.y - 1}, kBelow);           // I
  }

  {
    // To make sure we're really covering all of the cases, make sure that P1
    // and P2 were chosen so that L would really be below K, and O would really
    // be below N. (This would not hold if the Y values are too close together.)
    RTC_CHECK_LT(((p1.y + p2.y) / 2) + 1, p1.y);
    RTC_CHECK_LT(p2.y, ((p1.y + p2.y) / 2) - 1);

    // All cases where the point's x-value is between P1 and P2.
    constexpr float x = (p1.x + p2.x) / 2;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);                 // J
    CheckRelativePosition(curve, {x, p1.y + 0}, kAbove);                 // K
    CheckRelativePosition(curve, {x, ((p1.y + p2.y) / 2) + 1}, kAbove);  // L
    CheckRelativePosition(curve, {x, (p1.y + p2.y) / 2}, kOn);           // M
    CheckRelativePosition(curve, {x, ((p1.y + p2.y) / 2) - 1}, kBelow);  // N
    CheckRelativePosition(curve, {x, p2.y + 0}, kBelow);                 // O
    CheckRelativePosition(curve, {x, p2.y - 1}, kBelow);                 // Q
  }

  {
    // All cases where the point has the same x-value as P2.
    constexpr float x = p2.x;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);           // R
    CheckRelativePosition(curve, {x, p1.y + 0}, kAbove);           // S
    CheckRelativePosition(curve, {x, (p1.y + p2.y) / 2}, kAbove);  // T
    CheckRelativePosition(curve, {x, p2.y + 0}, kOn);              // P2
    CheckRelativePosition(curve, {x, p2.y - 1}, kBelow);           // U
  }

  {
    // All cases where the point lies to the right of P2.
    constexpr float x = p2.x + 1;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);           // V
    CheckRelativePosition(curve, {x, p1.y + 0}, kAbove);           // W
    CheckRelativePosition(curve, {x, (p1.y + p2.y) / 2}, kAbove);  // X
    CheckRelativePosition(curve, {x, p2.y + 0}, kOn);              // Y
    CheckRelativePosition(curve, {x, p2.y - 1}, kBelow);           // Z
  }
}

// Test that the curve correctly reports the below/above position of points,
// when the curve is defined by two points with the same Y value.
TEST(ThresholdCurveTest, PointPositionToCurveWithHorizaontalSegment) {
  // The points (P1-P2) define the curve.
  // All other points are above/below/on the curve.
  //
  //  ^
  //  |    |
  //  |    |
  //  | A  D   F  I  K
  //  |    |
  //  |    |
  //  | B  P1--G--P2-L--
  //  | C  E   H  J  M
  //  *------------------>

  constexpr ThresholdCurve::Point p1{100, 200};
  constexpr ThresholdCurve::Point p2{p1.x + 1, p1.y};

  RTC_CHECK_GT((p1.x + p2.x) / 2, p1.x);
  RTC_CHECK_LT((p1.x + p2.x) / 2, p2.x);

  const ThresholdCurve curve(p1, p2);

  {
    // All cases where the point lies to the left of P1.
    constexpr float x = p1.x - 1;
    CheckRelativePosition(curve, {x, p1.y + 1}, kBelow);  // A
    CheckRelativePosition(curve, {x, p1.y + 0}, kBelow);  // B
    CheckRelativePosition(curve, {x, p1.y - 1}, kBelow);  // C
  }

  {
    // All cases where the point has the same x-value as P1.
    constexpr float x = p1.x;
    CheckRelativePosition(curve, {x, p1.y + 1}, kOn);     // D
    CheckRelativePosition(curve, {x, p1.y + 0}, kOn);     // P1
    CheckRelativePosition(curve, {x, p1.y - 1}, kBelow);  // E
  }

  {
    // All cases where the point's x-value is between P1 and P2.
    constexpr float x = (p1.x + p2.x) / 2;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);  // F
    CheckRelativePosition(curve, {x, p1.y + 0}, kOn);     // G
    CheckRelativePosition(curve, {x, p1.y - 1}, kBelow);  // H
  }

  {
    // All cases where the point has the same x-value as P2.
    constexpr float x = p2.x;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);  // I
    CheckRelativePosition(curve, {x, p1.y + 0}, kOn);     // P2
    CheckRelativePosition(curve, {x, p1.y - 1}, kBelow);  // J
  }

  {
    // All cases where the point lies to the right of P2.
    constexpr float x = p2.x + 1;
    CheckRelativePosition(curve, {x, p1.y + 1}, kAbove);  // K
    CheckRelativePosition(curve, {x, p1.y + 0}, kOn);     // L
    CheckRelativePosition(curve, {x, p1.y - 1}, kBelow);  // M
  }
}

// Test that the curve correctly reports the below/above position of points,
// when the curve is defined by two points with the same X value.
TEST(ThresholdCurveTest, PointPositionToCurveWithVerticalSegment) {
  // The points (P1-P2) define the curve.
  // All other points are above/below/on the curve.
  //
  //  ^
  //  |    |
  //  | A  B   C
  //  |    |
  //  | D  P1  E
  //  |    |
  //  | F  G   H
  //  |    |
  //  | I  P2--J------
  //  | K  L   M
  //  *------------------>

  constexpr ThresholdCurve::Point p1{100, 200};
  constexpr ThresholdCurve::Point p2{p1.x, p1.y - 1};

  constexpr float left = p1.x - 1;
  constexpr float on = p1.x;
  constexpr float right = p1.x + 1;

  RTC_CHECK_LT((p1.y + p2.y) / 2, p1.y);
  RTC_CHECK_GT((p1.y + p2.y) / 2, p2.y);

  const ThresholdCurve curve(p1, p2);

  {
    // All cases where the point lies above P1.
    constexpr float y = p1.y + 1;
    CheckRelativePosition(curve, {left, y}, kBelow);   // A
    CheckRelativePosition(curve, {on, y}, kOn);        // B
    CheckRelativePosition(curve, {right, y}, kAbove);  // C
  }

  {
    // All cases where the point has the same y-value as P1.
    constexpr float y = p1.y;
    CheckRelativePosition(curve, {left, y}, kBelow);   // D
    CheckRelativePosition(curve, {on, y}, kOn);        // P1
    CheckRelativePosition(curve, {right, y}, kAbove);  // E
  }

  {
    // All cases where the point's y-value is between P1 and P2.
    constexpr float y = (p1.y + p2.y) / 2;
    CheckRelativePosition(curve, {left, y}, kBelow);   // F
    CheckRelativePosition(curve, {on, y}, kOn);        // G
    CheckRelativePosition(curve, {right, y}, kAbove);  // H
  }

  {
    // All cases where the point has the same y-value as P2.
    constexpr float y = p2.y;
    CheckRelativePosition(curve, {left, y}, kBelow);  // I
    CheckRelativePosition(curve, {on, y}, kOn);       // P2
    CheckRelativePosition(curve, {right, y}, kOn);    // J
  }

  {
    // All cases where the point lies below P2.
    constexpr float y = p2.y - 1;
    CheckRelativePosition(curve, {left, y}, kBelow);   // K
    CheckRelativePosition(curve, {on, y}, kBelow);     // L
    CheckRelativePosition(curve, {right, y}, kBelow);  // M
  }
}

// Test that the curve correctly reports the below/above position of points,
// when the curve is defined by two points which are identical.
TEST(ThresholdCurveTest, PointPositionCurveWithNullSegment) {
  // The points (P1-P2) define the curve.
  // All other points are above/below/on the curve.
  //
  //  ^
  //  |    |
  //  | A  D   F
  //  |    |
  //  | B  P---G------
  //  | C  E   H
  //  *------------------>

  constexpr ThresholdCurve::Point p{100, 200};

  const ThresholdCurve curve(p, p);

  {
    // All cases where the point lies to the left of P.
    constexpr float x = p.x - 1;
    CheckRelativePosition(curve, {x, p.y + 1}, kBelow);  // A
    CheckRelativePosition(curve, {x, p.y + 0}, kBelow);  // B
    CheckRelativePosition(curve, {x, p.y - 1}, kBelow);  // C
  }

  {
    // All cases where the point has the same x-value as P.
    constexpr float x = p.x + 0;
    CheckRelativePosition(curve, {x, p.y + 1}, kOn);     // D
    CheckRelativePosition(curve, {x, p.y + 0}, kOn);     // P
    CheckRelativePosition(curve, {x, p.y - 1}, kBelow);  // E
  }

  {
    // All cases where the point lies to the right of P.
    constexpr float x = p.x + 1;
    CheckRelativePosition(curve, {x, p.y + 1}, kAbove);  // F
    CheckRelativePosition(curve, {x, p.y + 0}, kOn);     // G
    CheckRelativePosition(curve, {x, p.y - 1}, kBelow);  // H
  }
}

// Test that the relative position of two curves is computed correctly when
// the two curves have the same projection on the X-axis.
TEST(ThresholdCurveTest, TwoCurvesSegmentHasSameProjectionAxisX) {
  //  ^                        //
  //  | C1 + C2                //
  //  |  |                     //
  //  |  |\                    //
  //  |  | \                   //
  //  |   \ \                  //
  //  |    \ \                 //
  //  |     \ \                //
  //  |      \ -------- C2     //
  //  |       --------- C1     //
  //  *--------------------->  //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  // Same x-values, but higher on Y. (Can be parallel, but doesn't have to be.)
  constexpr ThresholdCurve::Point c2_left{c1_left.x, c1_left.y + 20};
  constexpr ThresholdCurve::Point c2_right{c1_right.x, c1_right.y + 10};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_TRUE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the higher curve's projection on the X-axis is a strict subset of the
// lower curve's projection on the X-axis (on both ends).
TEST(ThresholdCurveTest, TwoCurvesSegmentOfHigherSubsetProjectionAxisX) {
  //  ^                       //
  //  | C1    C2              //
  //  |  |    |               //
  //  |  |    |               //
  //  |   \   |               //
  //  |    \  |               //
  //  |     \ \               //
  //  |      \ \              //
  //  |       \ --------- C2  //
  //  |        \              //
  //  |         \             //
  //  |          ---------C1  //
  //  *---------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  constexpr ThresholdCurve::Point c2_left{6, 11};
  constexpr ThresholdCurve::Point c2_right{9, 7};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_TRUE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the higher curve's right point is above lower curve's horizontal ray (meaning
// the higher curve's projection on the X-axis extends further right than
// the lower curve's).
TEST(ThresholdCurveTest,
     TwoCurvesRightPointOfHigherCurveAboveHorizontalRayOfLower) {
  //  ^                        //
  //  | C1 + C2                //
  //  |  |                     //
  //  |  |\                    //
  //  |  | \                   //
  //  |  |  \                  //
  //  |  |   \                 //
  //  |  |    \                //
  //  |   \    \               //
  //  |    \    \              //
  //  |     \    \             //
  //  |      \    ----- C2     //
  //  |       --------- C1     //
  //  *--------------------->  //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  constexpr ThresholdCurve::Point c2_left{c1_left.x, c1_left.y + 1};
  constexpr ThresholdCurve::Point c2_right{c1_right.x + 1, c1_right.y + 1};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_TRUE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the higher curve's points are on the lower curve's rays (left point on the
// veritcal ray, right point on the horizontal ray).
TEST(ThresholdCurveTest, TwoCurvesPointsOfHigherOnRaysOfLower) {
  //  ^
  //  | C1 + C2               //
  //  |  |                    //
  //  |  |\                   //
  //  |  | \                  //
  //  |   \ \                 //
  //  |    \ \                //
  //  |     \ \               //
  //  |      \ \              //
  //  |       ----- C1 + C2   //
  //  *---------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  // Same x-values, but one of the points is higher on Y (the other isn't).
  constexpr ThresholdCurve::Point c2_left{c1_left.x, c1_left.y + 2};
  constexpr ThresholdCurve::Point c2_right{c1_right.x + 3, c1_right.y};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_TRUE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the second curve's segment intersects the first curve's vertical ray.
TEST(ThresholdCurveTest, SecondCurveCrossesVerticalRayOfFirstCurve) {
  //  ^                       //
  //  | C2 C1                 //
  //  |  | |                  //
  //  |   \|                  //
  //  |    |                  //
  //  |    |\                 //
  //  |    | \                //
  //  |     \ \               //
  //  |      \ \              //
  //  |       \ \             //
  //  |        \ ------- C2   //
  //  |         -------- C1   //
  //  *---------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  constexpr ThresholdCurve::Point c2_left{c1_left.x - 1, c1_left.y + 1};
  constexpr ThresholdCurve::Point c2_right{c1_right.x, c1_right.y + 1};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_FALSE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the second curve's segment intersects the first curve's horizontal ray.
TEST(ThresholdCurveTest, SecondCurveCrossesHorizontalRayOfFirstCurve) {
  //  ^                      //
  //  | C1 +  C2             //
  //  |  |                   //
  //  |  |\                  //
  //  |  \ \                 //
  //  |   \ \                //
  //  |    \ \               //
  //  |     \ \              //
  //  |      ----------- C1  //
  //  |         \            //
  //  |          ------- C2  //
  //  *--------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  constexpr ThresholdCurve::Point c2_left{c1_left.x, c1_left.y + 1};
  constexpr ThresholdCurve::Point c2_right{c1_right.x + 2, c1_right.y - 1};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_FALSE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// the second curve's segment intersects the first curve's segment.
TEST(ThresholdCurveTest, TwoCurvesWithCrossingSegments) {
  //  ^                           //
  //  | C2 C1                     //
  //  | |  |                      //
  //  | |  |                      //
  //  | |  \                      //
  //  | |   \                     //
  //  |  -_  \                    //
  //  |    -_ \                   //
  //  |      -_\                  //
  //  |        -_                 //
  //  |          \-_              //
  //  |           \ ---------- C2 //
  //  |            ----------- C1 //
  //  |                           //
  //  |                           //
  //  *-------------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_right);

  constexpr ThresholdCurve::Point c2_left{4, 9};
  constexpr ThresholdCurve::Point c2_right{10, 6};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  // The test is structured so that the two curves intersect at (8, 7).
  RTC_CHECK(!c1_curve.IsAboveCurve({8, 7}));
  RTC_CHECK(!c1_curve.IsBelowCurve({8, 7}));
  RTC_CHECK(!c2_curve.IsAboveCurve({8, 7}));
  RTC_CHECK(!c2_curve.IsBelowCurve({8, 7}));

  EXPECT_FALSE(c1_curve <= c2_curve);
  EXPECT_FALSE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// both curves are identical.
TEST(ThresholdCurveTest, IdenticalCurves) {
  //  ^                       //
  //  |  C1 + C2              //
  //  |  |                    //
  //  |  |                    //
  //  |   \                   //
  //  |    \                  //
  //  |     \                 //
  //  |      ------- C1 + C2  //
  //  *---------------------> //

  constexpr ThresholdCurve::Point left{5, 10};
  constexpr ThresholdCurve::Point right{10, 5};

  const ThresholdCurve c1_curve(left, right);
  const ThresholdCurve c2_curve(left, right);

  EXPECT_TRUE(c1_curve <= c2_curve);
  EXPECT_TRUE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// they are "nearly identical" - the first curve's segment is contained within
// the second curve's segment, but the second curve's segment extends further
// to the left (which also produces separate vertical rays for the curves).
TEST(ThresholdCurveTest, NearlyIdenticalCurvesSecondContinuesOnOtherLeftSide) {
  //  ^                       //
  //  | C2 C1                 //
  //  |  | |                  //
  //  |  | |                  //
  //  |   \|                  //
  //  |    |                  //
  //  |     \                 //
  //  |      \                //
  //  |       \               //
  //  |        ----- C1 + C2  //
  //  *---------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_left);

  constexpr ThresholdCurve::Point c2_left{c1_left.x - 1, c1_left.y + 1};
  constexpr ThresholdCurve::Point c2_right = c1_right;
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_FALSE(c1_curve <= c2_curve);
  EXPECT_TRUE(c2_curve <= c1_curve);
}

// Test that the relative position of two curves is computed correctly when
// they are "nearly identical" - the first curve's segment is contained within
// the second curve's segment, but the second curve's segment extends further
// to the right (which also produces separate horizontal rays for the curves).
TEST(ThresholdCurveTest, NearlyIdenticalCurvesSecondContinuesOnOtherRightSide) {
  //  ^                       //
  //  | C1 + C2               //
  //  |  |                    //
  //  |  |                    //
  //  |   \                   //
  //  |    \                  //
  //  |     \                 //
  //  |      \----------- C1  //
  //  |       \               //
  //  |        ---------- C2  //
  //  *---------------------> //

  constexpr ThresholdCurve::Point c1_left{5, 10};
  constexpr ThresholdCurve::Point c1_right{10, 5};
  const ThresholdCurve c1_curve(c1_left, c1_left);

  constexpr ThresholdCurve::Point c2_left = c1_left;
  constexpr ThresholdCurve::Point c2_right{c1_right.x + 1, c1_right.y - 1};
  const ThresholdCurve c2_curve(c2_left, c2_right);

  EXPECT_FALSE(c1_curve <= c2_curve);
  EXPECT_TRUE(c2_curve <= c1_curve);
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
// The higher-left point must be given as the first point, and the lower-right
// point must be given as the second.
// This necessarily produces a non-positive slope.
TEST(ThresholdCurveDeathTest, WrongOrderPoints) {
  std::unique_ptr<ThresholdCurve> curve;
  constexpr ThresholdCurve::Point left{5, 10};
  constexpr ThresholdCurve::Point right{10, 5};
  EXPECT_DEATH(curve.reset(new ThresholdCurve(right, left)), "");
}
#endif

}  // namespace webrtc
