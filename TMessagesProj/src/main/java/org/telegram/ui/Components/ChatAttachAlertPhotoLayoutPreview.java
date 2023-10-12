package org.telegram.ui.Components;

import static org.telegram.messenger.MessageObject.POSITION_FLAG_BOTTOM;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_LEFT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_RIGHT;
import static org.telegram.messenger.MessageObject.POSITION_FLAG_TOP;
import static org.telegram.ui.Components.UndoView.ACTION_PREVIEW_MEDIA_DESELECTED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatAttachAlertPhotoLayoutPreview extends ChatAttachAlert.AttachAlertLayout {

    private final long durationMultiplier = 1;

    public float getPreviewScale() {
        // preview is 80% of real message size
        return AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x ? .8f : .45f;
    }

    private Theme.ResourcesProvider themeDelegate;

    public RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private PreviewGroupsView groupsView;
    private UndoView undoView;
    public TextView header;

    private float draggingCellTouchX = 0, draggingCellTouchY = 0;
    private float draggingCellTop = 0, draggingCellLeft = 0;
    private float draggingCellFromWidth = 0, draggingCellFromHeight = 0;

    private PreviewGroupsView.PreviewGroupCell.MediaCell draggingCell = null;
    private boolean draggingCellHiding = false;
    private ValueAnimator draggingAnimator;
    private float draggingCellGroupY = 0;

    private Drawable videoPlayImage;

    public ChatAttachAlertPhotoLayoutPreview(ChatAttachAlert alert, Context context, Theme.ResourcesProvider themeDelegate) {
        super(alert, context, themeDelegate);

        this.themeDelegate = themeDelegate;
        setWillNotDraw(false);

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        header = new TextView(context);
        ActionBarMenuItem dropDownContainer = new ActionBarMenuItem(context, menu, 0, 0, resourcesProvider) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setText(header.getText());
            }
        };
        parentAlert.actionBar.addView(dropDownContainer, 0, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, AndroidUtilities.isTablet() ? 64 : 56, 0, 40, 0));

        header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.setGravity(Gravity.LEFT);
        header.setSingleLine(true);
        header.setLines(1);
        header.setMaxLines(1);
        header.setEllipsize(TextUtils.TruncateAt.END);
        header.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        header.setText(LocaleController.getString("AttachMediaPreview", R.string.AttachMediaPreview));
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        header.setPadding(0, 0, AndroidUtilities.dp(10), 0);
        header.setAlpha(0);
        dropDownContainer.addView(header, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        listView = new RecyclerListView(context, resourcesProvider) {

            @Override
            public void onScrolled(int dx, int dy) {
                ChatAttachAlertPhotoLayoutPreview.this.invalidate();
                parentAlert.updateLayout(ChatAttachAlertPhotoLayoutPreview.this, true, dy);
                groupsView.onScroll();
                super.onScrolled(dx, dy);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (draggingCell != null) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (draggingCell != null) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        listView.setAdapter(new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerListView.Holder(groupsView);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                /**/
            }

            @Override
            public int getItemCount() {
                return 1;
            }
        });
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setClipChildren(false);
        listView.setClipToPadding(false);
        listView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(46));

        groupsView = new PreviewGroupsView(context);
        groupsView.setClipToPadding(true);
        groupsView.setClipChildren(true);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        this.photoLayout = parentAlert.getPhotoLayout();
        groupsView.deletedPhotos.clear();
        groupsView.fromPhotoLayout(photoLayout);

        undoView = new UndoView(context, null, false, parentAlert.parentThemeDelegate);
        undoView.setEnterOffsetMargin(AndroidUtilities.dp(8 + 24));
        addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 52));

        videoPlayImage = context.getResources().getDrawable(R.drawable.play_mini_video);
    }

    public void startMediaCrossfade() {
        for (PreviewGroupsView.PreviewGroupCell cell : groupsView.groupCells) {
            for (PreviewGroupsView.PreviewGroupCell.MediaCell mediaCell : cell.media) {
                mediaCell.startCrossfade();
            }
        }
    }

    public void invalidateGroupsView() {
        groupsView.invalidate();
    }

    private ViewPropertyAnimator headerAnimator;
    private ChatAttachAlertPhotoLayout photoLayout;
    private boolean shown = false;

    @Override
    void onShow(ChatAttachAlert.AttachAlertLayout previousLayout) {
        shown = true;
        if (previousLayout instanceof ChatAttachAlertPhotoLayout) {
            this.photoLayout = (ChatAttachAlertPhotoLayout) previousLayout;
            groupsView.deletedPhotos.clear();
            groupsView.fromPhotoLayout(photoLayout);
            groupsView.requestLayout();

            layoutManager.scrollToPositionWithOffset(0, 0);
            Runnable setScrollY = () -> {
                int currentItemTop = previousLayout.getCurrentItemTop(),
                        paddingTop = previousLayout.getListTopPadding();
                listView.scrollBy(0, (currentItemTop > AndroidUtilities.dp(7) ? paddingTop - currentItemTop : paddingTop));
            };
            listView.post(setScrollY);

            postDelayed(() -> {
                if (shown) {
                    if (parentAlert.getPhotoLayout() != null) {
                        parentAlert.getPhotoLayout().previewItem.setIcon(R.drawable.ic_ab_back);
                        parentAlert.getPhotoLayout().previewItem.setText(LocaleController.getString(R.string.Back));
                    }
                }
            }, 250);

            groupsView.toPhotoLayout(photoLayout, false);
        } else {
            scrollToTop();
        }

        if (headerAnimator != null) {
            headerAnimator.cancel();
        }

        headerAnimator = header.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT);
        headerAnimator.start();
    }

    @Override
    void onHide() {
        shown = false;
        if (headerAnimator != null) {
            headerAnimator.cancel();
        }
        headerAnimator = header.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        headerAnimator.start();

        if (getSelectedItemsCount() > 1) {
            if (parentAlert.getPhotoLayout() != null) {
                parentAlert.getPhotoLayout().previewItem.setIcon(R.drawable.msg_view_file);
                parentAlert.getPhotoLayout().previewItem.setText(LocaleController.getString(R.string.AttachMediaPreviewButton));
            }
        }

        groupsView.toPhotoLayout(photoLayout, true);
    }

    @Override
    int getSelectedItemsCount() {
        return groupsView.getPhotosCount();
    }

    @Override
    void onHidden() {
        draggingCell = null;
        if (undoView != null) {
            undoView.hide(false, 0);
        }
        for (PreviewGroupsView.PreviewGroupCell cell : groupsView.groupCells) {
            for (PreviewGroupsView.PreviewGroupCell.MediaCell mediaCell : cell.media) {
                if (mediaCell.wasSpoiler && mediaCell.photoEntry != null) {
                    mediaCell.photoEntry.isChatPreviewSpoilerRevealed = false;
                }
            }
        }
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(56);
    }

    @Override
    boolean shouldHideBottomButtons() {
        return true;
    }

    @Override
    void applyCaption(CharSequence text) {
        if (photoLayout != null) {
            photoLayout.applyCaption(text);
        }
    }

    private static HashMap<MediaController.PhotoEntry, Boolean> photoRotate = new HashMap<>();
    private class GroupCalculator {

        public ArrayList<MessageObject.GroupedMessagePosition> posArray = new ArrayList<>();
        public HashMap<MediaController.PhotoEntry, MessageObject.GroupedMessagePosition> positions = new HashMap<>();

        private class MessageGroupedLayoutAttempt {
            public int[] lineCounts;
            public float[] heights;

            public MessageGroupedLayoutAttempt(int i1, int i2, float f1, float f2) {
                lineCounts = new int[] {i1, i2};
                heights = new float[] {f1, f2};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, float f1, float f2, float f3) {
                lineCounts = new int[] {i1, i2, i3};
                heights = new float[] {f1, f2, f3};
            }

            public MessageGroupedLayoutAttempt(int i1, int i2, int i3, int i4, float f1, float f2, float f3, float f4) {
                lineCounts = new int[] {i1, i2, i3, i4};
                heights = new float[] {f1, f2, f3, f4};
            }
        }

        private float multiHeight(float[] array, int start, int end) {
            float sum = 0;
            for (int a = start; a < end; a++) {
                sum += array[a];
            }
            return (float) maxSizeWidth / sum;
        }

        int width, maxX, maxY;
        float height;

        ArrayList<MediaController.PhotoEntry> photos;

        public GroupCalculator(ArrayList<MediaController.PhotoEntry> photos) {
            this.photos = photos;
            calculate();
        }

        public void calculate(ArrayList<MediaController.PhotoEntry> photos) {
            this.photos = photos;
            calculate();
        }
        private final int maxSizeWidth = 1000; // was 800, made 1000 for preview

        public void calculate() {
            // TODO: copied from GroupedMessages, would be better to merge
            int firstSpanAdditionalSize = 200;

            int count = photos.size();
            posArray.clear();
            positions.clear();
            if (count == 0) {
                width = 0;
                height = 0;
                maxX = 0;
                maxY = 0;
                return;
            }
            posArray.ensureCapacity(count);

            final float maxSizeHeight = 814.0f;
            char[] proportionsArray = new char[count];
            float averageAspectRatio = 1.0f;
            boolean forceCalc = false;

            for (int a = 0; a < count; a++) {
                MediaController.PhotoEntry photo = photos.get(a);
                MessageObject.GroupedMessagePosition position = new MessageObject.GroupedMessagePosition();
                position.last = a == count - 1;

                int w = photo.cropState != null ? photo.cropState.width : photo.width,
                    h = photo.cropState != null ? photo.cropState.height : photo.height;
                boolean rotate;
                if (photoRotate.containsKey(photo)) {
                    rotate = photoRotate.get(photo);
                } else {
                    rotate = false;
                    try {
                        if (photo.isVideo) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                MediaMetadataRetriever m = new MediaMetadataRetriever();
                                m.setDataSource(photo.path);
                                String rotation = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                                rotate = rotation != null && (rotation.equals("90") || rotation.equals("270"));
                            }
                        } else {
                            ExifInterface exif = new ExifInterface(photo.path);
                            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                            switch (orientation) {
                                case ExifInterface.ORIENTATION_ROTATE_90:
                                case ExifInterface.ORIENTATION_ROTATE_270:
                                    rotate = true;
                                    break;
                                case ExifInterface.ORIENTATION_ROTATE_180:
                                default:
                                    break;
                            }
                        }
                    } catch (Exception ignore) {}
                    photoRotate.put(photo, rotate);
                }
                if (rotate) {
                    int wasW = w;
                    w = h;
                    h = wasW;
                }
                position.aspectRatio = w / (float) h;

                proportionsArray[a] = (
                    position.aspectRatio > 1.2f ? 'w' : (
                        position.aspectRatio < .8f ? 'n' : 'q'
                    )
                );

                averageAspectRatio += position.aspectRatio;

                if (position.aspectRatio > 2.0f) {
                    forceCalc = true;
                }

                positions.put(photo, position);
                posArray.add(position);
            }
            String proportions = new String(proportionsArray);

            int minHeight = AndroidUtilities.dp(120);
            int minWidth = (int) (AndroidUtilities.dp(120) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));
            int paddingsWidth = (int) (AndroidUtilities.dp(40) / (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / (float) maxSizeWidth));

            float maxAspectRatio = maxSizeWidth / maxSizeHeight;
            averageAspectRatio = averageAspectRatio / count;

            float minH = AndroidUtilities.dp(100) / maxSizeHeight;

            if (count == 1) {
                MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                int widthPx = AndroidUtilities.displaySize.x - parentAlert.getBackgroundPaddingLeft() * 2;
                float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .5f;
                position1.set(0, 0, 0, 0, 800, (widthPx * .8f) / position1.aspectRatio / maxHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);
            } else if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
                if (count == 2) {
                    MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                    MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                    if (proportions.equals("ww") && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
                        float height = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, Math.min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                        position2.set(0, 0, 1, 1, maxSizeWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                    } else if (proportions.equals("ww") || proportions.equals("qq")) {
                        int width = maxSizeWidth / 2;
                        float height = Math.round(Math.min(width / position1.aspectRatio, Math.min(width / position2.aspectRatio, maxSizeHeight))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, width, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, width, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                    } else {
                        int secondWidth = (int) Math.max(0.4f * maxSizeWidth, Math.round((maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio))));
                        int firstWidth = maxSizeWidth - secondWidth;
                        if (firstWidth < minWidth) {
                            int diff = minWidth - firstWidth;
                            firstWidth = minWidth;
                            secondWidth -= diff;
                        }

                        float height = Math.min(maxSizeHeight, Math.round(Math.min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio))) / maxSizeHeight;
                        position1.set(0, 0, 0, 0, firstWidth, height, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                        position2.set(1, 1, 0, 0, secondWidth, height, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);
                    }
                } else if (count == 3) {
                    MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                    MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                    MessageObject.GroupedMessagePosition position3 = posArray.get(2);
                    if (proportions.charAt(0) == 'n') {
                        float thirdHeight = Math.min(maxSizeHeight * 0.5f, Math.round(position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)));
                        float secondHeight = maxSizeHeight - thirdHeight;
                        int rightWidth = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.5f, Math.round(Math.min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio))));

                        int leftWidth = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - rightWidth));
                        position1.set(0, 0, 0, 1, leftWidth, 1.0f, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM | POSITION_FLAG_TOP);

                        position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                        position3.set(1, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position3.spanSize = maxSizeWidth;

                        position1.siblingHeights = new float[] {thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight};

                        position1.spanSize = maxSizeWidth - rightWidth;
                    } else {
                        float firstHeight = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, (maxSizeHeight) * 0.66f)) / maxSizeHeight;
                        position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        int width = maxSizeWidth / 2;
                        float secondHeight = Math.min(maxSizeHeight - firstHeight, Math.round(Math.min(width / position2.aspectRatio, width / position3.aspectRatio))) / maxSizeHeight;
                        if (secondHeight < minH) {
                            secondHeight = minH;
                        }
                        position2.set(0, 0, 1, 1, width, secondHeight, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, width, secondHeight, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                    }
                } else {
                    MessageObject.GroupedMessagePosition position1 = posArray.get(0);
                    MessageObject.GroupedMessagePosition position2 = posArray.get(1);
                    MessageObject.GroupedMessagePosition position3 = posArray.get(2);
                    MessageObject.GroupedMessagePosition position4 = posArray.get(3);
                    if (proportions.charAt(0) == 'w') {
                        float h0 = Math.round(Math.min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f)) / maxSizeHeight;
                        position1.set(0, 2, 0, 0, maxSizeWidth, h0, POSITION_FLAG_LEFT | POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);

                        float h = Math.round(maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio));
                        int w0 = (int) Math.max(minWidth, Math.min(maxSizeWidth * 0.4f, h * position2.aspectRatio));
                        int w2 = (int) Math.max(Math.max(minWidth, maxSizeWidth * 0.33f), h * position4.aspectRatio);
                        int w1 = maxSizeWidth - w0 - w2;
                        if (w1 < AndroidUtilities.dp(58)) {
                            int diff = AndroidUtilities.dp(58) - w1;
                            w1 = AndroidUtilities.dp(58);
                            w0 -= diff / 2;
                            w2 -= (diff - diff / 2);
                        }
                        h = Math.min(maxSizeHeight - h0, h);
                        h /= maxSizeHeight;
                        if (h < minH) {
                            h = minH;
                        }
                        position2.set(0, 0, 1, 1, w0, h, POSITION_FLAG_LEFT | POSITION_FLAG_BOTTOM);
                        position3.set(1, 1, 1, 1, w1, h, POSITION_FLAG_BOTTOM);
                        position4.set(2, 2, 1, 1, w2, h, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                    } else {
                        int w = Math.max(minWidth, Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / position4.aspectRatio)));
                        float h0 = Math.min(0.33f, Math.max(minHeight, w / position2.aspectRatio) / maxSizeHeight);
                        float h1 = Math.min(0.33f, Math.max(minHeight, w / position3.aspectRatio) / maxSizeHeight);
                        float h2 = 1.0f - h0 - h1;
                        int w0 = Math.round(Math.min(maxSizeHeight * position1.aspectRatio + paddingsWidth, maxSizeWidth - w));

                        position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, POSITION_FLAG_LEFT | POSITION_FLAG_TOP | POSITION_FLAG_BOTTOM);

                        position2.set(1, 1, 0, 0, w, h0, POSITION_FLAG_RIGHT | POSITION_FLAG_TOP);
                        position3.set(1, 1, 1, 1, w, h1, POSITION_FLAG_RIGHT);
                        position3.spanSize = maxSizeWidth;
                        position4.set(1, 1, 2, 2, w, h2, POSITION_FLAG_RIGHT | POSITION_FLAG_BOTTOM);
                        position4.spanSize = maxSizeWidth;

                        position1.spanSize = maxSizeWidth - w;
                        position1.siblingHeights = new float[] {h0, h1, h2};
                    }
                }
            } else {
                float[] croppedRatios = new float[posArray.size()];
                for (int a = 0; a < count; a++) {
                    if (averageAspectRatio > 1.1f) {
                        croppedRatios[a] = Math.max(1.0f, posArray.get(a).aspectRatio);
                    } else {
                        croppedRatios[a] = Math.min(1.0f, posArray.get(a).aspectRatio);
                    }
                    croppedRatios[a] = Math.max(0.66667f, Math.min(1.7f, croppedRatios[a]));
                }

                int firstLine;
                int secondLine;
                int thirdLine;
                int fourthLine;
                ArrayList<MessageGroupedLayoutAttempt> attempts = new ArrayList<>();
                for (firstLine = 1; firstLine < croppedRatios.length; firstLine++) {
                    secondLine = croppedRatios.length - firstLine;
                    if (firstLine > 3 || secondLine > 3) {
                        continue;
                    }
                    attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.length)));
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 1; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        thirdLine = croppedRatios.length - firstLine - secondLine;
                        if (firstLine > 3 || secondLine > (averageAspectRatio < 0.85f ? 4 : 3) || thirdLine > 3) {
                            continue;
                        }
                        attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.length)));
                    }
                }

                for (firstLine = 1; firstLine < croppedRatios.length - 2; firstLine++) {
                    for (secondLine = 1; secondLine < croppedRatios.length - firstLine; secondLine++) {
                        for (thirdLine = 1; thirdLine < croppedRatios.length - firstLine - secondLine; thirdLine++) {
                            fourthLine = croppedRatios.length - firstLine - secondLine - thirdLine;
                            if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
                                continue;
                            }
                            attempts.add(new MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.length)));
                        }
                    }
                }

                MessageGroupedLayoutAttempt optimal = null;
                float optimalDiff = 0.0f;
                float maxHeight = maxSizeWidth / 3 * 4;
                for (int a = 0; a < attempts.size(); a++) {
                    MessageGroupedLayoutAttempt attempt = attempts.get(a);
                    float height = 0;
                    float minLineHeight = Float.MAX_VALUE;
                    for (int b = 0; b < attempt.heights.length; b++){
                        height += attempt.heights[b];
                        if (attempt.heights[b] < minLineHeight) {
                            minLineHeight = attempt.heights[b];
                        }
                    }

                    float diff = Math.abs(height - maxHeight);
                    if (attempt.lineCounts.length > 1) {
                        if (attempt.lineCounts[0] > attempt.lineCounts[1] || (attempt.lineCounts.length > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.length > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
                            diff *= 1.2f;
                        }
                    }

                    if (minLineHeight < minWidth) {
                        diff *= 1.5f;
                    }

                    if (optimal == null || diff < optimalDiff) {
                        optimal = attempt;
                        optimalDiff = diff;
                    }
                }
                if (optimal == null) {
                    return;
                }

                int index = 0;

                for (int i = 0; i < optimal.lineCounts.length; i++) {
                    int c = optimal.lineCounts[i];
                    float lineHeight = optimal.heights[i];
                    int spanLeft = maxSizeWidth;
                    MessageObject.GroupedMessagePosition posToFix = null;
                    for (int k = 0; k < c; k++) {
                        float ratio = croppedRatios[index];
                        int width = (int) (ratio * lineHeight);
                        spanLeft -= width;
                        MessageObject.GroupedMessagePosition pos = posArray.get(index);
                        int flags = 0;
                        if (i == 0) {
                            flags |= POSITION_FLAG_TOP;
                        }
                        if (i == optimal.lineCounts.length - 1) {
                            flags |= POSITION_FLAG_BOTTOM;
                        }
                        if (k == 0) {
                            flags |= POSITION_FLAG_LEFT;
                            posToFix = pos;
                        }
                        if (k == c - 1) {
                            flags |= POSITION_FLAG_RIGHT;
                            posToFix = pos;
                        }
                        pos.set(k, k, i, i, width, Math.max(minH, lineHeight / maxSizeHeight), flags);
                        index++;
                    }
                    if (posToFix != null) {
                        posToFix.pw += spanLeft;
                        posToFix.spanSize += spanLeft;
                    }
                }
            }
            for (int a = 0; a < count; a++) {
                MessageObject.GroupedMessagePosition pos = posArray.get(a);
                if (pos.minX == 0) {
                    pos.spanSize += firstSpanAdditionalSize;
                }
                if ((pos.flags & POSITION_FLAG_RIGHT) != 0) {
                    pos.edge = true;
                }
                maxX = Math.max(maxX, pos.maxX);
                maxY = Math.max(maxY, pos.maxY);
                pos.left = getLeft(pos, pos.minY, pos.maxY, pos.minX);
            }
            for (int a = 0; a < count; ++a) {
                MessageObject.GroupedMessagePosition pos = posArray.get(a);
                pos.top = getTop(pos, pos.minY);
            }

            width = getWidth();
            height = getHeight();
        }

        public int getWidth() {
            int[] lineWidths = new int[10];
            Arrays.fill(lineWidths, 0);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                int width = pos.pw;
                for (int y = pos.minY; y <= pos.maxY; ++y) {
                    lineWidths[y] += width;
                }
            }
            int width = lineWidths[0];
            for (int y = 1; y < lineWidths.length; ++y) {
                if (width < lineWidths[y]) {
                    width = lineWidths[y];
                }
            }
            return width;
        }

        public float getHeight() {
            float[] lineHeights = new float[10];
            Arrays.fill(lineHeights, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                float height = pos.ph;
                for (int x = pos.minX; x <= pos.maxX; ++x) {
                    lineHeights[x] += height;
                }
            }
            float height = lineHeights[0];
            for (int y = 1; y < lineHeights.length; ++y) {
                if (height < lineHeights[y]) {
                    height = lineHeights[y];
                }
            }
            return height;
        }

        private float getLeft(MessageObject.GroupedMessagePosition except, int minY, int maxY, int minX) {
            float[] sums = new float[maxY - minY + 1];
            Arrays.fill(sums, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                if (pos != except && pos.maxX < minX) {
                    final int end = Math.min(pos.maxY, maxY) - minY;
                    for (int y = Math.max(pos.minY - minY, 0); y <= end; ++y) {
                        sums[y] += pos.pw;
                    }
                }
            }
            float max = 0;
            for (int i = 0; i < sums.length; ++i) {
                if (max < sums[i]) {
                    max = sums[i];
                }
            }
            return max;
        }

        private float getTop(MessageObject.GroupedMessagePosition except, int minY) {
            float[] sums = new float[maxX + 1];
            Arrays.fill(sums, 0f);
            final int count = posArray.size();
            for (int i = 0; i < count; ++i) {
                MessageObject.GroupedMessagePosition pos = posArray.get(i);
                if (pos != except && pos.maxY < minY) {
                    for (int x = pos.minX; x <= pos.maxX; ++x) {
                        sums[x] += pos.ph;
                    }
                }
            }
            float max = 0;
            for (int i = 0; i < sums.length; ++i) {
                if (max < sums[i]) {
                    max = sums[i];
                }
            }
            return max;
        }
    }

    @Override
    int getListTopPadding() {
        return listView.getPaddingTop()/* + AndroidUtilities.dp(9)*/;
    }

    @Override
    int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(listView.getPaddingTop());
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = AndroidUtilities.dp(8);
        if (top >= AndroidUtilities.dp(8) && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        listView.setTopGlowOffset(newOffset);
        return newOffset;
    }

    private int paddingTop;

    @Override
    public void onPreMeasure(int availableWidth, int availableHeight) {
        ignoreLayout = true;

        LayoutParams layoutParams = (LayoutParams) getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();

        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= AndroidUtilities.dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
//        paddingTop -= AndroidUtilities.dp(9);
        if (listView.getPaddingTop() != paddingTop) {
            listView.setPadding(listView.getPaddingLeft(), paddingTop, listView.getPaddingRight(), listView.getPaddingBottom());
            invalidate();
        }

        header.setTextSize(!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 18 : 20);
        ignoreLayout = false;
    }

    @Override
    void scrollToTop() {
//        scrollView.smoothScrollTo(0, 0);
        listView.smoothScrollToPosition(0);
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    @Override
    boolean onBackPressed() {
        parentAlert.updatePhotoPreview(false);
        return true;
    }

    private boolean ignoreLayout = false;
    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    void onMenuItemClick(int id) {
        try {
            parentAlert.getPhotoLayout().onMenuItemClick(id);
        } catch (Exception ignore) {}
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean restore = false;
        if (parentAlert.parentThemeDelegate != null) {
            Drawable chatBackgroundDrawable = parentAlert.parentThemeDelegate.getWallpaperDrawable();
            if (chatBackgroundDrawable != null) {
                int paddingTop = getCurrentItemTop();
                int finalMove;
                if (AndroidUtilities.isTablet()) {
                    finalMove = 16;
                } else if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    finalMove = 6;
                } else {
                    finalMove = 12;
                }
                if (paddingTop < ActionBar.getCurrentActionBarHeight()) {
                    paddingTop -= AndroidUtilities.dp((1f - paddingTop / (float) ActionBar.getCurrentActionBarHeight()) * finalMove);
                }
                paddingTop = Math.max(0, paddingTop);
                canvas.save();
                canvas.clipRect(0, paddingTop, getWidth(), getHeight());
                chatBackgroundDrawable.setBounds(0, paddingTop, getWidth(), paddingTop + AndroidUtilities.displaySize.y);
                chatBackgroundDrawable.draw(canvas);
                restore = true;
            }
        }
        super.dispatchDraw(canvas);
        if (restore) {
            canvas.restore();
        }
    }

    private boolean isPortrait = AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x;
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        boolean isPortrait = AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x;
        if (this.isPortrait != isPortrait) {
            this.isPortrait = isPortrait;
            final int groupCellsCount = groupsView.groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                PreviewGroupsView.PreviewGroupCell groupCell = groupsView.groupCells.get(i);
                if (groupCell.group.photos.size() == 1) {
                    groupCell.setGroup(groupCell.group, true);
                }
            }
        }
    }

    @Override
    void onSelectedItemsCountChanged(int count) {
        if (count > 1) {
            parentAlert.selectedMenuItem.showSubItem(ChatAttachAlertPhotoLayout.group);
        } else {
            parentAlert.selectedMenuItem.hideSubItem(ChatAttachAlertPhotoLayout.group);
        }
    }

    private class PreviewGroupsView extends ViewGroup {

        private ChatActionCell hintView;

        public PreviewGroupsView(Context context) {
            super(context);
            setWillNotDraw(false);

            hintView = new ChatActionCell(context, true, themeDelegate);
            hintView.setCustomText(LocaleController.getString("AttachMediaDragHint", R.string.AttachMediaDragHint));
            addView(hintView);
        }

        @Override
        protected void onLayout(boolean b, int i, int i1, int i2, int i3) {
            hintView.layout(0, 0, hintView.getMeasuredWidth(), hintView.getMeasuredHeight());
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            return false;
        }

        private ArrayList<PreviewGroupCell> groupCells = new ArrayList<>();

        private HashMap<Object, Object> deletedPhotos = new HashMap<>();
        public void saveDeletedImageId(MediaController.PhotoEntry photo) {
            if (photoLayout == null) {
                return;
            }
            HashMap<Object, Object> photosMap = photoLayout.getSelectedPhotos();

            List<Map.Entry<Object, Object>> entries = new ArrayList<>(photosMap.entrySet());
            final int entriesCount = entries.size();
            for (int i = 0; i < entriesCount; ++i) {
                if (entries.get(i).getValue() == photo) {
                    deletedPhotos.put(photo, entries.get(i).getKey());
                    break;
                }
            }
        }

        public void fromPhotoLayout(ChatAttachAlertPhotoLayout photoLayout) {
            photosOrder = photoLayout.getSelectedPhotosOrder();
            photosMap = photoLayout.getSelectedPhotos();
            fromPhotoArrays();
        }

        public void fromPhotoArrays() {
            groupCells.clear();
            ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
            final int photosOrderSize = photosOrder.size(),
                    photosOrderLast = photosOrderSize - 1;
            for (int i = 0; i < photosOrderSize; ++i) {
                int imageId = (Integer) photosOrder.get(i);
                photos.add((MediaController.PhotoEntry) photosMap.get(imageId));
                if (i % 10 == 9 || i == photosOrderLast) {
                    PreviewGroupCell groupCell = new PreviewGroupCell();
                    groupCell.setGroup(new GroupCalculator(photos), false);
                    groupCells.add(groupCell);
                    photos = new ArrayList<>();
                }
            }
        }

        HashMap<Object, Object> photosMap;
        List<Map.Entry<Object, Object>> photosMapKeys;
        HashMap<Object, Object> selectedPhotos;
        ArrayList<Object> photosOrder;

        public void calcPhotoArrays() {
            photosMap = photoLayout.getSelectedPhotos();
            photosMapKeys = new ArrayList<>(photosMap.entrySet());
            selectedPhotos = new HashMap<>();
            photosOrder = new ArrayList<>();

            final int groupCellsCount = groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                PreviewGroupCell groupCell = groupCells.get(i);
                GroupCalculator group = groupCell.group;
                if (group.photos.size() == 0) {
                    continue;
                }
                final int photosCount = group.photos.size();
                for (int j = 0; j < photosCount; ++j) {
                    MediaController.PhotoEntry photoEntry = group.photos.get(j);
                    if (deletedPhotos.containsKey(photoEntry)) {
                        Object imageId = deletedPhotos.get(photoEntry);
                        selectedPhotos.put(imageId, photoEntry);
                        photosOrder.add(imageId);
                    } else {
                        boolean found = false;
                        for (int k = 0; k < photosMapKeys.size(); ++k) {
                            Map.Entry<Object, Object> entry = photosMapKeys.get(k);
                            Object value = entry.getValue();
                            if (value == photoEntry) {
                                Object key = entry.getKey();
                                selectedPhotos.put(key, value);
                                photosOrder.add(key);
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            for (int k = 0; k < photosMapKeys.size(); ++k) {
                                Map.Entry<Object, Object> entry = photosMapKeys.get(k);
                                Object value = entry.getValue();
                                if (
                                    value instanceof MediaController.PhotoEntry &&
                                    ((MediaController.PhotoEntry) value).path != null &&
                                    photoEntry != null &&
                                    ((MediaController.PhotoEntry) value).path.equals(photoEntry.path)
                                ) {
                                    Object key = entry.getKey();
                                    selectedPhotos.put(key, value);
                                    photosOrder.add(key);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        public void toPhotoLayout(ChatAttachAlertPhotoLayout photoLayout, boolean updateLayout) {
            int previousCount = photoLayout.getSelectedPhotosOrder().size();
            calcPhotoArrays();

            photoLayout.updateSelected(selectedPhotos, photosOrder, updateLayout);
            if (previousCount != photosOrder.size()) {
                parentAlert.updateCountButton(1);
            }
        }

        public int getPhotosCount() {
            int count = 0;
            final int groupCellsCount = groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                PreviewGroupCell groupCell = groupCells.get(i);
                if (groupCell != null && groupCell.group != null && groupCell.group.photos != null) {
                    count += groupCell.group.photos.size();
                }
            }
            return count;
        }

        public ArrayList<MediaController.PhotoEntry> getPhotos() {
            ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
            final int groupCellsCount = groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                PreviewGroupCell groupCell = groupCells.get(i);
                if (groupCell != null && groupCell.group != null && groupCell.group.photos != null) {
                    photos.addAll(groupCell.group.photos);
                }
            }
            return photos;
        }

        private int paddingTop = AndroidUtilities.dp(8 + 8);
        private int paddingBottom = AndroidUtilities.dp(32 + 32);
        private int lastMeasuredHeight = 0;

        private int measurePureHeight() {
            int height = paddingTop + paddingBottom;
            final int groupCellsCount = groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                height += groupCells.get(i).measure();
            }
            if (hintView.getMeasuredHeight() <= 0) {
                hintView.measure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST)
                );
            }
            height += hintView.getMeasuredHeight();
            return height;
        }
        private int measureHeight() {
            return Math.max(measurePureHeight(), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(8 + 46 - 9));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            hintView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST));
            if (lastMeasuredHeight <= 0) {
                lastMeasuredHeight = measureHeight();
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(MeasureSpec.getSize(heightMeasureSpec), lastMeasuredHeight), MeasureSpec.EXACTLY));
        }

        @Override
        public void invalidate() {
            int measuredHeight = measureHeight();
            if (lastMeasuredHeight != measuredHeight) {
                lastMeasuredHeight = measuredHeight;
                requestLayout();
            }
            super.invalidate();
        }

        float viewTop, viewBottom;

        boolean[] lastGroupSeen = null;
        private boolean[] groupSeen() {
            boolean[] seen = new boolean[groupCells.size()];
            float y = paddingTop;
            int scrollY = listView.computeVerticalScrollOffset();
            viewTop = Math.max(0, scrollY - getListTopPadding());
            viewBottom = listView.getMeasuredHeight() - getListTopPadding() + scrollY;
            final int groupCellsSize = groupCells.size();
            for (int i = 0; i < groupCellsSize; ++i) {
                PreviewGroupCell groupCell = groupCells.get(i);
                float height = groupCell.measure();
                seen[i] = isSeen(y, y + height);
                y += height;
            }
            return seen;
        }

        public boolean isSeen(float fromY, float toY) {
            return (fromY >= viewTop && fromY <= viewBottom) || (toY >= viewTop && toY <= viewBottom) || (fromY <= viewTop && toY >= viewBottom);
        }

        public void onScroll() {
            boolean newGroupSeen = lastGroupSeen == null;
            if (!newGroupSeen) {
                boolean[] seen = groupSeen();
                if (seen.length != lastGroupSeen.length) {
                    newGroupSeen = true;
                } else {
                    for (int i = 0; i < seen.length; ++i) {
                        if (seen[i] != lastGroupSeen[i]) {
                            newGroupSeen = true;
                            break;
                        }
                    }
                }
            } else {
                lastGroupSeen = groupSeen();
            }

            if (newGroupSeen) {
                invalidate();
            }
        }

        public void remeasure() {
            float y = paddingTop;
            int i = 0;
            final int groupCellsCount = groupCells.size();
            for (int j = 0; j < groupCellsCount; ++j) {
                PreviewGroupCell groupCell = groupCells.get(j);
                float height = groupCell.measure();
                groupCell.y = y;
                groupCell.indexStart = i;
                y += height;
                i += groupCell.group.photos.size();
            }
        }

        @Override
        public void onDraw(Canvas canvas) {
            float y = paddingTop;
            int i = 0;

            int scrollY = listView.computeVerticalScrollOffset();
            viewTop = Math.max(0, scrollY - getListTopPadding());
            viewBottom = listView.getMeasuredHeight() - getListTopPadding() + scrollY;

            canvas.save();
            canvas.translate(0, paddingTop);
            final int groupCellsCount = groupCells.size();
            for (int j = 0; j < groupCellsCount; ++j) {
                PreviewGroupCell groupCell = groupCells.get(j);
                float height = groupCell.measure();
                groupCell.y = y;
                groupCell.indexStart = i;
                boolean groupIsSeen = (y >= viewTop && y <= viewBottom) || (y+height >= viewTop && y+height <= viewBottom) || (y <= viewTop && y+height >= viewBottom);
                if (groupIsSeen && groupCell.draw(canvas)) {
                    invalidate();
                }
                canvas.translate(0, height);
                y += height;
                i += groupCell.group.photos.size();
            }
            hintView.setVisiblePart(y, hintView.getMeasuredHeight());
            if (hintView.hasGradientService()) {
                hintView.drawBackground(canvas, true);
            }
            hintView.draw(canvas);
            canvas.restore();

            if (draggingCell != null) {
                canvas.save();
                Point point = dragTranslate();
                canvas.translate(point.x, point.y);
                if (draggingCell.draw(canvas, true)) {
                    invalidate();
                }
                canvas.restore();
            }

            super.onDraw(canvas);
        }

        long tapTime = 0;
        PreviewGroupCell tapGroupCell = null;
        PreviewGroupCell.MediaCell tapMediaCell = null;

        private float draggingT = 0;
        private float savedDragFromX, savedDragFromY, savedDraggingT;
        private final Point tmpPoint = new Point();
        Point dragTranslate() {
            if (draggingCell == null) {
                tmpPoint.x = 0;
                tmpPoint.y = 0;
                return tmpPoint;
            }
            if (!draggingCellHiding) {
                RectF drawingRect = draggingCell.rect();
                RectF finalDrawingRect = draggingCell.rect(1f);
                tmpPoint.x = AndroidUtilities.lerp(
                    finalDrawingRect.left + drawingRect.width() / 2f,
                    draggingCellTouchX - (draggingCellLeft - .5f) * draggingCellFromWidth,
                    draggingT
                );
                tmpPoint.y = AndroidUtilities.lerp(
                    draggingCell.groupCell.y + finalDrawingRect.top + drawingRect.height() / 2f,
                    draggingCellTouchY - (draggingCellTop - .5f) * draggingCellFromHeight + draggingCellGroupY,
                    draggingT
                );
            } else {
                RectF drawingRect = draggingCell.rect();
                RectF finalDrawingRect = draggingCell.rect(1f);
                tmpPoint.x = AndroidUtilities.lerp(
                    finalDrawingRect.left + drawingRect.width() / 2f,
                    savedDragFromX,
                    draggingT / savedDraggingT
                );
                tmpPoint.y = AndroidUtilities.lerp(
                    draggingCell.groupCell.y + finalDrawingRect.top + drawingRect.height() / 2f,
                    savedDragFromY,
                    draggingT / savedDraggingT
                );
            }

            return tmpPoint;
        }

        void stopDragging() {
            if (draggingAnimator != null) {
                draggingAnimator.cancel();
            }

            Point dragTranslate = dragTranslate();
            savedDraggingT = draggingT;
            savedDragFromX = dragTranslate.x;
            savedDragFromY = dragTranslate.y;

            draggingCellHiding = true;

            draggingAnimator = ValueAnimator.ofFloat(savedDraggingT, 0f);
            draggingAnimator.addUpdateListener(a -> {
                draggingT = (float) a.getAnimatedValue();
                invalidate();
            });
            draggingAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    draggingCell = null;
                    draggingCellHiding = false;
                    invalidate();
                }
            });
            draggingAnimator.setDuration((long) (200 * durationMultiplier));
            draggingAnimator.start();
            invalidate();
        }
        void startDragging(PreviewGroupCell.MediaCell cell) {
            draggingCell = cell;
            draggingCellGroupY = draggingCell.groupCell.y;
            draggingCellHiding = false;
            draggingT = 0;

            invalidate();
            if (draggingAnimator != null) {
                draggingAnimator.cancel();
            }
            draggingAnimator = ValueAnimator.ofFloat(0, 1f);
            draggingAnimator.addUpdateListener(a -> {
                draggingT = (float) a.getAnimatedValue();
                invalidate();
            });
            draggingAnimator.setDuration((long) (200 * durationMultiplier));
            draggingAnimator.start();
        }

        private boolean scrollerStarted = false;
        private final Runnable scroller = new Runnable() {
            @Override
            public void run() {
                if (draggingCell == null || draggingCellHiding) {
                    return;
                }

                int scrollY = listView.computeVerticalScrollOffset();

                boolean atBottom = scrollY + listView.computeVerticalScrollExtent() >= (measurePureHeight() - paddingBottom + paddingTop);

                float top = Math.max(0, draggingCellTouchY - Math.max(0, scrollY - getListTopPadding()) - AndroidUtilities.dp(52));
                float bottom = Math.max(0, (listView.getMeasuredHeight() - (draggingCellTouchY - scrollY) - getListTopPadding()) - AndroidUtilities.dp(52 + 32));

                final float r = AndroidUtilities.dp(32);
                float dy = 0;
                if (top < r && scrollY > getListTopPadding()) {
                    dy = -(1f - top / r) * (float) AndroidUtilities.dp(6);
                } else if (bottom < r) {
                    dy = (1f - bottom / r) * (float) AndroidUtilities.dp(6);
                }

                if (Math.abs((int) dy) > 0 && listView.canScrollVertically((int) dy) && !(dy > 0 && atBottom)) {
                    draggingCellTouchY += dy;
                    listView.scrollBy(0, (int) dy);
                    invalidate();
                }

                scrollerStarted = true;
                postDelayed(this, 15);
            }
        };
        /*
        *
        *
                    if (getSelectedItemsCount() > 1) {
                        // short tap -> remove photo
                        final MediaController.PhotoEntry photo = tapMediaCell.photoEntry;
                        final int index = tapGroupCell.group.photos.indexOf(photo);
                        if (index >= 0) {
                            saveDeletedImageId(photo);
                            final PreviewGroupCell groupCell = tapGroupCell;
                            groupCell.group.photos.remove(index);
                            groupCell.setGroup(groupCell.group, true);
                            updateGroups();
                            toPhotoLayout(photoLayout, false);

                            final int currentUndoViewId = ++undoViewId;
                            undoView.showWithAction(0, ACTION_PREVIEW_MEDIA_DESELECTED, photo, null, () -> {
                                if (draggingAnimator != null) {
                                    draggingAnimator.cancel();
                                }
                                draggingCell = null;
                                draggingT = 0;
                                pushToGroup(groupCell, photo, index);
                                updateGroups();
                                toPhotoLayout(photoLayout, false);
                            });

                            postDelayed(() -> {
                                if (currentUndoViewId == undoViewId && undoView.isShown()) {
                                    undoView.hide(true, 1);
                                }
                            }, 1000 * 4);
                        }

                        if (draggingAnimator != null) {
                            draggingAnimator.cancel();
                        }
                    }
                    * */

        GroupingPhotoViewerProvider photoViewerProvider = new GroupingPhotoViewerProvider();

        class GroupingPhotoViewerProvider extends PhotoViewer.EmptyPhotoViewerProvider {
            private ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
            public void init(ArrayList<MediaController.PhotoEntry> photos) {
                this.photos = photos;
            }

            @Override
            public void onClose() {
                fromPhotoArrays();
                toPhotoLayout(photoLayout, false);
            }

            @Override
            public boolean isPhotoChecked(int index) {
                if (index < 0 || index >= photos.size()) {
                    return false;
                }
                return photosOrder.contains((Integer) photos.get(index).imageId);
            }

            @Override
            public int setPhotoChecked(int index, VideoEditedInfo videoEditedInfo) {
                if (index < 0 || index >= photos.size()) {
                    return -1;
                }
                Object imageId = photos.get(index).imageId;
                int orderIndex = photosOrder.indexOf((Integer) imageId);
                if (orderIndex >= 0) {
                    if (photosOrder.size() <= 1) {
                        return -1;
                    }
                    photosOrder.remove(orderIndex);
                    fromPhotoArrays();
                    return orderIndex;
                } else {
                    photosOrder.add(imageId);
                    fromPhotoArrays();
                    return photosOrder.size() - 1;
                }
            }

            @Override
            public int setPhotoUnchecked(Object entry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                Object imageId = photoEntry.imageId;
                if (photosOrder.size() <= 1) {
                    return -1;
                }
                int index = photosOrder.indexOf((Integer) imageId);
                if (index >= 0) {
                    photosOrder.remove(index);
                    fromPhotoArrays();
                    return index;
                }
                return -1;
            }

            @Override
            public int getSelectedCount() {
                return photosOrder.size();
            }

            @Override
            public ArrayList<Object> getSelectedPhotosOrder() {
                return photosOrder;
            }

            @Override
            public HashMap<Object, Object> getSelectedPhotos() {
                return photosMap;
            }

            @Override
            public int getPhotoIndex(int index) {
                if (index < 0 || index >= photos.size()) {
                    return -1;
                }
                MediaController.PhotoEntry photoEntry = photos.get(index);
                if (photoEntry == null) {
                    return -1;
                }
                return photosOrder.indexOf(photoEntry.imageId);
            }

            @Override
            public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview) {
                if (index < 0 || index >= photos.size() || !isPhotoChecked(index)) {
                    return null;
                }
                MediaController.PhotoEntry photoEntry = photos.get(index);
                if (photoEntry != null) {
                    PreviewGroupCell group = null;
                    PreviewGroupCell.MediaCell mediaCell = null;
                    final int groupCellsCount = groupCells.size();
                    for (int i = 0; i < groupCellsCount; ++i) {
                        group = groupCells.get(i);
                        if (group != null && group.media != null) {
                            final int count = group.media.size();
                            for (int j = 0; j < count; ++j) {
                                PreviewGroupCell.MediaCell cell = group.media.get(j);
                                if (cell != null && cell.photoEntry == photoEntry && cell.scale > .5) {
                                    mediaCell = group.media.get(j);
                                    break;
                                }
                            }
                            if (mediaCell != null) {
                                break;
                            }
                        }
                    }

                    if (group != null && mediaCell != null) {
                        PhotoViewer.PlaceProviderObject object = new PhotoViewer.PlaceProviderObject();
                        int[] coords = new int[2];
                        getLocationInWindow(coords);
                        if (Build.VERSION.SDK_INT < 26) {
                            coords[0] -= parentAlert.getLeftInset();
                        }
                        object.viewX = coords[0];
                        object.viewY = coords[1] + (int) group.y;
                        object.scale = 1;
                        object.parentView = PreviewGroupsView.this;
                        object.imageReceiver = mediaCell.image;
                        object.thumb = object.imageReceiver.getBitmapSafe();
                        object.radius = new int[4];
                        object.radius[0] = (int) mediaCell.roundRadiuses.left;
                        object.radius[1] = (int) mediaCell.roundRadiuses.top;
                        object.radius[2] = (int) mediaCell.roundRadiuses.right;
                        object.radius[3] = (int) mediaCell.roundRadiuses.bottom;
                        object.clipTopAddition = (int) (-PreviewGroupsView.this.getY());
                        object.clipBottomAddition = PreviewGroupsView.this.getHeight() - (int) (-PreviewGroupsView.this.getY() + listView.getHeight() - parentAlert.getClipLayoutBottom());
                        return object;
                    }
                }
                return null;
            }

            @Override
            public boolean cancelButtonPressed() {
                return false;
            }

            @Override
            public void updatePhotoAtIndex(int index) {
                if (index < 0 || index >= photos.size()) {
                    return;
                }
                MediaController.PhotoEntry photoEntry = photos.get(index);
                if (photoEntry == null) {
                    return;
                }
                int imageId = photoEntry.imageId;
                invalidate();
                for (int i = 0; i < groupCells.size(); ++i) {
                    PreviewGroupCell groupCell = groupCells.get(i);
                    if (groupCell != null && groupCell.media != null) {
                        for (int j = 0; j < groupCell.media.size(); ++j) {
                            PreviewGroupCell.MediaCell mediaCell = groupCell.media.get(j);
                            if (mediaCell != null && mediaCell.photoEntry.imageId == imageId) {
                                mediaCell.setImage(photoEntry);
                            }
                        }
                        boolean hadUpdates = false;
                        if (groupCell.group != null && groupCell.group.photos != null) {
                            for (int j = 0; j < groupCell.group.photos.size(); ++j) {
                                if (groupCell.group.photos.get(j).imageId == imageId) {
                                    groupCell.group.photos.set(j, photoEntry);
                                    hadUpdates = true;
                                }
                            }
                        }
                        if (hadUpdates) {
                            groupCell.setGroup(groupCell.group, true);
                        }
                    }
                }
                remeasure();
                invalidate();
            }
        };

        private int undoViewId = 0;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean result = false;

            float touchX = event.getX(),
                  touchY = event.getY();

            PreviewGroupCell touchGroupCell = null;
            PreviewGroupCell.MediaCell touchMediaCell = null;
            float groupY = 0;
            final int groupCellsCount = groupCells.size();
            for (int j = 0; j < groupCellsCount; ++j) {
                PreviewGroupCell groupCell = groupCells.get(j);
                float height = groupCell.measure();
                if (touchY >= groupY && touchY <= groupY + height) {
                    touchGroupCell = groupCell;
                    break;
                }
                groupY += height;
            }
            if (touchGroupCell != null) {
                final int mediaCount = touchGroupCell.media.size();
                for (int i = 0; i < mediaCount; ++i) {
                    PreviewGroupCell.MediaCell mediaCell = touchGroupCell.media.get(i);
                    if (mediaCell != null && mediaCell.drawingRect().contains(touchX, touchY - groupY)) {
                        touchMediaCell = mediaCell;
                        break;
                    }
                }
            }

            PreviewGroupCell draggingOverGroupCell = null;
            PreviewGroupCell.MediaCell draggingOverMediaCell = null;
            if (draggingCell != null) {
                groupY = 0;
                RectF drawingRect = draggingCell.rect();
                Point dragPoint = dragTranslate();
                RectF draggingCellXY = new RectF();
                float cx = dragPoint.x, cy = dragPoint.y;
                draggingCellXY.set(
                    cx - drawingRect.width() / 2,
                    cy - drawingRect.height() / 2,
                    cx + drawingRect.width() / 2,
                    cy + drawingRect.height() / 2
                );
                float maxLength = 0;
                for (int j = 0; j < groupCellsCount; ++j) {
                    PreviewGroupCell groupCell = groupCells.get(j);
                    float height = groupCell.measure();
                    float top = groupY, bottom = groupY + height;
                    if (bottom >= draggingCellXY.top && draggingCellXY.bottom >= top) {
                        float length = Math.min(bottom, draggingCellXY.bottom) - Math.max(top, draggingCellXY.top);
                        if (length > maxLength) {
                            draggingOverGroupCell = groupCell;
                            maxLength = length;
                        }
                    }
                    groupY += height;
                }
                if (draggingOverGroupCell != null) {
                    float maxArea = 0;
                    final int mediaCount = draggingOverGroupCell.media.size();
                    for (int i = 0; i < mediaCount; ++i) {
                        PreviewGroupCell.MediaCell mediaCell = draggingOverGroupCell.media.get(i);
                        if (mediaCell != null && mediaCell != draggingCell && draggingOverGroupCell.group.photos.contains(mediaCell.photoEntry)) {
                            RectF mediaCellRect = mediaCell.drawingRect();
                            if ((mediaCell.positionFlags & POSITION_FLAG_TOP) > 0) {
                                mediaCellRect.top = 0;
                            }
                            if ((mediaCell.positionFlags & POSITION_FLAG_LEFT) > 0) {
                                mediaCellRect.left = 0;
                            }
                            if ((mediaCell.positionFlags & POSITION_FLAG_RIGHT) > 0) {
                                mediaCellRect.right = getWidth();
                            }
                            if ((mediaCell.positionFlags & POSITION_FLAG_BOTTOM) > 0) {
                                mediaCellRect.bottom = draggingOverGroupCell.height;
                            }
                            if (RectF.intersects(draggingCellXY, mediaCellRect)) {
                                float area = (
                                    (Math.min(mediaCellRect.right, draggingCellXY.right) - Math.max(mediaCellRect.left, draggingCellXY.left)) *
                                    (Math.min(mediaCellRect.bottom, draggingCellXY.bottom) - Math.max(mediaCellRect.top, draggingCellXY.top))
                                ) / (draggingCellXY.width() * draggingCellXY.height());
                                if (area > 0.15f && area > maxArea) {
                                    draggingOverMediaCell = mediaCell;
                                    maxArea = area;
                                }
                            }
                        }
                    }
                }
            }

            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN && draggingCell == null && !listView.scrollingByUser && (draggingAnimator == null || !draggingAnimator.isRunning()) && touchGroupCell != null && touchMediaCell != null && touchGroupCell.group != null && touchGroupCell.group.photos.contains(touchMediaCell.photoEntry)) {
                tapGroupCell = touchGroupCell;
                tapMediaCell = touchMediaCell;
                draggingCellTouchX = touchX;
                draggingCellTouchY = touchY;
                draggingCell = null;

                final long wasTapTime = tapTime = SystemClock.elapsedRealtime();
                final PreviewGroupCell.MediaCell wasTapMediaCell = tapMediaCell;
                AndroidUtilities.runOnUIThread(() -> {
                    if (listView.scrollingByUser || tapTime != wasTapTime || tapMediaCell != wasTapMediaCell) {
                        return;
                    }
                    startDragging(tapMediaCell);
                    RectF draggingCellRect = draggingCell.rect();
                    RectF draggingCellDrawingRect = draggingCell.drawingRect();
                    draggingCellLeft = (.5f + (draggingCellTouchX - draggingCellRect.left) / (float) draggingCellRect.width()) / 2;
                    draggingCellTop = (draggingCellTouchY - draggingCellRect.top) / (float) draggingCellRect.height();
                    draggingCellFromWidth = draggingCellDrawingRect.width();
                    draggingCellFromHeight = draggingCellDrawingRect.height();
                    try {
                        ChatAttachAlertPhotoLayoutPreview.this.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignore) {}
                }, ViewConfiguration.getLongPressTimeout());

                invalidate();
                result = true;
            } else if (action == MotionEvent.ACTION_MOVE && draggingCell != null && !draggingCellHiding) {
                draggingCellTouchX = touchX;
                draggingCellTouchY = touchY;

                if (!scrollerStarted) {
                    scrollerStarted = true;
                    postDelayed(scroller, 16);
                }

                invalidate();
                result = true;
            } else if (action == MotionEvent.ACTION_UP && draggingCell != null) {
                PreviewGroupCell replaceGroupCell = null;
                PreviewGroupCell.MediaCell replaceMediaCell = null;
                if (touchGroupCell != null && touchMediaCell != null && touchMediaCell != draggingCell) {
                    replaceGroupCell = touchGroupCell;
                    replaceMediaCell = touchMediaCell;
                } else if (draggingOverGroupCell != null && draggingOverMediaCell != null && draggingOverMediaCell != draggingCell && draggingOverMediaCell.photoEntry != draggingCell.photoEntry) {
                    replaceGroupCell = draggingOverGroupCell;
                    replaceMediaCell = draggingOverMediaCell;
                }
                if (replaceGroupCell != null && replaceMediaCell != null && replaceMediaCell != draggingCell) {
                    int draggingIndex = draggingCell.groupCell.group.photos.indexOf(draggingCell.photoEntry);
                    int tapIndex = replaceGroupCell.group.photos.indexOf(replaceMediaCell.photoEntry);
                    if (draggingIndex >= 0) {
                        draggingCell.groupCell.group.photos.remove(draggingIndex);
                        draggingCell.groupCell.setGroup(draggingCell.groupCell.group, true);
                    }
                    if (tapIndex >= 0) {
                        if (groupCells.indexOf(replaceGroupCell) > groupCells.indexOf(draggingCell.groupCell)) {
                            tapIndex++;
                        }
                        pushToGroup(replaceGroupCell, draggingCell.photoEntry, tapIndex);
                        if (draggingCell.groupCell != replaceGroupCell) {
                            PreviewGroupCell.MediaCell newDraggingCell = null;
                            final int mediaCount = replaceGroupCell.media.size();
                            for (int i = 0; i < mediaCount; ++i) {
                                PreviewGroupCell.MediaCell mediaCell = replaceGroupCell.media.get(i);
                                if (mediaCell.photoEntry == draggingCell.photoEntry) {
                                    newDraggingCell = mediaCell;
                                    break;
                                }
                            }

                            if (newDraggingCell != null) {
                                remeasure();
                                newDraggingCell.layoutFrom(draggingCell);
                                draggingCell = newDraggingCell;
                                newDraggingCell.groupCell = replaceGroupCell;
                                newDraggingCell.scale = draggingCell.fromScale = 1f;
                                remeasure();
                            }
                        }
                    }

                    try {
                        ChatAttachAlertPhotoLayoutPreview.this.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignore) {}

                    updateGroups();
                    toPhotoLayout(photoLayout, false);
                }

                stopDragging();
                result = true;
            } else if (action == MotionEvent.ACTION_UP && draggingCell == null && tapMediaCell != null && tapGroupCell != null) {
                if (tapMediaCell.wasSpoiler && tapMediaCell.spoilerRevealProgress == 0f) {
                    tapMediaCell.startRevealMedia(event.getX(), event.getY());
                    result = true;
                } else {
                    RectF cellRect = tapMediaCell.drawingRect();
                    AndroidUtilities.rectTmp.set(cellRect.right - AndroidUtilities.dp(36.4f), tapGroupCell.top + cellRect.top, cellRect.right, tapGroupCell.top + cellRect.top + AndroidUtilities.dp(36.4f));
                    boolean tappedAtIndex = AndroidUtilities.rectTmp.contains(touchX, touchY - tapMediaCell.groupCell.y);

                    if (tappedAtIndex) {
                        if (getSelectedItemsCount() > 1) {
                            // short tap -> remove photo
                            final MediaController.PhotoEntry photo = tapMediaCell.photoEntry;
                            final int index = tapGroupCell.group.photos.indexOf(photo);
                            if (index >= 0) {
                                saveDeletedImageId(photo);
                                final PreviewGroupCell groupCell = tapGroupCell;
                                groupCell.group.photos.remove(index);
                                groupCell.setGroup(groupCell.group, true);
                                updateGroups();
                                toPhotoLayout(photoLayout, false);

                                final int currentUndoViewId = ++undoViewId;
                                undoView.showWithAction(0, ACTION_PREVIEW_MEDIA_DESELECTED, photo, null, () -> {
                                    if (draggingAnimator != null) {
                                        draggingAnimator.cancel();
                                    }
                                    draggingCell = null;
                                    draggingT = 0;
                                    pushToGroup(groupCell, photo, index);
                                    updateGroups();
                                    toPhotoLayout(photoLayout, false);
                                });

                                postDelayed(() -> {
                                    if (currentUndoViewId == undoViewId && undoView.isShown()) {
                                        undoView.hide(true, 1);
                                    }
                                }, 1000 * 4);
                            }

                            if (draggingAnimator != null) {
                                draggingAnimator.cancel();
                            }
                        }
                    } else {
                        calcPhotoArrays();
                        ArrayList<MediaController.PhotoEntry> arrayList = getPhotos();
                        int position = arrayList.indexOf(tapMediaCell.photoEntry);
                        ChatActivity chatActivity;
                        int type;
                        if (parentAlert.avatarPicker != 0) {
                            chatActivity = null;
                            type = PhotoViewer.SELECT_TYPE_AVATAR;
                        } else if (parentAlert.baseFragment instanceof ChatActivity) {
                            chatActivity = (ChatActivity) parentAlert.baseFragment;
                            type = 0;
                        } else {
                            chatActivity = null;
                            type = 4;
                        }
                        BaseFragment fragment = parentAlert.baseFragment;
                        if (fragment == null) {
                            fragment = LaunchActivity.getLastFragment();
                        }
                        if (!parentAlert.delegate.needEnterComment()) {
                            AndroidUtilities.hideKeyboard(fragment.getFragmentView().findFocus());
                            AndroidUtilities.hideKeyboard(parentAlert.getContainer().findFocus());
                        }
                        PhotoViewer.getInstance().setParentActivity(fragment, resourcesProvider);
                        PhotoViewer.getInstance().setParentAlert(parentAlert);
                        PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder);
                        photoViewerProvider.init(arrayList);
                        ArrayList<Object> objectArrayList = new ArrayList<>(arrayList);
                        PhotoViewer.getInstance().openPhotoForSelect(objectArrayList, position, type, false, photoViewerProvider, chatActivity);
                        if (photoLayout.captionForAllMedia()) {
                            PhotoViewer.getInstance().setCaption(parentAlert.getCommentTextView().getText());
                        }
                    }
                    tapMediaCell = null;
                    tapTime = 0;
                    draggingCell = null;
                    draggingT = 0;
                    result = true;
                }
            }

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                tapTime = 0;
                removeCallbacks(scroller);
                scrollerStarted = false;
                if (!result) {
                    stopDragging();
                    result = true;
                }
            }

            return result;
        }

        private void pushToGroup(PreviewGroupCell groupCell, MediaController.PhotoEntry photoEntry, int index) {
            groupCell.group.photos.add(Math.min(groupCell.group.photos.size(), index), photoEntry);
            if (groupCell.group.photos.size() == 11) {
                MediaController.PhotoEntry jumpPhoto = groupCell.group.photos.get(10);
                groupCell.group.photos.remove(10);

                int groupIndex = groupCells.indexOf(groupCell);
                if (groupIndex >= 0) {
                    PreviewGroupCell nextGroupCell = groupIndex + 1 == groupCells.size() ? null : groupCells.get(groupIndex + 1);
                    if (nextGroupCell == null) {
                        nextGroupCell = new PreviewGroupCell();
                        ArrayList<MediaController.PhotoEntry> newPhotos = new ArrayList<>();
                        newPhotos.add(jumpPhoto);
                        nextGroupCell.setGroup(new GroupCalculator(newPhotos), true);
                        invalidate();
                    } else {
                        pushToGroup(nextGroupCell, jumpPhoto, 0);
                    }
                }
            }
            groupCell.setGroup(groupCell.group, true);
        }
        private void updateGroups() {
            final int groupCellsCount = groupCells.size();
            for (int i = 0; i < groupCellsCount; ++i) {
                PreviewGroupCell groupCell = groupCells.get(i);
                if (groupCell.group.photos.size() < 10 && i < groupCells.size() - 1) {
                    int photosToTake = 10 - groupCell.group.photos.size();
                    PreviewGroupCell nextGroup = groupCells.get(i + 1);

                    ArrayList<MediaController.PhotoEntry> takenPhotos = new ArrayList<>();
                    photosToTake = Math.min(photosToTake, nextGroup.group.photos.size());
                    for (int j = 0; j < photosToTake; ++j) {
                        takenPhotos.add(nextGroup.group.photos.remove(0));
                    }
                    groupCell.group.photos.addAll(takenPhotos);
                    groupCell.setGroup(groupCell.group, true);
                    nextGroup.setGroup(nextGroup.group, true);
                }
            }
        }

        private HashMap<MediaController.PhotoEntry, ImageReceiver> images = new HashMap<>();

        private class PreviewGroupCell {
            public float y = 0;
            public int indexStart = 0;

            private final long updateDuration = 200 * durationMultiplier;
            private long lastMediaUpdate = 0;
            private float groupWidth = 0, groupHeight = 0;
            private float previousGroupWidth = 0, previousGroupHeight = 0;
            public ArrayList<MediaCell> media = new ArrayList<>();

            private class MediaCell {
                public PreviewGroupCell groupCell = PreviewGroupCell.this;

                public MediaController.PhotoEntry photoEntry;
                public ImageReceiver image;
                public ImageReceiver blurredImage;
                public boolean wasSpoiler;
                private RectF fromRect = null;
                public RectF rect = new RectF();
                private long lastUpdate = 0;
                private final long updateDuration = 200 * durationMultiplier;
                private int positionFlags = 0;
                public float fromScale = 1f;
                public float scale = 0f;

                private float spoilerRevealProgress;
                private float spoilerRevealX;
                private float spoilerRevealY;
                private float spoilerMaxRadius;

                public RectF fromRoundRadiuses = null;
                public RectF roundRadiuses = new RectF();

                private String videoDurationText = null;

                private SpoilerEffect spoilerEffect = new SpoilerEffect();
                private Path path = new Path();
                private float[] radii = new float[8];

                private Bitmap spoilerCrossfadeBitmap;
                private float spoilerCrossfadeProgress = 1f;
                private Paint spoilerCrossfadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                public void startCrossfade() {
                    RectF drawingRect = this.drawingRect();
                    int w = Math.max(1, Math.round(drawingRect.width()));
                    int h = Math.max(1, Math.round(drawingRect.height()));
                    Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.save();
                    canvas.translate(-drawingRect.left, -drawingRect.top);
                    draw(canvas);
                    canvas.restore();

                    if (spoilerCrossfadeBitmap != null && !spoilerCrossfadeBitmap.isRecycled()) {
                        spoilerCrossfadeBitmap.recycle();
                    }
                    spoilerCrossfadeBitmap = bitmap;
                    spoilerCrossfadeProgress = 0f;
                    invalidate();
                }

                private void setImage(MediaController.PhotoEntry photoEntry) {
                    this.photoEntry = photoEntry;
                    if (photoEntry != null && photoEntry.isVideo) {
                        videoDurationText = AndroidUtilities.formatShortDuration(photoEntry.duration);
                    } else {
                        videoDurationText = null;
                    }
                    if (image == null) {
                        image = new ImageReceiver(PreviewGroupsView.this);
                        blurredImage = new ImageReceiver(PreviewGroupsView.this);

                        image.setDelegate((imageReceiver, set, thumb, memCache) -> {
                            if (set && !thumb && photoEntry != null && photoEntry.hasSpoiler && blurredImage.getBitmap() == null) {
                                if (blurredImage.getBitmap() != null && !blurredImage.getBitmap().isRecycled()) {
                                    blurredImage.getBitmap().recycle();
                                    blurredImage.setImageBitmap((Bitmap) null);
                                }

                                Bitmap bitmap = imageReceiver.getBitmap();
                                blurredImage.setImageBitmap(Utilities.stackBlurBitmapMax(bitmap));
                            }
                        });
                    }
                    if (photoEntry != null) {
                        if (photoEntry.thumbPath != null) {
                            image.setImage(ImageLocation.getForPath(photoEntry.thumbPath), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0);
                        } else if (photoEntry.path != null) {
                            if (photoEntry.isVideo) {
                                image.setImage(ImageLocation.getForPath("vthumb://" + photoEntry.imageId + ":" + photoEntry.path), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0);
                                image.setAllowStartAnimation(true);
                            } else {
                                image.setOrientation(photoEntry.orientation, true);
                                image.setImage(ImageLocation.getForPath("thumb://" + photoEntry.imageId + ":" + photoEntry.path), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0);
                            }
                        } else {
                            image.setImageBitmap(Theme.chat_attachEmptyDrawable);
                        }
                    }
                }

                private void layoutFrom(MediaCell fromCell) {
                    this.fromScale = AndroidUtilities.lerp(fromCell.fromScale, fromCell.scale, fromCell.getT());
                    if (this.fromRect == null) {
                        this.fromRect = new RectF();
                    }
                    RectF myRect = new RectF();
                    if (this.fromRect == null) {
                        myRect.set(rect);
                    } else {
                        AndroidUtilities.lerp(fromRect, rect, getT(), myRect);
                    }

                    if (fromCell.fromRect != null) {
                        AndroidUtilities.lerp(fromCell.fromRect, fromCell.rect, fromCell.getT(), this.fromRect);
                        this.fromRect.set(
                            myRect.centerX() - this.fromRect.width() / 2 * fromCell.groupCell.width / width,
                            myRect.centerY() - this.fromRect.height() / 2 * fromCell.groupCell.height / height,
                            myRect.centerX() + this.fromRect.width() / 2 * fromCell.groupCell.width / width,
                            myRect.centerY() + this.fromRect.height() / 2 * fromCell.groupCell.height / height
                        );
                    } else {
                        this.fromRect.set(
                            myRect.centerX() - fromCell.rect.width() / 2 * fromCell.groupCell.width / width,
                            myRect.centerY() - fromCell.rect.height() / 2 * fromCell.groupCell.height / height,
                            myRect.centerX() + fromCell.rect.width() / 2 * fromCell.groupCell.width / width,
                            myRect.centerY() + fromCell.rect.height() / 2 * fromCell.groupCell.height / height
                        );
                    }

                    fromScale = AndroidUtilities.lerp(fromScale, scale, getT());

                    this.lastUpdate = SystemClock.elapsedRealtime();
                }

                private void layout(GroupCalculator group, MessageObject.GroupedMessagePosition pos, boolean animated) {
                    if (group == null || pos == null) {
                        if (animated) {
                            final long now = SystemClock.elapsedRealtime();
                            fromScale = AndroidUtilities.lerp(fromScale, scale, getT());
                            if (fromRect != null) {
                                AndroidUtilities.lerp(fromRect, rect, getT(), fromRect);
                            }
                            scale = 0f;
                            lastUpdate = now;
                        } else {
                            scale = fromScale = 0f;
                        }
                        return;
                    }
                    positionFlags = pos.flags;
                    if (animated) {
                        final float t = getT();
                        if (fromRect != null) {
                            AndroidUtilities.lerp(fromRect, rect, t, fromRect);
                        }
                        if (fromRoundRadiuses != null) {
                            AndroidUtilities.lerp(fromRoundRadiuses, roundRadiuses, t, fromRoundRadiuses);
                        }
                        fromScale = AndroidUtilities.lerp(fromScale, scale, t);
                        lastUpdate = SystemClock.elapsedRealtime();
                    }
                    float x = pos.left / group.width,
                          y = pos.top / group.height,
                          w = pos.pw / (float) group.width,
                          h = pos.ph / group.height;
                    scale = 1f;
                    rect.set(x, y, x + w, y + h);
                    final float r = AndroidUtilities.dp(2),
                                R = AndroidUtilities.dp(SharedConfig.bubbleRadius - 1);
                    roundRadiuses.set(
                        (positionFlags & (POSITION_FLAG_TOP | POSITION_FLAG_LEFT)) == (POSITION_FLAG_TOP | POSITION_FLAG_LEFT) ? R : r,
                        (positionFlags & (POSITION_FLAG_TOP | POSITION_FLAG_RIGHT)) == (POSITION_FLAG_TOP | POSITION_FLAG_RIGHT) ? R : r,
                        (positionFlags & (POSITION_FLAG_BOTTOM | POSITION_FLAG_RIGHT)) == (POSITION_FLAG_BOTTOM | POSITION_FLAG_RIGHT) ? R : r,
                        (positionFlags & (POSITION_FLAG_BOTTOM | POSITION_FLAG_LEFT)) == (POSITION_FLAG_BOTTOM | POSITION_FLAG_LEFT) ? R : r
                    );
                    if (fromRect == null) {
                        fromRect = new RectF();
                        fromRect.set(rect);
                    }
                    if (fromRoundRadiuses == null) {
                        fromRoundRadiuses = new RectF();
                        fromRoundRadiuses.set(roundRadiuses);
                    }
                }

                public float getT() {
                    return interpolator.getInterpolation(Math.min(1, (SystemClock.elapsedRealtime() - lastUpdate) / (float) updateDuration));
                }

                @NonNull
                @Override
                protected MediaCell clone() {
                    MediaCell newMediaCell = new MediaCell();
                    newMediaCell.rect.set(this.rect);
                    newMediaCell.image = this.image;
                    newMediaCell.photoEntry = this.photoEntry;
                    return newMediaCell;
                }

                private RectF tempRect = new RectF();
                public RectF rect() {
                    return rect(getT());
                }
                public RectF rect(float t) {
                    if (rect == null || image == null) {
                        tempRect.set(0, 0, 0, 0);
                        return tempRect;
                    }
                    float x = left + rect.left * width,
                          y = top + rect.top * height,
                          w = rect.width() * width,
                          h = rect.height() * height;
                    if (t < 1f && fromRect != null) {
                        x = AndroidUtilities.lerp(left + fromRect.left * width, x, t);
                        y = AndroidUtilities.lerp(top + fromRect.top * height, y, t);
                        w = AndroidUtilities.lerp(fromRect.width() * width, w, t);
                        h = AndroidUtilities.lerp(fromRect.height() * height, h, t);
                    }
                    if ((positionFlags & POSITION_FLAG_TOP) == 0) {
                        y += halfGap;
                        h -= halfGap;
                    }
                    if ((positionFlags & POSITION_FLAG_BOTTOM) == 0) {
                        h -= halfGap;
                    }
                    if ((positionFlags & POSITION_FLAG_LEFT) == 0) {
                        x += halfGap;
                        w -= halfGap;
                    }
                    if ((positionFlags & POSITION_FLAG_RIGHT) == 0) {
                        w -= halfGap;
                    }
                    tempRect.set(x, y, x + w, y + h);
                    return tempRect;
                }

                public RectF drawingRect() {
                    if (rect == null || image == null) {
                        tempRect.set(0, 0, 0, 0);
                        return tempRect;
                    }
                    final float dragging = draggingCell != null && draggingCell.photoEntry == photoEntry ? draggingT : 0,
                                scale = AndroidUtilities.lerp(this.fromScale, this.scale, getT()) * (.8f + .2f * (1f - dragging));
                    RectF myRect = this.rect();
                    myRect.set(
                        myRect.left + myRect.width() * (1f - scale) / 2f,
                        myRect.top + myRect.height() * (1f - scale) / 2f,
                        myRect.left + myRect.width() * (1f + scale) / 2f,
                        myRect.top + myRect.height() * (1f + scale) / 2f
                    );
                    return myRect;
                }

                private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private TextPaint textPaint;
                private TextPaint videoDurationTextPaint;
                private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

                private Bitmap indexBitmap = null;
                private String indexBitmapText = null;
                private Bitmap videoDurationBitmap = null;
                private String videoDurationBitmapText = null;

                private Rect indexIn = new Rect(), indexOut = new Rect();
                private Rect durationIn = new Rect(), durationOut = new Rect();

                private void drawPhotoIndex(Canvas canvas, float top, float right, String indexText, float scale, float alpha) {
                    final int radius = AndroidUtilities.dp(12),
                              strokeWidth = AndroidUtilities.dp(1.2f),
                              sz = (radius + strokeWidth) * 2,
                              pad = strokeWidth * 4;

                    if (indexText != null && (indexBitmap == null || indexBitmapText == null || !indexBitmapText.equals(indexText))) {
                        if (indexBitmap == null) {
                            indexBitmap = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888);
                        }
                        Canvas bitmapCanvas = new Canvas(indexBitmap);
                        bitmapCanvas.drawColor(0x00000000);

                        if (textPaint == null) {
                            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                        }
                        textPaint.setColor(getThemedColor(Theme.key_chat_attachCheckBoxCheck));
                        final float textSize;
                        switch (indexText.length()) {
                            case 0:
                            case 1:
                            case 2:
                                textSize = 14f;
                                break;
                            case 3:
                                textSize = 10f;
                                break;
                            default:
                                textSize = 8f;
                        }
                        textPaint.setTextSize(AndroidUtilities.dp(textSize));

                        float cx = sz / 2f, cy = sz / 2f;
                        paint.setColor(getThemedColor(Theme.key_chat_attachCheckBoxBackground));
                        bitmapCanvas.drawCircle((int) cx, (int) cy, radius, paint);
                        strokePaint.setColor(AndroidUtilities.getOffsetColor(0xffffffff, getThemedColor(Theme.key_chat_attachCheckBoxCheck), 1f, 1f));
                        strokePaint.setStyle(Paint.Style.STROKE);
                        strokePaint.setStrokeWidth(strokeWidth);
                        bitmapCanvas.drawCircle((int) cx, (int) cy, radius, strokePaint);
                        bitmapCanvas.drawText(indexText, cx - textPaint.measureText(indexText) / 2f, cy + AndroidUtilities.dp(1) + AndroidUtilities.dp(textSize / 4f), textPaint);

                        indexIn.set(0, 0, sz, sz);
                        indexBitmapText = indexText;
                    }

                    if (indexBitmap != null) {
                        indexOut.set((int) (right - sz * scale + pad), (int) (top - pad), (int) (right + pad), (int) (top - pad + sz * scale));
                        bitmapPaint.setAlpha((int) (255 * alpha));
                        canvas.drawBitmap(indexBitmap, indexIn, indexOut, bitmapPaint);
                    }
                }

                private void drawDuration(Canvas canvas, float left, float bottom, String durationText, float scale, float alpha) {
                    if (durationText != null) {
                        if (videoDurationBitmap == null || videoDurationBitmapText == null || !videoDurationBitmapText.equals(durationText)) {
                            if (videoDurationTextPaint == null) {
                                videoDurationTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                                videoDurationTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                                videoDurationTextPaint.setColor(0xffffffff);
                            }
                            final float textSize = AndroidUtilities.dp(12);
                            videoDurationTextPaint.setTextSize(textSize);
                            float textWidth = videoDurationTextPaint.measureText(durationText);
                            float width = videoPlayImage.getIntrinsicWidth() + textWidth + AndroidUtilities.dp(15),
                                  height = Math.max(textSize, videoPlayImage.getIntrinsicHeight() + AndroidUtilities.dp(4));
                            int w = (int) Math.ceil(width), h = (int) Math.ceil(height);

                            if (videoDurationBitmap == null || videoDurationBitmap.getWidth() != w || videoDurationBitmap.getHeight() != h) {
                                if (videoDurationBitmap != null) {
                                    videoDurationBitmap.recycle();
                                }
                                videoDurationBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                            }
                            Canvas bitmapCanvas = new Canvas(videoDurationBitmap);

                            AndroidUtilities.rectTmp.set(0, 0, width, height);
                            bitmapCanvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);

                            int imageLeft = (int) AndroidUtilities.dp(5),
                                imageTop =  (int) ((height - videoPlayImage.getIntrinsicHeight()) / 2);
                            videoPlayImage.setBounds(imageLeft, imageTop, imageLeft + videoPlayImage.getIntrinsicWidth(), imageTop + videoPlayImage.getIntrinsicHeight());
                            videoPlayImage.draw(bitmapCanvas);
                            bitmapCanvas.drawText(durationText, AndroidUtilities.dp(18), textSize + AndroidUtilities.dp(-0.7f), videoDurationTextPaint);

                            durationIn.set(0, 0, w, h);
                            videoDurationBitmapText = durationText;
                        }

                        int w = videoDurationBitmap.getWidth(), h = videoDurationBitmap.getHeight();
                        durationOut.set((int) left, (int) (bottom - h * scale), (int) (left + w * scale), (int) bottom);
                        bitmapPaint.setAlpha((int) (255 * alpha));
                        canvas.drawBitmap(videoDurationBitmap, durationIn, durationOut, bitmapPaint);
                    }
                }

                private void startRevealMedia(float x, float y) {
                    spoilerRevealX = x;
                    spoilerRevealY = y;

                    RectF drawingRect = drawingRect();
                    spoilerMaxRadius = (float) Math.sqrt(Math.pow(drawingRect.width(), 2) + Math.pow(drawingRect.height(), 2));
                    ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration((long) MathUtils.clamp(spoilerMaxRadius * 0.3f, 250, 550));
                    animator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
                    animator.addUpdateListener(animation -> {
                        spoilerRevealProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            photoEntry.isChatPreviewSpoilerRevealed = true;
                            invalidate();
                        }
                    });
                    animator.start();
                }

                private float visibleT = 1;
                private long lastVisibleTUpdate = 0;

                public boolean draw(Canvas canvas) {
                    return draw(canvas, false);
                }
                public boolean draw(Canvas canvas, boolean ignoreBounds) {
                    return draw(canvas, getT(), ignoreBounds);
                }
                public boolean draw(Canvas canvas, float t, boolean ignoreBounds) {
                    if (rect == null || image == null) {
                        return false;
                    }
                    final float dragging = draggingCell == this ? draggingT : 0;
                    float scale = AndroidUtilities.lerp(this.fromScale, this.scale, t);
                    if (scale <= 0f) {
                        return false;
                    }
                    RectF drawingRect = this.drawingRect();
                    float R = AndroidUtilities.dp(SharedConfig.bubbleRadius - 1);
                    float tl = roundRadiuses.left, tr = roundRadiuses.top, br = roundRadiuses.right, bl = roundRadiuses.bottom;
                    if (t < 1f && fromRoundRadiuses != null) {
                        tl = AndroidUtilities.lerp(fromRoundRadiuses.left, tl, t);
                        tr = AndroidUtilities.lerp(fromRoundRadiuses.top, tr, t);
                        br = AndroidUtilities.lerp(fromRoundRadiuses.right, br, t);
                        bl = AndroidUtilities.lerp(fromRoundRadiuses.bottom, bl, t);
                    }
                    tl = AndroidUtilities.lerp(tl, R, dragging);
                    tr = AndroidUtilities.lerp(tr, R, dragging);
                    br = AndroidUtilities.lerp(br, R, dragging);
                    bl = AndroidUtilities.lerp(bl, R, dragging);
                    if (ignoreBounds) {
                        canvas.save();
                        canvas.translate(-drawingRect.centerX(), -drawingRect.centerY());
                    }
                    image.setRoundRadius((int) tl, (int) tr, (int) br, (int) bl);
                    image.setImageCoords(drawingRect.left, drawingRect.top, drawingRect.width(), drawingRect.height());
                    image.setAlpha(scale);
                    image.draw(canvas);

                    if (photoEntry != null && photoEntry.hasSpoiler && !photoEntry.isChatPreviewSpoilerRevealed) {
                        if (!wasSpoiler && blurredImage.getBitmap() == null && image.getBitmap() != null) {
                            wasSpoiler = true;
                            blurredImage.setImageBitmap(Utilities.stackBlurBitmapMax(image.getBitmap()));
                        } else if (!wasSpoiler && blurredImage.getBitmap() != null) {
                            wasSpoiler = true;
                        }

                        radii[0] = radii[1] = tl;
                        radii[2] = radii[3] = tr;
                        radii[4] = radii[5] = br;
                        radii[6] = radii[7] = bl;

                        canvas.save();
                        path.rewind();
                        path.addRoundRect(drawingRect, radii, Path.Direction.CW);
                        canvas.clipPath(path);

                        if (spoilerRevealProgress != 0f) {
                            path.rewind();
                            path.addCircle(spoilerRevealX, spoilerRevealY, spoilerMaxRadius * spoilerRevealProgress, Path.Direction.CW);
                            canvas.clipPath(path, Region.Op.DIFFERENCE);
                        }

                        blurredImage.setRoundRadius((int) tl, (int) tr, (int) br, (int) bl);
                        blurredImage.setImageCoords(drawingRect.left, drawingRect.top, drawingRect.width(), drawingRect.height());
                        blurredImage.setAlpha(scale);
                        blurredImage.draw(canvas);

                        int sColor = Color.WHITE;
                        spoilerEffect.setColor(ColorUtils.setAlphaComponent(sColor, (int) (Color.alpha(sColor) * 0.325f * scale)));
                        spoilerEffect.setBounds(0, 0, getWidth(), getHeight());
                        spoilerEffect.draw(canvas);
                        canvas.restore();

                        invalidate();
                        PreviewGroupsView.this.invalidate();
                    }

                    if (spoilerCrossfadeProgress != 1f && spoilerCrossfadeBitmap != null) {
                        radii[0] = radii[1] = tl;
                        radii[2] = radii[3] = tr;
                        radii[4] = radii[5] = br;
                        radii[6] = radii[7] = bl;

                        canvas.save();
                        path.rewind();
                        path.addRoundRect(drawingRect, radii, Path.Direction.CW);
                        canvas.clipPath(path);

                        long dt = Math.min(16, SystemClock.elapsedRealtime() - lastUpdate);
                        spoilerCrossfadeProgress = Math.min(1f, spoilerCrossfadeProgress + dt / 250f);

                        spoilerCrossfadePaint.setAlpha((int) ((1f - spoilerCrossfadeProgress) * 0xFF));
                        canvas.drawBitmap(spoilerCrossfadeBitmap, drawingRect.left, drawingRect.top, spoilerCrossfadePaint);

                        canvas.restore();

                        invalidate();
                    } else if (spoilerCrossfadeProgress == 1f && spoilerCrossfadeBitmap != null) {
                        spoilerCrossfadeBitmap.recycle();
                        spoilerCrossfadeBitmap = null;

                        invalidate();
                    }

                    int index = indexStart + group.photos.indexOf(photoEntry);
                    String indexText = index >= 0 ? (index + 1) + "" : null;
                    float shouldVisibleT = image.getVisible() ? 1 : 0;
                    boolean needVisibleTUpdate;
                    if (needVisibleTUpdate = Math.abs(visibleT - shouldVisibleT) > 0.01f) {
                        long tx = Math.min(17, SystemClock.elapsedRealtime() - lastVisibleTUpdate);
                        lastVisibleTUpdate = SystemClock.elapsedRealtime();
                        float upd = tx / 100f;
                        if (shouldVisibleT < visibleT) {
                            visibleT = Math.max(0, visibleT - upd);
                        } else {
                            visibleT = Math.min(1, visibleT + upd);
                        }
                    }
                    drawPhotoIndex(canvas, drawingRect.top + AndroidUtilities.dp(10), drawingRect.right - AndroidUtilities.dp(10), indexText, scale, scale * visibleT);
                    drawDuration(canvas, drawingRect.left + AndroidUtilities.dp(4), drawingRect.bottom - AndroidUtilities.dp(4), videoDurationText, scale, scale * visibleT);

                    if (ignoreBounds) {
                        canvas.restore();
                    }

                    return t < 1f || needVisibleTUpdate;
                }
            }

            private Interpolator interpolator = CubicBezierInterpolator.EASE_BOTH;
            private GroupCalculator group;
            private void setGroup(GroupCalculator group, boolean animated) {
                this.group = group;
                if (group == null) {
                    return;
                }
                group.calculate();
                final long now = SystemClock.elapsedRealtime();
                if (now - lastMediaUpdate < updateDuration) {
                    final float t = (now - lastMediaUpdate) / (float) updateDuration;
                    previousGroupHeight = AndroidUtilities.lerp(previousGroupHeight, groupHeight, t);
                    previousGroupWidth = AndroidUtilities.lerp(previousGroupWidth, groupWidth, t);
                } else {
                    previousGroupHeight = groupHeight;
                    previousGroupWidth = groupWidth;
                }
                groupWidth = group.width / 1000f;
                groupHeight = group.height;
                lastMediaUpdate = animated ? now : 0;
                List<MediaController.PhotoEntry> photoEntries = new ArrayList<>(group.positions.keySet());
                final int photoEntriesCount = photoEntries.size();
                for (int j = 0; j < photoEntriesCount; ++j) {
                    MediaController.PhotoEntry photoEntry = photoEntries.get(j);
                    MessageObject.GroupedMessagePosition pos = group.positions.get(photoEntry);
                    MediaCell properCell = null;
                    final int mediaCount = media.size();
                    for (int i = 0; i < mediaCount; ++i) {
                        MediaCell cell = media.get(i);
                        if (cell.photoEntry == photoEntry) {
                            properCell = cell;
                            break;
                        }
                    }

                    if (properCell == null) {
                        // new cell
                        properCell = new MediaCell();
                        properCell.setImage(photoEntry);
                        properCell.layout(group, pos, animated);
                        media.add(properCell);
                    } else {
                        properCell.layout(group, pos, animated);
                    }
                }
                int mediaCount = media.size();
                for (int i = 0; i < mediaCount; ++i) {
                    MediaCell cell = media.get(i);
                    if (!group.positions.containsKey(cell.photoEntry)) {
                        // old cell, remove it
                        if (cell.scale <= 0 && cell.lastUpdate + cell.updateDuration <= now) {
                            media.remove(i);
                            i--;
                            mediaCount--;
                        } else {
                            cell.layout(null, null, animated);
                        }
                    }
                }

                PreviewGroupsView.this.invalidate();
            }

            final int padding = AndroidUtilities.dp(4);
            final int gap = AndroidUtilities.dp(2),
                    halfGap = gap / 2;

            private float left, right, top, bottom, width, height;

            public float getT() {
                return interpolator.getInterpolation(Math.min(1, (SystemClock.elapsedRealtime() - lastMediaUpdate) / (float) updateDuration));
            }

            public float measure() {
                final float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                return AndroidUtilities.lerp(previousGroupHeight, this.groupHeight, getT()) * maxHeight * getPreviewScale(); // height
            }
            public float maxHeight() {
                final float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                return getT() >= 0.95f ? this.groupHeight * maxHeight * getPreviewScale() : measure();
            }

            private Theme.MessageDrawable messageBackground = (Theme.MessageDrawable) getThemedDrawable(Theme.key_drawable_msgOutMedia);
            private Theme.MessageDrawable.PathDrawParams backgroundCacheParams = new Theme.MessageDrawable.PathDrawParams();
            public boolean draw(Canvas canvas) {
                boolean update = false;
                final float t = interpolator.getInterpolation(Math.min(1, (SystemClock.elapsedRealtime() - lastMediaUpdate) / (float) updateDuration));
                if (t < 1f) {
                    update = true;
                }

                final float maxHeight = Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f;
                float groupWidth = AndroidUtilities.lerp(previousGroupWidth, this.groupWidth, t) * getWidth() * getPreviewScale(),
                      groupHeight = AndroidUtilities.lerp(previousGroupHeight, this.groupHeight, t) * maxHeight * getPreviewScale();

                if (messageBackground != null) {
                    top = 0;
                    left = (getWidth() - Math.max(padding, groupWidth)) / 2f;
                    right = (getWidth() + Math.max(padding, groupWidth)) / 2f;
                    bottom = Math.max(padding * 2, groupHeight);
                    messageBackground.setTop((int) 0, (int) groupWidth, (int) groupHeight, 0, 0, 0, false, false);
                    messageBackground.setBounds((int) left, (int) top, (int) right, (int) bottom);
                    float alpha = 1f;
                    if (this.groupWidth <= 0) {
                        alpha = 1f - t;
                    } else if (this.previousGroupWidth <= 0) {
                        alpha = t;
                    }
                    messageBackground.setAlpha((int) (255 * alpha));
                    messageBackground.drawCached(canvas, backgroundCacheParams);
                    top += padding;
                    left += padding;
                    bottom -= padding;
                    right -= padding;
                }

                width = right - left;
                height = bottom - top;
                final int count = media.size();
                for (int i = 0; i < count; ++i) {
                    MediaCell cell = media.get(i);
                    if (cell == null) {
                        continue;
                    }
                    if (draggingCell != null && draggingCell.photoEntry == cell.photoEntry) {
                        continue;
                    }
                    if (cell.draw(canvas)) {
                        update = true;
                    }
                }
                return update;
            }
        }
    }

    public Drawable getThemedDrawable(String drawableKey) {
        Drawable drawable = themeDelegate != null ? themeDelegate.getDrawable(drawableKey) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(drawableKey);
    }
}
