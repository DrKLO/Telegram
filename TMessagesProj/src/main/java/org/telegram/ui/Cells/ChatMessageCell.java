/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;

import com.aniways.AniwaysIconInfoSpan;
import com.aniways.IAniwaysTextContainer;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;

public class ChatMessageCell extends ChatBaseCell implements IAniwaysTextContainer {

    private int textX, textY;
    private int totalHeight = 0;
    private ClickableSpan pressedLink;
    private AniwaysIconInfoSpan pressedIcon;

    private int lastVisibleBlockNum = 0;
    private int firstVisibleBlockNum = 0;
    private int totalVisibleBlocksCount = 0;
    private long clickDownEventTIme = -1;

    public ChatMessageCell(Context context, boolean isChat) {
        super(context, isChat);
        drawForwardedName = true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMessageObject != null && currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty() && currentMessageObject.getAniwaysDecodedMessageTextBigIcons() instanceof Spannable && !isPressed) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || (pressedLink != null || pressedIcon != null) && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int)event.getX();
                int y = (int)event.getY();
                if (x >= textX && y >= textY && x <= textX + currentMessageObject.textWidth && y <= textY + currentMessageObject.textHeight) {
                    y -= textY;
                    int blockNum = Math.max(0, y / currentMessageObject.blockHeight);
                    if (blockNum < currentMessageObject.textLayoutBlocks.size()) {
                        MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(blockNum);
                        x -= textX - (int)Math.ceil(block.textXOffset);
                        y -= block.textYOffset;
                        final int line = block.textLayout.getLineForVertical(y);
                        final int off = block.textLayout.getOffsetForHorizontal(line, x) + block.charactersOffset;

                        final float left = block.textLayout.getLineLeft(line);
                        if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                            Spannable buffer = (Spannable)currentMessageObject.getAniwaysDecodedMessageTextBigIcons();
                            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                            AniwaysIconInfoSpan[] iconInfos = buffer.getSpans(off, off, AniwaysIconInfoSpan.class);

                            if (link.length != 0) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    pressedLink = link[0];
                                    return true;
                                } else {
                                    if (link[0] == pressedLink) {
                                        try {
                                            pressedLink.onClick(this);
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                        return true;
                                    }
                                }
                            } else {
                                pressedLink = null;
                            }

                            if (iconInfos.length != 0) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    clickDownEventTIme = event.getEventTime();
                                    pressedIcon = iconInfos[0];
                                    return true;
                                } else {
                                    if (iconInfos[0] == pressedIcon) {
                                        try {
                                            pressedIcon.onClick(this, clickDownEventTIme);
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                        return true;
                                    }
                                }
                            } else {
                                pressedIcon = null;
                            }

                        } else {
                            pressedLink = null;
                            pressedIcon = null;
                        }
                    } else {
                        pressedLink = null;
                        pressedIcon = null;
                    }
                } else {
                    pressedLink = null;
                    pressedIcon = null;
                }
            }
        } else {
            pressedLink = null;
            pressedIcon = null;
        }
        return super.onTouchEvent(event);
    }

    public void setVisiblePart(int position, int height) {
        if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null) {
            return;
        }
        int newFirst = -1, newLast = -1, newCount = 0;

        for (int a = Math.max(0, (position - textY) / currentMessageObject.blockHeight); a < currentMessageObject.textLayoutBlocks.size(); a++) {
            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
            float y = textY + block.textYOffset;
            if (intersect(y, y + currentMessageObject.blockHeight, position, position + height)) {
                if (newFirst == -1) {
                    newFirst = a;
                }
                newLast = a;
                newCount++;
            } else if (y > position) {
                break;
            }
        }

        if (lastVisibleBlockNum != newLast || firstVisibleBlockNum != newFirst || totalVisibleBlocksCount != newCount) {
            lastVisibleBlockNum = newLast;
            firstVisibleBlockNum = newFirst;
            totalVisibleBlocksCount = newCount;
            invalidate();
        }
    }

    private boolean intersect(float left1, float right1, float left2, float right2) {
        if (left1 <= left2) {
            return right1 >= left2;
        }
        return left1 <= right2;
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject || isUserDataChanged()) {
            if (currentMessageObject != messageObject) {
                firstVisibleBlockNum = 0;
                lastVisibleBlockNum = 0;
            }
            pressedLink = null;
            pressedIcon = null;
            int maxWidth;
            if (chat) {
                maxWidth = Utilities.displaySize.x - Utilities.dp(122);
                drawName = true;
            } else {
                maxWidth = Utilities.displaySize.x - Utilities.dp(80);
            }

            backgroundWidth = maxWidth;

            super.setMessageObject(messageObject);

            backgroundWidth = messageObject.textWidth;
            totalHeight = messageObject.textHeight + Utilities.dpf(19.5f) + namesOffset;

            int maxChildWidth = Math.max(backgroundWidth, nameWidth);
            maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);

            int timeMore = timeWidth + Utilities.dp(6);
            if (messageObject.messageOwner.out) {
                timeMore += Utilities.dpf(20.5f);
            }

            if (maxWidth - messageObject.lastLineWidth < timeMore) {
                totalHeight += Utilities.dp(14);
                backgroundWidth = Math.max(maxChildWidth, messageObject.lastLineWidth) + Utilities.dp(29);
            } else {
                int diff = maxChildWidth - messageObject.lastLineWidth;
                if (diff >= 0 && diff <= timeMore) {
                    backgroundWidth = maxChildWidth + timeMore - diff + Utilities.dp(29);
                } else {
                    backgroundWidth = Math.max(maxChildWidth, messageObject.lastLineWidth + timeMore) + Utilities.dp(29);
                }
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.messageOwner.out) {
            textX = layoutWidth - backgroundWidth + Utilities.dp(10);
            textY = Utilities.dp(10) + namesOffset;
        } else {
            textX = Utilities.dp(19) + (chat ? Utilities.dp(52) : 0);
            textY = Utilities.dp(10) + namesOffset;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null || currentMessageObject.textLayoutBlocks.isEmpty() || firstVisibleBlockNum < 0) {
            return;
        }

        if (currentMessageObject.messageOwner.out) {
            textX = layoutWidth - backgroundWidth + Utilities.dp(10);
            textY = Utilities.dp(10) + namesOffset;
        } else {
            textX = Utilities.dp(19) + (chat ? Utilities.dp(52) : 0);
            textY = Utilities.dp(10) + namesOffset;
        }

        for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
            if (a >= currentMessageObject.textLayoutBlocks.size()) {
                break;
            }
            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
            canvas.save();
            canvas.translate(textX - (int)Math.ceil(block.textXOffset), textY + block.textYOffset);
            try {
                block.textLayout.draw(canvas);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            canvas.restore();
        }
    }

    @Override
    public Spannable getText() {
        return (Spannable) this.currentMessageObject.getAniwaysDecodedMessageTextBigIcons();
    }

    /** Return the point (in pixels) of the received char position as it is displayed
     * relative to the upper left corner of the widget, or lower left if fromTop == false.
     * It accounts for scroll position and paddings
     * !! Be careful, it can return null!!
     **/
    @Override
    public Point getPointOfPositionInText(int position, boolean fromTop) {
        int leftPadding = this.textX;
        int topPadding = this.textY;

        // Get the text block this position is in

        MessageObject.TextLayoutBlock theBlock = null;
        for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
            if (a >= currentMessageObject.textLayoutBlocks.size()) {
                return null;
            }

            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);

            if(position < block.charactersOffset){
                return null;
            }

            int blockEnd = block.charactersOffset + block.textLayout.getText().length();
            // The second condition is because if the span is at the block end then the char after it (position) is in the next block
            // and then position == blockEnd. We should regard this block as the right one only if we are looking at the
            // end of the span and not the start of the span (because then we position the span as if it is in the previous block)MessageObject
            if(position < blockEnd || (position == blockEnd && a == lastVisibleBlockNum )){
                theBlock = block;
                break;
            }
        }

        if(theBlock == null){
            return null;
        }

        // The position in the block
        position = position - theBlock.charactersOffset;

        Layout layout = theBlock.textLayout;

        if(layout == null){
            // This could happen immediately after changing modes from vertical to horizontal, for instance..
            return null;
        }

        int line = layout.getLineForOffset(position);
        int baseline = layout.getLineBaseline(line);

        //float x = theBlock.textXOffset + layout.getPrimaryHorizontal(position);
        // The above line is more correct, but it doesn't work for right to left, and the offset always seems to be '0', so I remove for now
        // TODO: fix according to above comment
        float x = layout.getPrimaryHorizontal(position) - theBlock.textXOffset;
        x += leftPadding;
        //x -= mTextView.getScrollX();

        float y = theBlock.textYOffset + baseline;
        y += topPadding;
        //y -= mTextView.getScrollY();
        if(fromTop){
            int ascent = layout.getLineAscent(line);
            y += ascent;
        }
        else{
            int viewHeight = this.getHeight();
            y = viewHeight - y;
        }


        Point point = new Point((int)Math.round(x), (int)Math.round(y));
        return point;
    }

    @Override
    public View getView() {
        return this;
    }
}
