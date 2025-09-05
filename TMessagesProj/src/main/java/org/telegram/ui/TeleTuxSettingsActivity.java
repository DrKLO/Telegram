package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import android.widget.Toast;
import org.telegram.ui.ActionBar.AlertDialog;

public class TeleTuxSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int shamsiCalendarRow;
    private int fontRow;
    private int rowCount;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString("TeleTuxSettings", R.string.TeleTuxSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        updateRows();

        listAdapter = new ListAdapter(context);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == shamsiCalendarRow) {
                SharedConfig.toggleUseShamsiCalendar();
                ((TextCheckCell) view).setChecked(SharedConfig.useShamsiCalendar);
            } else if (position == fontRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SelectFont", R.string.SelectFont));
                final String[] fonts = {LocaleController.getString("Default", R.string.Default), LocaleController.getString("Vazir", R.string.Vazir)};
                int current = 0;
                if (SharedConfig.customFont.equals("Vazir")) {
                    current = 1;
                }
                builder.setSingleChoiceItems(fonts, current, (dialog, which) -> {
                    if (which == 0) {
                        SharedConfig.customFont = "Default";
                    } else {
                        SharedConfig.customFont = "Vazir";
                    }
                    SharedConfig.saveConfig();
                    org.telegram.messenger.AndroidUtilities.mediumTypeface = null;
                    listAdapter.notifyItemChanged(fontRow);
                    dialog.dismiss();
                    Toast.makeText(getParentActivity(), LocaleController.getString("FontChangedRestart", R.string.FontChangedRestart), Toast.LENGTH_LONG).show();
                });
                showDialog(builder.create());
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        shamsiCalendarRow = rowCount++;
        fontRow = rowCount++;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == shamsiCalendarRow || position == fontRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 1:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 0:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == fontRow) {
                        String currentFont = SharedConfig.customFont;
                        if ("Default".equals(currentFont)) {
                            currentFont = LocaleController.getString("Default", R.string.Default);
                        } else if ("Vazir".equals(currentFont)) {
                            currentFont = LocaleController.getString("Vazir", R.string.Vazir);
                        }
                        cell.setTextAndValue(LocaleController.getString("Font", R.string.Font), currentFont, false);
                    }
                    break;
                }
                case 0:
                default: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == shamsiCalendarRow) {
                        cell.setTextAndCheck(LocaleController.getString("UseShamsiCalendar", R.string.UseShamsiCalendar), SharedConfig.useShamsiCalendar, true);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == shamsiCalendarRow) {
                return 0;
            } else if (i == fontRow) {
                return 1;
            }
            return 0;
        }
    }
}
