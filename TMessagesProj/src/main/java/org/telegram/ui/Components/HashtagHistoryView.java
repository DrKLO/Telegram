package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.R;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class HashtagHistoryView extends FrameLayout {
    private int currentAccount;
    private Theme.ResourcesProvider resourcesProvider;
    private AnimatorSet animation;
    private ArrayList<String> history;

    public FrameLayout emptyView;
    private ImageView emptyImage;
    private TextView emptyText;

    private UniversalRecyclerView recyclerView;
    private UniversalAdapter adapter;

    public HashtagHistoryView(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount) {
        super(context);
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

        recyclerView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onClick, this::onLongClick, resourcesProvider);
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                HashtagHistoryView.this.onScrolled(recyclerView, dx, dy);
            }
        });
        adapter = (UniversalAdapter) recyclerView.getAdapter();
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

    protected void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {

    }

    public void show(boolean show) {
        if (show == isShowing()) {
            return;
        }

        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        if (show) {
            setVisibility(View.VISIBLE);
        }
        setTag(show ? 1 : null);
        animation = new AnimatorSet();
        animation.playTogether(ObjectAnimator.ofFloat(this, View.ALPHA, show ? 1.0f : 0.0f));
        animation.setInterpolator(CubicBezierInterpolator.EASE_IN);
        animation.setDuration(180);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(HashtagHistoryView.this.animation)) {
                    HashtagHistoryView.this.animation = null;
                    if (!show) {
                        setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(HashtagHistoryView.this.animation)) {
                    HashtagHistoryView.this.animation = null;
                }
            }
        });
        animation.start();
    }

    public boolean isShowing() {
        return getTag() != null;
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
        } else {
            onClick(history.get(item.id - 1));
        }
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

    protected void onClick(String hashtag) {
    }
}
