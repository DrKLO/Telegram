/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;

import java.util.ArrayList;
import java.util.HashSet;

public class MessagesSearchAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private final Context mContext;
    private final HashSet<Integer> messageIds = new HashSet<>();
    private final ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private final BaseFragment fragment;

    public boolean containsStories;

    public int loadedCount;
    public int flickerCount;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private int searchType;

    private boolean isSavedMessages;

    public MessagesSearchAdapter(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, int searchType, boolean isSavedMessages) {
        this.resourcesProvider = resourcesProvider;
        mContext = context;
        this.fragment = fragment;
        this.searchType = searchType;
        this.isSavedMessages = isSavedMessages;
    }

    public String storiesListQuery;
    public StoriesController.SearchStoriesList storiesList;
    public void setStoriesList(StoriesController.SearchStoriesList storiesList) {
        this.storiesList = storiesList;
        if (storiesList != null) {
            storiesList.load(true, 3);
        }
    }

    private Runnable loadStories = () -> {
        if (storiesList != null) {
            storiesList.load(true, 3);
        }
    };

    public void searchStories(String query, boolean instant) {
        if (TextUtils.equals(storiesListQuery, query)) return;

        String hashtag = null, username = null;
        String tquery = query.trim();
        if (tquery.charAt(0) == '$' || tquery.charAt(0) == '#') {
            int atIndex = tquery.indexOf('@');
            if (atIndex >= 0) {
                hashtag = tquery.substring(0, atIndex);
                username = tquery.substring(atIndex + 1);
            } else {
                hashtag = tquery;
            }
        }

        final boolean wereContainingStories = containsStories;

        AndroidUtilities.cancelRunOnUIThread(loadStories);
        if (storiesList != null) {
            storiesList.cancel();
        }

        if (!TextUtils.isEmpty(hashtag)) {
            storiesListQuery = query;
            storiesList = new StoriesController.SearchStoriesList(currentAccount, username, hashtag);
            if (instant) {
                loadStories.run();
            } else {
                AndroidUtilities.runOnUIThread(loadStories, 1000);
            }
        }

        final boolean nowContainingStories = storiesList != null && storiesList.getCount() > 0;
        if (nowContainingStories != wereContainingStories) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            if (args[0] == storiesList) {
                notifyDataSetChanged();
            }
        }
    }

    @Override
    public void notifyDataSetChanged() {
        final int oldItemsCount = getItemCount();

        containsStories = false;//storiesList != null && storiesList.getCount() > 0;

        searchResultMessages.clear();
        messageIds.clear();
        ArrayList<MessageObject> searchResults = searchType == 0 ? MediaDataController.getInstance(currentAccount).getFoundMessageObjects() : HashtagSearchController.getInstance(currentAccount).getMessages(searchType);
        for (int i = 0; i < searchResults.size(); ++i) {
            MessageObject m = searchResults.get(i);
            if ((!m.hasValidGroupId() || m.isPrimaryGroupMessage) && !messageIds.contains(m.getId())) {
                searchResultMessages.add(m);
                messageIds.add(m.getId());
            }
        }

        final int oldLoadedCount = loadedCount;
        final int oldFlickerCount = flickerCount;

        loadedCount = searchResultMessages.size();
        if (searchType != 0) {
            boolean hasMore = !HashtagSearchController.getInstance(currentAccount).isEndReached(searchType);
            flickerCount = hasMore && loadedCount != 0 ? Utilities.clamp(HashtagSearchController.getInstance(currentAccount).getCount(searchType) - loadedCount, 3, 0) : 0;
        } else {
            boolean hasMore = !MediaDataController.getInstance(currentAccount).searchEndReached();
            flickerCount = hasMore && loadedCount != 0 ? Utilities.clamp(MediaDataController.getInstance(currentAccount).getSearchCount() - loadedCount, 3, 0) : 0;
        }

        final int newItemsCount = getItemCount();

        if (oldItemsCount < newItemsCount) {
            if (oldFlickerCount > 0) notifyItemRangeChanged(oldItemsCount - oldFlickerCount, oldFlickerCount);
            notifyItemRangeInserted(oldItemsCount, newItemsCount - oldItemsCount);
        } else {
            super.notifyDataSetChanged();
        }
    }

    @Override
    public int getItemCount() {
        return (containsStories ? 1 : 0) + searchResultMessages.size() + flickerCount;
    }

    public Object getItem(int i) {
        if (containsStories) {
            i--;
        }
        if (i < 0 || i >= searchResultMessages.size()) {
            return null;
        }
        return searchResultMessages.get(i);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return holder.getItemViewType() == 0 || holder.getItemViewType() == 2;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                view = new DialogCell(null, mContext, false, true, currentAccount, resourcesProvider);
                break;
            case 1:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
                flickerLoadingView.setIsSingleCell(true);
                flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CELL_TYPE);
                view = flickerLoadingView;
                break;
            case 2:
                view = new StoriesView(mContext, resourcesProvider);
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == 0) {
            DialogCell cell = (DialogCell) holder.itemView;
            cell.useSeparator = true;
            MessageObject messageObject = (MessageObject) getItem(position);
            int date;
            long did;
            boolean useMe = false;
            did = messageObject.getDialogId();
            date = messageObject.messageOwner.date;
            if (isSavedMessages) {
                cell.isSavedDialog = true;
                did = messageObject.getSavedDialogId();
                if (messageObject.messageOwner.fwd_from != null && (messageObject.messageOwner.fwd_from.date != 0 || messageObject.messageOwner.fwd_from.saved_date != 0)) {
                    date = messageObject.messageOwner.fwd_from.date;
                    if (date == 0) {
                        date = messageObject.messageOwner.fwd_from.saved_date;
                    }
                } else {
                    date = messageObject.messageOwner.date;
                }
            } else {
                if (messageObject.isOutOwner()) {
                    did = messageObject.getFromChatId();
                }
                useMe = true;
            }
            cell.setDialog(did, messageObject, date, useMe, false);
            cell.setDialogCellDelegate(new DialogCell.DialogCellDelegate() {
                @Override
                public void onButtonClicked(DialogCell dialogCell) {

                }

                @Override
                public void onButtonLongPress(DialogCell dialogCell) {

                }

                @Override
                public boolean canClickButtonInside() {
                    return false;
                }

                @Override
                public void openStory(DialogCell dialogCell, Runnable onDone) {
                    if (MessagesController.getInstance(currentAccount).getStoriesController().hasStories(dialogCell.getDialogId())) {
                        fragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
                        fragment.getOrCreateStoryViewer().open(mContext, dialogCell.getDialogId(), StoriesListPlaceProvider.of((RecyclerListView) dialogCell.getParent()));
                        return;
                    }
                }

                @Override
                public void showChatPreview(DialogCell dialogCell) {

                }

                @Override
                public void openHiddenStories() {

                }
            });
        } else if (holder.getItemViewType() == 2) {
            ((StoriesView) holder.itemView).set(storiesList);
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (containsStories) {
            i--;
            if (i == -1)
                return 2;
        }
        if (i < searchResultMessages.size()) {
            return 0;
        }
        return 1;
    }

    public void attach() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
    }

    public void detach() {
        AndroidUtilities.cancelRunOnUIThread(loadStories);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
    }

    public static class StoriesView extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;
        private final AvatarsDrawable avatarsDrawable;
        private final TextView[] titleTextView = new TextView[2];
        private final TextView[] subtitleTextView = new TextView[2];
        private final ImageView arrowView;

        public StoriesView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;
            setWillNotDraw(false);

            avatarsDrawable = new AvatarsDrawable(this, false);
            avatarsDrawable.setCentered(true);
            avatarsDrawable.width = dp(75);
            avatarsDrawable.height = dp(48);
            avatarsDrawable.drawStoriesCircle = true;
            avatarsDrawable.setSize(dp(22));

            for (int i = 0; i < 2; ++i) {
                titleTextView[i] = new TextView(context);
                titleTextView[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
                titleTextView[i].setTypeface(AndroidUtilities.bold());
                titleTextView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                titleTextView[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
                addView(titleTextView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 76, 7, 40, 0));

                subtitleTextView[i] = new TextView(context);
                subtitleTextView[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
                subtitleTextView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                subtitleTextView[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
                addView(subtitleTextView[i], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 76, 26.33f, 40, 0));
            }

            arrowView = new ImageView(context);
            arrowView.setImageResource(R.drawable.msg_arrowright);
            arrowView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogSearchHint, resourcesProvider), PorterDuff.Mode.SRC_IN));
            addView(arrowView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 8.66f, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
        }

        public boolean set(StoriesController.SearchStoriesList list) {
            int actualCount = 0;
            for (int i = 0; i < list.messageObjects.size() && actualCount < 3; ++i) {
                MessageObject msg = list.messageObjects.get(i);
                final long dialogId = msg.storyItem.dialogId;
                if (!TextUtils.isEmpty(list.username) || true) {
                    avatarsDrawable.setObject(actualCount, list.currentAccount, msg.storyItem);
                    actualCount++;
                } else if (dialogId >= 0) {
                    TLRPC.User user = MessagesController.getInstance(list.currentAccount).getUser(dialogId);
                    if (user != null) {
                        avatarsDrawable.setObject(actualCount, list.currentAccount, user);
                        actualCount++;
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(list.currentAccount).getChat(-dialogId);
                    if (chat != null) {
                        avatarsDrawable.setObject(actualCount, list.currentAccount, chat);
                        actualCount++;
                    }
                }
            }
            avatarsDrawable.setCount(actualCount);
            avatarsDrawable.commitTransition(false);

            if (!TextUtils.isEmpty(list.username)) {
                titleTextView[0].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatPluralStringSpaced("HashtagStoriesFoundChannel", list.getCount(), "@" + list.username), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), null));
            } else {
                titleTextView[0].setText(LocaleController.formatPluralStringSpaced("HashtagStoriesFound", list.getCount()));
            }
            subtitleTextView[0].setText(LocaleController.formatString(R.string.HashtagStoriesFoundSubtitle, list.query));

            return actualCount > 0;
        }

        public void setMessages(int messagesCount, String hashtag, String username) {
            if (!TextUtils.isEmpty(username)) {
                titleTextView[1].setText(AndroidUtilities.replaceSingleLink(LocaleController.formatPluralStringSpaced("HashtagMessagesFoundChannel", messagesCount, "@" + username), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), null));
            } else {
                titleTextView[1].setText(LocaleController.formatPluralStringSpaced("HashtagMessagesFound", messagesCount));
            }
            subtitleTextView[1].setText(LocaleController.formatString(R.string.HashtagMessagesFoundSubtitle, hashtag));
        }

        private float transitValue;
        private ValueAnimator transitionAnimator;
        public void transition(boolean stories) {
            if (transitionAnimator != null) {
                transitionAnimator.cancel();
            }
            transitionAnimator = ValueAnimator.ofFloat(transitValue, stories ? 1.0f : 0.0f);
            transitionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                    transitValue = (float) animation.getAnimatedValue();
                    invalidate();
                    for (int i = 0; i < 2; ++i) {
                        titleTextView[i].setTranslationX(AndroidUtilities.lerp(0, -dp(62), transitValue));
                        titleTextView[i].setVisibility(View.VISIBLE);
                        titleTextView[i].setAlpha(AndroidUtilities.lerp(i == 0 ? 1.0f : 0.0f, i == 1 ? 1.0f : 0.0f, transitValue));
                        subtitleTextView[i].setTranslationX(AndroidUtilities.lerp(0, -dp(62), transitValue));
                        subtitleTextView[i].setVisibility(View.VISIBLE);
                        subtitleTextView[i].setAlpha(AndroidUtilities.lerp(i == 0 ? 1.0f : 0.0f, i == 1 ? 1.0f : 0.0f, transitValue));
                    }
                }
            });
            transitionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    transitValue = stories ? 1.0f : 0.0f;
                    invalidate();
                    for (int i = 0; i < 2; ++i) {
                        titleTextView[i].setTranslationX(AndroidUtilities.lerp(0, -dp(62), transitValue));
                        titleTextView[i].setVisibility((i == 1) == stories ? View.VISIBLE : View.GONE);
                        titleTextView[i].setAlpha(AndroidUtilities.lerp(i == 0 ? 1.0f : 0.0f, i == 1 ? 1.0f : 0.0f, transitValue));
                        subtitleTextView[i].setTranslationX(AndroidUtilities.lerp(0, -dp(62), transitValue));
                        subtitleTextView[i].setVisibility((i == 1) == stories ? View.VISIBLE : View.GONE);
                        subtitleTextView[i].setAlpha(AndroidUtilities.lerp(i == 0 ? 1.0f : 0.0f, i == 1 ? 1.0f : 0.0f, transitValue));
                    }
                }
            });
            transitionAnimator.setDuration(320);
            transitionAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            transitionAnimator.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (transitValue > 0) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * (1.0f - transitValue)), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(AndroidUtilities.lerp(0, -dp(62), transitValue), 0);
            avatarsDrawable.onDraw(canvas);
            canvas.restore();

            super.onDraw(canvas);
            Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
            canvas.drawRect(0, getHeight() - 1, getWidth(), getHeight(), dividerPaint);
        }

        public static class Factory extends UItem.UItemFactory<StoriesView> {
            static { setup(new Factory()); }

            @Override
            public StoriesView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new StoriesView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider) {
                ((StoriesView) view).set((StoriesController.SearchStoriesList) item.object);
            }

            public static UItem asStoriesList(StoriesController.SearchStoriesList list) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = list;
                return item;
            }
        }
    }
}
