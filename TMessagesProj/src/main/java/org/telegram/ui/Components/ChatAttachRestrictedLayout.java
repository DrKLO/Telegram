package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

public class ChatAttachRestrictedLayout extends ChatAttachAlert.AttachAlertLayout {

    private final EmptyTextProgressView progressView;
    private final RecyclerListView listView;
    public final int id;
    private final RecyclerView.Adapter adapter;
    private int gridExtraSpace;

    public ChatAttachRestrictedLayout(int id, ChatAttachAlert alert, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        this.id = id;
        progressView = new EmptyTextProgressView(context, null, resourcesProvider);
        progressView.setText(LocaleController.getString("NoPhotos", R.string.NoPhotos));
        progressView.setOnTouchListener(null);
        progressView.setTextSize(16);
        addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        progressView.setLottie(R.raw.media_forbidden, 150, 150);
        TLRPC.Chat chat = ((ChatActivity) parentAlert.baseFragment).getCurrentChat();
        if (id == 1) {
            progressView.setText(ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_MEDIA));
        } else if (id == 3) {
            progressView.setText(ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_MUSIC));
        } else if (id == 4) {
            progressView.setText(ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_DOCUMENTS));
        } else {
            progressView.setText(ChatObject.getRestrictedErrorText(chat, ChatObject.ACTION_SEND_PLAIN));
        }
        progressView.showTextView();

        listView = new RecyclerListView(context, resourcesProvider);
        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_DATE);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setClipToPadding(false);
        listView.setAdapter(adapter = new RecyclerView.Adapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = new View(getContext()) {

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(gridExtraSpace, MeasureSpec.EXACTLY));
                    }
                };
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {

            }

            @Override
            public int getItemCount() {
                return 1;
            }
        });
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachRestrictedLayout.this, true, dy);
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }


    @Override
    int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            //   runShadowAnimation(false);
        } else {
//            runShadowAnimation(true);
        }
        progressView.setTranslationY(newOffset + (getMeasuredHeight() - newOffset - AndroidUtilities.dp(50) - progressView.getMeasuredHeight()) / 2);
//        frameLayout.setTranslationY(newOffset);
        return newOffset + AndroidUtilities.dp(12);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(4);
    }

    @Override
    int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    void onPreMeasure(int availableWidth, int availableHeight) {
        super.onPreMeasure(availableWidth, availableHeight);
        int newSize = Math.max(0, availableHeight - ActionBar.getCurrentActionBarHeight());
        if (gridExtraSpace != newSize) {
            gridExtraSpace = newSize;
            adapter.notifyDataSetChanged();
        }
        int paddingTop;
        if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
            paddingTop = (int) (availableHeight / 3.5f);
        } else {
            paddingTop = (availableHeight / 5 * 2);
        }
        paddingTop -= AndroidUtilities.dp(52);
        if (paddingTop < 0) {
            paddingTop = 0;
        }
        if (listView.getPaddingTop() != paddingTop) {
            listView.setPadding(AndroidUtilities.dp(6), paddingTop, AndroidUtilities.dp(6), AndroidUtilities.dp(48));
        }
    }


}
