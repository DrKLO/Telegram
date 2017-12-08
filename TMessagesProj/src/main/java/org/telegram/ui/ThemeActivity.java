/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.support.v4.content.FileProvider;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.ThemeCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;

public class ThemeActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString("Theme", R.string.Theme));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 0) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
                    editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));

                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("NewTheme", R.string.NewTheme));
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialog, int which) {

                        }
                    });

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    final TextView message = new TextView(getParentActivity());
                    message.setText(LocaleController.formatString("EnterThemeName", R.string.EnterThemeName));
                    message.setTextSize(16);
                    message.setPadding(AndroidUtilities.dp(23), AndroidUtilities.dp(12), AndroidUtilities.dp(23), AndroidUtilities.dp(6));
                    message.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    editText.setMaxLines(1);
                    editText.setLines(1);
                    editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    editText.setGravity(Gravity.LEFT | Gravity.TOP);
                    editText.setSingleLine(true);
                    editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                    editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    editText.setCursorSize(AndroidUtilities.dp(20));
                    editText.setCursorWidth(1.5f);
                    editText.setPadding(0, AndroidUtilities.dp(4), 0, 0);
                    linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));
                    editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                        @Override
                        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                            AndroidUtilities.hideKeyboard(textView);
                            return false;
                        }
                    });
                    final AlertDialog alertDialog = builder.create();
                    alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                        @Override
                        public void onShow(DialogInterface dialog) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    editText.requestFocus();
                                    AndroidUtilities.showKeyboard(editText);
                                }
                            });
                        }
                    });
                    showDialog(alertDialog);
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (editText.length() == 0) {
                                Vibrator vibrator = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                                if (vibrator != null) {
                                    vibrator.vibrate(200);
                                }
                                AndroidUtilities.shakeView(editText, 2, 0);
                                return;
                            }
                            ThemeEditorView themeEditorView = new ThemeEditorView();
                            String name = editText.getText().toString() + ".attheme";
                            themeEditorView.show(getParentActivity(), name);
                            Theme.saveCurrentTheme(name, true);
                            listAdapter.notifyDataSetChanged();
                            alertDialog.dismiss();

                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            if (preferences.getBoolean("themehint", false)) {
                                return;
                            }
                            preferences.edit().putBoolean("themehint", true).commit();
                            try {
                                Toast.makeText(getParentActivity(), LocaleController.getString("CreateNewThemeHelp", R.string.CreateNewThemeHelp), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                } else {
                    position -= 2;
                    if (position >= 0 && position < Theme.themes.size()) {
                        Theme.ThemeInfo themeInfo = Theme.themes.get(position);
                        Theme.applyTheme(themeInfo);
                        if (parentLayout != null) {
                            parentLayout.rebuildAllFragmentViews(false, false);
                        }
                        finishFragment();
                    }
                }
            }
        });

        return fragmentView;
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
            return Theme.themes.size() + 3;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ThemeCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ThemeCell) view).setOnOptionsClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final Theme.ThemeInfo themeInfo = ((ThemeCell) v.getParent()).getCurrentThemeInfo();
                            if (getParentActivity() == null) {
                                return;
                            }

                            BottomSheet.Builder builder = new BottomSheet.Builder(getParentActivity());
                            CharSequence[] items;
                            if (themeInfo.pathToFile == null) {
                                items = new CharSequence[]{
                                        LocaleController.getString("ShareFile", R.string.ShareFile)
                                };
                            } else {
                                items = new CharSequence[]{
                                        LocaleController.getString("ShareFile", R.string.ShareFile),
                                        LocaleController.getString("Edit", R.string.Edit),
                                        LocaleController.getString("Delete", R.string.Delete)};
                            }
                            builder.setItems(items, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, final int which) {
                                    if (which == 0) {
                                        File currentFile;
                                        if (themeInfo.pathToFile == null && themeInfo.assetName == null) {
                                            StringBuilder result = new StringBuilder();
                                            for (HashMap.Entry<String, Integer> entry : Theme.getDefaultColors().entrySet()) {
                                                result.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                                            }
                                            currentFile = new File(ApplicationLoader.getFilesDirFixed(), "default_theme.attheme");
                                            FileOutputStream stream = null;
                                            try {
                                                stream = new FileOutputStream(currentFile);
                                                stream.write(result.toString().getBytes());
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            } finally {
                                                try {
                                                    if (stream != null) {
                                                        stream.close();
                                                    }
                                                } catch (Exception e) {
                                                    FileLog.e("tmessage", e);
                                                }
                                            }
                                        } else if (themeInfo.assetName != null) {
                                            currentFile = Theme.getAssetFile(themeInfo.assetName);
                                        } else {
                                            currentFile = new File(themeInfo.pathToFile);
                                        }
                                        File finalFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), currentFile.getName());
                                        try {
                                            if (!AndroidUtilities.copyFile(currentFile, finalFile)) {
                                                return;
                                            }
                                            Intent intent = new Intent(Intent.ACTION_SEND);
                                            intent.setType("text/xml");
                                            if (Build.VERSION.SDK_INT >= 24) {
                                                try {
                                                    intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(getParentActivity(), BuildConfig.APPLICATION_ID + ".provider", finalFile));
                                                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                } catch (Exception ignore) {
                                                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                                                }
                                            } else {
                                                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(finalFile));
                                            }
                                            startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    } else if (which == 1) {
                                        if (parentLayout != null) {
                                            Theme.applyTheme(themeInfo);
                                            parentLayout.rebuildAllFragmentViews(true, true);
                                            new ThemeEditorView().show(getParentActivity(), themeInfo.name);
                                        }
                                    } else {
                                        if (getParentActivity() == null) {
                                            return;
                                        }
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                        builder.setMessage(LocaleController.getString("DeleteThemeAlert", R.string.DeleteThemeAlert));
                                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                        builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                if (Theme.deleteTheme(themeInfo)) {
                                                    parentLayout.rebuildAllFragmentViews(true, true);
                                                }
                                                if (listAdapter != null) {
                                                    listAdapter.notifyDataSetChanged();
                                                }
                                            }
                                        });
                                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                        showDialog(builder.create());
                                    }
                                }
                            });
                            showDialog(builder.create());
                        }
                    });
                    break;
                case 1:
                    view = new TextSettingsCell(mContext);
                    ((TextSettingsCell) view).setText(LocaleController.getString("CreateNewTheme", R.string.CreateNewTheme), false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    ((TextInfoPrivacyCell) view).setText(LocaleController.getString("CreateNewThemeInfo", R.string.CreateNewThemeInfo));
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                default:
                    view = new ShadowSectionCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                position -= 2;
                Theme.ThemeInfo themeInfo = Theme.themes.get(position);
                ((ThemeCell) holder.itemView).setTheme(themeInfo, position != Theme.themes.size() - 1);
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == 0) {
                return 1;
            } else if (i == 1) {
                return 2;
            } else if (i == Theme.themes.size() + 2) {
                return 3;
            }
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon),
                new ThemeDescription(listView, 0, new Class[]{ThemeCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
        };
    }
}
