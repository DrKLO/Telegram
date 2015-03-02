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
import android.widget.FrameLayout;
import android.widget.ListView;

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
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ColorSelectorDialog;
import org.telegram.ui.Components.NumberPicker;

import static org.telegram.ui.Components.ColorSelectorDialog.OnColorChangedListener;

public class ThemingChatsActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    //private static final String TAG = "ThemingChatsActivity";

    private int headerSection2Row;
    private int headerColorRow;
    private int rowsSectionRow;
    private int rowsSection2Row;
    private int rowColorRow;
    private int nameColorRow;
    private int checksColorRow;
    private int muteColorRow;
    private int nameSizeRow;
    private int messageColorRow;
    private int participantColorRow;
    private int messageSizeRow;
    private int timeColorRow;
    private int timeSizeRow;
    private int countColorRow;
    private int countSizeRow;
    private int countBGColorRow;
    private int floatingPencilColorRow;
    private int floatingBGColorRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerColorRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;
        rowColorRow = rowCount++;

        nameColorRow = rowCount++;
        nameSizeRow = rowCount++;
        muteColorRow = rowCount++;
        checksColorRow = rowCount++;

        messageColorRow = rowCount++;
        messageSizeRow = rowCount++;
        participantColorRow = rowCount++;
        timeColorRow = rowCount++;
        timeSizeRow = rowCount++;
        countColorRow = rowCount++;
        countSizeRow = rowCount++;
        countBGColorRow = rowCount++;

        floatingPencilColorRow = rowCount++;
        floatingBGColorRow = rowCount++;

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

    }

    @Override
    public View createView(LayoutInflater inflater) {
        if (fragmentView == null) {

            actionBar.setItemsBackground(AvatarDrawable.getButtonColorForId(5));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);

            if (AndroidUtilities.isTablet()) {
                actionBar.setOccupyStatusBar(false);
            }
            actionBar.setTitle(LocaleController.getString("MainScreen", R.string.MainScreen));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });

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

                    SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);

                    if (i == headerColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsHeaderColor", color);
                            }

                        },themePrefs.getInt("chatsHeaderColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == rowColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsRowColor", color);
                            }

                        },themePrefs.getInt("chatsRowColor", 0xffffffff), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == nameColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsNameColor", color);
                            }

                        },themePrefs.getInt("chatsNameColor", 0xff000000), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == muteColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsMuteColor", color);
                            }

                        },themePrefs.getInt("chatsMuteColor", 0xffa8a8a8), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == checksColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsChecksColor", color);
                            }

                        },themePrefs.getInt("chatsChecksColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == messageColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsMessageColor", color);
                            }

                        },themePrefs.getInt("chatsMessageColor", 0xff8f8f8f), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == participantColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsParticipantColor", color);
                            }

                        },themePrefs.getInt("chatsParticipantColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == timeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsTimeColor", color);
                            }

                        },themePrefs.getInt("chatsTimeColor", 0xff999999), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == countColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsCountColor", color);
                            }

                        },themePrefs.getInt("chatsCountColor", 0xffffffff), CENTER, 0, false);
                        colorDialog.show();
                    } else if (i == countBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsCountBGColor", color);
                            }

                        },themePrefs.getInt("chatsCountBGColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == nameSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("NameSize", R.string.NameSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatsNameSize", 17);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatsNameSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == messageSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("MessageSize", R.string.MessageSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatsMessageSize", 16);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("chatsMessageSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == timeSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TimeDateSize", R.string.TimeDateSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatsTimeSize", 13);
                        numberPicker.setMinValue(5);
                        numberPicker.setMaxValue(25);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatsTimeSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == countSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("CountSize", R.string.CountSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatsCountSize", 13);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatsCountSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == floatingPencilColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsFloatingPencilColor", color);
                            }

                        },themePrefs.getInt("chatsFloatingPencilColor", 0xffffffff), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == floatingBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatsFloatingBGColor", color);
                            }

                        },themePrefs.getInt("chatsFloatingBGColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, true);
                        colorDialog.show();
                    }
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (getParentActivity() == null) {
                        return false;
                    }
                    if (i == headerColorRow) {
                        resetInt("chatsHeaderColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == rowColorRow) {
                        resetInt("chatsRowColor", 0xffffffff);
                    } else if (i == nameColorRow) {
                        resetInt("chatsNameColor", 0xff000000);
                    } else if (i == muteColorRow) {
                        resetInt("chatsMuteColor", 0xffa8a8a8);
                    } else if (i == checksColorRow) {
                        resetInt("chatsChecksColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == nameSizeRow) {
                        resetInt("chatsNameSize", 17);
                    } else if (i == messageColorRow) {
                        resetInt("chatsMessageColor", 0xff8f8f8f);
                    } else if (i == participantColorRow) {
                        resetInt("chatsParticipantColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15));
                    } else if (i == messageSizeRow) {
                        resetInt("chatsMessageSize", 16);
                    } else if (i == timeColorRow) {
                        resetInt("chatsTimeColor", 0xff999999);
                    } else if (i == timeSizeRow) {
                        resetInt("chatsTimeSize", 13);
                    } else if (i == countColorRow) {
                        resetInt("chatsCountColor", 0xffffffff);
                    } else if (i == countSizeRow) {
                        resetInt("chatsCountSize", 13);
                    } else if (i == countBGColorRow) {
                        resetInt("chatsCountBGColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == floatingPencilColorRow) {
                        resetInt("chatsFloatingPencilColor", 0xffffffff);
                    } else if (i == floatingBGColorRow) {
                        resetInt("chatsFloatingBGColor", AndroidUtilities.getIntColor("themeColor"));
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

    private void resetInt(String key, int value){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove(key);
        editor.commit();
        if (listView != null) {
            listView.invalidateViews();
        }
    }

    private void commitInt(String key, int value){
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(key, value);
        editor.commit();
        if (listView != null) {
            listView.invalidateViews();
        }
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
        //actionBar.setBackgroundColor(AndroidUtilities.getIntColor("themeColor"));
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
            return  i == headerColorRow || i == rowColorRow || i == nameColorRow || i == muteColorRow || i == checksColorRow || i == nameSizeRow || i == messageColorRow || i == participantColorRow || i == messageSizeRow ||
                    i == timeColorRow || i == timeSizeRow || i == countColorRow || i == countSizeRow || i == countBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow;
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
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, Activity.MODE_PRIVATE);
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
                if (i == headerSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("Header", R.string.Header));
                } else if (i == rowsSection2Row) {
                    ((HeaderCell) view).setText(LocaleController.getString("ChatsList", R.string.ChatsList));
                }
            }
            else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == nameSizeRow) {
                    int size = themePrefs.getInt("chatsNameSize", AndroidUtilities.isTablet() ? 19 : 17);
                    textCell.setTextAndValue(LocaleController.getString("NameSize", R.string.NameSize), String.format("%d", size), true);
                } else if (i == messageSizeRow) {
                    int size = themePrefs.getInt("chatsMessageSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("MessageSize", R.string.MessageSize), String.format("%d", size), true);
                } else if (i == timeSizeRow) {
                    int size = themePrefs.getInt("chatsTimeSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("TimeDateSize", R.string.TimeDateSize), String.format("%d", size), true);
                } else if (i == countSizeRow) {
                    int size = themePrefs.getInt("chatsCountSize", AndroidUtilities.isTablet() ? 15 : 13);
                    textCell.setTextAndValue(LocaleController.getString("CountSize", R.string.CountSize), String.format("%d", size), true);
                }

            }
            else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("chatsHeaderColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == rowColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RowColor", R.string.RowColor), themePrefs.getInt("chatsRowColor", 0xffffffff), true);
                } else if (i == nameColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("chatsNameColor", 0xff000000), true);
                } else if (i == muteColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("MuteColor", R.string.MuteColor), themePrefs.getInt("chatsMuteColor", 0xffa8a8a8), true);
                } else if (i == checksColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ChecksColor", R.string.ChecksColor), themePrefs.getInt("chatsChecksColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == messageColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("MessageColor", R.string.MessageColor), themePrefs.getInt("chatsMessageColor", 0xff8f8f8f), true);
                } else if (i == participantColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ParticipantColor", R.string.ParticipantColor), themePrefs.getInt("chatsParticipantColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), true);
                } else if (i == timeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("TimeDateColor", R.string.TimeDateColor), themePrefs.getInt("chatsTimeColor", 0xff999999), true);
                } else if (i == countColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("CountColor", R.string.CountColor), themePrefs.getInt("chatsCountColor", 0xffffffff), true);
                } else if (i == countBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("CountBGColor", R.string.CountBGColor), themePrefs.getInt("chatsCountBGColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == floatingPencilColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("FloatingPencilColor", R.string.FloatingPencilColor), themePrefs.getInt("chatsFloatingPencilColor", 0xffffffff), true);
                } else if (i == floatingBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("FloatingBGColor", R.string.FloatingBGColor), themePrefs.getInt("chatsFloatingBGColor", AndroidUtilities.getIntColor("themeColor")), true);
                }
            }
            return view;
        }

        @Override
        public int getItemViewType(int i) {
            if ( i == rowsSectionRow ) {
                return 0;
            }
            else if ( i == headerSection2Row || i == rowsSection2Row ) {
                return 1;
            }
            else if ( i == nameSizeRow ||  i == messageSizeRow || i == timeSizeRow || i == countSizeRow ) {
                return 2;
            }

            else if ( i == headerColorRow || i == rowColorRow || i == nameColorRow || i == muteColorRow || i == checksColorRow || i == messageColorRow  || i == participantColorRow || i == timeColorRow || i == countColorRow
                    || i == countBGColorRow || i == floatingPencilColorRow || i == floatingBGColorRow) {
                return 3;
            }
            else {
                return 2;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }
}
