package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class GroupedPhotosListView extends View implements GestureDetector.OnGestureListener {

    private Paint backgroundPaint = new Paint();
    private ArrayList<ImageReceiver> unusedReceivers = new ArrayList<>();
    private ArrayList<ImageReceiver> imagesToDraw = new ArrayList<>();
    public ArrayList<ImageLocation> currentPhotos = new ArrayList<>();
    private ArrayList<Object> currentObjects = new ArrayList<>();
    private int currentImage;
    private long currentGroupId;
    private int itemWidth;
    private int itemHeight;
    private int itemY;
    private int itemSpacing;
    private int drawDx;
    private float moveLineProgress;
    private float currentItemProgress = 1.0f;
    private float nextItemProgress = 0.0f;
    private int nextImage;
    private long lastUpdateTime;
    private boolean moving;
    private boolean animateAllLine;
    private int animateToDX;
    private int animateToDXStart;
    private int animateToItem = -1;
    private android.widget.Scroller scroll;
    private GestureDetector gestureDetector;
    private boolean scrolling;
    private boolean stopedScrolling;
    private boolean ignoreChanges;
    private int nextPhotoScrolling = -1;

    private GroupedPhotosListViewDelegate delegate;

    public interface GroupedPhotosListViewDelegate {
        int getCurrentIndex();
        int getCurrentAccount();
        int getAvatarsDialogId();
        int getSlideshowMessageId();
        ArrayList<ImageLocation> getImagesArrLocations();
        ArrayList<MessageObject> getImagesArr();
        ArrayList<TLRPC.PageBlock> getPageBlockArr();
        Object getParentObject();
        void setCurrentIndex(int index);
    }

    public GroupedPhotosListView(Context context) {
        super(context);
        gestureDetector = new GestureDetector(context, this);
        scroll = new Scroller(context);
        itemWidth = AndroidUtilities.dp(42);
        itemHeight = AndroidUtilities.dp(56);
        itemSpacing = AndroidUtilities.dp(1);
        itemY = AndroidUtilities.dp(3);
        backgroundPaint.setColor(0x7f000000);
    }

    public void clear() {
        currentPhotos.clear();
        currentObjects.clear();
        imagesToDraw.clear();
    }

    public void fillList() {
        if (ignoreChanges) {
            ignoreChanges = false;
            return;
        }

        int currentIndex = delegate.getCurrentIndex();
        ArrayList<ImageLocation> imagesArrLocations = delegate.getImagesArrLocations();
        ArrayList<MessageObject> imagesArr = delegate.getImagesArr();
        ArrayList<TLRPC.PageBlock> pageBlockArr = delegate.getPageBlockArr();
        int slideshowMessageId = delegate.getSlideshowMessageId();
        int currentAccount = delegate.getCurrentAccount();

        boolean changed = false;
        int newCount = 0;
        Object currentObject = null;
        if (imagesArrLocations != null && !imagesArrLocations.isEmpty()) {
            ImageLocation location = imagesArrLocations.get(currentIndex);
            newCount = imagesArrLocations.size();
            currentObject = location;
        } else if (imagesArr != null && !imagesArr.isEmpty()) {
            MessageObject messageObject = imagesArr.get(currentIndex);
            currentObject = messageObject;
            if (messageObject.getGroupIdForUse() != currentGroupId) {
                changed = true;
                currentGroupId = messageObject.getGroupIdForUse();
            } else {
                int max = Math.min(currentIndex + 10, imagesArr.size());
                for (int a = currentIndex; a < max; a++) {
                    MessageObject object = imagesArr.get(a);
                    if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                        newCount++;
                    } else {
                        break;
                    }
                }
                int min = Math.max(currentIndex - 10, 0);
                for (int a = currentIndex - 1; a >= min; a--) {
                    MessageObject object = imagesArr.get(a);
                    if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                        newCount++;
                    } else {
                        break;
                    }
                }
            }
        } else if (pageBlockArr != null && !pageBlockArr.isEmpty()) {
            TLRPC.PageBlock pageBlock = pageBlockArr.get(currentIndex);
            currentObject = pageBlock;
            if (pageBlock.groupId != currentGroupId) {
                changed = true;
                currentGroupId = pageBlock.groupId;
            } else {
                for (int a = currentIndex, size = pageBlockArr.size(); a < size; a++) {
                    TLRPC.PageBlock object = pageBlockArr.get(a);
                    if (object.groupId == currentGroupId) {
                        newCount++;
                    } else {
                        break;
                    }
                }
                for (int a = currentIndex - 1; a >= 0; a--) {
                    TLRPC.PageBlock object = pageBlockArr.get(a);
                    if (object.groupId == currentGroupId) {
                        newCount++;
                    } else {
                        break;
                    }
                }
            }
        }
        if (currentObject == null) {
            return;
        }
        if (!changed) {
            if (newCount != currentPhotos.size() || currentObjects.indexOf(currentObject) == -1) {
                changed = true;
            } else {
                int newImageIndex = currentObjects.indexOf(currentObject);
                if (currentImage != newImageIndex && newImageIndex != -1) {
                    if (animateAllLine) {
                        nextImage = animateToItem = newImageIndex;
                        animateToDX = (currentImage - newImageIndex) * (itemWidth + itemSpacing);
                        moving = true;
                        animateAllLine = false;
                        lastUpdateTime = System.currentTimeMillis();
                        invalidate();
                    } else {
                        fillImages(true, (currentImage - newImageIndex) * (itemWidth + itemSpacing));
                        currentImage = newImageIndex;
                        moving = false;
                    }
                    drawDx = 0;
                }
            }
        }
        if (changed) {
            animateAllLine = false;
            currentPhotos.clear();
            currentObjects.clear();
            if (imagesArrLocations != null && !imagesArrLocations.isEmpty()) {
                currentObjects.addAll(imagesArrLocations);
                currentPhotos.addAll(imagesArrLocations);
                currentImage = currentIndex;
                animateToItem = -1;
            } else if (imagesArr != null && !imagesArr.isEmpty()) {
                if (currentGroupId != 0 || slideshowMessageId != 0) {
                    int max = Math.min(currentIndex + 10, imagesArr.size());
                    for (int a = currentIndex; a < max; a++) {
                        MessageObject object = imagesArr.get(a);
                        if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                            currentObjects.add(object);
                            currentPhotos.add(ImageLocation.getForObject(FileLoader.getClosestPhotoSizeWithSize(object.photoThumbs, 56, true), object.photoThumbsObject));
                        } else {
                            break;
                        }
                    }
                    currentImage = 0;
                    animateToItem = -1;
                    int min = Math.max(currentIndex - 10, 0);
                    for (int a = currentIndex - 1; a >= min; a--) {
                        MessageObject object = imagesArr.get(a);
                        if (slideshowMessageId != 0 || object.getGroupIdForUse() == currentGroupId) {
                            currentObjects.add(0, object);
                            currentPhotos.add(0, ImageLocation.getForObject(FileLoader.getClosestPhotoSizeWithSize(object.photoThumbs, 56, true), object.photoThumbsObject));
                            currentImage++;
                        } else {
                            break;
                        }
                    }
                }
            } else if (pageBlockArr != null && !pageBlockArr.isEmpty()) {
                if (currentGroupId != 0) {
                    for (int a = currentIndex, size = pageBlockArr.size(); a < size; a++) {
                        TLRPC.PageBlock object = pageBlockArr.get(a);
                        if (object.groupId == currentGroupId) {
                            currentObjects.add(object);
                            currentPhotos.add(ImageLocation.getForObject(object.thumb, object.thumbObject));
                        } else {
                            break;
                        }
                    }
                    currentImage = 0;
                    animateToItem = -1;
                    for (int a = currentIndex - 1; a >= 0; a--) {
                        TLRPC.PageBlock object = pageBlockArr.get(a);
                        if (object.groupId == currentGroupId) {
                            currentObjects.add(0, object);
                            currentPhotos.add(0, ImageLocation.getForObject(object.thumb, object.thumbObject));
                            currentImage++;
                        } else {
                            break;
                        }
                    }
                }
            }
            if (currentPhotos.size() == 1) {
                currentPhotos.clear();
                currentObjects.clear();
            }
            fillImages(false, 0);
        }
    }

    public void setMoveProgress(float progress) {
        if (scrolling || animateToItem >= 0) {
            return;
        }
        if (progress > 0) {
            nextImage = currentImage - 1;
        } else {
            nextImage = currentImage + 1;
        }
        if (nextImage >= 0 && nextImage < currentPhotos.size()) {
            currentItemProgress = 1.0f - Math.abs(progress);
        } else {
            currentItemProgress = 1.0f;
        }
        nextItemProgress = 1.0f - currentItemProgress;
        moving = progress != 0;
        invalidate();
        if (currentPhotos.isEmpty() || progress < 0 && currentImage == currentPhotos.size() - 1 || progress > 0 && currentImage == 0) {
            return;
        }
        drawDx = (int) (progress * (itemWidth + itemSpacing));
        fillImages(true, drawDx);
    }

    private ImageReceiver getFreeReceiver() {
        ImageReceiver receiver;
        if (unusedReceivers.isEmpty()) {
            receiver = new ImageReceiver(this);
        } else {
            receiver = unusedReceivers.get(0);
            unusedReceivers.remove(0);
        }
        imagesToDraw.add(receiver);
        receiver.setCurrentAccount(delegate.getCurrentAccount());
        return receiver;
    }

    private void fillImages(boolean move, int dx) {
        if (!move && !imagesToDraw.isEmpty()) {
            unusedReceivers.addAll(imagesToDraw);
            imagesToDraw.clear();
            moving = false;
            moveLineProgress = 1.0f;
            currentItemProgress = 1.0f;
            nextItemProgress = 0.0f;
        }
        invalidate();
        if (getMeasuredWidth() == 0 || currentPhotos.isEmpty()) {
            return;
        }
        int width = getMeasuredWidth();
        int startX = getMeasuredWidth() / 2 - itemWidth / 2;

        int addRightIndex;
        int addLeftIndex;
        if (move) {
            addRightIndex = Integer.MIN_VALUE;
            addLeftIndex = Integer.MAX_VALUE;
            int count = imagesToDraw.size();
            for (int a = 0; a < count; a++) {
                ImageReceiver receiver = imagesToDraw.get(a);
                int num = receiver.getParam();
                int x = startX + (num - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x > width || x + itemWidth < 0) {
                    unusedReceivers.add(receiver);
                    imagesToDraw.remove(a);
                    count--;
                    a--;
                }
                addLeftIndex = Math.min(addLeftIndex, num - 1);
                addRightIndex = Math.max(addRightIndex, num + 1);
            }
        } else {
            addRightIndex = currentImage;
            addLeftIndex = currentImage - 1;
        }

        if (addRightIndex != Integer.MIN_VALUE) {
            int count = currentPhotos.size();
            for (int a = addRightIndex; a < count; a++) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx;
                if (x < width) {
                    ImageLocation location = currentPhotos.get(a);
                    ImageReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    Object parent;
                    if (currentObjects.get(0) instanceof MessageObject) {
                        parent = currentObjects.get(a);
                    } else if (currentObjects.get(0) instanceof TLRPC.PageBlock) {
                        parent = delegate.getParentObject();
                    } else {
                        parent = "avatar_" + delegate.getAvatarsDialogId();
                    }
                    receiver.setImage(null, null, location, "80_80", 0, null, parent, 1);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
        if (addLeftIndex != Integer.MAX_VALUE) {
            for (int a = addLeftIndex; a >= 0; a--) {
                int x = startX + (a - currentImage) * (itemWidth + itemSpacing) + dx + itemWidth;
                if (x > 0) {
                    ImageLocation location = currentPhotos.get(a);
                    ImageReceiver receiver = getFreeReceiver();
                    receiver.setImageCoords(x, itemY, itemWidth, itemHeight);
                    Object parent;
                    if (currentObjects.get(0) instanceof MessageObject) {
                        parent = currentObjects.get(a);
                    } else if (currentObjects.get(0) instanceof TLRPC.PageBlock) {
                        parent = delegate.getParentObject();
                    } else {
                        parent = "avatar_" + delegate.getAvatarsDialogId();
                    }
                    receiver.setImage(null, null, location, "80_80", 0, null, parent, 1);
                    receiver.setParam(a);
                } else {
                    break;
                }
            }
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        animateToItem = -1;
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        int currentIndex = delegate.getCurrentIndex();
        ArrayList<ImageLocation> imagesArrLocations = delegate.getImagesArrLocations();
        ArrayList<MessageObject> imagesArr = delegate.getImagesArr();
        ArrayList<TLRPC.PageBlock> pageBlockArr = delegate.getPageBlockArr();

        stopScrolling();
        int count = imagesToDraw.size();
        for (int a = 0; a < count; a++) {
            ImageReceiver receiver = imagesToDraw.get(a);
            if (receiver.isInsideImage(e.getX(), e.getY())) {
                int num = receiver.getParam();
                if (num < 0 || num >= currentObjects.size()) {
                    return true;
                }
                if (imagesArr != null && !imagesArr.isEmpty()) {
                    MessageObject messageObject = (MessageObject) currentObjects.get(num);
                    int idx = imagesArr.indexOf(messageObject);
                    if (currentIndex == idx) {
                        return true;
                    }
                    moveLineProgress = 1.0f;
                    animateAllLine = true;
                    delegate.setCurrentIndex(idx);
                } else if (pageBlockArr != null && !pageBlockArr.isEmpty()) {
                    TLRPC.PageBlock pageBlock = (TLRPC.PageBlock) currentObjects.get(num);
                    int idx = pageBlockArr.indexOf(pageBlock);
                    if (currentIndex == idx) {
                        return true;
                    }
                    moveLineProgress = 1.0f;
                    animateAllLine = true;
                    delegate.setCurrentIndex(idx);
                } else if (imagesArrLocations != null && !imagesArrLocations.isEmpty()) {
                    ImageLocation location = (ImageLocation) currentObjects.get(num);
                    int idx = imagesArrLocations.indexOf(location);
                    if (currentIndex == idx) {
                        return true;
                    }
                    moveLineProgress = 1.0f;
                    animateAllLine = true;
                    delegate.setCurrentIndex(idx);
                }
                break;
            }
        }
        return false;
    }

    private void updateAfterScroll() {
        int indexChange = 0;
        int dx = drawDx;
        if (Math.abs(dx) > itemWidth / 2 + itemSpacing) {
            if (dx > 0) {
                dx -= itemWidth / 2 + itemSpacing;
                indexChange++;
            } else {
                dx += itemWidth / 2 + itemSpacing;
                indexChange--;
            }
            indexChange += dx / (itemWidth + itemSpacing * 2);
        }
        nextPhotoScrolling = currentImage - indexChange;

        int currentIndex = delegate.getCurrentIndex();
        ArrayList<ImageLocation> imagesArrLocations = delegate.getImagesArrLocations();
        ArrayList<MessageObject> imagesArr = delegate.getImagesArr();
        ArrayList<TLRPC.PageBlock> pageBlockArr = delegate.getPageBlockArr();

        if (currentIndex != nextPhotoScrolling && nextPhotoScrolling >= 0 && nextPhotoScrolling < currentPhotos.size()) {
            Object photo = currentObjects.get(nextPhotoScrolling);
            int nextPhoto = -1;
            if (imagesArr != null && !imagesArr.isEmpty()) {
                MessageObject messageObject = (MessageObject) photo;
                nextPhoto = imagesArr.indexOf(messageObject);
            } else if (pageBlockArr != null && !pageBlockArr.isEmpty()) {
                TLRPC.PageBlock pageBlock = (TLRPC.PageBlock) photo;
                nextPhoto = pageBlockArr.indexOf(pageBlock);
            } else if (imagesArrLocations != null && !imagesArrLocations.isEmpty()) {
                ImageLocation location = (ImageLocation) photo;
                nextPhoto = imagesArrLocations.indexOf(location);
            }
            if (nextPhoto >= 0) {
                ignoreChanges = true;
                delegate.setCurrentIndex(nextPhoto);
            }
        }
        if (!scrolling) {
            scrolling = true;
            stopedScrolling = false;
        }
        fillImages(true, drawDx);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        drawDx -= distanceX;
        int min = getMinScrollX();
        int max = getMaxScrollX();
        if (drawDx < min) {
            drawDx = min;
        } else if (drawDx > max) {
            drawDx = max;
        }
        updateAfterScroll();
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        scroll.abortAnimation();
        if (currentPhotos.size() >= 10) {
            scroll.fling(drawDx, 0, Math.round(velocityX), 0, getMinScrollX(), getMaxScrollX(), 0, 0);
        }
        return false;
    }

    private void stopScrolling() {
        scrolling = false;
        if (!scroll.isFinished()) {
            scroll.abortAnimation();
        }
        if (nextPhotoScrolling >= 0 && nextPhotoScrolling < currentObjects.size()) {
            stopedScrolling = true;

            nextImage = animateToItem = nextPhotoScrolling;
            animateToDX = (currentImage - nextPhotoScrolling) * (itemWidth + itemSpacing);
            animateToDXStart = drawDx;
            moveLineProgress = 1.0f;
            nextPhotoScrolling = -1;
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentPhotos.isEmpty() || getAlpha() != 1.0f) {
            return false;
        }
        boolean result = gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
        if (scrolling && event.getAction() == MotionEvent.ACTION_UP && scroll.isFinished()) {
            stopScrolling();
        }
        return result;
    }

    private int getMinScrollX() {
        return -(currentPhotos.size() - currentImage - 1) * (itemWidth + itemSpacing * 2);
    }

    private int getMaxScrollX() {
        return currentImage * (itemWidth + itemSpacing * 2);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        fillImages(false, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (imagesToDraw.isEmpty()) {
            return;
        }
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
        int count = imagesToDraw.size();

        int moveX = drawDx;

        int maxItemWidth = (int) (itemWidth * 2.0f);
        int padding = AndroidUtilities.dp(8);

        ImageLocation object = currentPhotos.get(currentImage);
        int trueWidth;
        int currentPaddings;
        if (object != null && object.photoSize != null) {
            trueWidth = Math.max(itemWidth, (int) (object.photoSize.w * (itemHeight / (float) object.photoSize.h)));
        } else {
            trueWidth = itemHeight;
        }
        trueWidth = Math.min(maxItemWidth, trueWidth);
        currentPaddings = (int) (padding * 2 * currentItemProgress);
        trueWidth = itemWidth + (int) ((trueWidth - itemWidth) * currentItemProgress) + currentPaddings;

        int nextTrueWidth;
        int nextPaddings;
        if (nextImage >= 0 && nextImage < currentPhotos.size()) {
            object = currentPhotos.get(nextImage);
            if (object != null && object.photoSize != null) {
                nextTrueWidth = Math.max(itemWidth, (int) (object.photoSize.w * (itemHeight / (float) object.photoSize.h)));
            } else {
                nextTrueWidth = itemHeight;
            }
        } else {
            nextTrueWidth = itemWidth;
        }
        nextTrueWidth = Math.min(maxItemWidth, nextTrueWidth);
        nextPaddings = (int) (padding * 2 * nextItemProgress);
        moveX += (nextTrueWidth + nextPaddings - itemWidth) / 2 * nextItemProgress * (nextImage > currentImage ? -1 : 1);
        nextTrueWidth = itemWidth + (int) ((nextTrueWidth - itemWidth) * nextItemProgress) + nextPaddings;

        int startX = (getMeasuredWidth() - trueWidth) / 2;
        for (int a = 0; a < count; a++) {
            ImageReceiver receiver = imagesToDraw.get(a);
            int num = receiver.getParam();
            if (num == currentImage) {
                receiver.setImageX(startX + moveX + currentPaddings / 2);
                receiver.setImageWidth(trueWidth - currentPaddings);
            } else {
                if (nextImage < currentImage) {
                    if (num < currentImage) {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage + 1) * (itemWidth + itemSpacing) - (nextTrueWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                        }
                    } else {
                        receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                    }
                } else {
                    if (num < currentImage) {
                        receiver.setImageX(startX + (receiver.getParam() - currentImage) * (itemWidth + itemSpacing) + moveX);
                    } else {
                        if (num <= nextImage) {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 1) * (itemWidth + itemSpacing) + moveX);
                        } else {
                            receiver.setImageX(startX + trueWidth + itemSpacing + (receiver.getParam() - currentImage - 2) * (itemWidth + itemSpacing) + (nextTrueWidth + itemSpacing) + moveX);
                        }
                    }
                }
                if (num == nextImage) {
                    receiver.setImageWidth(nextTrueWidth - nextPaddings);
                    receiver.setImageX(receiver.getImageX() + nextPaddings / 2);
                } else {
                    receiver.setImageWidth(itemWidth);
                }
            }
            receiver.draw(canvas);
        }

        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        if (animateToItem >= 0) {
            if (moveLineProgress > 0.0f) {
                moveLineProgress -= dt / 200.0f;
                if (animateToItem == currentImage) {
                    if (currentItemProgress < 1.0f) {
                        currentItemProgress += dt / 200.0f;
                        if (currentItemProgress > 1.0f) {
                            currentItemProgress = 1.0f;
                        }
                    }
                    drawDx = animateToDXStart + (int) Math.ceil(currentItemProgress * (animateToDX - animateToDXStart));
                } else {
                    nextItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(1.0f - moveLineProgress);
                    if (stopedScrolling) {
                        if (currentItemProgress > 0.0f) {
                            currentItemProgress -= dt / 200.0f;
                            if (currentItemProgress < 0.0f) {
                                currentItemProgress = 0.0f;
                            }
                        }
                        drawDx = animateToDXStart + (int) Math.ceil(nextItemProgress * (animateToDX - animateToDXStart));
                    } else {
                        currentItemProgress = CubicBezierInterpolator.EASE_OUT.getInterpolation(moveLineProgress);
                        drawDx = (int) Math.ceil(nextItemProgress * animateToDX);
                    }
                }
                if (moveLineProgress <= 0) {
                    currentImage = animateToItem;
                    moveLineProgress = 1.0f;
                    currentItemProgress = 1.0f;
                    nextItemProgress = 0.0f;
                    moving = false;
                    stopedScrolling = false;
                    drawDx = 0;
                    animateToItem = -1;
                }
            }
            fillImages(true, drawDx);
            invalidate();
        }
        if (scrolling && currentItemProgress > 0.0f) {
            currentItemProgress -= dt / 200.0f;
            if (currentItemProgress < 0.0f) {
                currentItemProgress = 0.0f;
            }
            invalidate();
        }
        if (!scroll.isFinished()) {
            if (scroll.computeScrollOffset()) {
                drawDx = scroll.getCurrX();
                updateAfterScroll();
                invalidate();
            }
            if (scroll.isFinished()) {
                stopScrolling();
            }
        }
    }

    public void setDelegate(GroupedPhotosListViewDelegate groupedPhotosListViewDelegate) {
        delegate = groupedPhotosListViewDelegate;
    }
}
