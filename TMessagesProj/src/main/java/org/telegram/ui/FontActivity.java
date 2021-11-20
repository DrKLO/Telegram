package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jakewharton.processphoenix.ProcessPhoenix;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.FontCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class FontActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;

    private ArrayList<FontType> fontsList;

    @Override
    public boolean onFragmentCreate() {
        fillFonts();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Font", R.string.Font));

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

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.showTextView();
        emptyView.setShowAtCenter(true);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {

            if (getParentActivity() == null || parentLayout == null || !(view instanceof FontCell)) {
                return;
            }

            //restart app to apply new font
            AlertDialog alertDialog
                    = new AlertDialog.Builder(context)
                    .setTitle(LocaleController.getString("exitAppDialogTitle", R.string.exitAppDialogTitle))
                    .setMessage(LocaleController.getString("exitAppDialogContent", R.string.exitAppDialogContent))
                    .setPositiveButton(LocaleController.getString("confirmExitApp", R.string.confirmExitApp), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            LocaleController.getInstance().saveSelectedFont(fontsList.get(position));
                            ProcessPhoenix.triggerRebirth(context);
                            //finishFragment();
                        }
                    }).setNegativeButton(LocaleController.getString("declineExitApp", R.string.declineExitApp), (dialog, which) -> {
                        dialog.dismiss();
                        finishFragment();
                    }).show();

            showDialog(alertDialog);

            //instead of reopen project we can use this function
            //getParentLayout().rebuildAllFragmentViews(false,true);
            //finishFragment();
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            if (listAdapter != null) {
                fillFonts();
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void fillFonts() {
        fontsList = new ArrayList<>();


        fontsList.add(new FontType(
                LocaleController.getString("Anton_font", R.string.Anton_font),
                LocaleController.getString("sample_text_for_fonts", R.string.sample_text_for_fonts),
                R.drawable.ic_reply_icon,
                "fonts/anton_regular.ttf",
                AndroidUtilities.getTypeface("fonts/anton_regular.ttf")));
        fontsList.add(new FontType(
                LocaleController.getString("DancingScript_font", R.string.DancingScript_font),
                LocaleController.getString("sample_text_for_fonts", R.string.sample_text_for_fonts),
                R.drawable.ic_reply_icon,
                "fonts/dancingscript_regular.ttf",
                AndroidUtilities.getTypeface("fonts/dancingscript_regular.ttf")));
        fontsList.add(new FontType(
                LocaleController.getString("Fruktur_font", R.string.Fruktur_font),
                LocaleController.getString("sample_text_for_fonts", R.string.sample_text_for_fonts),
                R.drawable.ic_reply_icon,
                "fonts/fruktur_regular.ttf",
                AndroidUtilities.getTypeface("fonts/fruktur_regular.ttf")));
        fontsList.add(new FontType(
                LocaleController.getString("ZenKurenaido_font", R.string.ZenKurenaido_font),
                LocaleController.getString("sample_text_for_fonts", R.string.sample_text_for_fonts),
                R.drawable.ic_reply_icon,
                "fonts/zenkurenaido_regular.ttf",
                AndroidUtilities.getTypeface("fonts/zenkurenaido_regular.ttf")));

        fontsList.add(new FontType(
                LocaleController.getString("Default_font", R.string.Default),
                LocaleController.getString("sample_text_for_fonts", R.string.sample_text_for_fonts),
                R.drawable.ic_reply_icon,
                LocaleController.getString("Default_font",R.string.Default),
                AndroidUtilities.getTypeface("fonts/rmedium.ttf")));


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
        //private ArrayList<FontType> list;

        public ListAdapter(/*ArrayList<FontType> fontsList,*/ Context context) {
           // this.list = fontsList;
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return fontsList.size();
        }


        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new FontCell(mContext, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 1:
                default: {
                    view = new ShadowSectionCell(mContext);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    FontCell fontCell = (FontCell) holder.itemView;
                    FontType fontType;
                    fontType = fontsList.get(position);
                    boolean last = position == fontsList.size() - 1;

                    fontCell.setFont(fontType, !last);
                    fontCell.setValue(fontType);

                    break;
                }
                case 1: {
                    ShadowSectionCell sectionCell = (ShadowSectionCell) holder.itemView;
                    if (!fontsList.isEmpty() && position == fontsList.size()) {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }

    }

    public FontType getFontByName(String name) {
        for (FontType it : fontsList) {
            if (it.fontName == name)
                return it;
        }
        return null;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{FontCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FontCell.class}, new String[]{"fontNameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FontCell.class}, new String[]{"fontSampletextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{FontCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon));

        return themeDescriptions;
    }
}