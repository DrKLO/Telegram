package org.telegram.ui.Components.Paint.Views;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManagerFixed;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BotHelpCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BlurringShader;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageBackgroundDrawable;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.Rect;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Stories.recorder.PreviewView;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.util.ArrayList;

public class MessageEntityView extends EntityView {

//    private final ChatActionCell dateCell;
    public final FrameLayout container;
    public final RecyclerListView listView;
    public final ArrayList<MessageObject> messageObjects = new ArrayList<>();
    private MessageObject.GroupedMessages groupedMessages;
    private final BlurringShader.BlurManager blurManager;
    private boolean clipVideoMessageForBitmap;
    private boolean usesBackgroundPaint;
    private PreviewView.TextureViewHolder videoTextureHolder;
    private TextureView textureView;
    private boolean textureViewActive;
    private int videoWidth = 1, videoHeight = 1;

    public boolean drawForBitmap() {
        return false;
    }

    public MessageEntityView(Context context, Point position, ArrayList<MessageObject> messageObjects, BlurringShader.BlurManager blurManager, boolean isRepostVideoPreview, PreviewView.TextureViewHolder videoTextureHolder) {
        this(context, position, 0.0f, 1.0f, messageObjects, blurManager, isRepostVideoPreview, videoTextureHolder);
    }

    public MessageEntityView(Context context, Point position, float angle, float scale, ArrayList<MessageObject> thisMessageObjects, BlurringShader.BlurManager blurManager, boolean isRepostVideoPreview, PreviewView.TextureViewHolder videoTextureHolder) {
        super(context, position);
        this.blurManager = blurManager;
        setRotation(angle);
        setScale(scale);
        int date = 0;
        for (int i = 0; i < thisMessageObjects.size(); ++i) {
            MessageObject msg = thisMessageObjects.get(i);
            date = msg.messageOwner.date;
            TLRPC.Message messageOwner = copyMessage(msg.messageOwner);
            Boolean b = StoryEntry.useForwardForRepost(msg);
            if (b != null && b && messageOwner.fwd_from != null && messageOwner.fwd_from.from_id != null) {
                messageOwner.from_id = messageOwner.fwd_from.from_id;
                messageOwner.peer_id = messageOwner.fwd_from.from_id;
                messageOwner.flags &=~ 4;
                messageOwner.fwd_from = null;
            }
            messageOwner.voiceTranscriptionOpen = false;
            MessageObject newMsg = new MessageObject(msg.currentAccount, messageOwner, msg.replyMessageObject, MessagesController.getInstance(msg.currentAccount).getUsers(), MessagesController.getInstance(msg.currentAccount).getChats(), null, null, true, true, 0, true, isRepostVideoPreview, false);
            messageObjects.add(newMsg);
        }
//        dateCell = new ChatActionCell(context, false, resourcesProvider) {
//            public final BlurringShader.StoryBlurDrawer blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);
//            private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG); {
//                textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
//                textPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
//                textPaint.setColor(0xffffffff);
//            }
//
//            @Override
//            protected Paint getThemedPaint(String paintKey) {
//                if (Theme.key_paint_chatActionText.equals(paintKey) || Theme.key_paint_chatActionText2.equals(paintKey)) {
//                    return textPaint;
//                }
//                if (Theme.key_paint_chatActionBackground.equals(paintKey)) {
//                    usesBackgroundPaint = true;
//                    Paint paint = blurDrawer.adapt(isDark).getPaint(1f);
//                    if (paint != null) {
//                        return paint;
//                    }
//                }
//                return super.getThemedPaint(paintKey);
//            }
//        };
//        dateCell.setTranslationX(dp(26));
//        dateCell.setCustomDate(date, false, false);
//        addView(dateCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        groupedMessages = null;
        if (messageObjects.size() > 1) {
            groupedMessages = new MessageObject.GroupedMessages();
            groupedMessages.messages.addAll(messageObjects);
            groupedMessages.groupId = messageObjects.get(0).getGroupId();
            groupedMessages.calculate();
        }
        container = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                listView.measure(
                    widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                );
                if (textureView != null) {
                    textureView.measure(
                        MeasureSpec.makeMeasureSpec(listView.getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(listView.getMeasuredHeight(), MeasureSpec.EXACTLY)
                    );
                }
                int left = listView.getMeasuredWidth();
                int right = 0;
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    int childleft = child.getLeft(), childright = child.getRight();
                    if (child instanceof ChatMessageCell) {
                        childleft = child.getLeft() + ((ChatMessageCell) child).getBoundsLeft();
                        childright = child.getLeft() + ((ChatMessageCell) child).getBoundsRight();
                    }
                    left = Math.min(childleft, left);
                    right = Math.max(childright, right);
                }
                setMeasuredDimension(right - left, listView.getMeasuredHeight());
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int cleft = listView.getMeasuredWidth();
                int cright = 0;
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    int childleft = child.getLeft(), childright = child.getRight();
                    if (child instanceof ChatMessageCell) {
                        childleft = child.getLeft() + ((ChatMessageCell) child).getBoundsLeft();
                        childright = child.getLeft() + ((ChatMessageCell) child).getBoundsRight();
                    }
                    cleft = Math.min(childleft, cleft);
                    cright = Math.max(childright, cright);
                }
                listView.layout(-cleft, 0, listView.getMeasuredWidth() - cleft, listView.getMeasuredHeight());
                if (textureView != null) {
                    textureView.layout(0, 0, getMeasuredWidth(), listView.getMeasuredHeight());
                }
            }

            private final Matrix videoMatrix = new Matrix();
            private final float[] radii = new float[8];
            private final Path clipPath = new Path();

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == textureView) {
                    ChatMessageCell cell = getCell();
                    if (cell == null) return false;
                    ImageReceiver photoImage = cell.getPhotoImage();
                    if (photoImage == null) return false;
                    videoMatrix.reset();
                    float scale = Math.max(photoImage.getImageWidth() / videoWidth, photoImage.getImageHeight() / videoHeight);
                    videoMatrix.postScale((float) videoWidth / textureView.getWidth() * scale, (float) videoHeight / textureView.getHeight() * scale);
                    videoMatrix.postTranslate(listView.getX() + cell.getX() + photoImage.getCenterX() - videoWidth * scale / 2f, listView.getY() + cell.getY() + photoImage.getCenterY() - videoHeight * scale / 2f);
                    textureView.setTransform(videoMatrix);
                    canvas.save();
                    clipPath.rewind();
                    AndroidUtilities.rectTmp.set(listView.getX() + cell.getX() + photoImage.getImageX(), listView.getY() + cell.getY() + photoImage.getImageY(), listView.getX() + cell.getX() + photoImage.getImageX2(), listView.getY() + cell.getY() + photoImage.getImageY2());
                    for (int a = 0; a < photoImage.getRoundRadius().length; a++) {
                        radii[a * 2] = photoImage.getRoundRadius()[a];
                        radii[a * 2 + 1] = photoImage.getRoundRadius()[a];
                    }
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, radii,  Path.Direction.CW);
                    canvas.clipPath(clipPath);
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView = new RecyclerListView(context, resourcesProvider) {

            private final ArrayList<ChatMessageCell> drawTimeAfter = new ArrayList<>();
            private final ArrayList<ChatMessageCell> drawNamesAfter = new ArrayList<>();
            private final ArrayList<ChatMessageCell> drawCaptionAfter = new ArrayList<>();
            private final ArrayList<MessageObject.GroupedMessages> drawingGroups = new ArrayList<>(10);

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                selectorRect.setEmpty();
                drawChatBackgroundElements(canvas);
                super.dispatchDraw(canvas);
                drawChatForegroundElements(canvas);
                canvas.restore();
            }

            private void drawChatForegroundElements(Canvas canvas) {
                int size = drawTimeAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell cell = drawTimeAfter.get(a);
                        canvas.save();
                        canvas.translate(cell.getLeft() + cell.getNonAnimationTranslationX(false), cell.getY());
                        cell.drawTime(canvas, cell.shouldDrawAlphaLayer() ? cell.getAlpha() : 1f, true);
                        canvas.restore();
                    }
                    drawTimeAfter.clear();
                }
                size = drawNamesAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell cell = drawNamesAfter.get(a);
                        float canvasOffsetX = cell.getLeft() + cell.getNonAnimationTranslationX(false);
                        float canvasOffsetY = cell.getY();
                        float alpha = cell.shouldDrawAlphaLayer() ? cell.getAlpha() : 1f;

                        canvas.save();
                        canvas.translate(canvasOffsetX, canvasOffsetY);
                        cell.setInvalidatesParent(true);
                        cell.drawNamesLayout(canvas, alpha);
                        cell.setInvalidatesParent(false);
                        canvas.restore();
                    }
                    drawNamesAfter.clear();
                }
                size = drawCaptionAfter.size();
                if (size > 0) {
                    for (int a = 0; a < size; a++) {
                        ChatMessageCell cell = drawCaptionAfter.get(a);
                        boolean selectionOnly = false;
                        if (cell.getCurrentPosition() != null) {
                            selectionOnly = (cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_LEFT) == 0;
                        }
                        float alpha = cell.shouldDrawAlphaLayer() ? cell.getAlpha() : 1f;
                        float canvasOffsetX = cell.getLeft() + cell.getNonAnimationTranslationX(false);
                        float canvasOffsetY = cell.getY();
                        canvas.save();
                        MessageObject.GroupedMessages groupedMessages = cell.getCurrentMessagesGroup();
                        if (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds) {
                            float x = cell.getNonAnimationTranslationX(true);
                            float l = (groupedMessages.transitionParams.left + x + groupedMessages.transitionParams.offsetLeft);
                            float t = (groupedMessages.transitionParams.top + groupedMessages.transitionParams.offsetTop);
                            float r = (groupedMessages.transitionParams.right + x + groupedMessages.transitionParams.offsetRight);
                            float b = (groupedMessages.transitionParams.bottom + groupedMessages.transitionParams.offsetBottom);

                            if (!groupedMessages.transitionParams.backgroundChangeBounds) {
                                t += cell.getTranslationY();
                                b += cell.getTranslationY();
                            }
                            canvas.clipRect(
                                    l + AndroidUtilities.dp(8), t + AndroidUtilities.dp(8),
                                    r - AndroidUtilities.dp(8), b - AndroidUtilities.dp(8)
                            );
                        }
                        if (cell.getTransitionParams().wasDraw) {
                            canvas.translate(canvasOffsetX, canvasOffsetY);
                            cell.setInvalidatesParent(true);
                            cell.drawCaptionLayout(canvas, selectionOnly, alpha);
                            cell.setInvalidatesParent(false);
                            canvas.restore();
                        }
                    }
                    drawCaptionAfter.clear();
                }
            }

            private void drawChatBackgroundElements(Canvas canvas) {
                int count = getChildCount();
                MessageObject.GroupedMessages lastDrawnGroup = null;

                for (int a = 0; a < count; a++) {
                    View child = getChildAt(a);
                    if (child.getVisibility() == View.INVISIBLE) {
                        continue;
                    }
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                        if (group == null || group != lastDrawnGroup) {
                            lastDrawnGroup = group;
                            MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                            MessageBackgroundDrawable backgroundDrawable = cell.getBackgroundDrawable();
                            if ((backgroundDrawable.isAnimationInProgress() || cell.isDrawingSelectionBackground()) && (position == null || (position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0)) {
                                int y = (int) cell.getY();
                                int height;
                                canvas.save();
                                if (position == null) {
                                    height = cell.getMeasuredHeight();
                                } else {
                                    height = y + cell.getMeasuredHeight();
                                    long time = 0;
                                    float touchX = 0;
                                    float touchY = 0;
                                    for (int i = 0; i < count; i++) {
                                        View inner = getChildAt(i);
                                        if (inner instanceof ChatMessageCell) {
                                            ChatMessageCell innerCell = (ChatMessageCell) inner;
                                            MessageObject.GroupedMessages innerGroup = innerCell.getCurrentMessagesGroup();
                                            if (innerGroup == group) {
                                                MessageBackgroundDrawable drawable = innerCell.getBackgroundDrawable();
                                                y = Math.min(y, (int) innerCell.getY());
                                                height = Math.max(height, (int) innerCell.getY() + innerCell.getMeasuredHeight());
                                                long touchTime = drawable.getLastTouchTime();
                                                if (touchTime > time) {
                                                    touchX = drawable.getTouchX() + innerCell.getX();
                                                    touchY = drawable.getTouchY() + innerCell.getY();
                                                    time = touchTime;
                                                }
                                            }
                                        }
                                    }
                                    backgroundDrawable.setTouchCoordsOverride(touchX, touchY - y);
                                    height -= y;
                                }
                                canvas.clipRect(0, y, getMeasuredWidth(), y + height);
                                backgroundDrawable.setCustomPaint(null);
                                backgroundDrawable.setColor(getThemedColor(Theme.key_chat_selectedBackground));
                                backgroundDrawable.setBounds(0, y, getMeasuredWidth(), y + height);
                                backgroundDrawable.draw(canvas);
                                canvas.restore();
                            }
                        }
                    } else if (child instanceof ChatActionCell) {
                        ChatActionCell cell = (ChatActionCell) child;
                        if (cell.hasGradientService()) {
                            canvas.save();
                            canvas.translate(cell.getX(), cell.getY());
                            canvas.scale(cell.getScaleX(), cell.getScaleY(), cell.getMeasuredWidth() / 2f, cell.getMeasuredHeight() / 2f);
                            cell.drawBackground(canvas, true);
                            canvas.restore();
                        }
                    }
                }
                for (int k = 0; k < 3; k++) {
                    drawingGroups.clear();
                    if (k == 2 && !isFastScrollAnimationRunning()) {
                        continue;
                    }
                    for (int i = 0; i < count; i++) {
                        View child = getChildAt(i);
                        if (child instanceof ChatMessageCell) {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (child.getY() > getHeight() || child.getY() + child.getHeight() < 0 || cell.getVisibility() == View.INVISIBLE || cell.getVisibility() == View.GONE) {
                                continue;
                            }
                            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                            if (group == null || (k == 0 && group.messages.size() == 1) || (k == 1 && !group.transitionParams.drawBackgroundForDeletedItems)) {
                                continue;
                            }
                            if ((k == 0 && cell.getMessageObject().deleted) || (k == 1 && !cell.getMessageObject().deleted)) {
                                continue;
                            }
                            if ((k == 2 && !cell.willRemovedAfterAnimation()) || (k != 2 && cell.willRemovedAfterAnimation())) {
                                continue;
                            }

                            if (!drawingGroups.contains(group)) {
                                group.transitionParams.left = 0;
                                group.transitionParams.top = 0;
                                group.transitionParams.right = 0;
                                group.transitionParams.bottom = 0;

                                group.transitionParams.pinnedBotton = false;
                                group.transitionParams.pinnedTop = false;
                                group.transitionParams.cell = cell;
                                drawingGroups.add(group);
                            }

                            group.transitionParams.pinnedTop = cell.isPinnedTop();
                            group.transitionParams.pinnedBotton = cell.isPinnedBottom();

                            int left = (cell.getLeft() + cell.getBackgroundDrawableLeft());
                            int right = (cell.getLeft() + cell.getBackgroundDrawableRight());
                            int top = (cell.getTop() + cell.getBackgroundDrawableTop());
                            int bottom = (cell.getTop() + cell.getBackgroundDrawableBottom());

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                                top -= AndroidUtilities.dp(10);
                            }

                            if ((cell.getCurrentPosition().flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                                bottom += AndroidUtilities.dp(10);
                            }

                            if (cell.willRemovedAfterAnimation()) {
                                group.transitionParams.cell = cell;
                            }

                            if (group.transitionParams.top == 0 || top < group.transitionParams.top) {
                                group.transitionParams.top = top;
                            }
                            if (group.transitionParams.bottom == 0 || bottom > group.transitionParams.bottom) {
                                group.transitionParams.bottom = bottom;
                            }
                            if (group.transitionParams.left == 0 || left < group.transitionParams.left) {
                                group.transitionParams.left = left;
                            }
                            if (group.transitionParams.right == 0 || right > group.transitionParams.right) {
                                group.transitionParams.right = right;
                            }
                        }
                    }

                    for (int i = 0; i < drawingGroups.size(); i++) {
                        MessageObject.GroupedMessages group = drawingGroups.get(i);
                        float x = group.transitionParams.cell.getNonAnimationTranslationX(true);
                        float l = (group.transitionParams.left + x + group.transitionParams.offsetLeft);
                        float t = (group.transitionParams.top + group.transitionParams.offsetTop);
                        float r = (group.transitionParams.right + x + group.transitionParams.offsetRight);
                        float b = (group.transitionParams.bottom + group.transitionParams.offsetBottom);

                        if (!group.transitionParams.backgroundChangeBounds) {
                            t += group.transitionParams.cell.getTranslationY();
                            b += group.transitionParams.cell.getTranslationY();
                        }

                        boolean useScale = group.transitionParams.cell.getScaleX() != 1f || group.transitionParams.cell.getScaleY() != 1f;
                        if (useScale) {
                            canvas.save();
                            canvas.scale(group.transitionParams.cell.getScaleX(), group.transitionParams.cell.getScaleY(), l + (r - l) / 2, t + (b - t) / 2);
                        }
                        boolean selected = false;
                        group.transitionParams.cell.drawBackground(canvas, (int) l, (int) t, (int) r, (int) b, group.transitionParams.pinnedTop, group.transitionParams.pinnedBotton, selected, 0);
                        group.transitionParams.cell = null;
                        group.transitionParams.drawCaptionLayout = group.hasCaption;
                        if (useScale) {
                            canvas.restore();
                            for (int ii = 0; ii < count; ii++) {
                                View child = getChildAt(ii);
                                if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getCurrentMessagesGroup() == group) {
                                    ChatMessageCell cell = ((ChatMessageCell) child);
                                    int left = cell.getLeft();
                                    int top = cell.getTop();
                                    child.setPivotX(l - left + (r - l) / 2);
                                    child.setPivotY(t - top + (b - t) / 2);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                ChatMessageCell cell = null;
                ChatActionCell actionCell = null;

                if (child instanceof ChatMessageCell) {
                    cell = (ChatMessageCell) child;
                } else if (child instanceof ChatActionCell) {
                    actionCell = (ChatActionCell) child;
                }

                boolean result = super.drawChild(canvas, child, drawingTime);
                if (cell != null && cell.hasOutboundsContent()) {
                    canvas.save();
                    canvas.translate(cell.getX(), cell.getY());
                    cell.drawOutboundsContent(canvas);
                    canvas.restore();
                } else if (actionCell != null) {
                    canvas.save();
                    canvas.translate(actionCell.getX(), actionCell.getY());
                    actionCell.drawOutboundsContent(canvas);
                    canvas.restore();
                }

                if (child.getTranslationY() != 0) {
                    canvas.save();
                    canvas.translate(0, child.getTranslationY());
                }

                if (cell != null) {
                    cell.drawCheckBox(canvas);
                }

                if (child.getTranslationY() != 0) {
                    canvas.restore();
                }

                if (child.getTranslationY() != 0) {
                    canvas.save();
                    canvas.translate(0, child.getTranslationY());
                }

                if (cell != null) {
                    MessageObject message = cell.getMessageObject();
                    MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                    if (position != null || cell.getTransitionParams().animateBackgroundBoundsInner) {
                        if (position == null || (position.last || position.minX == 0 && position.minY == 0)) {
                            if (position == null || position.last) {
                                drawTimeAfter.add(cell);
                            }
                            if ((position == null || (position.minX == 0 && position.minY == 0)) && cell.hasNameLayout()) {
                                drawNamesAfter.add(cell);
                            }
                        }
                        if (position != null || cell.getTransitionParams().transformGroupToSingleMessage || cell.getTransitionParams().animateBackgroundBoundsInner) {
                            if (position == null || (position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                drawCaptionAfter.add(cell);
                            }
                        }
                    }
                    ImageReceiver imageReceiver = cell.getAvatarImage();
                    if (imageReceiver != null) {
                        boolean replaceAnimation = isFastScrollAnimationRunning() || (groupedMessages != null && groupedMessages.transitionParams.backgroundChangeBounds);
                        int top = replaceAnimation ? child.getTop() : (int) child.getY();
                        if (cell.drawPinnedBottom()) {
                            int p;
                            ViewHolder holder = listView.getChildViewHolder(child);
                            p = holder.getAdapterPosition();

                            if (p >= 0) {
                                int nextPosition;
                                if (groupedMessages != null && position != null) {
                                    int idx = groupedMessages.posArray.indexOf(position);
                                    int size = groupedMessages.posArray.size();
                                    if ((position.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) {
                                        nextPosition = p - size + idx;
                                    } else {
                                        nextPosition = p - 1;
                                        for (int a = idx + 1; a < size; a++) {
                                            if (groupedMessages.posArray.get(a).minY > position.maxY) {
                                                break;
                                            } else {
                                                nextPosition--;
                                            }
                                        }
                                    }
                                } else {
                                    nextPosition = p - 1;
                                }
                                holder = findViewHolderForAdapterPosition(nextPosition);
                                if (holder != null) {
                                    if (child.getTranslationY() != 0) {
                                        canvas.restore();
                                    }
                                    imageReceiver.setVisible(false, false);
                                    return result;
                                }
                            }
                        }
                        float tx = cell.getSlidingOffsetX() + cell.getCheckBoxTranslation();
                        int y = (int) ((replaceAnimation ? child.getTop() : child.getY()) + cell.getLayoutHeight() + cell.getTransitionParams().deltaBottom);
                        int maxY = getMeasuredHeight() - getPaddingBottom();
                        boolean canUpdateTx = cell.isCheckBoxVisible() && tx == 0;
                        if (cell.isPlayingRound() || cell.getTransitionParams().animatePlayingRound) {
                            if (cell.getTransitionParams().animatePlayingRound) {
                                float progressLocal = cell.getTransitionParams().animateChangeProgress;
                                if (!cell.isPlayingRound()) {
                                    progressLocal = 1f - progressLocal;
                                }
                                int fromY = y;
                                int toY = Math.min(y, maxY);
                                y = (int) (fromY * progressLocal + toY * (1f - progressLocal));
                            }
                        } else {
                            if (y > maxY) {
                                y = maxY;
                            }
                        }

                        if (!replaceAnimation && child.getTranslationY() != 0) {
                            canvas.restore();
                        }
                        if (cell.drawPinnedTop()) {
                            int p;
                            ViewHolder holder = getChildViewHolder(child);
                            p = holder.getAdapterPosition();
                            if (p >= 0) {
                                int tries = 0;
                                while (true) {
                                    if (tries >= 20) {
                                        break;
                                    }
                                    tries++;

                                    int prevPosition;
                                    if (groupedMessages != null && position != null) {
                                        int idx = groupedMessages.posArray.indexOf(position);
                                        if (idx < 0) {
                                            break;
                                        }
                                        int size = groupedMessages.posArray.size();
                                        if ((position.flags & MessageObject.POSITION_FLAG_TOP) != 0) {
                                            prevPosition = p + idx + 1;
                                        } else {
                                            prevPosition = p + 1;
                                            for (int a = idx - 1; a >= 0; a--) {
                                                if (groupedMessages.posArray.get(a).maxY < position.minY) {
                                                    break;
                                                } else {
                                                    prevPosition++;
                                                }
                                            }
                                        }
                                    } else {
                                        prevPosition = p + 1;
                                    }
                                    holder = findViewHolderForAdapterPosition(prevPosition);
                                    if (holder != null) {
                                        top = holder.itemView.getTop();
                                        if (holder.itemView instanceof ChatMessageCell) {
                                            cell = (ChatMessageCell) holder.itemView;
                                            float newTx = cell.getSlidingOffsetX() + cell.getCheckBoxTranslation();
                                            if (canUpdateTx && newTx > 0) {
                                                tx = newTx;
                                            }
                                            if (!cell.drawPinnedTop()) {
                                                break;
                                            } else {
                                                p = prevPosition;
                                            }
                                        } else {
                                            break;
                                        }
                                    } else {
                                        break;
                                    }
                                }
                            }
                        }
                        if (y - AndroidUtilities.dp(42) < top) {
                            y = top + AndroidUtilities.dp(42);
                        }
                        if (!cell.drawPinnedBottom()) {
                            int cellBottom = replaceAnimation ? cell.getBottom() : (int) (cell.getY() + cell.getMeasuredHeight() + cell.getTransitionParams().deltaBottom);
                            if (y > cellBottom) {
                                y = cellBottom;
                            }
                        }
                        canvas.save();
                        if (tx != 0) {
                            canvas.translate(tx, 0);
                        }
                        if (cell.getCurrentMessagesGroup() != null) {
                            if (cell.getCurrentMessagesGroup().transitionParams.backgroundChangeBounds) {
                                y -= cell.getTranslationY();
                            }
                        }
                        imageReceiver.setImageY(y - AndroidUtilities.dp(40));
                        if (cell.shouldDrawAlphaLayer()) {
                            imageReceiver.setAlpha(cell.getAlpha());
                            canvas.scale(
                                    cell.getScaleX(), cell.getScaleY(),
                                    cell.getX() + cell.getPivotX(), cell.getY() + (cell.getHeight() >> 1)
                            );
                        } else {
                            imageReceiver.setAlpha(1f);
                        }
                        imageReceiver.setVisible(true, false);
                        imageReceiver.draw(canvas);
                        canvas.restore();

                        if (!replaceAnimation && child.getTranslationY() != 0) {
                            canvas.save();
                        }
                    }
                }

                if (child.getTranslationY() != 0) {
                    canvas.restore();
                }
                return result;
            }
        };
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                ChatMessageCell cell = new ChatMessageCell(context, false, null, resourcesProvider) {
                    public BlurringShader.StoryBlurDrawer blurDrawer = new BlurringShader.StoryBlurDrawer(blurManager, this, BlurringShader.StoryBlurDrawer.BLUR_TYPE_ACTION_BACKGROUND);

                    @Override
                    protected void onDraw(Canvas canvas) {
                        if (videoTextureHolder != null && videoTextureHolder.active && videoTextureHolder.textureViewActive || clipVideoMessageForBitmap) {
                            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                        } else {
                            canvas.save();
                        }
                        super.onDraw(canvas);
                        canvas.restore();
                    }

                    @Override
                    public boolean onTouchEvent(MotionEvent event) {
                        return false;
                    }

                    @Override
                    public Paint getThemedPaint(String paintKey) {
                        if (Theme.key_paint_chatActionBackground.equals(paintKey)) {
                            usesBackgroundPaint = true;
                            Paint paint = blurDrawer.getPaint(1f);
                            if (paint != null) {
                                return paint;
                            }
                        }
                        return super.getThemedPaint(paintKey);
                    }

                    private final float[] radii = new float[8];
                    private final Path clipPath = new Path();
                    private final Paint clearPaint = new Paint();
                    { clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); }
                    private final android.graphics.Rect src = new android.graphics.Rect();
                    private final android.graphics.RectF dst = new android.graphics.RectF();

                    @Override
                    protected boolean drawPhotoImage(Canvas canvas) {
                        ImageReceiver photoImage = getPhotoImage();
                        if (isRepostVideoPreview && photoImage != null && (videoTextureHolder != null && videoTextureHolder.active && videoTextureHolder.textureViewActive && textureViewActive || clipVideoMessageForBitmap || textureView != null && drawForBitmap())) {
                            for (int a = 0; a < photoImage.getRoundRadius().length; a++) {
                                radii[a * 2] = photoImage.getRoundRadius()[a];
                                radii[a * 2 + 1] = photoImage.getRoundRadius()[a];
                            }
                            AndroidUtilities.rectTmp.set(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX2(), photoImage.getImageY2());
                            clipPath.rewind();
                            clipPath.addRoundRect(AndroidUtilities.rectTmp, radii, Path.Direction.CW);
                            if (textureView != null && drawForBitmap()) {
                                Bitmap bitmap = textureView.getBitmap();
                                if (bitmap != null) {
                                    canvas.save();
                                    canvas.clipPath(clipPath);
                                    canvas.translate(-getX(), -getY());
                                    float scale = Math.max(photoImage.getImageWidth() / videoWidth, photoImage.getImageHeight() / videoHeight);
                                    canvas.translate(photoImage.getCenterX() - videoWidth * scale / 2f, photoImage.getCenterY() - videoHeight * scale / 2f);
                                    canvas.scale((float) videoWidth / textureView.getWidth() * scale, (float) videoHeight / textureView.getHeight() * scale);
                                    src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                                    dst.set(0, 0, textureView.getWidth(), textureView.getHeight());
                                    canvas.drawBitmap(bitmap, src, dst, null);
                                    canvas.restore();
                                } else {
                                    return super.drawPhotoImage(canvas);
                                }
                            } else {
                                canvas.drawPath(clipPath, clearPaint);
                            }
                            return true;
                        }
                        return super.drawPhotoImage(canvas);
                    }
                };
                cell.isChat = true;
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                position = messageObjects.size() - 1 - position;
                MessageObject message = messageObjects.get(position);
                boolean pinnedTop = false;
                if (groupedMessages != null) {
                    MessageObject.GroupedMessagePosition p = groupedMessages.getPosition(message);
                    if (p != null) {
                        pinnedTop = p.minY != 0;
                    }
                }
                ((ChatMessageCell) holder.itemView).setMessageObject(message, groupedMessages, groupedMessages != null, pinnedTop);
            }

            @Override
            public int getItemCount() {
                return messageObjects.size();
            }
        });
        GridLayoutManagerFixed layoutManager = new GridLayoutManagerFixed(context, 1000, LinearLayoutManager.VERTICAL, true) {

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public boolean shouldLayoutChildFromOpositeSide(View child) {
                if (child instanceof ChatMessageCell) {
                    return !((ChatMessageCell) child).getMessageObject().isOutOwner();
                }
                return false;
            }

            @Override
            protected boolean hasSiblingChild(int position) {
                position = messageObjects.size() - 1 - position;
                if (groupedMessages != null && position >= 0 && position < messageObjects.size()) {
                    MessageObject message = messageObjects.get(position);
                    MessageObject.GroupedMessagePosition pos = groupedMessages.getPosition(message);
                    if (pos == null || pos.minX == pos.maxX || pos.minY != pos.maxY || pos.minY == 0) {
                        return false;
                    }
                    int count = groupedMessages.posArray.size();
                    for (int a = 0; a < count; a++) {
                        MessageObject.GroupedMessagePosition p = groupedMessages.posArray.get(a);
                        if (p == pos) {
                            continue;
                        }
                        if (p.minY <= pos.minY && p.maxY >= pos.minY) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                position = messageObjects.size() - 1 - position;
                if (groupedMessages != null && position >= 0 && position < groupedMessages.messages.size()) {
                    MessageObject message = groupedMessages.messages.get(position);
                    MessageObject.GroupedMessagePosition groupedPosition = groupedMessages.getPosition(message);
                    if (groupedPosition != null) {
                        return groupedPosition.spanSize;
                    }
                }
                return 1000;
            }
        });
        listView.setLayoutManager(layoutManager);
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.bottom = 0;
                if (view instanceof ChatMessageCell) {
                    ChatMessageCell cell = (ChatMessageCell) view;
                    MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
                    if (group != null) {
                        MessageObject.GroupedMessagePosition position = cell.getCurrentPosition();
                        if (position != null && position.siblingHeights != null) {
                            float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                            int h = cell.getExtraInsetHeight();
                            for (int a = 0; a < position.siblingHeights.length; a++) {
                                h += (int) Math.ceil(maxHeight * position.siblingHeights[a]);
                            }
                            h += (position.maxY - position.minY) * Math.round(7 * AndroidUtilities.density);
                            int count = group.posArray.size();
                            for (int a = 0; a < count; a++) {
                                MessageObject.GroupedMessagePosition pos = group.posArray.get(a);
                                if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
                                    continue;
                                }
                                if (pos.minY == position.minY) {
                                    h -= (int) Math.ceil(maxHeight * pos.ph) - AndroidUtilities.dp(4);
                                    break;
                                }
                            }
                            outRect.bottom = -h;
                        }
                    }
                }
            }
        });
        container.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (videoTextureHolder != null && videoTextureHolder.active) {
            videoTextureHolder.takeTextureView(textureView -> {
                this.textureView = textureView;
                if (textureView != null) {
                    container.addView(textureView, 0);
                }
            }, (w, h) -> {
                videoWidth = w;
                videoHeight = h;
                AndroidUtilities.runOnUIThread(() -> {
                    textureViewActive = true;
                    invalidateAll();
                }, 60);
            });
        }
        updatePosition();
    }

    private ChatMessageCell getCell() {
        if (listView == null) return null;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof ChatMessageCell) {
                return (ChatMessageCell) listView.getChildAt(i);
            }
        }
        return null;
    }

    public void getBubbleBounds(RectF rect) {
        float left = Integer.MAX_VALUE;
        float right = Integer.MIN_VALUE;
        float top = Integer.MAX_VALUE;
        float bottom = Integer.MIN_VALUE;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                float cleft, ctop, cright, cbottom;
                if (cell.getMessageObject() != null && cell.getMessageObject().isRoundVideo() && cell.getPhotoImage() != null) {
                    cleft = container.getX() + cell.getX() + cell.getPhotoImage().getImageX();
                    cright = container.getX() + cell.getX() + cell.getPhotoImage().getImageX2();
                    ctop = container.getY() + cell.getY() + cell.getPhotoImage().getImageY();
                    cbottom = container.getY() + cell.getY() + cell.getPhotoImage().getImageY2();
                } else {
                    cleft = container.getX() + child.getX() + cell.getBackgroundDrawableLeft() + dp(1);
                    if (groupedMessages == null) { // pinned bottom
                        cleft += dp(8);
                    }
                    cright = container.getX() + child.getX() + cell.getBackgroundDrawableRight() - dp(1);
                    ctop = container.getY() + child.getY() + cell.getBackgroundDrawableTop() + dp(1.33f);
                    cbottom = container.getY() + child.getY() + cell.getBackgroundDrawableBottom() - dp(.66f);
                }
                left = Math.min(left, cleft);
                left = Math.min(left, cright);
                right = Math.max(right, cleft);
                right = Math.max(right, cright);
                top = Math.min(top, ctop);
                top = Math.min(top, cbottom);
                bottom = Math.max(bottom, ctop);
                bottom = Math.max(bottom, cbottom);
            }
        }
        rect.set(left, top, right, bottom);
    }

    public void invalidateAll() {
//        dateCell.invalidate();
        listView.invalidate();
        for (int i = 0; i < listView.getChildCount(); ++i) {
            listView.getChildAt(i).invalidate();
        }
    }

    public void prepareToDraw(boolean drawingToBitmap) {
        clipVideoMessageForBitmap = drawingToBitmap;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ((ChatMessageCell) child).drawingToBitmap = drawingToBitmap;
            }
        }
    }

    protected void updatePosition() {
        float halfWidth = getMeasuredWidth() / 2.0f;
        float halfHeight = getMeasuredHeight() / 2.0f;
        setX(getPositionX() - halfWidth);
        setY(getPositionY() - halfHeight);
        updateSelectionView();
        if (usesBackgroundPaint) {
            invalidateAll();
        }
    }

    public boolean firstMeasure = true;

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//        dateCell.measure(container.getMeasuredWidth() > 0 ? MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), MeasureSpec.EXACTLY) : widthMeasureSpec, heightMeasureSpec);
//        container.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - dateCell.getMeasuredHeight(), MeasureSpec.getMode(heightMeasureSpec)));
//        dateCell.measure(container.getMeasuredWidth() > 0 ? MeasureSpec.makeMeasureSpec(container.getMeasuredWidth(), MeasureSpec.EXACTLY) : widthMeasureSpec, heightMeasureSpec);
        container.measure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(container.getMeasuredWidth(), container.getMeasuredHeight());
        updatePosition();
        if (firstMeasure) {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - dp(22 * 2);
            int maxHeight = MeasureSpec.getSize(heightMeasureSpec) - dp(96 * 2);

            int width = getMeasuredWidth();
            int height = getMeasuredHeight();

            float scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
            if (scale < 1) {
                setScale(scale);
            }
            Point p = getPosition();
            p.x -= dp(19) * Math.min(1, scale);
            setPosition(p);

            firstMeasure = false;
        }
    }

    @Override
    public Rect getSelectionBounds() {
        ViewGroup parentView = (ViewGroup) getParent();
        if (parentView == null) {
            return new Rect();
        }
        float scale = parentView.getScaleX();
        return new Rect(
            getPositionX() * scale - getMeasuredWidth() * getScale() / 2.0f * scale - dp(1.0f + 19.5f + 15),
            getPositionY() * scale - getMeasuredHeight() * getScale() / 2.0f * scale - dp(1.0f + 19.5f + 15),
            (getMeasuredWidth() * getScale()) * scale + dp((1.0f + 19.5f + 15) * 2),
            (getMeasuredHeight() * getScale()) * scale + dp((1.0f + 19.5f + 15) * 2)
        );
    }

    @Override
    protected float getBounceScale() {
        return 0.02f;
    }

    @Override
    protected SelectionView createSelectionView() {
        return new MessageEntityViewSelectionView(getContext());
    }

    public class MessageEntityViewSelectionView extends SelectionView {

        private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MessageEntityViewSelectionView(Context context) {
            super(context);
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        protected int pointInsideHandle(float x, float y) {
            float thickness = dp(1.0f);
            float radius = dp(19.5f);

            float inset = radius + thickness;
            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            float middle = inset + height / 2.0f;

            if (x > inset - radius && y > middle - radius && x < inset + radius && y < middle + radius) {
                return SELECTION_LEFT_HANDLE;
            } else if (x > inset + width - radius && y > middle - radius && x < inset + width + radius && y < middle + radius) {
                return SELECTION_RIGHT_HANDLE;
            }

            if (x > inset && x < width && y > inset && y < height) {
                return SELECTION_WHOLE_HANDLE;
            }

            return 0;
        }

        private Path path = new Path();

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int count = canvas.getSaveCount();

            float alpha = getShowAlpha();
            if (alpha <= 0) {
                return;
            } else if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            float thickness = dp(2.0f);
            float radius = AndroidUtilities.dpf2(5.66f);

            float inset = radius + thickness + dp(15);

            float width = getMeasuredWidth() - inset * 2;
            float height = getMeasuredHeight() - inset * 2;

            AndroidUtilities.rectTmp.set(inset, inset, inset + width, inset + height);

            float R = dp(12);
            float rx = Math.min(R, width / 2f), ry = Math.min(R, height / 2f);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset, inset + rx * 2, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 180, 90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset, inset + width, inset + ry * 2);
            path.arcTo(AndroidUtilities.rectTmp, 270, 90);
            canvas.drawPath(path, paint);

            path.rewind();
            AndroidUtilities.rectTmp.set(inset, inset + height - ry * 2, inset + rx * 2, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 180, -90);
            AndroidUtilities.rectTmp.set(inset + width - rx * 2, inset + height - ry * 2, inset + width, inset + height);
            path.arcTo(AndroidUtilities.rectTmp, 90, -90);
            canvas.drawPath(path, paint);

            canvas.drawCircle(inset, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.drawCircle(inset + width, inset + height / 2.0f, radius, dotStrokePaint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius - dp(1) + 1, dotPaint);

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

            canvas.drawLine(inset, inset + ry, inset, inset + height - ry, paint);
            canvas.drawLine(inset + width, inset + ry, inset + width, inset + height - ry, paint);
            canvas.drawCircle(inset + width, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);
            canvas.drawCircle(inset, inset + height / 2.0f, radius + dp(1) - 1, clearPaint);

            canvas.restoreToCount(count);
        }
    }

    private boolean isDark = Theme.isCurrentThemeDark();
    private final SparseIntArray currentColors = new SparseIntArray();
    public final Theme.ResourcesProvider resourcesProvider = new Theme.ResourcesProvider() {
        public final TextPaint chat_actionTextPaint = new TextPaint();
        public final TextPaint chat_actionTextPaint2 = new TextPaint();
        public final TextPaint chat_botButtonPaint = new TextPaint();

        public final Paint chat_actionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        public final Paint chat_actionBackgroundSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        public final Paint chat_actionBackgroundGradientDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        {
            chat_actionTextPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
            chat_actionTextPaint2.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
            chat_botButtonPaint.setTextSize(AndroidUtilities.dp(15));
            chat_botButtonPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            chat_actionBackgroundGradientDarkenPaint.setColor(0x15000000);
        }

        @Override
        public int getColor(int key) {
            return currentColors.get(key, Theme.getColor(key));
        }

        @Override
        public Paint getPaint(String paintKey) {
            switch (paintKey) {
                case Theme.key_paint_chatActionBackgroundSelected: return chat_actionBackgroundSelectedPaint;
                case Theme.key_paint_chatActionBackgroundDarken: return chat_actionBackgroundGradientDarkenPaint;
                case Theme.key_paint_chatActionText: return chat_actionTextPaint;
                case Theme.key_paint_chatActionText2: return chat_actionTextPaint2;
                case Theme.key_paint_chatBotButton: return chat_botButtonPaint;
            }
            return Theme.ResourcesProvider.super.getPaint(paintKey);
        }

        @Override
        public Drawable getDrawable(String drawableKey) {
            if (drawableKey.equals(Theme.key_drawable_msgIn)) {
                if (msgInDrawable == null) {
                    msgInDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, false, resourcesProvider);
                }
                return msgInDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgInSelected)) {
                if (msgInDrawableSelected == null) {
                    msgInDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, false, true, resourcesProvider);
                }
                return msgInDrawableSelected;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOut)) {
                if (msgOutDrawable == null) {
                    msgOutDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, false, resourcesProvider);
                }
                return msgOutDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutSelected)) {
                if (msgOutDrawableSelected == null) {
                    msgOutDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_TEXT, true, true, resourcesProvider);
                }
                return msgOutDrawableSelected;
            }

            if (drawableKey.equals(Theme.key_drawable_msgInMedia)) {
                if (msgMediaInDrawable == null) {
                    msgMediaInDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, false, false, resourcesProvider);
                }
                msgMediaInDrawable.invalidateSelf();
                return msgMediaInDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgInMediaSelected)) {
                if (msgMediaInDrawableSelected == null) {
                    msgMediaInDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, false, true, resourcesProvider);
                }
                return msgMediaInDrawableSelected;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutMedia)) {
                if (msgMediaOutDrawable == null) {
                    msgMediaOutDrawable = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, false, resourcesProvider);
                }
                return msgMediaOutDrawable;
            }
            if (drawableKey.equals(Theme.key_drawable_msgOutMediaSelected)) {
                if (msgMediaOutDrawableSelected == null) {
                    msgMediaOutDrawableSelected = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, true, resourcesProvider);
                }
                return msgMediaOutDrawableSelected;
            }

            return Theme.getThemeDrawable(drawableKey);
        }

        @Override
        public boolean isDark() {
            return isDark;
        }
    };
    private Theme.MessageDrawable msgInDrawable, msgInDrawableSelected;
    private Theme.MessageDrawable msgOutDrawable, msgOutDrawableSelected;
    private Theme.MessageDrawable msgMediaInDrawable, msgMediaInDrawableSelected;
    private Theme.MessageDrawable msgMediaOutDrawable, msgMediaOutDrawableSelected;

    public void setupTheme(StoryEntry entry) {
        if (entry == null) {
            currentColors.clear();
            return;
        }

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        Theme.ThemeInfo themeInfo = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (themeInfo.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }
        if (this.isDark = entry.isDark) {
            themeInfo = Theme.getTheme(nightThemeName);
        } else {
            themeInfo = Theme.getTheme(dayThemeName);
        }
        final String[] wallpaperLink = new String[1];
        final SparseIntArray themeColors;
        if (themeInfo.assetName != null) {
            themeColors = Theme.getThemeFileValues(null, themeInfo.assetName, wallpaperLink);
        } else {
            themeColors = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
        }
        currentColors.clear();
        int[] defaultColors = Theme.getDefaultColors();
        if (defaultColors != null) {
            for (int i = 0; i < defaultColors.length; ++i) {
                currentColors.put(i, defaultColors[i]);
            }
        }
        if (themeColors != null) {
            for (int i = 0; i < themeColors.size(); ++i) {
                currentColors.put(themeColors.keyAt(i), themeColors.valueAt(i));
            }
            Theme.ThemeAccent accent = themeInfo.getAccent(false);
            if (accent != null) {
                accent.fillAccentColors(themeColors, currentColors);
            }
        }

        invalidateAll();
    }

    public TLRPC.TL_message copyMessage(TLRPC.Message msg) {
        TLRPC.TL_message newmsg = new TLRPC.TL_message();
        newmsg.id = msg.id;
        newmsg.from_id = msg.from_id;
        newmsg.peer_id = msg.peer_id;
        newmsg.date = msg.date;
        newmsg.expire_date = msg.expire_date;
        newmsg.action = msg.action;
        newmsg.message = msg.message;
        newmsg.media = msg.media;
        newmsg.flags = msg.flags;
        newmsg.mentioned = msg.mentioned;
        newmsg.media_unread = msg.media_unread;
        newmsg.out = msg.out;
        newmsg.unread = msg.unread;
        newmsg.entities = msg.entities;
        newmsg.via_bot_name = msg.via_bot_name;
        newmsg.reply_markup = msg.reply_markup;
        newmsg.views = msg.views;
        newmsg.forwards = msg.forwards;
        newmsg.replies = msg.replies;
        newmsg.edit_date = msg.edit_date;
        newmsg.silent = msg.silent;
        newmsg.post = msg.post;
        newmsg.from_scheduled = msg.from_scheduled;
        newmsg.legacy = msg.legacy;
        newmsg.edit_hide = msg.edit_hide;
        newmsg.pinned = msg.pinned;
        newmsg.fwd_from = msg.fwd_from;
        newmsg.via_bot_id = msg.via_bot_id;
        newmsg.reply_to = msg.reply_to;
        newmsg.post_author = msg.post_author;
        newmsg.grouped_id = msg.grouped_id;
        newmsg.reactions = msg.reactions;
        newmsg.restriction_reason = msg.restriction_reason;
        newmsg.ttl_period = msg.ttl_period;
        newmsg.noforwards = msg.noforwards;
        newmsg.invert_media = msg.invert_media;
        newmsg.send_state = msg.send_state;
        newmsg.fwd_msg_id = msg.fwd_msg_id;
        newmsg.attachPath = msg.attachPath;
        newmsg.params = msg.params;
        newmsg.random_id = msg.random_id;
        newmsg.local_id = msg.local_id;
        newmsg.dialog_id = msg.dialog_id;
        newmsg.ttl = msg.ttl;
        newmsg.destroyTime = msg.destroyTime;
        newmsg.destroyTimeMillis = msg.destroyTimeMillis;
        newmsg.layer = msg.layer;
        newmsg.seq_in = msg.seq_in;
        newmsg.seq_out = msg.seq_out;
        newmsg.with_my_score = msg.with_my_score;
        newmsg.replyMessage = msg.replyMessage;
        newmsg.reqId = msg.reqId;
        newmsg.realId = msg.realId;
        newmsg.stickerVerified = msg.stickerVerified;
        newmsg.isThreadMessage = msg.isThreadMessage;
        newmsg.voiceTranscription = msg.voiceTranscription;
        newmsg.voiceTranscriptionOpen = msg.voiceTranscriptionOpen;
        newmsg.voiceTranscriptionRated = msg.voiceTranscriptionRated;
        newmsg.voiceTranscriptionFinal = msg.voiceTranscriptionFinal;
        newmsg.voiceTranscriptionForce = msg.voiceTranscriptionForce;
        newmsg.voiceTranscriptionId = msg.voiceTranscriptionId;
        newmsg.premiumEffectWasPlayed = msg.premiumEffectWasPlayed;
        newmsg.originalLanguage = msg.originalLanguage;
        newmsg.translatedToLanguage = msg.translatedToLanguage;
        newmsg.translatedText = msg.translatedText;
        newmsg.replyStory = msg.replyStory;
        return newmsg;
    }
}
