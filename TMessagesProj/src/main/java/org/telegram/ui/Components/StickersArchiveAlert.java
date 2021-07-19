/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ArchivedStickerSetCell;
import org.telegram.ui.StickersActivity;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StickersArchiveAlert extends AlertDialog.Builder {

    private ArrayList<TLRPC.StickerSetCovered> stickerSets;

    private int scrollOffsetY;
    private int reqId;
    private boolean ignoreLayout;
    private int currentType;
    private BaseFragment parentFragment;

    public StickersArchiveAlert(Context context, BaseFragment baseFragment, ArrayList<TLRPC.StickerSetCovered> sets) {
        super(context);

        TLRPC.StickerSetCovered set = sets.get(0);
        if (set.set.masks) {
            currentType = MediaDataController.TYPE_MASK;
            setTitle(LocaleController.getString("ArchivedMasksAlertTitle", R.string.ArchivedMasksAlertTitle));
        } else {
            currentType = MediaDataController.TYPE_IMAGE;
            setTitle(LocaleController.getString("ArchivedStickersAlertTitle", R.string.ArchivedStickersAlertTitle));
        }
        stickerSets = new ArrayList<>(sets);
        parentFragment = baseFragment;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        setView(container);

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setGravity(LayoutHelper.getAbsoluteGravityStart());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setPadding(AndroidUtilities.dp(23), AndroidUtilities.dp(10), AndroidUtilities.dp(23), 0);
        if (set.set.masks) {
            textView.setText(LocaleController.getString("ArchivedMasksAlertInfo", R.string.ArchivedMasksAlertInfo));
        } else {
            textView.setText(LocaleController.getString("ArchivedStickersAlertInfo", R.string.ArchivedStickersAlertInfo));
        }
        container.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        RecyclerListView listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        listView.setGlowColor(0xfff5f6f7);
        container.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        setNegativeButton(LocaleController.getString("Close", R.string.Close), (dialog, which) -> dialog.dismiss());
        if (parentFragment != null) {
            setPositiveButton(LocaleController.getString("Settings", R.string.Settings), (dialog, which) -> {
                parentFragment.presentFragment(new StickersActivity(currentType));
                dialog.dismiss();
            });
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return stickerSets.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ArchivedStickerSetCell(context, false);
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(82)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((ArchivedStickerSetCell) holder.itemView).setStickersSet(stickerSets.get(position), position != stickerSets.size() - 1);
        }
    }
}
