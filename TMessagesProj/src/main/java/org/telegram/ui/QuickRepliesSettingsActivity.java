/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EditTextSettingsCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class QuickRepliesSettingsActivity extends BaseFragment {

	private ListAdapter listAdapter;
	private RecyclerListView listView;

	private int reply1Row;
	private int reply2Row;
	private int reply3Row;
	private int reply4Row;
	private int explanationRow;

	private int rowCount;
	private EditTextSettingsCell[] textCells = new EditTextSettingsCell[4];

	@Override
	public boolean onFragmentCreate() {
		super.onFragmentCreate();

		rowCount = 0;
		reply1Row = rowCount++;
		reply2Row = rowCount++;
		reply3Row = rowCount++;
		reply4Row = rowCount++;
		explanationRow = rowCount++;

		return true;
	}

	@Override
	public View createView(Context context) {
		actionBar.setBackButtonImage(R.drawable.ic_ab_back);
		actionBar.setTitle(LocaleController.getString("VoipQuickReplies", R.string.VoipQuickReplies));
		if (AndroidUtilities.isTablet()) {
			actionBar.setOccupyStatusBar(false);
		}
		actionBar.setAllowOverlayTitle(true);
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
			}
		});

		listAdapter = new ListAdapter(context);

		fragmentView = new FrameLayout(context);
		fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
		FrameLayout frameLayout = (FrameLayout) fragmentView;

		listView = new RecyclerListView(context);
		listView.setVerticalScrollBarEnabled(false);
		listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
		listView.setAdapter(listAdapter);

		return fragmentView;
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		SharedPreferences prefs = getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		for (int i = 0; i < textCells.length; i++) {
			if (textCells[i] != null) {
				String text = textCells[i].getTextView().getText().toString();
				if (!TextUtils.isEmpty(text))
					editor.putString("quick_reply_msg" + (i + 1), text);
				else
					editor.remove("quick_reply_msg" + (i + 1));
			}
		}
		editor.commit();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	private class ListAdapter extends RecyclerListView.SelectionAdapter {

		private Context mContext;

		public ListAdapter(Context context) {
			mContext = context;
		}

		@Override
		public int getItemCount() {
			return rowCount;
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 0: {
					TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
					cell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
					cell.setText(LocaleController.getString("VoipQuickRepliesExplain", R.string.VoipQuickRepliesExplain));
					break;
				}
				case 1: {
					TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
					break;
				}
				case 9:
				case 10:
				case 11:
				case 12: {
					EditTextSettingsCell textCell = (EditTextSettingsCell) holder.itemView;
					String settingsKey = null;
					String defValue = null;
					if (position == reply1Row) {
						settingsKey = "quick_reply_msg1";
						defValue = LocaleController.getString("QuickReplyDefault1", R.string.QuickReplyDefault1);
					} else if (position == reply2Row) {
						settingsKey = "quick_reply_msg2";
						defValue = LocaleController.getString("QuickReplyDefault2", R.string.QuickReplyDefault2);
					} else if (position == reply3Row) {
						settingsKey = "quick_reply_msg3";
						defValue = LocaleController.getString("QuickReplyDefault3", R.string.QuickReplyDefault3);
					} else if (position == reply4Row) {
						settingsKey = "quick_reply_msg4";
						defValue = LocaleController.getString("QuickReplyDefault4", R.string.QuickReplyDefault4);
					}
					textCell.setTextAndHint(getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getString(settingsKey, ""), defValue, position != reply4Row);

					break;
				}
				case 4: {
					TextCheckCell cell = (TextCheckCell) holder.itemView;
					cell.setTextAndCheck(LocaleController.getString("AllowCustomQuickReply", R.string.AllowCustomQuickReply), getParentActivity().getSharedPreferences("mainconfig", Context.MODE_PRIVATE).getBoolean("quick_reply_allow_custom", true), false);
				}
			}
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			int position = holder.getAdapterPosition();
			return position == reply1Row || position == reply2Row || position == reply3Row || position == reply4Row;
		}

		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view;
			switch (viewType) {
				case 0:
					view = new TextInfoPrivacyCell(mContext);
					break;
				case 1:
					view = new TextSettingsCell(mContext);
					view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
					break;
				case 9:
				case 10:
				case 11:
				case 12:
					view = new EditTextSettingsCell(mContext);
					view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
					textCells[viewType - 9] = (EditTextSettingsCell) view;
					break;
				case 4:
				default:
					view = new TextCheckCell(mContext);
					view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
					break;
			}
			view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
			return new RecyclerListView.Holder(view);
		}

		@Override
		public int getItemViewType(int position) {
			if (position == explanationRow) {
				return 0;
			} else if (position == reply1Row || position == reply2Row || position == reply3Row || position == reply4Row) {
				return 9 + (position - reply1Row);
			} else {
				return 1;
			}
		}
	}

	@Override
	public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, EditTextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
		themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
		themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{EditTextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));

		themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
		themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

		return themeDescriptions;
	}
}
