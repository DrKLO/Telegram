/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextColorCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorSelectorDialog;

import java.io.File;
import java.util.ArrayList;

import static org.telegram.ui.Components.ColorSelectorDialog.OnColorChangedListener;

public class ThemingActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private static final String TAG = "ThemingActivity";

    private boolean reseting = false;
    private boolean saving = false;

    private int generalSection2Row;
    private int themeColorRow;
    private int screensSectionRow;
    private int screensSection2Row;
    private int chatsRow;
    private int chatRow;
    private int contactsRow;

    private int themesSectionRow;
    private int themesSection2Row;
    private int resetThemeRow;
    private int saveThemeRow;
    private int applyThemeRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        generalSection2Row = rowCount++;
        themeColorRow = rowCount++;

        screensSectionRow = rowCount++;
        screensSection2Row = rowCount++;
        chatsRow = rowCount++;
        chatRow = rowCount++;
        contactsRow = rowCount++;

        themesSectionRow = rowCount++;
        themesSection2Row = rowCount++;
        saveThemeRow = rowCount++;
        applyThemeRow = rowCount++;
        resetThemeRow = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if(AndroidUtilities.needRestart){
            //AndroidUtilities.needRestart = false;
            AndroidUtilities.restartApp();
        }
    }

    @Override
    public View createView(LayoutInflater inflater) {
        if (fragmentView == null) {
            //SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(THEME_PREFS, Activity.MODE_PRIVATE);
            //themeColor = themePrefs.getInt("themeColor", defThemeColor);
            //actionBar.setBackgroundColor(AvatarDrawable.getProfileBackColorForId(5));
            //actionBar.setBackgroundColor(themeColor);

            actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);

            //actionBar.setExtraHeight(AndroidUtilities.dp(88), false);
            if (AndroidUtilities.isTablet()) {
                actionBar.setOccupyStatusBar(false);
            }
            actionBar.setTitle(LocaleController.getString("Theming", R.string.Theming));

            //plus
            //TextView title = (TextView)getParentActivity().findViewById(R.id.action_bar_title);
            //AndroidUtilities.paintActionBarHeader(getParentActivity(),actionBar,"chatsHeaderBackgroundColorCheck","");
            //AndroidUtilities.setTVTextColor(getParentActivity(),title,"plus_header_title_color_check", Color.WHITE);
            //plus*
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });
            /*
            ActionBarMenu menu = actionBar.createMenu();
            ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_other);
            item.addSubItem(edit_name, LocaleController.getString("EditName", R.string.EditName), 0);
            item.addSubItem(logout, LocaleController.getString("LogOut", R.string.LogOut), 0);
            */
            listAdapter = new ListAdapter(getParentActivity());

            fragmentView = new FrameLayout(getParentActivity());
            FrameLayout frameLayout = (FrameLayout) fragmentView;


            listView = new ListView(getParentActivity());
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setVerticalScrollBarEnabled(false);
            AndroidUtilities.setListViewEdgeEffectColor(listView, AvatarDrawable.getProfileBackColorForId(5));
            frameLayout.addView(listView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            listView.setLayoutParams(layoutParams);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {

                    if (i == themeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt(color);
                            }

                        },preferences.getInt("themeColor", AndroidUtilities.defColor), CENTER, 0, false);

                        colorDialog.show();
                    } else if(i == saveThemeRow){
                        LayoutInflater li = LayoutInflater.from(getParentActivity());
                        View promptsView = li.inflate(R.layout.editbox_dialog, null);
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setView(promptsView);
                        final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);
                        builder.setMessage(LocaleController.getString("EnterName", R.string.EnterName));
                        //builder.setTitle(LocaleController.getString("SaveTheme", R.string.SaveTheme));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (saving) {
                                    return;
                                }
                                saving = true;
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        saving = false;
                                        if (getParentActivity() != null) {
                                            String pName = userInput.getText().toString();
                                            AndroidUtilities.setStringPref(getParentActivity(),"themeName",pName);
                                            AndroidUtilities.savePreferencesToSD(getParentActivity(), AndroidUtilities.THEME_PREFS+".xml", pName+".xml", true);
                                            AndroidUtilities.copyWallpaperToSD(getParentActivity(), pName, true);
                                            //Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("SaveThemeToastText", R.string.SaveThemeToastText), Toast.LENGTH_SHORT);
                                            //toast.show();
                                        }
                                    }
                                });
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    }  else if (i == applyThemeRow) {
                        DocumentSelectActivity fragment = new DocumentSelectActivity();
                        fragment.fileFilter = ".xml";
                        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
                            @Override
                            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files) {
                                final String xmlFile = files.get(0);
                                File themeFile = new File(xmlFile);
                                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                                builder.setTitle(LocaleController.getString("ApplyTheme", R.string.ApplyTheme));
                                builder.setMessage(themeFile.getName());
                                final String wName = xmlFile.substring(0, xmlFile.lastIndexOf(".")) + "_wallpaper.jpg";
                                File wFile = new File(wName);
                                if(wFile.exists()){
                                    builder.setMessage(themeFile.getName()+"\n"+wFile.getName());
                                    //Change Stock Background to set Custom Wallpaper
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    int selectedBackground = preferences.getInt("selectedBackground", 1000001);
                                    if (selectedBackground == 1000001) {
                                        //File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                                        //if (!toFile.exists()) {
                                            SharedPreferences.Editor editor = preferences.edit();
                                            editor.putInt("selectedBackground", 113);
                                            editor.putInt("selectedColor", 0);
                                            editor.commit();
                                        //}
                                    }
                                }
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                    if(AndroidUtilities.loadPrefFromSD(getParentActivity(), xmlFile) == 4){
                                                        AndroidUtilities.loadWallpaperFromSDPath(getParentActivity(), wName);
                                                        AndroidUtilities.restartApp();
                                                    }
                                            }
                                        });
                                    }
                                });
                                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                                showAlertDialog(builder);
                            }

                            @Override
                            public void startDocumentSelectActivity() {}
                        });
                        presentFragment(fragment);
                    } else if(i == resetThemeRow){
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(LocaleController.getString("AreYouSure", R.string.AreYouSure));
                        builder.setTitle(LocaleController.getString("ResetThemeSettings", R.string.ResetThemeSettings));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if (reseting) {
                                    return;
                                }
                                reseting = true;
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                reseting = false;
                                                SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
                                                SharedPreferences.Editor editor = themePrefs.edit();
                                                editor.clear();
                                                editor.commit();
                                                //Stock Background
                                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                                editor = preferences.edit();
                                                editor.putInt("selectedBackground", 1000001);
                                                editor.putInt("selectedColor", 0);
                                                editor.commit();
                                                File toFile = new File(ApplicationLoader.applicationContext.getFilesDir(), "wallpaper.jpg");
                                                if (toFile.exists()) {
                                                    toFile.delete();
                                                }
                                                fixLayout();
                                                if (getParentActivity() != null) {
                                                    Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("ResetThemeToastText", R.string.ResetThemeToastText), Toast.LENGTH_SHORT);
                                                    toast.show();
                                                }
                                            }
                                });
                                AndroidUtilities.needRestart = true;
                            }
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showAlertDialog(builder);
                    } else if (i == chatsRow) {
                        presentFragment(new ThemingChatsActivity());
                    } else if (i == chatRow) {
                        presentFragment(new ThemingChatActivity());
                    } else if (i == contactsRow) {
                        presentFragment(new ThemingContactsActivity());
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (i == themeColorRow) {
                        commitInt(AndroidUtilities.defColor);
                    }
                    return true;
                }
            });

            frameLayout.addView(actionBar);

        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    private void showAttachmentError() {
        if (getParentActivity() == null) {
            return;
        }
        Toast toast = Toast.makeText(getParentActivity(), LocaleController.getString("UnsupportedAttachment", R.string.UnsupportedAttachment), Toast.LENGTH_SHORT);
        toast.show();
    }
/*
    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 0) {

            } else if (requestCode == 22) {
                if (data == null || data.getData() == null) {
                    showAttachmentError();
                    return;
                }
                String tempPath = Utilities.getPath(data.getData());
                String originalPath = tempPath;
                if (tempPath == null) {
                    originalPath = data.toString();
                    tempPath = MediaController.copyDocumentToCache(data.getData(), "file");
                }
                if (tempPath == null) {
                    showAttachmentError();
                    return;
                }
                Toast toast = Toast.makeText(getParentActivity(), tempPath + "\n " + originalPath, Toast.LENGTH_SHORT);
                toast.show();
                //SendMessagesHelper.prepareSendingDocument(tempPath, originalPath, null, null, Long.parseLong(null));
            }
        }
    }*/
/*
    private void saveThemeDialog(){

        LayoutInflater li = LayoutInflater.from(getParentActivity());
        View promptsView = li.inflate(R.layout.editbox_dialog, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getParentActivity());
        alertDialogBuilder.setView(promptsView);
        final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);

        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                savePrefs(Utils.this);
                                String pName = userInput.getText().toString();
                                functions.savePreferencesToSD(Utils.this,my_pref_file_name+".xml",pName+".xml",true);
                                functions.copyWallpaperToSD(Utils.this,pName,true);
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }*/

    private void commitInt(int i){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("themeColor", i);
        //Reset Theme Colors
        editor.putInt("chatsHeaderColor", i);
        editor.putInt("chatsCountBGColor", i);
        editor.putInt("chatsChecksColor", i);
        editor.putInt("chatsParticipantColor", AndroidUtilities.setDarkColor(i, 0x15));
        editor.putInt("chatsFloatingBGColor", i);

        editor.putInt("chatHeaderColor", i);
        editor.putInt("chatRBubbleColor", AndroidUtilities.setDarkColor(i, -0x80));
        editor.putInt("chatStatusColor", AndroidUtilities.setDarkColor(i, -0x40));
        editor.putInt("chatRTimeColor", AndroidUtilities.setDarkColor(i, 0x15));
        editor.putInt("chatEmojiViewTabColor", AndroidUtilities.setDarkColor(i, 0x15));
        editor.putInt("chatChecksColor", i);
        editor.putInt("chatSendIconColor", i);

        editor.putInt("contactsHeaderColor", i);
        editor.putInt("contactsOnlineColor", AndroidUtilities.setDarkColor(i, 0x15));

        editor.commit();
        fixLayout();
        AndroidUtilities.themeColor = i;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (fragmentView == null) {
            return;
        }
        fragmentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (fragmentView != null) {
                    //needLayout();
                    fragmentView.getViewTreeObserver().removeOnPreDrawListener(this);
                }
                return false;
            }
        });
        listView.setAdapter(listAdapter);
        actionBar.setBackgroundColor(AndroidUtilities.getIntColor("themeColor"));

    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int i) {
            return  i == themeColorRow || i == chatsRow || i == chatRow || i == contactsRow || i == resetThemeRow || i == saveThemeRow || i == applyThemeRow;
        }

        @Override
        public int getCount() {
            return rowCount;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            int type = getItemViewType(i);
            if (type == 0) {
                if (view == null) {
                    view = new ShadowSectionCell(mContext);
                }
            }
            else if (type == 1) {
                if (view == null) {
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(0xffffffff);
                }
                if (i == generalSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("General", R.string.General));
                } else if (i == screensSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("Screens", R.string.Screens));
                } else if (i == themesSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("Themes", R.string.Themes));
                }
            }
            else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == chatsRow) {
                    textCell.setText(LocaleController.getString("MainScreen", R.string.MainScreen), true);
                } else if (i == chatRow) {
                    textCell.setText(LocaleController.getString("ChatScreen", R.string.ChatScreen), true);
                } else if (i == contactsRow) {
                    textCell.setText(LocaleController.getString("ContactsScreen", R.string.ContactsScreen), true);
                }

            }
            else if (type == 3) {
                if (view == null) {
                    view = new TextDetailSettingsCell(mContext);
                }
                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                if (i == resetThemeRow) {
                    textCell.setMultilineDetail(true);
                    textCell.setTextAndValue(LocaleController.getString("ResetThemeSettings", R.string.ResetThemeSettings), LocaleController.getString("ResetThemeSettingsSum", R.string.ResetThemeSettingsSum), false);
                } else if (i == saveThemeRow) {
                    textCell.setMultilineDetail(true);
                    textCell.setTextAndValue(LocaleController.getString("SaveTheme", R.string.SaveTheme), LocaleController.getString("SaveThemeSum", R.string.SaveThemeSum), false);
                }  else if (i == applyThemeRow) {
                    textCell.setMultilineDetail(true);
                    textCell.setTextAndValue(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), LocaleController.getString("ApplyThemeSum", R.string.ApplyThemeSum), false);
                }
            }
            else if (type == 4){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }
                TextColorCell textCell = (TextColorCell) view;
                if (i == themeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("themeColor", R.string.themeColor), AndroidUtilities.getIntColor("themeColor"), true);
                }
            }

            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if ( i == screensSectionRow || i == themesSectionRow ) {
                return 0;
            }
            else if ( i == generalSection2Row || i == screensSection2Row || i == themesSection2Row) {
                return 1;
            }
            else if ( i == chatsRow ) {
                return 2;
            }
            else if ( i == resetThemeRow || i == saveThemeRow || i == applyThemeRow) {
                return 3;
            }
            else if ( i == themeColorRow) {
                return 4;
            }
            else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 5;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
