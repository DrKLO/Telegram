package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PaddingItemDecoration;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SpanningLinearLayoutManager;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import org.telegram.messenger.R;

public class ShortcutsCell extends FrameLayout {

    public enum ButtonType {
        STOP(R.string.Stop, R.drawable.profile_block),
        CALL(R.string.Call, R.drawable.profile_call),
        GIFT(R.string.ActionStarGift, R.drawable.profile_gift),
        JOIN(R.string.VoipChatJoin, R.drawable.profile_join),
        LEAVE(R.string.VoipGroupLeave, R.drawable.profile_leave),
        LIVE_STREAM(R.string.StartVoipChannelTitle, R.drawable.profile_live_stream),
        VOICE_CHAT(R.string.StartVoipChatTitle, R.drawable.profile_live_stream),
        MESSAGE(R.string.Message, R.drawable.profile_message),
        DISCUSS(R.string.ProfileDiscuss, R.drawable.profile_message),
        MUTE(R.string.Mute, R.drawable.profile_mute),
        UNMUTE(R.string.Unmute, R.drawable.profile_unmute),
        REPORT(R.string.ReportChat, R.drawable.profile_report),
        SHARE(R.string.LinkActionShare, R.drawable.profile_share),
        STORY(R.string.Story, R.drawable.profile_story),
        VIDEO(R.string.GroupCallCreateVideo, R.drawable.profile_video);

        private final int titleResId;
        private final int iconResId;

        ButtonType(int titleResId, int iconResId) {
            this.titleResId = titleResId;
            this.iconResId = iconResId;
        }
    }

    public interface OnButtonClickListener {
        void onClick(View v, int index, int type);
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final int height = AndroidUtilities.dp(70);
    private final int spacing = AndroidUtilities.dp(4);

    private OnButtonClickListener onButtonClickListener = null;

    private final RecyclerListView listView;
    private final Adapter adapter;

    private final int sidePadding = AndroidUtilities.dp(8);

    public ShortcutsCell(Context context) {
        this(context, null);
    }

    public ShortcutsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        listView = new RecyclerListView(context);
        SpanningLinearLayoutManager lm = new SpanningLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false, listView);
        lm.setMinimumItemWidth(AndroidUtilities.dp(36));

        lm.setItemPadding(spacing);
        lm.setItemHeight(height - spacing * 2);

        adapter = new Adapter();

        listView.setSelectorRadius(AndroidUtilities.dp(16));
        listView.setSelectorDrawableColor(getThemedColor(Theme.key_actionBarTabSelector));

        listView.setAdapter(adapter);
        listView.setLayoutManager(lm);
        listView.addItemDecoration(new PaddingItemDecoration(spacing));
        listView.setPadding(sidePadding, 0, sidePadding, 0);
        listView.setEnabled(true);
        listView.setOnItemClickListener((view, position) -> {
            if (onButtonClickListener != null) {
                int tp = adapter.items.get(position).type;
                long clickDelay;
                if (view instanceof ShortcutButton) {
                    ShortcutButton btn = ((ShortcutButton) view);
                    if (btn.isAnimationPlaying()) {
                        return;
                    }
                    clickDelay = ((ShortcutButton) view).getClickDelay(true);
                } else {
                    clickDelay = 0;
                }

                if (clickDelay > 0) {
                    this.postDelayed(() -> {
                        onButtonClickListener.onClick(view, position, tp);
                    }, clickDelay);
                } else {
                    onButtonClickListener.onClick(view, position, tp);
                }
            }
        });

        SpanningLinearLayoutManager.SpanningItemAnimator itemAnimator = lm.new SpanningItemAnimator() {
            @Override
            public boolean animateAdd(RecyclerView.ViewHolder holder) {
                int count = lm.getItemCount();
                if (count == 4) {
                    this.translateMultiplier = 2f;
                } else if (count == 3) {
                    this.translateMultiplier = 1.5f;
                }

                boolean result = super.animateAdd(holder);
                this.translateMultiplier = 0;
                return result;
            }

            @Override
            public boolean animateRemove(RecyclerView.ViewHolder holder, RecyclerView.ItemAnimator.ItemHolderInfo info) {
                return super.animateRemove(holder, info);
            }
        };

        listView.setItemAnimator(itemAnimator);
        itemAnimator.setSupportsChangeAnimations(true);

        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 80, Gravity.LEFT));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private void changeItems(boolean cacheAlso, Consumer<ShortcutButton> func) {
        listView.getRecycledViewPool().clear();
        int count = 0;
        if (cacheAlso) {
            count = listView.getHiddenChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getHiddenChildAt(a));
            }
            count = listView.getCachedChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getCachedChildAt(a));
            }
            count = listView.getAttachedScrapChildCount();
            for (int a = 0; a < count; a++) {
                func.accept((ShortcutButton) listView.getAttachedScrapChildAt(a));
            }
        }
        count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            func.accept((ShortcutButton) listView.getChildAt(a));
        }
    }

    public void setShowProgress(float pr) {
        changeItems(false, shortcutButton -> {
            shortcutButton.buttonImage.setScaleX(pr);
            shortcutButton.buttonImage.setScaleY(pr);
            shortcutButton.buttonText.setScaleX(pr);
            shortcutButton.buttonText.setScaleY(pr);
            shortcutButton.getLayoutParams();
            ViewGroup.LayoutParams lp = shortcutButton.getLayoutParams();
            if (lp != null) {
                lp.height = (int) ((height - spacing * 2) * pr);
            }
            shortcutButton.requestLayout();
        });
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public RecyclerListView getListView() { return listView; };

    public void setOnButtonClickListener(OnButtonClickListener onButtonClickListener) {
        this.onButtonClickListener = onButtonClickListener;
    }

    public static class ListItem {
        public int type;
        public String text;
        public int iconResource;
        public int animatedIconResource;

        public ListItem(int type, String text, int iconResource) {
            this(type, text, iconResource, 0);
        }

        public ListItem(int type, String text, int iconResource, int animatedIconResource) {
            this.type = type;
            this.text = text;
            this.iconResource = iconResource;
            this.animatedIconResource = animatedIconResource;
        }

        public boolean compare(ListItem other) {
            return other != null
                    && type == other.type
                    && Objects.equals(text, other.text)
                    && iconResource == other.iconResource
                    && animatedIconResource == other.animatedIconResource;
        }
    }

    public class ShortcutButton extends FrameLayout {

        RLottieImageView buttonImage;
        SimpleTextView buttonText;

        public ShortcutButton(Context context) {
            super(context);

            buttonText = new SimpleTextView(getContext());
            buttonText.setTextColor(getThemedColor(Theme.key_actionBarDefaultIcon));
            buttonText.setTextSize(14);
            buttonText.setScrollNonFitText(true);

            buttonImage = new RLottieImageView(getContext());
            buttonImage.setScaleType(ImageView.ScaleType.CENTER);

            addView(buttonImage, LayoutHelper.createFrame(24, 24, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));
            addView(buttonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 36, 0, 0));

            setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), 0x17000000)); // todo background color
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            int center = this.getMeasuredWidth() / 2;
            int bWidth = Math.min(buttonText.getTextWidth(), buttonText.getMeasuredWidth());
            int l = center - (bWidth / 2);
            buttonText.layout(l, buttonText.getTop(), l + bWidth, buttonText.getBottom());
        }

        private ListItem currentItem;
        private int currentIndex;

        public void setData(int index, ListItem item) {
            currentIndex = index;
            if (item == null || item.compare(currentItem)) {
                currentItem = item;

                if (buttonImage != null) {
                    RLottieDrawable drawable = buttonImage.getAnimatedDrawable();
                    if (drawable != null) {
                        buttonImage.stopAnimation();
                        drawable.setCurrentFrame(0);
                    }
                }

                return;
            }

            currentItem = item;

            final RLottieDrawable aDrawable;
            Drawable drawable;
            if (item.animatedIconResource != 0) {
                aDrawable = new RLottieDrawable(item.animatedIconResource, "" + item.animatedIconResource, AndroidUtilities.dp(24), AndroidUtilities.dp(24), true, null);
                aDrawable.beginApplyLayerColors();
                aDrawable.setLayerColor("*.**", getThemedColor(Theme.key_actionBarDefaultIcon));
                aDrawable.commitApplyLayerColors();
                drawable = aDrawable;
            } else {
                drawable = ContextCompat.getDrawable(getContext(), item.iconResource);
                if (drawable != null) {
                    drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.SRC_IN));
                }
            }

            if (drawable != null) {
                if (drawable instanceof RLottieDrawable) {
                    buttonImage.setAnimation((RLottieDrawable) drawable);
                } else {
                    buttonImage.setImageDrawable(drawable);
                }
            }

            buttonText.setText(item.text);
        }

        public long getClickDelay(boolean startAnimation) {
            boolean animationsEnabled = MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
            if (!animationsEnabled) {
                return 0;
            }

            if (buttonImage.getAnimatedDrawable() != null) {
                if (startAnimation) {
                    buttonImage.playAnimation();
                }

                return buttonImage.getAnimatedDrawable().getDuration();
            }
            return 0;
        }

        public boolean isAnimationPlaying() {
            return buttonImage.isPlaying();
        }
    }

    public class Adapter extends RecyclerListView.SelectionAdapter {
        ArrayList<ListItem> items = new ArrayList<>(4);

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ShortcutButton view = new ShortcutButton(parent.getContext());
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            if (lp != null) {
                lp.width = -1;
                lp.height = -1;
            }
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ListItem item = items.get(position);
            ShortcutButton button = (ShortcutButton) holder.itemView;
            button.setData(position, item);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        public void setItems(ArrayList<ListItem> items) {
            this.items = items;
        }

        public ArrayList<ListItem> getItems() {
            return items;
        }

        @Override
        public boolean isEnabled(@NonNull RecyclerView.ViewHolder holder) {
            return true;
        }

        public ListItem createButton(ButtonType type) {
            return new ListItem(type.ordinal(), LocaleController.getString(type.titleResId), type.iconResId, 0);
        }

        public ListItem createButton(int type, String text, int iconResource) {
            return new ListItem(type, text, iconResource);
        }

        public ListItem createButton(int type, String text, int iconResource, int animatedIconResource) {
            return new ListItem(type, text, iconResource, animatedIconResource);
        }
    }

    public static class DiffUtilCallback extends DiffUtil.Callback {

        ArrayList<ListItem> oldItems;
        ArrayList<ListItem> newItems;
        boolean sizeChanged;

        public DiffUtilCallback(ArrayList<ListItem> oldItems, ArrayList<ListItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
            sizeChanged = oldItems.size() != newItems.size();
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldItems.get(oldItemPosition).type == newItems.get(newItemPosition).type;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            if (sizeChanged) return false;

            ListItem oldItem = oldItems.get(oldItemPosition);
            ListItem newItem = newItems.get(newItemPosition);

            return oldItem.iconResource == newItem.iconResource && Objects.equals(oldItem.text, newItem.text);
        }
    }
}
