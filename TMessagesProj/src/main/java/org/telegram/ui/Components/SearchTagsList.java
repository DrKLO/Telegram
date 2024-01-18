package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class SearchTagsList extends BlurredFrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    public final RecyclerListView listView;
    private final Adapter adapter;

    private long chosen;
    private final ArrayList<Item> oldItems = new ArrayList<>();
    private final ArrayList<Item> items = new ArrayList<>();

    private static class Item {
        ReactionsLayoutInBubble.VisibleReaction reaction;
        int count;

        public static Item get(ReactionsLayoutInBubble.VisibleReaction reaction, int count) {
            Item item = new Item();
            item.reaction = reaction;
            item.count = count;
            return item;
        }

        public long hash() {
            return reaction.hash;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (!(obj instanceof Item)) {
                return false;
            }
            Item that = (Item) obj;
            return this.count == that.count && this.reaction.hash == that.reaction.hash;
        }
    }

    public SearchTagsList(Context context, SizeNotifierFrameLayout contentView, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context, contentView);

        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        listView.setPadding(dp(5.66f), 0, dp(5.66f), 0);
        listView.setClipToPadding(false);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(RecyclerView.HORIZONTAL);
        listView.setLayoutManager(layoutManager);
        listView.setAdapter(adapter = new Adapter());
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            listView.forAllChild(view2 -> {
                if (view2 instanceof TagButton) {
                    ((TagButton) view2).setChosen(false, true);
                }
            });
            long hash = items.get(position).hash();
            if (chosen == hash) {
                chosen = 0;
                setFilter(null);
            } else {
                chosen = hash;
                setFilter(items.get(position).reaction);
                ((TagButton) view).setChosen(true, true);
            }
        });

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            public boolean animateMove(RecyclerView.ViewHolder holder, ItemHolderInfo info, int fromX, int fromY, int toX, int toY) {
                int position = holder.getAdapterPosition();
                if (position >= 0 && position < items.size()) {
                    Item item = items.get(position);
                    TagButton btn = (TagButton) holder.itemView;
                    boolean updatedChosen = btn.setChosen(chosen == item.hash(), true);
                    boolean updatedCount = btn.setCount(item.count);
                    if (updatedChosen || updatedCount) {
                        return true;
                    }
                }
                return super.animateMove(holder, info, fromX, fromY, toX, toY);
            }

            @Override
            protected void animateMoveImpl(RecyclerView.ViewHolder holder, MoveInfo moveInfo) {
                super.animateMoveImpl(holder, moveInfo);
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(320);
        listView.setItemAnimator(itemAnimator);

        MediaDataController.getInstance(currentAccount).loadSavedReactions(false);
        updateTags();
    }

    public boolean hasFilters() {
        return !items.isEmpty();
    }

    public void clear() {
        listView.forAllChild(view2 -> {
            if (view2 instanceof TagButton) {
                ((TagButton) view2).setChosen(false, true);
            }
        });
        chosen = 0;
    }

    protected void setFilter(ReactionsLayoutInBubble.VisibleReaction reaction) {

    }

    public void attach() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.savedReactionTagsUpdate);
    }

    public void detach() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.savedReactionTagsUpdate);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.savedReactionTagsUpdate) {
            updateTags();
        }
    }

    public void updateTags() {
        HashSet<Long> hashes = new HashSet<>();
        oldItems.clear();
        oldItems.addAll(items);
        items.clear();
        TLRPC.TL_messages_savedReactionsTags savedReactionsTags = MessagesController.getInstance(currentAccount).getSavedReactionTags();
        if (savedReactionsTags != null) {
            for (int i = 0; i < savedReactionsTags.tags.size(); ++i) {
                TLRPC.TL_savedReactionTag tag = savedReactionsTags.tags.get(i);
                ReactionsLayoutInBubble.VisibleReaction r = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(tag.reaction);
                if (!hashes.contains(r.hash)) {
                    items.add(Item.get(r, tag.count));
                    hashes.add(r.hash);
                }
            }
        }
//        ArrayList<TLRPC.Reaction> defaultReactions = MediaDataController.getInstance(currentAccount).getSavedReactions();
//        for (int i = 0; i < defaultReactions.size(); ++i) {
//            ReactionsLayoutInBubble.VisibleReaction r = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(defaultReactions.get(i));
//            if (!hashes.contains(r.hash)) {
//                items.add(Item.get(r, 0));
//                hashes.add(r.hash);
//            }
//        }

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }
            @Override
            public int getNewListSize() {
                return items.size();
            }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).equals(items.get(newItemPosition));
            }
            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).hash() == items.get(newItemPosition).hash();
            }
        }).dispatchUpdatesTo(adapter);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (shownT < .5f) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    private float shownT;
    public void setShown(float shownT) {
        this.shownT = shownT;
        listView.setPivotX(listView.getWidth() / 2f);
        listView.setPivotY(0);
        listView.setScaleX(lerp(0.8f, 1, shownT));
        listView.setScaleY(lerp(0.8f, 1, shownT));
        listView.setAlpha(shownT);
        invalidate();
    }

    public boolean shown() {
        return shownT > 0.5f;
    }

//    private final Paint backgroundPaint = new Paint();
//    @Override
//    public void setBackgroundColor(int color) {
//        backgroundPaint.setColor(color);
//    }

    public int getCurrentHeight() {
        return (int) (getMeasuredHeight() * shownT);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
//        canvas.drawRect(0, 0, getWidth(), getCurrentHeight(), backgroundPaint);
        canvas.clipRect(0, 0, getWidth(), getCurrentHeight());
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        public Adapter() {
//            setHasStableIds(true);
        }

//        @Override
//        public long getItemId(int position) {
//            if (position < 0 || position >= items.size()) return position;
//            return items.get(position).hash();
//        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = new TagButton(getContext());
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) return;
            final Item item = items.get(position);
            ((TagButton) holder.itemView).set(item.reaction.toTLReaction(), item.count);
            ((TagButton) holder.itemView).setChosen(item.hash() == chosen, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }
    }

    private class TagButton extends View {
        public ReactionsLayoutInBubble.ReactionButton reactionButton;

        public TagButton(Context context) {
            super(context);
            ScaleStateListAnimator.apply(this);
        }

        private int count;
        public void set(TLRPC.Reaction reaction, Integer count) {
            TLRPC.TL_reactionCount reactionCount = new TLRPC.TL_reactionCount();
            reactionCount.reaction = reaction;
            reactionCount.count = this.count = count == null ? 0 : count;

            reactionButton = new ReactionsLayoutInBubble.ReactionButton(null, currentAccount, this, reactionCount, false, resourcesProvider) {
                @Override
                protected void updateColors(float progress) {
                    lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, chosen ? Theme.getColor(Theme.key_chat_inReactionButtonTextSelected) : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), progress);
                    lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, chosen ? Theme.getColor(Theme.key_chat_inReactionButtonBackground, resourcesProvider) : Theme.getColor(Theme.key_actionBarActionModeReaction, resourcesProvider), progress);
                }

                @Override
                protected boolean drawTagDot() {
                    return !drawCounter();
                }

                @Override
                protected int getCacheType() {
                    return AnimatedEmojiDrawable.CACHE_TYPE_ALERT_EMOJI_STATUS;
                }

                @Override
                protected boolean drawCounter() {
                    return count > 0 || counterDrawable.countChangeProgress != 1f;
                }
            };
            reactionButton.width = dp(44.33f);
            reactionButton.counterDrawable.setCount(reactionCount.count, false);
            if (reactionButton.counterDrawable != null && reactionButton.count > 0) {
                reactionButton.width += reactionButton.counterDrawable.textPaint.measureText(reactionButton.countText);
            }
            reactionButton.height = dp(28);
            reactionButton.choosen = chosen;
            if (attached) {
                reactionButton.attach();
            }
        }

        private boolean chosen;
        public boolean setChosen(boolean value, boolean animated) {
            if (chosen == value) return false;
            chosen = value;
            if (reactionButton != null) {
                reactionButton.choosen = value;

                if (animated) {
                    reactionButton.fromTextColor = reactionButton.lastDrawnTextColor;
                    reactionButton.fromBackgroundColor = reactionButton.lastDrawnBackgroundColor;
                    progress.set(0, true);
                } else {
                    progress.set(1, true);
                }
                invalidate();
            }
            return true;
        }

        public boolean setCount(int count) {
            if (this.count != count && reactionButton != null) {
                reactionButton.animateFromWidth = reactionButton.width;
                reactionButton.count = count;
                reactionButton.width = dp(44.33f);
                reactionButton.counterDrawable.setCount(count, true);
                if (reactionButton.counterDrawable != null && reactionButton.count > 0) {
                    reactionButton.width += reactionButton.counterDrawable.textPaint.measureText(reactionButton.countText);
                }
                progress.set(0, true);
                invalidate();
                return true;
            }
            return false;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(dp(8.67f) + (reactionButton != null ? reactionButton.width : dp(44.33f)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
        }


        private AnimatedFloat progress = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);

        @Override
        protected void onDraw(Canvas canvas) {
            reactionButton.draw(canvas, (getWidth() - reactionButton.width) / 2f, (getHeight() - reactionButton.height) / 2f, progress.set(1f), 1f, false);
        }

        private boolean attached;

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!attached) {
                if (reactionButton != null) {
                    reactionButton.attach();
                }
                attached = true;
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (attached) {
                if (reactionButton != null) {
                    reactionButton.detach();
                }
                attached = false;
            }
        }
    }
}
