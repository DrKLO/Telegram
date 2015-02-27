/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.SizeF;
import android.widget.FrameLayout;

public class PhotoFilterLinearBlurControl extends FrameLayout {

    private final static float LinearBlurInsetProximity = 20;
    private final static float LinearBlurMinimumFalloff = 0.1f;
    private final static float LinearBlurMinimumDifference = 0.02f;
    private final static float LinearBlurViewCenterInset = 30.0f;
    private final static float LinearBlurViewRadiusInset = 30.0f;

    private enum LinearBlurViewActiveControl {
        TGLinearBlurViewActiveControlNone,
        TGLinearBlurViewActiveControlCenter,
        TGLinearBlurViewActiveControlInnerRadius,
        TGLinearBlurViewActiveControlOuterRadius,
        TGLinearBlurViewActiveControlWholeArea,
        TGLinearBlurViewActiveControlRotation
    }

    private LinearBlurViewActiveControl activeControl;
    private PointF startCenterPoint = new PointF();
    private PointF startDistance = new PointF();
    private PointF startRadius = new PointF();
    private boolean isTracking;
    private SizeF actualAreaSize;
    private PointF centerPoint;
    private float falloff;
    private float size;
    private float angle;

    //@property (nonatomic, copy) void (^valueChanged)(CGPoint centerPoint, CGFloat falloff, CGFloat size, CGFloat angle);
    //@property (nonatomic, copy) void(^interactionEnded)(void);
    //UILongPressGestureRecognizer *_pressGestureRecognizer;
    //UIPanGestureRecognizer *_panGestureRecognizer;
    //UIPinchGestureRecognizer *_pinchGestureRecognizer;

    public PhotoFilterLinearBlurControl(Context context) {
        super(context);

        setWillNotDraw(false);

        centerPoint = new PointF(0.5f, 0.5f);
        falloff = 0.15f;
        size = 0.35f;

        /*_pressGestureRecognizer = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(handlePress:)];
        _pressGestureRecognizer.delegate = self;
        _pressGestureRecognizer.minimumPressDuration = 0.1f;
        [self addGestureRecognizer:_pressGestureRecognizer];

        _panGestureRecognizer = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(handlePan:)];
        _panGestureRecognizer.delegate = self;
        [self addGestureRecognizer:_panGestureRecognizer];

        _pinchGestureRecognizer = [[UIPinchGestureRecognizer alloc] initWithTarget:self action:@selector(handlePinch:)];
        _pinchGestureRecognizer.delegate = self;
        [self addGestureRecognizer:_pinchGestureRecognizer];*/
    }


    private void handlePress() {
        /*switch (gestureRecognizer.state) {
            case UIGestureRecognizerStateBegan:
                [self setSelected:true animated:true];
                break;

            case UIGestureRecognizerStateEnded:
            case UIGestureRecognizerStateCancelled:
            case UIGestureRecognizerStateFailed:
                [self setSelected:false animated:true];
                break;

            default:
                break;
        }*/
    }

    private void handlePan() {
        /*CGPoint location = [gestureRecognizer locationInView:self];
        CGPoint centerPoint = [self _actualCenterPoint];
        CGPoint delta = CGPointMake(location.x - centerPoint.x, location.y - centerPoint.y);
        CGFloat radialDistance = sqrtf(delta.x * delta.x + delta.y * delta.y);
        CGFloat distance = fabsf(delta.x * cosf(self.angle + (CGFloat)M_PI_2) + delta.y * sinf(self.angle + (CGFloat)M_PI_2));

        CGFloat shorterSide = (self.actualAreaSize.width > self.actualAreaSize.height) ? self.actualAreaSize.height : self.actualAreaSize.width;

        CGFloat innerRadius = shorterSide * self.falloff;
        CGFloat outerRadius = shorterSide * self.size;

        switch (gestureRecognizer.state) {
            case UIGestureRecognizerStateBegan:
            {
                bool close = fabsf(outerRadius - innerRadius) < TGLinearBlurInsetProximity;
                CGFloat innerRadiusOuterInset = close ? 0 : TGLinearBlurViewRadiusInset;
                CGFloat outerRadiusInnerInset = close ? 0 : TGLinearBlurViewRadiusInset;

                if (radialDistance < TGLinearBlurViewCenterInset)
                {
                    _activeControl = TGLinearBlurViewActiveControlCenter;
                    _startCenterPoint = centerPoint;
                }
                else if (distance > innerRadius - TGLinearBlurViewRadiusInset && distance < innerRadius + innerRadiusOuterInset)
                {
                    _activeControl = TGLinearBlurViewActiveControlInnerRadius;
                    _startDistance = distance;
                    _startRadius = innerRadius;
                }
                else if (distance > outerRadius - outerRadiusInnerInset && distance < outerRadius + TGLinearBlurViewRadiusInset)
                {
                    _activeControl = TGLinearBlurViewActiveControlOuterRadius;
                    _startDistance = distance;
                    _startRadius = outerRadius;
                }
                else if (distance <= innerRadius - TGLinearBlurViewRadiusInset || distance >= outerRadius + TGLinearBlurViewRadiusInset)
                {
                    _activeControl = TGLinearBlurViewActiveControlRotation;
                }

                [self setSelected:true animated:true];
            }
            break;

            case UIGestureRecognizerStateChanged:
            {
                switch (_activeControl)
                {
                    case TGLinearBlurViewActiveControlCenter:
                    {
                        CGPoint translation = [gestureRecognizer translationInView:self];

                        CGRect actualArea = CGRectMake((self.frame.size.width - self.actualAreaSize.width) / 2, (self.frame.size.height - self.actualAreaSize.height) / 2, self.actualAreaSize.width, self.actualAreaSize.height);

                        CGPoint newPoint = CGPointMake(MAX(CGRectGetMinX(actualArea), MIN(CGRectGetMaxX(actualArea), _startCenterPoint.x + translation.x)),
                                MAX(CGRectGetMinY(actualArea), MIN(CGRectGetMaxY(actualArea), _startCenterPoint.y + translation.y)));

                        CGPoint offset = CGPointMake(0, (self.actualAreaSize.width - self.actualAreaSize.height) / 2);
                        CGPoint actualPoint = CGPointMake(newPoint.x - actualArea.origin.x, newPoint.y - actualArea.origin.y);
                        self.centerPoint = CGPointMake((actualPoint.x + offset.x) / self.actualAreaSize.width, (actualPoint.y + offset.y) / self.actualAreaSize.width);
                    }
                    break;

                    case TGLinearBlurViewActiveControlInnerRadius:
                    {
                        CGFloat delta = distance - _startDistance;
                        self.falloff = MIN(MAX(TGLinearBlurMinimumFalloff, (_startRadius + delta) / shorterSide), self.size - TGLinearBlurMinimumDifference);
                    }
                    break;

                    case TGLinearBlurViewActiveControlOuterRadius:
                    {
                        CGFloat delta = distance - _startDistance;
                        self.size = MAX(self.falloff + TGLinearBlurMinimumDifference, (_startRadius + delta) / shorterSide);
                    }
                    break;

                    case TGLinearBlurViewActiveControlRotation:
                    {
                        CGPoint translation = [gestureRecognizer translationInView:self];
                        bool clockwise = false;

                        bool right = location.x > centerPoint.x;
                        bool bottom = location.y > centerPoint.y;

                        if (!right && !bottom)
                        {
                            if (fabsf(translation.y) > fabsf(translation.x))
                            {
                                if (translation.y < 0)
                                    clockwise = true;
                            }
                            else
                            {
                                if (translation.x > 0)
                                    clockwise = true;
                            }
                        }
                        else if (right && !bottom)
                        {
                            if (fabsf(translation.y) > fabsf(translation.x))
                            {
                                if (translation.y > 0)
                                    clockwise = true;
                            }
                            else
                            {
                                if (translation.x > 0)
                                    clockwise = true;
                            }
                        }
                        else if (right && bottom)
                        {
                            if (fabsf(translation.y) > fabsf(translation.x))
                            {
                                if (translation.y > 0)
                                    clockwise = true;
                            }
                            else
                            {
                                if (translation.x < 0)
                                    clockwise = true;
                            }
                        }
                        else
                        {
                            if (fabsf(translation.y) > fabsf(translation.x))
                            {
                                if (translation.y < 0)
                                    clockwise = true;
                            }
                            else
                            {
                                if (translation.x < 0)
                                    clockwise = true;
                            }
                        }

                        CGFloat delta = sqrtf(translation.x * translation.x + translation.y * translation.y);

                        CGFloat angleInDegrees = TGRadiansToDegrees(_angle);
                        CGFloat newAngleInDegrees = angleInDegrees + delta * (clockwise * 2 - 1) / (CGFloat)M_PI / 1.15f;

                        _angle = TGDegreesToRadians(newAngleInDegrees);

                        [gestureRecognizer setTranslation:CGPointZero inView:self];
                    }
                    break;

                    default:
                        break;
                }

                [self setNeedsDisplay];

                if (self.valueChanged != nil)
                    self.valueChanged(self.centerPoint, self.falloff, self.size, self.angle);
            }
            break;

            case UIGestureRecognizerStateEnded:
            case UIGestureRecognizerStateCancelled:
            case UIGestureRecognizerStateFailed:
            {
                _activeControl = TGLinearBlurViewActiveControlNone;

                [self setSelected:false animated:true];

                if (self.interactionEnded != nil)
                    self.interactionEnded();
            }
            break;

            default:
                break;
        }*/
    }

    private void handlePinch() {
        /*switch (gestureRecognizer.state) {
            case UIGestureRecognizerStateBegan: {
                _activeControl = TGLinearBlurViewActiveControlWholeArea;
                [self setSelected:true animated:true];
            }
            case UIGestureRecognizerStateChanged: {
                CGFloat scale = gestureRecognizer.scale;

                self.falloff = MAX(TGLinearBlurMinimumFalloff, self.falloff * scale);
                self.size = MAX(self.falloff + TGLinearBlurMinimumDifference, self.size * scale);

                gestureRecognizer.scale = 1.0f;

                [self setNeedsDisplay];

                if (self.valueChanged != nil)
                    self.valueChanged(self.centerPoint, self.falloff, self.size, self.angle);
            }
            break;

            case UIGestureRecognizerStateEnded: {
                _activeControl = TGLinearBlurViewActiveControlNone;
                [self setSelected:false animated:true];
            }
            break;

            case UIGestureRecognizerStateCancelled:
            case UIGestureRecognizerStateFailed: {
                _activeControl = TGLinearBlurViewActiveControlNone;
                [self setSelected:false animated:true];
            }
            break;

            default:
                break;
        }*/
    }

    /*- (BOOL)gestureRecognizerShouldBegin:(UIGestureRecognizer *)gestureRecognizer
    {
        if (gestureRecognizer == _pressGestureRecognizer || gestureRecognizer == _panGestureRecognizer)
        {
            CGPoint location = [gestureRecognizer locationInView:self];
            CGPoint centerPoint = [self _actualCenterPoint];
            CGPoint delta = CGPointMake(location.x - centerPoint.x, location.y - centerPoint.y);
            CGFloat radialDistance = sqrtf(delta.x * delta.x + delta.y * delta.y);
            CGFloat distance = fabsf(delta.x * cosf(self.angle + (CGFloat)M_PI_2) + delta.y * sinf(self.angle + (CGFloat)M_PI_2));

            CGFloat innerRadius = [self _actualInnerRadius];
            CGFloat outerRadius = [self _actualOuterRadius];

            bool close = fabsf(outerRadius - innerRadius) < TGLinearBlurInsetProximity;
            CGFloat innerRadiusOuterInset = close ? 0 : TGLinearBlurViewRadiusInset;
            CGFloat outerRadiusInnerInset = close ? 0 : TGLinearBlurViewRadiusInset;

            if (radialDistance < TGLinearBlurViewCenterInset && gestureRecognizer == _panGestureRecognizer)
                return true;
            else if (distance > innerRadius - TGLinearBlurViewRadiusInset && distance < innerRadius + innerRadiusOuterInset)
                return true;
            else if (distance > outerRadius - outerRadiusInnerInset && distance < outerRadius + TGLinearBlurViewRadiusInset)
                return true;
            else if ((distance <= innerRadius - TGLinearBlurViewRadiusInset) || distance >= outerRadius + TGLinearBlurViewRadiusInset)
                return true;

            return false;
        }

        return true;
    }

    - (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer
    {
        if (gestureRecognizer == _pressGestureRecognizer || otherGestureRecognizer == _pressGestureRecognizer)
            return true;

        return false;
    }*/

    private void setSelected(boolean selected, boolean animated) {
        /*if (animated) {
            [UIView animateWithDuration:0.16f delay:0.0f options:UIViewAnimationOptionBeginFromCurrentState animations:^
            {
                self.alpha = selected ? 0.6f : 1.0f;
            } completion:nil];
        } else {
            self.alpha = selected ? 0.6f : 1.0f;
        }*/
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        /*PointF centerPoint = getActualCenterPoint();
        float innerRadius = getActualInnerRadius();
        float outerRadius = getActualOuterRadius();

        CGContextTranslateCTM(context, centerPoint.x, centerPoint.y);
        CGContextRotateCTM(context, self.angle);

        CGContextSetFillColorWithColor(context, [UIColor whiteColor].CGColor);
        CGContextSetShadowWithColor(context, CGSizeZero, 2.5f, [UIColor colorWithWhite:0.0f alpha:0.3f].CGColor);

        float space = 6.0f;
        float length = 12.0f;
        float thickness = 1.5f;
        for (int i = 0; i < 30; i++) {
            CGContextAddRect(context, CGRectMake(i * (length + space), -innerRadius, length, thickness));
            CGContextAddRect(context, CGRectMake(-i * (length + space) - space - length, -innerRadius, length, thickness));

            CGContextAddRect(context, CGRectMake(i * (length + space), innerRadius, length, thickness));
            CGContextAddRect(context, CGRectMake(-i * (length + space) - space - length, innerRadius, length, thickness));
        }

        length = 6.0f;
        thickness = 1.5f;
        for (int i = 0; i < 64; i++) {
            CGContextAddRect(context, CGRectMake(i * (length + space), -outerRadius, length, thickness));
            CGContextAddRect(context, CGRectMake(-i * (length + space) - space - length, -outerRadius, length, thickness));

            CGContextAddRect(context, CGRectMake(i * (length + space), outerRadius, length, thickness));
            CGContextAddRect(context, CGRectMake(-i * (length + space) - space - length, outerRadius, length, thickness));
        }

        CGContextFillPath(context);

        CGContextFillEllipseInRect(context, CGRectMake(-16 / 2, - 16 / 2, 16, 16));*/
    }

    private PointF getActualCenterPoint() {
        RectF actualArea = new RectF((getWidth() - actualAreaSize.getWidth()) / 2, (getHeight() - actualAreaSize.getHeight()) / 2, actualAreaSize.getWidth(), actualAreaSize.getHeight());
        PointF offset = new PointF(0, (actualAreaSize.getWidth() - actualAreaSize.getHeight()) / 2);
        return new PointF(actualArea.left - offset.x + centerPoint.x * actualAreaSize.getWidth(), actualArea.top - offset.y + centerPoint.y * actualAreaSize.getWidth());
    }

    private float getActualInnerRadius() {
        float shorterSide = (actualAreaSize.getWidth() > actualAreaSize.getHeight()) ? actualAreaSize.getHeight() : actualAreaSize.getWidth();
        return shorterSide * falloff;
    }

    private float getActualOuterRadius() {
        float shorterSide = (actualAreaSize.getWidth() > actualAreaSize.getHeight()) ? actualAreaSize.getHeight() : actualAreaSize.getWidth();
        return shorterSide * size;
    }
}
