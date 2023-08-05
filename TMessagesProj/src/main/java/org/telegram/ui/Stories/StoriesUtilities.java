package org.telegram.ui.Stories;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GradientTools;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

import java.util.Collections;

public class StoriesUtilities {

    private final static int ANIMATION_SEGMENT_COUNT = 16;
    public static final int STATE_EMPTY = 0;
    public static final int STATE_HAS_UNREAD = 1;
    public static final int STATE_READ = 2;
    public static final int STATE_PROGRESS = 3;
    public static GradientTools[] storiesGradientTools = new GradientTools[2];
    public static GradientTools closeFriendsGradientTools;
    public static Paint grayPaint;

    public static Paint closeFriendsLastColor;
    public static int grayLastColor;
    public static Paint[] storyCellGreyPaint = new Paint[2];
    public static int storyCellGrayLastColor;

    public static Drawable expiredStoryDrawable;

    private final static RectF rectTmp = new RectF();

    public static void drawAvatarWithStory(long dialogId, Canvas canvas, ImageReceiver avatarImage, AvatarStoryParams params) {
        StoriesController storiesController = MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController();
        boolean hasStories = storiesController.hasStories(dialogId);
        drawAvatarWithStory(dialogId, canvas, avatarImage, UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId() != dialogId && hasStories, params);
    }

    static boolean scheduled = false;
    static int debugState = 0;
    static Runnable debugRunnable = new Runnable() {
        @Override
        public void run() {
            debugState = Math.abs(Utilities.random.nextInt() % 3);
            if (debugState == STATE_READ) {
                debugState = STATE_HAS_UNREAD;
            } else {
                debugState = STATE_READ;
            }
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, 0);
            AndroidUtilities.runOnUIThread(debugRunnable, 1000);
            LaunchActivity.getLastFragment().getFragmentView();
        }
    };

    public static void drawAvatarWithStory(long dialogId, Canvas canvas, ImageReceiver avatarImage, boolean hasStories, AvatarStoryParams params) {
        StoriesController storiesController = MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController();
        boolean animated = params.animate;
        if (params.dialogId != dialogId) {
            params.dialogId = dialogId;
            params.reset();
            animated = false;
        }
//        if (!scheduled) {
//            scheduled = true;
//            AndroidUtilities.runOnUIThread(debugRunnable);
//        }
//            int state = debugState;
        int state;
        int unreadState = 0;
        boolean showProgress = storiesController.isLoading(dialogId);

        if (params.drawHiddenStoriesAsSegments) {
            hasStories = storiesController.hasHiddenStories();
        }

        if (params.storyItem != null) {
            unreadState = storiesController.getUnreadState(dialogId, params.storyId);
            state = unreadState == StoriesController.STATE_READ ? STATE_READ : STATE_HAS_UNREAD;
            showProgress = false;
        }
        if (showProgress) {
            state = STATE_PROGRESS;
            animated = false;
            if (storiesController.hasStories(dialogId)) {
                unreadState = STATE_READ;//storiesController.getUnreadState(dialogId);
            } else {
                unreadState = getPredictiveUnreadState(storiesController, dialogId);
            }
        } else if (hasStories) {
            if (params.drawSegments) {
                // unreadState = storiesController.getUnreadState(dialogId, params.storyId);
                unreadState = state = STATE_READ;
            } else {
                unreadState = storiesController.getUnreadState(dialogId, params.storyId);
                state = unreadState == StoriesController.STATE_READ ? STATE_READ : STATE_HAS_UNREAD;
            }
        } else {
            unreadState = state = getPredictiveUnreadState(storiesController, dialogId);
        }

        if (params.currentState != state) {
            if (params.currentState == STATE_PROGRESS) {
                animated = true;
            }
            if (state == STATE_PROGRESS) {
                params.animateFromUnreadState = unreadState;
            }
            if (animated) {
                params.prevState = params.currentState;
                params.prevUnreadState = params.unreadState;
                params.currentState = state;
                params.progressToSate = 0;
            } else {
                params.currentState = state;
                params.progressToSate = 1f;
            }
        }

        params.unreadState = unreadState;

        float scale = params.buttonBounce != null ? params.buttonBounce.getScale(0.08f) : 1f;
        if (params.showProgress != showProgress && showProgress) {
            params.sweepAngle = 1f;
            params.inc = false;
        }
        params.showProgress = showProgress;
        if (params.currentState == STATE_EMPTY && params.progressToSate == 1f) {
            avatarImage.setImageCoords(params.originalAvatarRect);
            avatarImage.draw(canvas);
            return;
        }
        int restoreCount = 0;
        if (scale != 1f) {
            restoreCount = canvas.save();
            canvas.scale(scale, scale, params.originalAvatarRect.centerX(), params.originalAvatarRect.centerY());
        }

        float progressToSate = params.progressToSate;
        if (progressToSate != 1f) {
            progressToSate = CubicBezierInterpolator.DEFAULT.getInterpolation(progressToSate);
        }
        float insetTo = params.isStoryCell ? 0 : AndroidUtilities.lerp(
                getInset(params.prevState, params.animateFromUnreadState),
                getInset(params.currentState, params.animateFromUnreadState),
                params.progressToSate
        );
        if (insetTo == 0) {
            avatarImage.setImageCoords(params.originalAvatarRect);
        } else {
            rectTmp.set(params.originalAvatarRect);
            rectTmp.inset(insetTo, insetTo);
            avatarImage.setImageCoords(rectTmp);
        }
        if ((params.prevState == STATE_HAS_UNREAD && params.progressToSate != 1f) || params.currentState == STATE_HAS_UNREAD) {
            GradientTools gradientTools;
            if (unreadState == StoriesController.STATE_UNREAD_CLOSE_FRIEND) {
                getCloseFriendsPaint(avatarImage);
                gradientTools = closeFriendsGradientTools;
            } else {
                getActiveCirclePaint(avatarImage, params.isStoryCell);
                gradientTools = storiesGradientTools[params.isStoryCell ? 1 : 0];
            }
            boolean animateOut = params.prevState == STATE_HAS_UNREAD && params.progressToSate != 1f;

            float inset = params.isStoryCell ? -AndroidUtilities.dp(4) : 0;//AndroidUtilities.lerp(AndroidUtilities.dp(2), 0, imageScale);
            if (animateOut) {
                inset += AndroidUtilities.dp(5) * progressToSate;
                gradientTools.paint.setAlpha((int) (255 * (1f - progressToSate)));
            } else {
                gradientTools.paint.setAlpha((int) (255 * progressToSate));
                inset += AndroidUtilities.dp(5) * (1f - progressToSate);
            }
            rectTmp.set(params.originalAvatarRect);
            rectTmp.inset(inset, inset);

            drawCircleInternal(canvas, avatarImage.getParentView(), params, gradientTools.paint);
        }
        if ((params.prevState == STATE_READ && params.progressToSate != 1f) || params.currentState == STATE_READ) {
            boolean animateOut = params.prevState == STATE_READ && params.progressToSate != 1f;
            Paint paint;
            if (params.isStoryCell) {
                checkStoryCellGrayPaint(params.isArchive);
                paint = storyCellGreyPaint[params.isArchive ? 1 : 0];
            } else {
                checkGrayPaint();
                paint = grayPaint;
            }
            Paint unreadPaint = null;
            Paint closeFriendsPaint = null;
            if (params.drawSegments) {
                unreadPaint = getActiveCirclePaint(avatarImage, params.isStoryCell);
                unreadPaint.setAlpha(255);
                closeFriendsPaint = getCloseFriendsPaint(avatarImage);
                closeFriendsPaint.setAlpha(255);
                checkGrayPaint();
            }
            float inset;
            if (params.drawSegments) {
                inset = params.isStoryCell ? -AndroidUtilities.dpf2(3.5f) : 0;
            } else {
                inset = params.isStoryCell ? -AndroidUtilities.dpf2(2.7f) : 0;
            }
            if (animateOut) {
                inset += AndroidUtilities.dp(5) * progressToSate;
                paint.setAlpha((int) (255 * (1f - progressToSate)));
            } else {
                paint.setAlpha((int) (255 * progressToSate));
                inset += AndroidUtilities.dp(5) * (1f - progressToSate);
            }
            rectTmp.set(params.originalAvatarRect);
            rectTmp.inset(inset, inset);
            if (params.drawSegments) {
                checkGrayPaint();
                checkStoryCellGrayPaint(params.isArchive);
                int globalState;
                if (params.crossfadeToDialog != 0) {
                    globalState = storiesController.getUnreadState(params.crossfadeToDialog);
                } else {
                    globalState = storiesController.getUnreadState(dialogId);
                }

                params.globalState = globalState == StoriesController.STATE_READ ? STATE_READ : STATE_HAS_UNREAD;
                TLRPC.TL_userStories userStories = storiesController.getStories(params.dialogId);
                int storiesCount;
                if (params.drawHiddenStoriesAsSegments) {
                    storiesCount = storiesController.getHiddenList().size();
                } else {
                    storiesCount = userStories == null || userStories.stories.size() == 1 ? 1 : userStories.stories.size();
                }
                Paint globalPaint;
                if (globalState == StoriesController.STATE_UNREAD_CLOSE_FRIEND) {
                    getCloseFriendsPaint(avatarImage);
                    globalPaint = closeFriendsGradientTools.paint;
                } else if (globalState == StoriesController.STATE_UNREAD) {
                    getActiveCirclePaint(avatarImage, params.isStoryCell);
                    globalPaint = storiesGradientTools[params.isStoryCell ? 1 : 0].paint;
                } else {
                    globalPaint = params.isStoryCell ? storyCellGreyPaint[params.isArchive ? 1 : 0] : grayPaint;
                }
                if (storiesCount == 1) {
                    Paint localPaint = paint;
                    if (storiesController.hasUnreadStories(dialogId)) {
                        localPaint = unreadPaint;
                    }
                    float startAngle = -90;
                    float endAngle = 90;
                    drawSegment(canvas, rectTmp, localPaint, startAngle, endAngle, params);
                    startAngle = 90;
                    endAngle = 270;
                    drawSegment(canvas, rectTmp, localPaint, startAngle, endAngle, params);

                    if (params.progressToSegments != 1 && localPaint != globalPaint) {
                        globalPaint.setAlpha((int) (255 * (1f - params.progressToSegments)));
                        startAngle = -90;
                        endAngle = 90;
                        drawSegment(canvas, rectTmp, globalPaint, startAngle, endAngle, params);
                        startAngle = 90;
                        endAngle = 270;
                        drawSegment(canvas, rectTmp, globalPaint, startAngle, endAngle, params);
                        globalPaint.setAlpha(255);
                    }
                    // canvas.drawCircle(rectTmp.centerX(), rectTmp.centerY(), rectTmp.width() / 2f, localPaint);
                } else {
                    float step = 360 / (float) storiesCount;
                    int gap = storiesCount > 20 ? 3 : 5;
                    float gapLen = gap * params.progressToSegments;
                    if (gapLen > step) {
                        gapLen = 0;//step * 0.4f;
                    }


                    int maxUnread = params.drawHiddenStoriesAsSegments ? 0 : Math.max(userStories.max_read_id, storiesController.dialogIdToMaxReadId.get(dialogId, 0));
                    for (int i = 0; i < storiesCount; i++) {
                        Paint segmentPaint = params.isStoryCell ? storyCellGreyPaint[params.isArchive ? 1 : 0] : grayPaint;
                        if (params.drawHiddenStoriesAsSegments) {
                            int userUnreadState = storiesController.getUnreadState(storiesController.getHiddenList().get(storiesCount - 1 - i).user_id);
                            if (userUnreadState == StoriesController.STATE_UNREAD_CLOSE_FRIEND) {
                                segmentPaint = closeFriendsPaint;
                            } else if (userUnreadState == StoriesController.STATE_UNREAD) {
                                segmentPaint = unreadPaint;
                            }
                        } else {
                            if (userStories.stories.get(i).justUploaded || userStories.stories.get(i).id > maxUnread) {
                                if (userStories.stories.get(i).close_friends) {
                                    segmentPaint = closeFriendsPaint;
                                } else {
                                    segmentPaint = unreadPaint;
                                }
                            }
                        }
                        float startAngle = step * i - 90;
                        float endAngle = startAngle + step;
                        startAngle += gapLen;
                        endAngle -= gapLen;

                        drawSegment(canvas, rectTmp, segmentPaint, startAngle, endAngle, params);
                        if (params.progressToSegments != 1 && segmentPaint != globalPaint) {
                            float strokeWidth = globalPaint.getStrokeWidth();
                            //globalPaint.setStrokeWidth(AndroidUtilities.lerp(segmentPaint.getStrokeWidth(), strokeWidth, 1f - params.progressToSegments));
                            globalPaint.setAlpha((int) (255 * (1f - params.progressToSegments)));
                            drawSegment(canvas, rectTmp, globalPaint, startAngle, endAngle, params);
                            //  globalPaint.setStrokeWidth(strokeWidth);
                            globalPaint.setAlpha(255);
                        }
                    }
                }
            } else {
                drawCircleInternal(canvas, avatarImage.getParentView(), params, paint);
            }
        }
        if ((params.prevState == STATE_PROGRESS && params.progressToSate != 1f) || params.currentState == STATE_PROGRESS) {
            Paint paint;
            if (params.animateFromUnreadState == STATE_HAS_UNREAD) {
                getActiveCirclePaint(avatarImage, params.isStoryCell);
                paint = storiesGradientTools[params.isStoryCell ? 1 : 0].paint;
            } else {
                checkGrayPaint();
                paint = grayPaint;
            }
            paint.setAlpha((int) (255 * progressToSate));
            float inset = 0;
            boolean animateOut = params.prevState == STATE_PROGRESS && params.progressToSate != 1f;
            if (animateOut) {
                inset += AndroidUtilities.dp(7) * progressToSate;
                paint.setAlpha((int) (255 * (1f - progressToSate)));
            } else {
                paint.setAlpha((int) (255 * progressToSate));
                inset += AndroidUtilities.dp(5) * (1f - progressToSate);
            }
            rectTmp.set(params.originalAvatarRect);
            rectTmp.inset(inset, inset);
            drawProgress(canvas, params, avatarImage.getParentView(), paint);
        }

        avatarImage.draw(canvas);

        if (params.progressToSate != 1f) {
            params.progressToSate += AndroidUtilities.screenRefreshTime / 250;
            if (params.progressToSate > 1f) {
                params.progressToSate = 1f;
            }
            if (avatarImage.getParentView() != null) {
                avatarImage.invalidate();
                avatarImage.getParentView().invalidate();
            }
        }
        if (restoreCount != 0) {
            canvas.restoreToCount(restoreCount);
        }
    }

    private static int getPredictiveUnreadState(StoriesController storiesController, long dialogId) {
        TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
        if (dialogId != UserConfig.getInstance(UserConfig.selectedAccount).clientUserId && user != null && user.stories_max_id > 0 && !user.stories_unavailable) {
            int maxReadId = storiesController.dialogIdToMaxReadId.get(dialogId, 0);
            if (user.stories_max_id > maxReadId) {
                return STATE_HAS_UNREAD;
            } else {
                return STATE_READ;
            }
        } else {
            return STATE_EMPTY;
        }
    }

    private static void drawProgress(Canvas canvas, AvatarStoryParams params, View view, Paint paint) {
        float len = 360 / (float) ANIMATION_SEGMENT_COUNT;
        params.updateProgressParams();
        view.invalidate();

        if (params.inc) {
            canvas.drawArc(rectTmp, params.globalAngle, 360 * params.sweepAngle, false, paint);
        } else {
            canvas.drawArc(rectTmp, params.globalAngle + 360, -360 * (params.sweepAngle), false, paint);
        }

        for (int i = 0; i < ANIMATION_SEGMENT_COUNT; i++) {
            float startAngle = i * len + 10;
            float endAngle = startAngle + len - 10;
            float segmentLen = endAngle - startAngle;
            canvas.drawArc(rectTmp, params.globalAngle + startAngle, segmentLen, false, paint);
        }
    }

    private static void checkStoryCellGrayPaint(boolean isArchive) {
        int index = isArchive ? 1 : 0;
        if (storyCellGreyPaint[index] == null) {
            storyCellGreyPaint[index] = new Paint(Paint.ANTI_ALIAS_FLAG);
            storyCellGreyPaint[index].setStyle(Paint.Style.STROKE);
            storyCellGreyPaint[index].setStrokeWidth(AndroidUtilities.dpf2(1.3f));
            storyCellGreyPaint[index].setStrokeCap(Paint.Cap.ROUND);
        }
        int color = !isArchive ? Theme.getColor(Theme.key_actionBarDefault) : Theme.getColor(Theme.key_actionBarDefaultArchived);
        if (storyCellGrayLastColor != color) {
            storyCellGrayLastColor = color;
            float brightness = AndroidUtilities.computePerceivedBrightness(color);
            final boolean isDark = brightness < 0.721f;
            if (isDark) {
                if (brightness < 0.25f) {
                    storyCellGreyPaint[index].setColor(ColorUtils.blendARGB(color, Color.WHITE, 0.2f));
                } else {
                    storyCellGreyPaint[index].setColor(ColorUtils.blendARGB(color, Color.WHITE, 0.44f));
                }
            } else {
                storyCellGreyPaint[index].setColor(ColorUtils.blendARGB(color, Color.BLACK, 0.2f));
            }
        }
    }

    private static void checkGrayPaint() {
        if (grayPaint == null) {
            grayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            grayPaint.setStyle(Paint.Style.STROKE);
            grayPaint.setStrokeWidth(AndroidUtilities.dpf2(1.3f));
            grayPaint.setStrokeCap(Paint.Cap.ROUND);
        }
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (grayLastColor != color) {
            grayLastColor = color;
            float brightness = AndroidUtilities.computePerceivedBrightness(color);
            final boolean isDark = brightness < 0.721f;
            if (isDark) {
                if (brightness < 0.25f) {
                    grayPaint.setColor(ColorUtils.blendARGB(color, Color.WHITE, 0.2f));
                } else {
                    grayPaint.setColor(ColorUtils.blendARGB(color, Color.WHITE, 0.44f));
                }
            } else {
                grayPaint.setColor(ColorUtils.blendARGB(color, Color.BLACK, 0.2f));
            }
        }
    }

    private static void drawCircleInternal(Canvas canvas, View view, AvatarStoryParams params, Paint paint) {
        if (params.progressToArc == 0) {
            canvas.drawCircle(rectTmp.centerX(), rectTmp.centerY(), rectTmp.width() / 2f, paint);
        } else {
            canvas.drawArc(rectTmp, 360 + params.progressToArc / 2f, 360 - params.progressToArc, false, paint);
        }
    }

    private static void drawSegment(Canvas canvas, RectF rectTmp, Paint paint, float startAngle, float endAngle, AvatarStoryParams params) {
        if (!params.isFirst && !params.isLast) {
            if (startAngle < 90) {
                drawArcExcludeArc(canvas, rectTmp, paint, startAngle, endAngle, -params.progressToArc / 2, params.progressToArc / 2);
            } else {
                drawArcExcludeArc(canvas, rectTmp, paint, startAngle, endAngle, -params.progressToArc / 2 + 180, params.progressToArc / 2 + 180);
            }
        } else if (params.isLast) {
            drawArcExcludeArc(canvas, rectTmp, paint, startAngle, endAngle, -params.progressToArc / 2 + 180, params.progressToArc / 2 + 180);
        } else if (params.isFirst) {
            // canvas.drawArc(rectTmp, startAngle, endAngle - startAngle, false, paint);
            drawArcExcludeArc(canvas, rectTmp, paint, startAngle, endAngle, -params.progressToArc / 2, params.progressToArc / 2);
        } else {
            canvas.drawArc(rectTmp, startAngle, endAngle - startAngle, false, paint);
        }
    }

    private static int getInset(int currentState, int unreadState) {
        int stateToCheck = currentState;
        if (currentState == STATE_PROGRESS) {
            stateToCheck = unreadState;
        }
        if (stateToCheck == STATE_READ) {
            return AndroidUtilities.dp(3);
        } else if (stateToCheck == STATE_HAS_UNREAD) {
            return AndroidUtilities.dp(4);
        }
        return 0;
    }

    public static Paint getActiveCirclePaint(ImageReceiver avatarImage, boolean isDialogCell) {
        int i = isDialogCell ? 1 : 0;
        if (storiesGradientTools[i] == null) {
            storiesGradientTools[i] = new GradientTools();
            storiesGradientTools[i].isDiagonal = true;
            storiesGradientTools[i].isRotate = true;
            if (isDialogCell) {
                storiesGradientTools[i].setColors(0xFF4AED55, 0xFF4DC3FF);
            } else {
                storiesGradientTools[i].setColors(0xFF39DF3C, 0xFF4DBBFF);
            }
            storiesGradientTools[i].paint.setStrokeWidth(AndroidUtilities.dpf2(2.3f));
            storiesGradientTools[i].paint.setStyle(Paint.Style.STROKE);
            storiesGradientTools[i].paint.setStrokeCap(Paint.Cap.ROUND);
        }
        storiesGradientTools[i].setBounds(avatarImage.getImageX(), avatarImage.getImageY(), avatarImage.getImageX2(), avatarImage.getImageY2());
        return storiesGradientTools[i].paint;
    }

    public static Paint getCloseFriendsPaint(ImageReceiver avatarImage) {
        if (closeFriendsGradientTools == null) {
            closeFriendsGradientTools = new GradientTools();
            closeFriendsGradientTools.isDiagonal = true;
            closeFriendsGradientTools.isRotate = true;
            closeFriendsGradientTools.setColors(0xFFC9EB38, 0xFF09C167);
            closeFriendsGradientTools.paint.setStrokeWidth(AndroidUtilities.dp(2.3f));
            closeFriendsGradientTools.paint.setStyle(Paint.Style.STROKE);
            closeFriendsGradientTools.paint.setStrokeCap(Paint.Cap.ROUND);
        }
        closeFriendsGradientTools.setBounds(avatarImage.getImageX(), avatarImage.getImageY(), avatarImage.getImageX2(), avatarImage.getImageY2());
        return closeFriendsGradientTools.paint;
    }

    public static void setStoryMiniImage(ImageReceiver imageReceiver, TLRPC.StoryItem storyItem) {
        if (storyItem == null) {
            return;
        }
        if (storyItem.media.document != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.document.thumbs, 1000);
            imageReceiver.setImage(ImageLocation.getForDocument(size, storyItem.media.document), "100_100", null, null, ImageLoader.createStripedBitmap(storyItem.media.document.thumbs), 0, null, storyItem, 0);
        } else {
            TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
            if (photo != null && photo.sizes != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 1000);
                imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), "100_100", null, null, ImageLoader.createStripedBitmap(photo.sizes), 0, null, storyItem, 0);
            } else {
                imageReceiver.clearImage();
            }
        }
    }

    public static void setImage(ImageReceiver imageReceiver, TLRPC.StoryItem storyItem) {
        setImage(imageReceiver, storyItem, "320_320");
    }

    public static void setImage(ImageReceiver imageReceiver, TLRPC.StoryItem storyItem, String filter) {
        if (storyItem == null) {
            return;
        }
        if (storyItem.media != null && storyItem.media.document != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.document.thumbs, Integer.MAX_VALUE);
            imageReceiver.setImage(ImageLocation.getForDocument(size, storyItem.media.document), filter, null, null, ImageLoader.createStripedBitmap(storyItem.media.document.thumbs), 0, null, storyItem, 0);
        } else {
            TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
            if (storyItem.media instanceof TLRPC.TL_messageMediaUnsupported) {
                Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));
                imageReceiver.setImageBitmap(bitmap);
            } else if (photo != null && photo.sizes != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), filter, null, null, ImageLoader.createStripedBitmap(photo.sizes), 0, null, storyItem, 0);
            } else {
                imageReceiver.clearImage();
            }
        }
    }

    public static void setImage(ImageReceiver imageReceiver, StoriesController.UploadingStory uploadingStory) {
        if (uploadingStory.entry.isVideo) {
            imageReceiver.setImage(ImageLocation.getForPath(uploadingStory.firstFramePath), "320_180", null, null, null, 0, null, null, 0);
        } else {
            imageReceiver.setImage(ImageLocation.getForPath(uploadingStory.path), "320_180", null, null, null, 0, null, null, 0);
        }
    }

    public static void setThumbImage(ImageReceiver imageReceiver, TLRPC.StoryItem storyItem, int w, int h) {
        if (storyItem.media != null && storyItem.media.document != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(storyItem.media.document.thumbs, AndroidUtilities.dp(Math.max(w, h)), false, null, true);
            imageReceiver.setImage(ImageLocation.getForDocument(size, storyItem.media.document), w + "_" + h, null, null, ImageLoader.createStripedBitmap(storyItem.media.document.thumbs), 0, null, storyItem, 0);
        } else {
            TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
            if (photo != null && photo.sizes != null) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.dp(Math.max(w, h)), false, null, true);
                imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), w + "_" + h, null, null, ImageLoader.createStripedBitmap(photo.sizes), 0, null, storyItem, 0);
            } else {
                imageReceiver.clearImage();
            }
        }
    }

    public static Drawable getExpiredStoryDrawable() {
        if (expiredStoryDrawable == null) {
            Bitmap bitmap = Bitmap.createBitmap(360, 180, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.GRAY);
            Canvas canvas = new Canvas(bitmap);
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(15);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 100));
            canvas.drawText("expired", 360 / 2f, 180 / 2f - 4, textPaint);
            canvas.drawText("story", 360 / 2f, 180 / 2f + 16, textPaint);
            expiredStoryDrawable = new BitmapDrawable(bitmap);
        }
        return expiredStoryDrawable;
    }

    public static CharSequence getUploadingStr(TextView textView, boolean medium, boolean edit) {
        String str;
        if (edit) {
            str = LocaleController.getString("StoryEditing", R.string.StoryEditing);
        } else {
            str = LocaleController.getString("UploadingStory", R.string.UploadingStory);
        }
        int index = str.indexOf("…");
        if (index > 0) {
            SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(str);
            UploadingDotsSpannable dotsSpannable = new UploadingDotsSpannable();
            spannableStringBuilder.setSpan(dotsSpannable, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            dotsSpannable.setParent(textView, medium);
            return spannableStringBuilder;
        } else {
            return str;
        }
    }

    public static void applyUploadingStr(SimpleTextView textView, boolean medium, boolean edit) {
        String str;
        if (edit) {
            str = LocaleController.getString("StoryEditing", R.string.StoryEditing);
        } else {
            str = LocaleController.getString("UploadingStory", R.string.UploadingStory);
        }
        int index = str.indexOf("…");
        if (index > 0) {
            SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(str);
            UploadingDotsSpannable dotsSpannable = new UploadingDotsSpannable();
            spannableStringBuilder.setSpan(dotsSpannable, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            dotsSpannable.setParent(textView, medium);
            textView.setText(spannableStringBuilder);//, animated);
        } else {
            textView.setText(str);
        }
    }

    public static void applyUploadingStr(AnimatedTextView textView, boolean medium, boolean animated) {
        String str = LocaleController.getString("UploadingStory", R.string.UploadingStory);
        int index = str.indexOf("…");
        if (index > 0) {
            SpannableStringBuilder spannableStringBuilder = SpannableStringBuilder.valueOf(str);
            UploadingDotsSpannable dotsSpannable = new UploadingDotsSpannable();
            spannableStringBuilder.setSpan(dotsSpannable, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), 0);
            dotsSpannable.setParent(textView, medium);
            textView.setText(str, animated);
        } else {
            textView.setText(str);
        }
    }

    public static CharSequence createExpiredStoryString() {
        return createExpiredStoryString(false, "ExpiredStory", R.string.ExpiredStory);
    }

    public static CharSequence createExpiredStoryString(boolean useScale, String strKey, int strRes, Object... args) {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d ").append(LocaleController.formatString(strKey, strRes, args));
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.msg_mini_bomb);
        if (useScale) {
            coloredImageSpan.setScale(0.8f);
        } else {
            coloredImageSpan.setTopOffset(-1);
        }
        spannableStringBuilder.setSpan(coloredImageSpan, 0, 1, 0);
        return spannableStringBuilder;
    }

    public static CharSequence createReplyStoryString() {
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
        spannableStringBuilder.append("d ").append(LocaleController.getString("Story", R.string.Story));
        ColoredImageSpan coloredImageSpan = new ColoredImageSpan(R.drawable.msg_mini_replystory2);
        spannableStringBuilder.setSpan(coloredImageSpan, 0, 1, 0);
        return spannableStringBuilder;
    }

    public static boolean hasExpiredViews(TLRPC.StoryItem storyItem) {
        if (storyItem == null) {
            return false;
        }
        return ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime() > storyItem.expire_date + 86400;
    }

    public static void applyViewedUser(TLRPC.StoryItem storyItem, TLRPC.User currentUser) {
        if (currentUser == null) {
            return;
        }
        if (storyItem.dialogId == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId && !hasExpiredViews(storyItem)) {
            if (storyItem.views == null) {
                storyItem.views = new TLRPC.TL_storyViews();
            }
            if (storyItem.views.views_count == 0) {
                storyItem.views.views_count = 1;
                storyItem.views.recent_viewers.add(currentUser.id);
            }
        }
    }

    public static void drawArcExcludeArc(Canvas canvas, RectF rect, Paint paint, float startAngle, float endAngle, float excludeStartAngle, float excludeAndAngle) {
        float len = endAngle - startAngle;
        float originalStart = startAngle;
        float originalEnd = endAngle;
        boolean drawn = false;
        if (startAngle < excludeStartAngle && endAngle < excludeStartAngle + len) {
            float endAngle2 = Math.min(endAngle, excludeStartAngle);
            drawn = true;
            canvas.drawArc(rect, startAngle, endAngle2 - startAngle, false, paint);
        }

        startAngle = Math.max(startAngle, excludeAndAngle);
        endAngle = Math.min(endAngle, 360 + excludeStartAngle);
        if (endAngle < startAngle) {
            if (!drawn && !(originalStart > excludeStartAngle && originalEnd < excludeAndAngle)) {
                canvas.drawArc(rect, originalStart, originalEnd - originalStart, false, paint);
            }
            return;
        }

        canvas.drawArc(rect, startAngle, endAngle - startAngle, false, paint);
    }

    public static boolean isExpired(int currentAccount, TLRPC.StoryItem newStory) {
        return ConnectionsManager.getInstance(currentAccount).getCurrentTime() > newStory.expire_date;
    }

    public static String getStoryImageFilter() {
        int maxWidth = Math.max(AndroidUtilities.getRealScreenSize().x, AndroidUtilities.getRealScreenSize().y);
        int filterSize = (int) (maxWidth / AndroidUtilities.density);
        return filterSize + "_" + filterSize;
    }


    public static class AvatarStoryParams {
        public boolean drawSegments = true;
        public boolean animate = true;
        public int storyId;
        public TLRPC.StoryItem storyItem;
        public float progressToSegments = 1f;
        public float progressToArc = 0;
        public boolean isLast;
        public boolean isFirst;
        public int globalState;
        public boolean isArchive;
        public boolean forceAnimateProgressToSegments;
        public int prevUnreadState;
        public int unreadState;
        public int animateFromUnreadState;
        public boolean drawHiddenStoriesAsSegments;
        public long crossfadeToDialog;
        public float crossfadeToDialogProgress;

        private long dialogId;
        public int currentState;
        public int prevState;
        public float progressToSate = 1f;
        public boolean showProgress = false;

        private final boolean isStoryCell;
        public RectF originalAvatarRect = new RectF();

        ButtonBounce buttonBounce;
        public boolean allowLongress = false;

        public AvatarStoryParams(boolean isStoryCell) {
            this.isStoryCell = isStoryCell;
        }
        float sweepAngle;
        boolean inc;
        float globalAngle;
        boolean pressed;
        UserStoriesLoadOperation operation;

        private void updateProgressParams() {
            if (inc) {
                sweepAngle += 16 / 1000f;
                if (sweepAngle >= 1f) {
                    sweepAngle = 1f;
                    inc = false;
                }
            } else {
                sweepAngle -= 16 / 1000f;
                if (sweepAngle < 0) {
                    sweepAngle = 0;
                    inc = true;
                }
            }
            globalAngle += 16 / 5000f * 360;
        }

        float startX, startY;
        Runnable longPressRunnable;
        public View child;

        public boolean checkOnTouchEvent(MotionEvent event, View view) {
            child = view;
            StoriesController storiesController = MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController();
            if (event.getAction() == MotionEvent.ACTION_DOWN && originalAvatarRect.contains(event.getX(), event.getY())) {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(dialogId);
                boolean hasStories;
                if (drawHiddenStoriesAsSegments) {
                    hasStories = storiesController.hasHiddenStories();
                } else {
                    hasStories = (MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController().hasStories(dialogId) || user != null && !user.stories_unavailable && user.stories_max_id > 0);
                }
                if (dialogId != UserConfig.getInstance(UserConfig.selectedAccount).clientUserId && hasStories) {
                    if (buttonBounce == null) {
                        buttonBounce = new ButtonBounce(view, 1.5f);
                    } else {
                        buttonBounce.setView(view);
                    }
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    buttonBounce.setPressed(true);
                    pressed = true;
                    startX = event.getX();
                    startY = event.getY();
                    if (allowLongress) {
                        if (longPressRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        }
                        AndroidUtilities.runOnUIThread(longPressRunnable = () -> {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            if (buttonBounce != null) {
                                buttonBounce.setPressed(false);
                            }
                            ViewParent parent = view.getParent();
                            if (parent instanceof ViewGroup) {
                                ((ViewGroup) parent).requestDisallowInterceptTouchEvent(false);
                            }
                            pressed = false;
                            onLongPress();
                        }, ViewConfiguration.getLongPressTimeout());
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_MOVE && pressed) {
                if (Math.abs(startX - event.getX()) > AndroidUtilities.touchSlop || Math.abs(startY - event.getY()) > AndroidUtilities.touchSlop) {
                    if (buttonBounce != null) {
                        buttonBounce.setView(view);
                        buttonBounce.setPressed(false);
                    }
                    if (longPressRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    }
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    pressed = false;
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (buttonBounce != null) {
                    buttonBounce.setView(view);
                    buttonBounce.setPressed(false);
                }
                if (pressed && event.getAction() == MotionEvent.ACTION_UP) {
                    processOpenStory(view);
                }
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).requestDisallowInterceptTouchEvent(false);
                }
                pressed = false;
                if (longPressRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                }
            }
            return pressed;
        }

        public void onLongPress() {

        }

        private void processOpenStory(View view) {
            int currentAccount = UserConfig.selectedAccount;
            MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
            StoriesController storiesController = messagesController.getStoriesController();
            if (drawHiddenStoriesAsSegments) {
                openStory(0, null);
                return;
            }
            if (dialogId != UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
                if (storiesController.hasStories(dialogId)) {
                    openStory(dialogId, null);
                    return;
                }
                TLRPC.User user = messagesController.getUser(dialogId);
                if (user != null && !user.stories_unavailable && user.stories_max_id > 0) {
                    UserStoriesLoadOperation operation = new UserStoriesLoadOperation();
                    operation.load(dialogId, view, this);
                    return;
                }
            }
        }

        public void openStory(long dialogId, Runnable onDone) {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null && child != null) {
                fragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
                fragment.getOrCreateStoryViewer().open(fragment.getContext(), dialogId, StoriesListPlaceProvider.of((RecyclerListView) child.getParent()));
            }
        }

        public float getScale() {
            return buttonBounce == null ? 1f : buttonBounce.getScale(0.08f);
        }

        public void reset() {
            if (operation != null) {
                operation.cancel();
                operation = null;
            }
            buttonBounce = null;
            pressed = false;
        }

        public void onDetachFromWindow() {
            reset();
        }
    }

    public static class UserStoriesLoadOperation {

        int guid = ConnectionsManager.generateClassGuid();
        long dialogId;
        private int currentAccount;
        AvatarStoryParams params;
        View view;

        boolean canceled;
        int reqId;

        void load(long dialogId, View view, AvatarStoryParams params) {
            currentAccount = UserConfig.selectedAccount;
            this.dialogId = dialogId;
            this.params = params;
            this.view = view;
            MessagesController messagesController = MessagesController.getInstance(UserConfig.selectedAccount);
            StoriesController storiesController = messagesController.getStoriesController();

            storiesController.setLoading(dialogId, true);
            view.invalidate();

            TLRPC.User user = messagesController.getUser(dialogId);

            TLRPC.TL_stories_getUserStories req = new TLRPC.TL_stories_getUserStories();
            req.user_id = MessagesController.getInstance(currentAccount).getInputUser(dialogId);
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                boolean openned = false;
                boolean finished = true;
                if (response != null) {
                    TLRPC.TL_stories_userStories stories_userStories = (TLRPC.TL_stories_userStories) response;
                    MessagesController.getInstance(currentAccount).putUsers(stories_userStories.users, false);
                    TLRPC.TL_userStories stories = stories_userStories.stories;
                    if (!stories.stories.isEmpty()) {
                        MessagesController.getInstance(currentAccount).getStoriesController().putStories(dialogId, stories);
                        finished = false;
                        ensureStoryFileLoaded(stories, () -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                view.invalidate();
                                MessagesController.getInstance(currentAccount).getStoriesController().setLoading(dialogId, false);
                            }, 500);
                            params.openStory(dialogId, null);
                        });
                    }
                }
                if (!openned) {
                    TLRPC.User user2 = messagesController.getUser(dialogId);
                    user2.stories_unavailable = true;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(Collections.singletonList(user2), null, false, true);
                    messagesController.putUser(user2, false);
                }

                if (finished) {
                    view.invalidate();
                    MessagesController.getInstance(currentAccount).getStoriesController().setLoading(dialogId, false);
                }
            }));
        }

        private void ensureStoryFileLoaded(TLRPC.TL_userStories stories, Runnable onDoneOrTimeout) {
            if (stories == null || stories.stories.isEmpty()) {
                onDoneOrTimeout.run();
                return;
            }
            TLRPC.StoryItem storyItem = null;
            int maxReadId = MessagesController.getInstance(currentAccount).storiesController.dialogIdToMaxReadId.get(stories.user_id);

            for (int i = 0; i < stories.stories.size(); i++) {
                if (stories.stories.get(i).id > maxReadId) {
                    storyItem = stories.stories.get(i);
                    break;
                }
            }
            if (storyItem == null) {
                storyItem = stories.stories.get(0);
            }
            Runnable[] runnableRef = new Runnable[1];
            runnableRef[0] = () -> {
                runnableRef[0] = null;
                onDoneOrTimeout.run();
            };

            AndroidUtilities.runOnUIThread(runnableRef[0], 1000);

            ImageReceiver imageReceiver = new ImageReceiver() {
                @Override
                protected boolean setImageBitmapByKey(Drawable drawable, String key, int type, boolean memCache, int guid) {
                    boolean res = super.setImageBitmapByKey(drawable, key, type, memCache, guid);
                    if (runnableRef[0] != null) {
                        AndroidUtilities.cancelRunOnUIThread(runnableRef[0]);
                        onDoneOrTimeout.run();
                    }
                    AndroidUtilities.runOnUIThread(this::onDetachedFromWindow);
                    return res;
                }
            };
            imageReceiver.setAllowLoadingOnAttachedOnly(true);
            imageReceiver.onAttachedToWindow();

            String filter = getStoryImageFilter();

            if (storyItem.media != null && storyItem.media.document != null) {
                imageReceiver.setImage(ImageLocation.getForDocument(storyItem.media.document), filter + "_pframe", null, null, null, 0, null, storyItem, 0);
            } else {
                TLRPC.Photo photo = storyItem.media != null ? storyItem.media.photo : null;
                if (photo != null && photo.sizes != null) {
                    TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, Integer.MAX_VALUE);
                    imageReceiver.setImage(null, null, ImageLocation.getForPhoto(size, photo), filter, null, null, null, 0, null, storyItem, 0);
                } else {
                    onDoneOrTimeout.run();
                    return;
                }
            }
        }

        void cancel() {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false);
            canceled = true;
            params = null;
        }
    }
}
