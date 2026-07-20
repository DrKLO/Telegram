package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.R;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class HashtagHistoryView extends FrameLayout {
    private final int currentAccount;
    private final Theme.ResourcesProvider resourcesProvider;
    private ArrayList<String> history;

    public final FrameLayout emptyView;
    private final ImageView emptyImage;
    private final TextView emptyText;

    private final UniversalRecyclerView recyclerView;
    private final UniversalAdapter adapter;

    public HashtagHistoryView(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        recyclerView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onClick, this::onLongClick, resourcesProvider);
        recyclerView.setClipToPadding(false);
        adapter = (UniversalAdapter) recyclerView.getAdapter();
        adapter.setApplyBackground(false);
        addView(recyclerView, LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);

        emptyView = new FrameLayout(context);

        emptyImage = new ImageView(context);
        emptyImage.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        emptyImage.setScaleType(ImageView.ScaleType.CENTER);
        emptyImage.setImageResource(R.drawable.large_hashtags);
        emptyView.addView(emptyImage, LayoutHelper.createFrame(56, 56, Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        emptyText = new TextView(context);
        emptyText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider));
        emptyText.setText(LocaleController.getString(R.string.HashtagSearchPlaceholder));
        emptyText.setGravity(Gravity.CENTER);
        emptyView.addView(emptyText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 56, 0, 0));

        addView(emptyView, LayoutHelper.createFrame(210, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        recyclerView.setEmptyView(emptyView);
    }

    public void setTopBottomPadding(int top, int bottom) {
        recyclerView.setPadding(0, top, 0, bottom);
        emptyView.setTranslationY((top - bottom) / 2f);
    }

    public void update() {
        adapter.update(true);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        history = new ArrayList<>(0);
        history.addAll(HashtagSearchController.getInstance(currentAccount).history);
        if (history.isEmpty()) {
            return;
        }
        for (int i = 0; i < history.size(); i++) {
            String hashtag = history.get(i);
            if (!hashtag.startsWith("#") && !hashtag.startsWith("$")) {
                continue;
            }
            int iconId = hashtag.startsWith("$") ? R.drawable.menu_cashtag : R.drawable.menu_hashtag;
            hashtag = hashtag.substring(1);
            items.add(UItem.asButton(i + 1, iconId, hashtag));
        }
        items.add(UItem.asButton(0, R.drawable.msg_clear_recent, LocaleController.getString(R.string.ClearHistory)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 0) {
            HashtagSearchController.getInstance(currentAccount).clearHistory();
            update();
        } else if (onClickListener != null) {
            onClickListener.run(history.get(item.id - 1));
        }
    }

    private Utilities.Callback<String> onClickListener;

    public void setOnHashtagClickListener(Utilities.Callback<String> onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setOnScrollListener(RecyclerView.OnScrollListener onScrollListener) {
        recyclerView.addOnScrollListener(onScrollListener);
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id != 0) {
            String hashtag = history.get(item.id - 1);
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
            builder.setTitle(LocaleController.getString(R.string.ClearSearchSingleAlertTitle));
            builder.setMessage(LocaleController.formatString(R.string.ClearSearchSingleHashtagAlertText, hashtag));
            builder.setPositiveButton(LocaleController.getString(R.string.ClearSearchRemove), (dialogInterface, i) -> {
                HashtagSearchController.getInstance(currentAccount).removeHashtagFromHistory(hashtag);
                update();
            });
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
            return true;
        }
        return false;
    }
}
