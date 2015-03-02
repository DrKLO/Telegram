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
import org.telegram.android.MessagesController;
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

public class ThemingChatActivity extends BaseFragment {

    private ListView listView;
    private ListAdapter listAdapter;

    private static final String TAG = "ThemingChatsActivity";

    private int headerSection2Row;
    private int headerColorRow;
    private int rowsSectionRow;
    private int rowsSection2Row;
    private int rBubbleColorRow;
    private int lBubbleColorRow;
    private int rTextColorRow;
    private int textSizeRow;
    private int lTextColorRow;
    private int rTimeColorRow;
    private int lTimeColorRow;
    private int checksColorRow;
    private int dateBubbleColorRow;
    private int nameColorRow;
    private int nameSizeRow;
    private int statusColorRow;
    private int statusSizeRow;
    private int dateColorRow;
    private int dateSizeRow;
    private int timeSizeRow;
    private int editTextColorRow;
    private int editTextSizeRow;
    private int editTextBGColorRow;
    private int emojiViewBGColorRow;
    private int emojiViewTabColorRow;
    private int sendColorRow;

    private int rowCount;

    public final static int CENTER = 0;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;
        headerSection2Row = rowCount++;
        headerColorRow = rowCount++;

        nameSizeRow = rowCount++;
        nameColorRow = rowCount++;
        statusSizeRow = rowCount++;
        statusColorRow = rowCount++;

        rowsSectionRow = rowCount++;
        rowsSection2Row = rowCount++;

        textSizeRow = rowCount++;
        rTextColorRow = rowCount++;
        //lTextColorRow = rowCount++;

        timeSizeRow = rowCount++;
        rTimeColorRow = rowCount++;
        lTimeColorRow = rowCount++;
        checksColorRow = rowCount++;

        dateSizeRow = rowCount++;
        dateColorRow = rowCount++;

        rBubbleColorRow = rowCount++;
        lBubbleColorRow = rowCount++;
        dateBubbleColorRow = rowCount++;

        sendColorRow = rowCount++;
        editTextSizeRow = rowCount++;
        editTextColorRow = rowCount++;
        editTextBGColorRow = rowCount++;

        emojiViewBGColorRow = rowCount++;
        emojiViewTabColorRow = rowCount++;

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
            actionBar.setTitle(LocaleController.getString("ChatScreen", R.string.ChatScreen));

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
                                commitInt("chatHeaderColor", color);
                            }

                        },themePrefs.getInt("chatHeaderColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == rBubbleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRBubbleColor", color);
                            }

                        },themePrefs.getInt("chatRBubbleColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x80)), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == lBubbleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLBubbleColor", color);
                            }

                        },themePrefs.getInt("chatLBubbleColor", 0xffffffff), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == rTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRTextColor", color);
                            }

                        },themePrefs.getInt("chatRTextColor", 0xff000000), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == lTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLTextColor", color);
                            }

                        },themePrefs.getInt("chatLTextColor", 0xff000000), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == rTimeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatRTimeColor", color);
                            }

                        },themePrefs.getInt("chatRTimeColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == lTimeColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatLTimeColor", color);
                            }

                        },themePrefs.getInt("chatLTimeColor", 0xffa1aab3), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == dateBubbleColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatDateBubbleColor", color);
                            }

                        },themePrefs.getInt("chatDateBubbleColor", 0x59000000), CENTER, 0, true);

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
                                commitInt("chatNameColor", color);
                            }

                        },themePrefs.getInt("chatNameColor", 0xffffffff), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == sendColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatSendIconColor", color);
                            }

                        },themePrefs.getInt("chatSendIconColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == editTextColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEditTextColor", color);
                            }

                        },themePrefs.getInt("chatEditTextColor", 0xff000000), CENTER, 0, false);

                        colorDialog.show();
                    } else if (i == editTextBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEditTextBGColor", color);
                            }

                        },themePrefs.getInt("chatEditTextBGColor", 0xffffffff), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == emojiViewBGColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEmojiViewBGColor", color);
                            }

                        },themePrefs.getInt("chatEmojiViewBGColor", 0xff222222), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == emojiViewTabColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }

                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                        view = li.inflate(R.layout.colordialog, null, false);

                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatEmojiViewTabColor", color);
                            }

                        },themePrefs.getInt("chatEmojiViewTabColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), CENTER, 0, true);

                        colorDialog.show();
                    } else if (i == statusColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatStatusColor", color);
                            }

                        },themePrefs.getInt("chatStatusColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), CENTER, 0, false);

                        colorDialog.show();
                    }  else if (i == dateColorRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        LayoutInflater li = (LayoutInflater)getParentActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        view = li.inflate(R.layout.colordialog, null, false);
                        ColorSelectorDialog colorDialog = new ColorSelectorDialog(getParentActivity(), new OnColorChangedListener() {
                            @Override
                            public void colorChanged(int color) {
                                commitInt("chatDateColor", color);
                            }

                        },themePrefs.getInt("chatDateColor", 0xffffffff), CENTER, 0, false);
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
                                commitInt("chatChecksColor", color);
                            }

                        },themePrefs.getInt("chatChecksColor", AndroidUtilities.getIntColor("themeColor")), CENTER, 0, true);
                        colorDialog.show();
                    } else if (i == nameSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("NameSize", R.string.NameSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatNameSize", 18);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatNameSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == statusSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("StatusSize", R.string.StatusSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatStatusSize", 14);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(22);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(numberPicker.getValue() != currentValue){
                                    commitInt("chatStatusSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == textSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TextSize", R.string.TextSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatTextSize", 16);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(30);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatTextSize", numberPicker.getValue());
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = preferences.edit();
                                    editor.putInt("fons_size", numberPicker.getValue());
                                    MessagesController.getInstance().fontSize = numberPicker.getValue();
                                    editor.commit();
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == timeSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("TimeSize", R.string.TimeSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatTimeSize", 12);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatTimeSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    } else if (i == dateSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("DateSize", R.string.DateSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatDateSize", 16);
                        numberPicker.setMinValue(8);
                        numberPicker.setMaxValue(20);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatDateSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
                    }  else if (i == editTextSizeRow) {
                        if (getParentActivity() == null) {
                            return;
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(LocaleController.getString("EditTextSize", R.string.EditTextSize));
                        final NumberPicker numberPicker = new NumberPicker(getParentActivity());
                        final int currentValue = themePrefs.getInt("chatEditTextSize", 18);
                        numberPicker.setMinValue(12);
                        numberPicker.setMaxValue(28);
                        numberPicker.setValue(currentValue);
                        builder.setView(numberPicker);
                        builder.setNegativeButton(LocaleController.getString("Done", R.string.Done), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (numberPicker.getValue() != currentValue) {
                                    commitInt("chatEditTextSize", numberPicker.getValue());
                                }
                            }
                        });
                        showAlertDialog(builder);
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
                        resetInt("chatHeaderColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == rBubbleColorRow) {
                        resetInt("chatRBubbleColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x80));
                    } else if (i == lBubbleColorRow) {
                        resetInt("chatLBubbleColor", 0xffffffff);
                    } else if (i == rTextColorRow) {
                        resetInt("chatRTextColor", 0xff000000);
                    } else if (i == lTextColorRow) {
                        resetInt("chatLTextColor", 0xff000000);
                    } else if (i == nameColorRow) {
                        resetInt("chatNameColor", 0xffffffff);
                    } else if (i == nameSizeRow) {
                        resetInt("chatNameSize", 17);
                    } else if (i == statusColorRow) {
                        resetInt("chatStatusColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40));
                    } else if (i == statusSizeRow) {
                        resetInt("chatStatusSize", 14);
                    } else if (i == rTimeColorRow) {
                        resetInt("chatRTimeColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15));
                    } else if (i == lTimeColorRow) {
                        resetInt("chatLTimeColor", 0xffa1aab3);
                    } else if (i == dateColorRow) {
                        resetInt("chatDateColor", 0xffffffff);
                    } else if (i == checksColorRow) {
                        resetInt("chatChecksColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == textSizeRow) {
                        resetInt("chatTextSize", 16);
                    } else if (i == timeSizeRow) {
                        resetInt("chatTimeSize", 12);
                    } else if (i == dateSizeRow) {
                        resetInt("chatDateSize", 16);
                    } else if (i == dateBubbleColorRow) {
                        resetInt("chatDateBubbleColor", 0x59000000);
                    } else if (i == sendColorRow) {
                        resetInt("chatSendIconColor", AndroidUtilities.getIntColor("themeColor"));
                    } else if (i == editTextColorRow) {
                        resetInt("chatEditTextColor", 0xff000000);
                    } else if (i == editTextSizeRow) {
                        resetInt("chatEditTextSize", 18);
                    } else if (i == editTextBGColorRow) {
                        resetInt("chatEditTextBGColor", 0xffffffff);
                    } else if (i == emojiViewBGColorRow) {
                        resetInt("chatEmojiViewBGColor", 0xff222222);
                    } else if (i == emojiViewTabColorRow) {
                        resetInt("chatEmojiViewTabColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15));
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
        if(key.equals("chatTextSize")){
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            editor = preferences.edit();
            editor.putInt("fons_size", value);
            MessagesController.getInstance().fontSize = value;
            editor.commit();
        }
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
            return  i == headerColorRow || i == rBubbleColorRow || i == lBubbleColorRow || i == nameColorRow || i == nameSizeRow || i == statusColorRow || i == statusSizeRow ||
                    i == textSizeRow || i == timeSizeRow || i == dateColorRow || i == dateSizeRow || i == dateBubbleColorRow || i == rTextColorRow || i == lTextColorRow ||
                    i == rTimeColorRow|| i == lTimeColorRow || i == checksColorRow || i == editTextSizeRow || i == editTextColorRow || i == sendColorRow || i == editTextBGColorRow ||
                    i == emojiViewBGColorRow || i == emojiViewTabColorRow;
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
                    ((HeaderCell) view).setText(LocaleController.getString("ChatList", R.string.ChatList));
                }
            }
            else if (type == 2) {
                if (view == null) {
                    view = new TextSettingsCell(mContext);
                }
                TextSettingsCell textCell = (TextSettingsCell) view;
                if (i == nameSizeRow) {
                    int size = themePrefs.getInt("chatNameSize", AndroidUtilities.isTablet() ? 20 : 18);
                    textCell.setTextAndValue(LocaleController.getString("NameSize", R.string.NameSize), String.format("%d", size), true);
                } else if (i == statusSizeRow) {
                    int size = themePrefs.getInt("chatStatusSize", AndroidUtilities.isTablet() ? 16 : 14);
                    textCell.setTextAndValue(LocaleController.getString("StatusSize", R.string.StatusSize), String.format("%d", size), true);
                } else if (i == textSizeRow) {
                    int size = themePrefs.getInt("chatTextSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("TextSize", R.string.TextSize), String.format("%d", size), true);
                } else if (i == timeSizeRow) {
                    int size = themePrefs.getInt("chatTimeSize", AndroidUtilities.isTablet() ? 14 : 12);
                    textCell.setTextAndValue(LocaleController.getString("TimeSize", R.string.TimeSize), String.format("%d", size), true);
                } else if (i == dateSizeRow) {
                    int size = themePrefs.getInt("chatDateSize", AndroidUtilities.isTablet() ? 18 : 16);
                    textCell.setTextAndValue(LocaleController.getString("DateSize", R.string.DateSize), String.format("%d", size), true);
                }  else if (i == editTextSizeRow) {
                    int size = themePrefs.getInt("chatEditTextSize", AndroidUtilities.isTablet() ? 20 : 18);
                    textCell.setTextAndValue(LocaleController.getString("EditTextSize", R.string.EditTextSize), String.format("%d", size), true);
                }
            }
            else if (type == 3){
                if (view == null) {
                    view = new TextColorCell(mContext);
                }

                TextColorCell textCell = (TextColorCell) view;

                if (i == headerColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("HeaderColor", R.string.HeaderColor), themePrefs.getInt("chatHeaderColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == rBubbleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RBubbleColor", R.string.RBubbleColor), themePrefs.getInt("chatRBubbleColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x80)), true);
                } else if (i == lBubbleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("LBubbleColor", R.string.LBubbleColor), themePrefs.getInt("chatLBubbleColor", 0xffffffff), true);
                } else if (i == rTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RTextColor", R.string.RTextColor), themePrefs.getInt("chatRTextColor", 0xff000000), true);
                } else if (i == lTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("LTextColor", R.string.LTextColor), themePrefs.getInt("chatLTextColor", 0xff000000), true);
                } else if (i == nameColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("NameColor", R.string.NameColor), themePrefs.getInt("chatNameColor", 0xffffffff), true);
                } else if (i == statusColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("StatusColor", R.string.StatusColor), themePrefs.getInt("chatStatusColor", AndroidUtilities.getIntDarkerColor("themeColor",-0x40)), true);
                } else if (i == rTimeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("RTimeColor", R.string.RTimeColor), themePrefs.getInt("chatRTimeColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), true);
                } else if (i == lTimeColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("LTimeColor", R.string.LTimeColor), themePrefs.getInt("chatLTimeColor", 0xffa1aab3), true);
                } else if (i == checksColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("ChecksColor", R.string.ChecksColor), themePrefs.getInt("chatChecksColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == dateColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("DateColor", R.string.DateColor), themePrefs.getInt("chatDateColor", 0xffffffff), true);
                } else if (i == dateBubbleColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("DateBubbleColor", R.string.DateBubbleColor), themePrefs.getInt("chatDateBubbleColor", 0x59000000), true);
                } else if (i == sendColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("SendIcon", R.string.SendIcon), themePrefs.getInt("chatSendIconColor", AndroidUtilities.getIntColor("themeColor")), true);
                } else if (i == editTextColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EditTextColor", R.string.EditTextColor), themePrefs.getInt("chatEditTextColor", 0xff000000), true);
                } else if (i == editTextBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EditTextBGColor", R.string.EditTextBGColor), themePrefs.getInt("chatEditTextBGColor", 0xffffffff), true);
                } else if (i == emojiViewBGColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EmojiViewBGColor", R.string.EmojiViewBGColor), themePrefs.getInt("chatEmojiViewBGColor", 0xff222222), true);
                } else if (i == emojiViewTabColorRow) {
                    textCell.setTextAndColor(LocaleController.getString("EmojiViewTabColor", R.string.EmojiViewTabColor), themePrefs.getInt("chatEmojiViewTabColor", AndroidUtilities.getIntDarkerColor("themeColor",0x15)), true);
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
            else if ( i == nameSizeRow ||  i == statusSizeRow || i == textSizeRow || i == timeSizeRow || i == dateSizeRow  || i == editTextSizeRow) {
                return 2;
            }

            else if ( i == headerColorRow || i == rBubbleColorRow || i == lBubbleColorRow || i == nameColorRow || i == statusColorRow || i == dateColorRow || i == dateBubbleColorRow ||
                    i == rTextColorRow || i == lTextColorRow || i == rTimeColorRow || i == lTimeColorRow || i == checksColorRow || i == sendColorRow || i == editTextColorRow || i == editTextBGColorRow ||
                    i == emojiViewBGColorRow || i == emojiViewTabColorRow) {
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
