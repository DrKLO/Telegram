package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import android.os.SystemClock;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ArticleViewer;

import java.util.ArrayList;

public class LinkSpanDrawable<S extends CharacterStyle> {

    private int cornerRadius;
    private int color;
    private Paint mSelectionPaint, mRipplePaint;
    private int mSelectionAlpha, mRippleAlpha;

    private static final ArrayList<LinkPath> pathCache = new ArrayList<>();
    private final ArrayList<LinkPath> mPathes = new ArrayList<>();
    private int mPathesCount = 0;

    private final S mSpan;
    private final Theme.ResourcesProvider mResourcesProvider;
    private final float mTouchX;
    private final float mTouchY;
    private final Path circlePath = new Path();

    private Rect mBounds;
    private float mMaxRadius;
    private long mStart = -1;
    private long mReleaseStart = -1;
    private final long mDuration;
    private final long mLongPressDuration;
    private final boolean mSupportsLongPress;
    private static final long mReleaseDelay = 75;
    private static final long mReleaseDuration = 100;

    private final float selectionAlpha = 0.2f;
    private final float rippleAlpha = 0.8f;

    public LinkSpanDrawable(S span, Theme.ResourcesProvider resourcesProvider, float touchX, float touchY) {
        this(span, resourcesProvider, touchX, touchY, true);
    }

    public LinkSpanDrawable(S span, Theme.ResourcesProvider resourcesProvider, float touchX, float touchY, boolean supportsLongPress) {
        mSpan = span;
        mResourcesProvider = resourcesProvider;
        setColor(getThemedColor(Theme.key_chat_linkSelectBackground));
        mTouchX = touchX;
        mTouchY = touchY;
        final long tapTimeout = ViewConfiguration.getTapTimeout();
        mLongPressDuration = ViewConfiguration.getLongPressTimeout();
        mDuration = (long) Math.min(tapTimeout * 1.8f, mLongPressDuration * 0.8f);
        mSupportsLongPress = false;
    }

    public void setColor(int color) {
        this.color = color;
        if (mSelectionPaint != null) {
            mSelectionPaint.setColor(color);
            mSelectionAlpha = mSelectionPaint.getAlpha();
        }
        if (mRipplePaint != null) {
            mRipplePaint.setColor(color);
            mRippleAlpha = mRipplePaint.getAlpha();
        }
    }

    public void release() {
        mReleaseStart = Math.max(mStart + mDuration, SystemClock.elapsedRealtime());
    }

    public LinkPath obtainNewPath() {
        LinkPath linkPath;
        if (!pathCache.isEmpty()) {
            linkPath = pathCache.remove(0);
        } else {
            linkPath = new LinkPath(true);
        }
        linkPath.reset();
        mPathes.add(linkPath);
        mPathesCount = mPathes.size();
        return linkPath;
    }

    public void reset() {
        if (mPathes.isEmpty()) {
            return;
        }
        pathCache.addAll(mPathes);
        mPathes.clear();
        mPathesCount = 0;
    }

    public S getSpan() {
        return mSpan;
    }

    public boolean draw(Canvas canvas) {
        boolean cornerRadiusUpdate = cornerRadius != AndroidUtilities.dp(4);
        if (mSelectionPaint == null || cornerRadiusUpdate) {
            mSelectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mSelectionPaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mSelectionPaint.setColor(color);
            mSelectionAlpha = mSelectionPaint.getAlpha();
            mSelectionPaint.setPathEffect(new CornerPathEffect(cornerRadius = AndroidUtilities.dp(4)));
        }
        if (mRipplePaint == null || cornerRadiusUpdate) {
            mRipplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mRipplePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mRipplePaint.setColor(color);
            mRippleAlpha = mRipplePaint.getAlpha();
            mRipplePaint.setPathEffect(new CornerPathEffect(cornerRadius = AndroidUtilities.dp(4)));
        }
        if (mBounds == null && mPathesCount > 0) {
            mPathes.get(0).computeBounds(AndroidUtilities.rectTmp, false);
            mBounds = new Rect(
                (int) AndroidUtilities.rectTmp.left,
                (int) AndroidUtilities.rectTmp.top,
                (int) AndroidUtilities.rectTmp.right,
                (int) AndroidUtilities.rectTmp.bottom
            );
            for (int i = 1; i < mPathesCount; ++i) {
                mPathes.get(i).computeBounds(AndroidUtilities.rectTmp, false);
                mBounds.left = Math.min(mBounds.left, (int) AndroidUtilities.rectTmp.left);
                mBounds.top = Math.min(mBounds.top, (int) AndroidUtilities.rectTmp.top);
                mBounds.right = Math.max(mBounds.right, (int) AndroidUtilities.rectTmp.right);
                mBounds.bottom = Math.max(mBounds.bottom, (int) AndroidUtilities.rectTmp.bottom);
            }
            mMaxRadius = (float) Math.sqrt(
                Math.max(
                    Math.max(
                        Math.pow(mBounds.left - mTouchX, 2) + Math.pow(mBounds.top - mTouchY, 2),
                        Math.pow(mBounds.right - mTouchX, 2) + Math.pow(mBounds.top - mTouchY, 2)
                    ),
                    Math.max(
                        Math.pow(mBounds.left - mTouchX, 2) + Math.pow(mBounds.bottom - mTouchY, 2),
                        Math.pow(mBounds.right - mTouchX, 2) + Math.pow(mBounds.bottom - mTouchY, 2)
                    )
                )
            );
        }

        final long now = SystemClock.elapsedRealtime();
        if (mStart < 0) {
            mStart = now;
        }
        float pressT = CubicBezierInterpolator.DEFAULT.getInterpolation(Math.min(1, (now - mStart) / (float) mDuration)),
              releaseT = mReleaseStart < 0 ? 0 : Math.min(1, Math.max(0, (now - mReleaseDelay - mReleaseStart) / (float) mReleaseDuration));
        float longPress;
        if (mSupportsLongPress) {
            longPress = Math.max(0, (now - mStart - mDuration * 2) / (float) (mLongPressDuration - mDuration * 2));
            if (longPress > 1f) {
                longPress = 1f - ((now - mStart - mLongPressDuration) / (float) mDuration);
            } else {
                longPress *= .5f;
            }
            longPress *= (1f - releaseT);
        } else {
            longPress = 1f;
        }

        mSelectionPaint.setAlpha((int) (mSelectionAlpha * selectionAlpha * Math.min(1, pressT * 5f) * (1f - releaseT)));
        mSelectionPaint.setStrokeWidth(Math.min(1, 1f - longPress) * AndroidUtilities.dp(5));
        for (int i = 0; i < mPathesCount; ++i) {
            canvas.drawPath(mPathes.get(i), mSelectionPaint);
        }

        mRipplePaint.setAlpha((int) (mRippleAlpha * rippleAlpha * (1f - releaseT)));
        mRipplePaint.setStrokeWidth(Math.min(1, 1f - longPress) * AndroidUtilities.dp(5));
        if (pressT < 1f) {
            float r = pressT * mMaxRadius;
            canvas.save();
            circlePath.reset();
            circlePath.addCircle(mTouchX, mTouchY, r, Path.Direction.CW);
            canvas.clipPath(circlePath);
            for (int i = 0; i < mPathesCount; ++i) {
                canvas.drawPath(mPathes.get(i), mRipplePaint);
            }
            canvas.restore();
        } else {
            for (int i = 0; i < mPathesCount; ++i) {
                canvas.drawPath(mPathes.get(i), mRipplePaint);
            }
        }

        return pressT < 1f || mReleaseStart >= 0 || (mSupportsLongPress && now - mStart < mLongPressDuration + mDuration);
    }

    private int getThemedColor(String key) {
        Integer color = mResourcesProvider != null ? mResourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public static class LinkCollector {

        private View mParent;

        public LinkCollector() {}
        public LinkCollector(View parentView) {
            mParent = parentView;
        }

        private ArrayList<Pair<LinkSpanDrawable, Object>> mLinks = new ArrayList<>();
        private int mLinksCount = 0;

        public void addLink(LinkSpanDrawable link) {
            addLink(link, null);
        }

        public void addLink(LinkSpanDrawable link, Object obj) {
            mLinks.add(new Pair<>(link, obj));
            mLinksCount++;
            invalidate(obj);
        }

        public void removeLink(LinkSpanDrawable link) {
            removeLink(link, true);
        }

        public void removeLink(LinkSpanDrawable link, boolean animated) {
            if (link == null) {
                return;
            }
            Pair<LinkSpanDrawable, Object> pair = null;
            for (int i = 0; i < mLinksCount; ++i) {
                if (mLinks.get(i).first == link) {
                    pair = mLinks.get(i);
                    break;
                }
            }
            if (pair == null) {
                return;
            }
            if (animated) {
                if (link.mReleaseStart < 0) {
                    link.release();
                    invalidate(pair.second);
                    final long now = SystemClock.elapsedRealtime();
                    AndroidUtilities.runOnUIThread(
                        () -> removeLink(link, false),
                        Math.max(0, (link.mReleaseStart - now) + mReleaseDelay + mReleaseDuration)
                    );
                }
            } else {
                mLinks.remove(pair);
                link.reset();
                mLinksCount = mLinks.size();
                invalidate(pair.second);
            }
        }

        private void removeLink(int index, boolean animated) {
            if (index < 0 || index >= mLinksCount) {
                return;
            }
            if (animated) {
                Pair<LinkSpanDrawable, Object> pair = mLinks.get(index);
                LinkSpanDrawable link = pair.first;
                if (link.mReleaseStart < 0) {
                    link.release();
                    invalidate(pair.second);
                    final long now = SystemClock.elapsedRealtime();
                    AndroidUtilities.runOnUIThread(
                        () -> removeLink(link, false),
                        Math.max(0, (link.mReleaseStart - now) + mReleaseDelay + mReleaseDuration)
                    );
                }
            } else {
                Pair<LinkSpanDrawable, Object> pair = mLinks.remove(index);
                LinkSpanDrawable link = pair.first;
                link.reset();
                mLinksCount = mLinks.size();
                invalidate(pair.second);
            }
        }

        public void clear() {
            clear(true);
        }

        public void clear(boolean animated) {
            if (animated) {
                for (int i = 0; i < mLinksCount; ++i) {
                    removeLink(i, true);
                }
            } else if (mLinksCount > 0) {
                for (int i = 0; i < mLinksCount; ++i) {
                    mLinks.get(i).first.reset();
                    invalidate(mLinks.get(i).second, false);
                }
                mLinks.clear();
                mLinksCount = 0;
                invalidate();
            }
        }

        public void removeLinks(Object obj) {
            removeLinks(obj, true);
        }

        public void removeLinks(Object obj, boolean animated) {
            for (int i = 0; i < mLinksCount; ++i) {
                if (mLinks.get(i).second == obj) {
                    removeLink(i, animated);
                }
            }
        }

        public boolean draw(Canvas canvas) {
            boolean invalidate = false;
            for (int i = 0; i < mLinksCount; ++i) {
                invalidate = mLinks.get(i).first.draw(canvas) || invalidate;
            }
            return invalidate;
        }

        public boolean draw(Canvas canvas, Object obj) {
            boolean invalidate = false;
            for (int i = 0; i < mLinksCount; ++i) {
                if (mLinks.get(i).second == obj) {
                    invalidate = mLinks.get(i).first.draw(canvas) || invalidate;
                }
            }
            invalidate(obj, false);
            return invalidate;
        }

        public boolean isEmpty() {
            return mLinksCount <= 0;
        }

        private void invalidate() {
            invalidate(null, true);
        }
        private void invalidate(Object obj) {
            invalidate(obj, true);
        }
        private void invalidate(Object obj, boolean tryParent) {
            if (obj instanceof View) {
                ((View) obj).invalidate();
            } else if (obj instanceof ArticleViewer.DrawingText) {
                ArticleViewer.DrawingText text = (ArticleViewer.DrawingText) obj;
                if (text.latestParentView != null) {
                    text.latestParentView.invalidate();
                }
            } else if (tryParent && mParent != null) {
                mParent.invalidate();
            }
        }
    }
}
