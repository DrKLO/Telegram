/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;
import java.util.HashSet;

public class MessagesSearchAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private Context mContext;
    private HashSet<Integer> messageIds = new HashSet<>();
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();

    public boolean containsStories;

    public int loadedCount;
    public int flickerCount;

    private int currentAccount = UserConfig.selectedAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private int searchType;

    private boolean isSavedMessages;

    public MessagesSearchAdapter(Context context, Theme.ResourcesProvider resourcesProvider, int searchType, boolean isSavedMessages) {
        this.resourcesProvider = resourcesProvider;
        mContext = context;
        this.searchType = searchType;
        this.isSavedMessages = isSavedMessages;
    }

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

    public void searchStories(String hashtag, boolean instant) {
//        if (hashtag.startsWith("$")) hashtag = hashtag.substring(1);
//        if (hashtag.startsWith("#")) hashtag = hashtag.substring(1);

        final String currentHashtag = storiesList == null ? "" : storiesList.query;
        if (TextUtils.equals(currentHashtag, hashtag)) return;

        final boolean wereContainingStories = containsStories;

        AndroidUtilities.cancelRunOnUIThread(loadStories);
        if (storiesList != null) {
            storiesList.cancel();
        }

        if (!TextUtils.isEmpty(hashtag)) {
            storiesList = new StoriesController.SearchStoriesList(currentAccount, hashtag);
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

        containsStories = storiesList != null && storiesList.getCount() > 0;

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
            notifyItemRangeChanged(oldItemsCount - oldFlickerCount, oldFlickerCount);
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
        private final TextView titleTextView;
        private final TextView subtitleTextView;

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

            titleTextView = new TextView(context);
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleTextView.setTypeface(AndroidUtilities.bold());
            titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 76, 7, 12, 0));

            subtitleTextView = new TextView(context);
            subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            subtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 76, 26.33f, 12, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
        }

        public void set(StoriesController.SearchStoriesList list) {
            int actualCount = 0;
            for (int i = 0; i < list.messageObjects.size() && actualCount < 3; ++i) {
                MessageObject msg = list.messageObjects.get(i);
                final long dialogId = msg.storyItem.dialogId;
                if (dialogId >= 0) {
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

            titleTextView.setText(LocaleController.formatPluralStringSpaced("HashtagStoriesFound", list.getCount()));
            subtitleTextView.setText(LocaleController.formatString(R.string.HashtagStoriesFoundSubtitle, list.query));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.translate(0, 0);
            avatarsDrawable.onDraw(canvas);
            canvas.restore();

            super.onDraw(canvas);
            Paint dividerPaint = Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider);
            if (dividerPaint == null) dividerPaint = Theme.dividerPaint;
            canvas.drawRect(0, getHeight() - 1, getWidth(), getHeight(), dividerPaint);
        }
    }
}
