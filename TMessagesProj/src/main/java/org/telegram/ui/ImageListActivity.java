package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

public class ImageListActivity extends BaseFragment {

    private ListView list;
    private CustomListAdapter listAdapter;

    private static String[] bubblesNamesArray ={
            "Telegram",
            "Lex",
            "Hangouts",
            "Notepad",
            "Ed",
            "Edge",
            "iOS"
    };

    Integer[] imgid ={
            R.drawable.msg_in,
            R.drawable.msg_in_2,
            R.drawable.msg_in_3,
            R.drawable.msg_in_4,
            R.drawable.msg_in_5,
            R.drawable.msg_in_6,
            R.drawable.msg_in_7,
            R.drawable.msg_out,
            R.drawable.msg_out_2,
            R.drawable.msg_out_3,
            R.drawable.msg_out_4,
            R.drawable.msg_out_5,
            R.drawable.msg_out_6,
            R.drawable.msg_out_7
    };

    public static String getBubbleName(int i){
        return bubblesNamesArray[i];
    }

    @Override
    public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("BubbleStyle", R.string.BubbleStyle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = getParentActivity().getLayoutInflater().inflate(R.layout.imagelistlayout, null, false);

        listAdapter = new CustomListAdapter(context, bubblesNamesArray, imgid);
        list=(ListView) fragmentView.findViewById(R.id.list);
        list.setAdapter(listAdapter);
        list.setDivider(null);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                String Slecteditem = bubblesNamesArray[+position];
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("chatBubbleStyle", Slecteditem);
                editor.commit();
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
            String name = themePrefs.getString("chatBubbleStyle", itemname[0]);
            view = inflater.inflate(R.layout.imagelist, parent, false);
            if(name.equals(itemname[position]) ){
                view.setBackgroundColor(0xffe6e6e6);
            }

            TextView txtTitle = (TextView) view.findViewById(R.id.bubble_title);
            ImageView inImageView = (ImageView) view.findViewById(R.id.bubble_in);
            ImageView outImageView = (ImageView) view.findViewById(R.id.bubble_out);

            txtTitle.setText(itemname[position]);
            //Drawable in = mContext.getResources().getDrawable(imgid[position]);
            //in.setColorFilter(themePrefs.getInt("chatLBubbleColor", 0xffffffff), PorterDuff.Mode.SRC_IN);
            //inImageView.setImageDrawable(in);
            inImageView.setImageResource(imgid[position]);

            //Drawable out = mContext.getResources().getDrawable(imgid[position + itemname.length]);
            //out.setColorFilter(themePrefs.getInt("chatRBubbleColor", themePrefs.getInt("themeColor", AndroidUtilities.defColor)), PorterDuff.Mode.SRC_IN);
            //outImageView.setImageDrawable(out);
            outImageView.setImageResource(imgid[position + itemname.length]);

            return view;

        };
    }
}
