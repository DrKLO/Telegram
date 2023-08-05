package org.telegram.ui.Stories;

import android.graphics.Canvas;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;

public class StoriesListPlaceProvider implements StoryViewer.PlaceProvider {

    private final RecyclerListView recyclerListView;
    int[] clipPoint = new int[2];
    private boolean isHiddenArchive;

    public static StoriesListPlaceProvider of(RecyclerListView recyclerListView) {
        return of(recyclerListView, false);
    }

    public static StoriesListPlaceProvider of(RecyclerListView recyclerListView, boolean hiddenArchive) {
        return new StoriesListPlaceProvider(recyclerListView, hiddenArchive);
    }

    public StoriesListPlaceProvider(RecyclerListView recyclerListView, boolean hiddenArchive) {
        this.recyclerListView = recyclerListView;
        this.isHiddenArchive = hiddenArchive;
    }

    @Override
    public void preLayout(long currentDialogId, int messageId, Runnable r) {
        if (recyclerListView.getParent() instanceof DialogStoriesCell) {
            DialogStoriesCell dilogsCell = (DialogStoriesCell) recyclerListView.getParent();
            if (dilogsCell.scrollTo(currentDialogId)) {
                dilogsCell.afterNextLayout(r);
            } else {
                r.run();
            }
        } else {
            if (isHiddenArchive) {
                MessagesController.getInstance(UserConfig.selectedAccount).getStoriesController().sortHiddenStories();
            }
            r.run();
        }
    }

    public boolean findView(long dialogId, int messageId, int storyId, int type, StoryViewer.TransitionViewHolder holder) {
        holder.view = null;
        holder.avatarImage = null;
        holder.storyImage = null;
        holder.drawAbove = null;

        if (recyclerListView == null) {
            return false;
        }

        DialogStoriesCell dialogStoriesCell = null;
        if (recyclerListView.getParent() instanceof DialogStoriesCell) {
            dialogStoriesCell = (DialogStoriesCell) recyclerListView.getParent();
        }
        RecyclerListView listView = recyclerListView;
        if (dialogStoriesCell != null && !dialogStoriesCell.isExpanded()) {
            listView = dialogStoriesCell.listViewMini;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);

            if (child instanceof DialogStoriesCell.StoryCell) {
                DialogStoriesCell.StoryCell cell = (DialogStoriesCell.StoryCell) child;

                if (cell.dialogId == dialogId) {
                    holder.view = child;
                    holder.avatarImage = cell.avatarImage;
                    holder.params = cell.params;
                    holder.radialProgressUpload = cell.radialProgress;
                    DialogStoriesCell storiesCell = (DialogStoriesCell) cell.getParent().getParent();
                    holder.clipParent = storiesCell;
                    holder.clipTop = holder.clipBottom = 0;
                 //   updateClip(holder);
                    return true;
                }
            } else if (child instanceof DialogCell) {
                DialogCell cell = (DialogCell) child;
                if (cell.getDialogId() == dialogId || (isHiddenArchive && cell.isDialogFolder())) {
                    holder.view = child;
                    holder.params = cell.storyParams;
                    holder.avatarImage = cell.avatarImage;
                    holder.clipParent = (View) cell.getParent();
                    if (isHiddenArchive) {
                        holder.crossfadeToAvatarImage = cell.avatarImage;
                    }
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                if (cell.getMessageObject().getId() == messageId) {
                    holder.view = child;
                    if (type == 1 || type == 2) {
                        holder.storyImage = cell.getPhotoImage();
                    } else {
                        holder.storyImage = cell.replyImageReceiver;
                    }
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ChatActionCell) {
                ChatActionCell cell = (ChatActionCell) child;
                if (cell.getMessageObject().getId() == messageId) {
                    holder.view = child;
                    TLRPC.StoryItem storyItem = cell.getMessageObject().messageOwner.media.storyItem;
                    if (storyItem.noforwards) {
                        holder.avatarImage = cell.getPhotoImage();
                    } else {
                        holder.storyImage = cell.getPhotoImage();
                    }
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof SharedPhotoVideoCell2) {
                SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) child;
                MessageObject msg = cell.getMessageObject();
                if (
                    cell.getStyle() == SharedPhotoVideoCell2.STYLE_CACHE && cell.storyId == storyId ||
                    msg != null && msg.isStory() && msg.getId() == storyId && msg.storyItem.dialogId == dialogId
                ) {
                    final RecyclerListView.FastScroll fastScroll = listView.getFastScroll();
                    final int[] loc = new int[2];
                    if (fastScroll != null) {
                        fastScroll.getLocationInWindow(loc);
                    }
                    holder.view = child;
                    holder.storyImage = cell.imageReceiver;
                    holder.drawAbove = (canvas, bounds, alpha) -> {
                        cell.drawDuration(canvas, bounds, alpha);
                        if (fastScroll != null && fastScroll.isVisible && fastScroll.getVisibility() == View.VISIBLE) {
                            canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
                            canvas.translate(loc[0], loc[1]);
                            fastScroll.draw(canvas);
                            canvas.restore();
                        }
                    };
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof UserCell) {
                UserCell cell = (UserCell) child;
                if (cell.getDialogId() == dialogId) {
                    holder.view = cell.avatarImageView;
                    holder.params = cell.storyParams;
                    holder.avatarImage = cell.avatarImageView.getImageReceiver();
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ReactedUserHolderView) {
                ReactedUserHolderView cell = (ReactedUserHolderView) child;
                if (cell.dialogId == dialogId) {
                    holder.view = cell.avatarView;
                    holder.params = cell.params;
                    holder.avatarImage = cell.avatarView.getImageReceiver();
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            } else if (child instanceof ProfileSearchCell) {
                ProfileSearchCell cell = (ProfileSearchCell) child;
                if (cell.getDialogId() == dialogId) {
                    holder.view = cell;
                    holder.params = cell.avatarStoryParams;
                    holder.avatarImage = cell.avatarImage;
                    holder.clipParent = (View) cell.getParent();
                    updateClip(holder);
                    return true;
                }
            }
        }
        return false;
    }

    private void updateClip(StoryViewer.TransitionViewHolder holder) {
        if (holder.clipParent == null) {
            return;
        }
        if (holder.clipParent instanceof ClippedView) {
            ((ClippedView) holder.clipParent).updateClip(clipPoint);
            holder.clipTop = clipPoint[0];
            holder.clipBottom = clipPoint[1];
        } else if (holder.clipParent instanceof BlurredRecyclerView) {
            holder.clipTop = ((BlurredRecyclerView) holder.clipParent).blurTopPadding;
            holder.clipBottom = holder.clipParent.getMeasuredHeight() - holder.clipParent.getPaddingBottom();
        } else {
            holder.clipTop = holder.clipParent.getPaddingTop();
            holder.clipBottom = holder.clipParent.getMeasuredHeight() - holder.clipParent.getPaddingBottom();
        }
    }

    public interface ClippedView {
        void updateClip(int[] clip);
    }

    public interface AvatarOverlaysView {
        boolean drawAvatarOverlays(Canvas canvas);
    }
}
