package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class ImageListActivity extends BaseFragment {

    private int arrayId;

    public ImageListActivity(Bundle args) {
        super(args);
    }

    @Override
    public boolean onFragmentCreate() {
        arrayId = arguments.getInt("array_id", 0);

        if (arrayId != 0) {

        }
        //Log.e("ImageListActivity","arrayId " + arrayId);
        super.onFragmentCreate();
        return true;
    }
    //private ListView list;
    private CustomListAdapter listAdapter;

    private static String[] bubblesNamesArray ={
            "Telegram",
            "Lex",
            "Hangouts",
            "Notepad",
            "Ed",
            "Edge",
            "iOS",
            "Telegram_old"
    };

    Integer[] imgid ={
            R.drawable.msg_in,
            R.drawable.msg_in_2,
            R.drawable.msg_in_3,
            R.drawable.msg_in_4,
            R.drawable.msg_in_5,
            R.drawable.msg_in_6,
            R.drawable.msg_in_7,
            R.drawable.msg_in_8,
            R.drawable.msg_out,
            R.drawable.msg_out_2,
            R.drawable.msg_out_3,
            R.drawable.msg_out_4,
            R.drawable.msg_out_5,
            R.drawable.msg_out_6,
            R.drawable.msg_out_7,
            R.drawable.msg_out_8
    };

    private static String[] checksNamesArray ={
            "Stock",
            "EdCheck",
            "Lex",
            "Gladiator",
            "MaxChecks",
            "ElipLex",
            "CubeLex",
            "MaxLines",
            "RLex",
            "MaxLinesPro",
            "ReadLex",
            "MaxHeart"
    };

    Integer[] checkid ={
            R.drawable.dialogs_check,
            R.drawable.dialogs_check_2,
            R.drawable.dialogs_check_3,
            R.drawable.dialogs_check_4,
            R.drawable.dialogs_check_5,
            R.drawable.dialogs_check_6,
            R.drawable.dialogs_check_7,
            R.drawable.dialogs_check_8,
            R.drawable.dialogs_check_9,
            R.drawable.dialogs_check_10,
            R.drawable.dialogs_check_11,
            R.drawable.dialogs_check_12,
            R.drawable.dialogs_halfcheck,
            R.drawable.dialogs_halfcheck_2,
            R.drawable.dialogs_halfcheck_3,
            R.drawable.dialogs_halfcheck_4,
            R.drawable.dialogs_halfcheck_5,
            R.drawable.dialogs_halfcheck_6,
            R.drawable.dialogs_halfcheck_7,
            R.drawable.dialogs_halfcheck_8,
            R.drawable.dialogs_halfcheck_9,
            R.drawable.dialogs_halfcheck_10,
            R.drawable.dialogs_halfcheck_11,
            R.drawable.dialogs_halfcheck_12,
            R.drawable.msg_check,
            R.drawable.msg_check_2,
            R.drawable.msg_check_3,
            R.drawable.msg_check_4,
            R.drawable.msg_check_5,
            R.drawable.msg_check_6,
            R.drawable.msg_check_7,
            R.drawable.msg_check_8,
            R.drawable.msg_check_9,
            R.drawable.msg_check_10,
            R.drawable.msg_check_11,
            R.drawable.msg_check_12,
            R.drawable.msg_halfcheck,
            R.drawable.msg_halfcheck_2,
            R.drawable.msg_halfcheck_3,
            R.drawable.msg_halfcheck_4,
            R.drawable.msg_halfcheck_5,
            R.drawable.msg_halfcheck_6,
            R.drawable.msg_halfcheck_7,
            R.drawable.msg_halfcheck_8,
            R.drawable.msg_halfcheck_9,
            R.drawable.msg_halfcheck_10,
            R.drawable.msg_halfcheck_11,
            R.drawable.msg_halfcheck_12,
            R.drawable.msg_check_w,
            R.drawable.msg_check_w_2,
            R.drawable.msg_check_w_3,
            R.drawable.msg_check_w_4,
            R.drawable.msg_check_w_5,
            R.drawable.msg_check_w_6,
            R.drawable.msg_check_w_7,
            R.drawable.msg_check_w_8,
            R.drawable.msg_check_w_9,
            R.drawable.msg_check_w_10,
            R.drawable.msg_check_w_11,
            R.drawable.msg_check_w_12,
            R.drawable.msg_halfcheck_w,
            R.drawable.msg_halfcheck_w_2,
            R.drawable.msg_halfcheck_w_3,
            R.drawable.msg_halfcheck_w_4,
            R.drawable.msg_halfcheck_w_5,
            R.drawable.msg_halfcheck_w_6,
            R.drawable.msg_halfcheck_w_7,
            R.drawable.msg_halfcheck_w_8,
            R.drawable.msg_halfcheck_w_9,
            R.drawable.msg_halfcheck_w_10,
            R.drawable.msg_halfcheck_w_11,
            R.drawable.msg_halfcheck_w_12
    };

    public static String getBubbleName(int i){
        return bubblesNamesArray[i];
    }

    public static String getCheckName(int i){
        return checksNamesArray[i];
    }

    @Override
    public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(arrayId == 0 ? LocaleController.getString("BubbleStyle", R.string.BubbleStyle) : LocaleController.getString("CheckStyle", R.string.CheckStyle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = getParentActivity().getLayoutInflater().inflate(R.layout.imagelistlayout, null, false);

        listAdapter = new CustomListAdapter(context, arrayId == 0 ? bubblesNamesArray : checksNamesArray, arrayId == 0 ? imgid : checkid);
        ListView list = (ListView) fragmentView.findViewById(R.id.list);
        list.setAdapter(listAdapter);
        list.setDivider(null);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String selectedItem = arrayId == 0 ? bubblesNamesArray[+position] : checksNamesArray[+position];
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                String key = arrayId == 0 ? "chatBubbleStyle" : "chatCheckStyle";
                String oldVal = preferences.getString(key, "");
                if(!oldVal.equals(selectedItem)){
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(key, selectedItem);
                    editor.apply();
                    if(arrayId == 0) {
                        Theme.setBubbles(getParentActivity());
                    } else{
                        Theme.setChecks(getParentActivity());
                    }
                }
                listAdapter.notifyDataSetChanged();
                finishFragment();
            }
        });

        return fragmentView;
    }

    private class CustomListAdapter extends ArrayAdapter<String> {

        private final Context mContext;
        private final String[] itemname;
        private final Integer[] imgid;

        public CustomListAdapter(Context context, String[] itemname, Integer[] imgid) {
            super(context, R.layout.imagelist, itemname);

            this.mContext = context;
            this.itemname = itemname;
            this.imgid = imgid;
        }

        public View getView(int position, View view, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
            String name = themePrefs.getString(arrayId == 0 ? "chatBubbleStyle" : "chatCheckStyle", itemname[0]);
            view = inflater.inflate(R.layout.imagelist, parent, false);
            if(name.equals(itemname[position]) ){
                view.setBackgroundColor(0xffd0d0d0);
            } else{
                view.setBackgroundColor(0xfff0f0f0);
            }

            TextView txtTitle = (TextView) view.findViewById(R.id.bubble_title);
            ImageView inImageView = (ImageView) view.findViewById(R.id.bubble_in);
            ImageView outImageView = (ImageView) view.findViewById(R.id.bubble_out);

            txtTitle.setText(itemname[position]);
            inImageView.setImageResource(imgid[position]);
            outImageView.setImageResource(imgid[position + itemname.length]);

            if(arrayId == 1){
                view.setPadding(50, 0, 0, 0);
                //inImageView.getLayoutParams().height = 70;
                inImageView.getLayoutParams().width = 70;
                inImageView.setColorFilter(0, PorterDuff.Mode.SRC_ATOP);
                //outImageView.getLayoutParams().height = 70;
                outImageView.getLayoutParams().width = 70;
                outImageView.setColorFilter(0, PorterDuff.Mode.SRC_ATOP);
            }

            return view;

        };
    }
}
