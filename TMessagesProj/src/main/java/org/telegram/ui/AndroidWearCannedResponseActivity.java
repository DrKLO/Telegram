/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.mp4parser.authoring.Edit;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesController;
import org.telegram.android.NotificationCenter;
import org.telegram.android.NotificationsController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Adapters.BaseFragmentAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class AndroidWearCannedResponseActivity extends BaseFragment {
    private ArrayList<String> editTextList = new ArrayList<String>();
    private ArrayList<String> stringResponses = new ArrayList<String>();
    private EditText editTextView;
    private String ANDROIDWEAR_SHAREDPREFS_RESPONSE = "androidwearresponse";
    private Map<String,?> keys;
    private EditText firstNameField;
    private EditText lastNameField;
    private View headerLabelView;
    private View doneButton;
    private ImageView addResponseButton;
    private ImageView header;
    private SharedPreferences myPreferences;
    private SharedPreferences.Editor editor;
    private Context context;
    private final static int done_button = 1;
    private ListView listView;
    private CustomAdapter listAdapter;

    @Override
    public View createView(final Context context, LayoutInflater inflater) {
        this.context = context;

        myPreferences = ApplicationLoader.applicationContext.getSharedPreferences(ANDROIDWEAR_SHAREDPREFS_RESPONSE, Activity.MODE_PRIVATE);
        editor = myPreferences.edit();

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AndroidWear", R.string.AndroidWear));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {

                    if(editTextList.size()>0) {
                        saveTextResponse(editTextList);

                    }

                    else {
                        finishFragment();
                    }

                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        doneButton = menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));

        listView = new ListView(context);
        ViewGroup header = (ViewGroup)inflater.inflate(R.layout.android_responselist_header, listView, false);


        if(myPreferences!=null) {

            stringResponses = getAndroidWearResponses();

            for (String text : stringResponses) {
                Log.d("text: ", text);
                editTextList.add(text);
            }


        }

        else {
            editTextList.add("");
        }

        listAdapter = new CustomAdapter(context, editTextList);

        listView.setAdapter(listAdapter);
        listView.addHeaderView(header);
        listView.setDivider(null);
        listView.setDividerHeight(20);
        fragmentView = new FrameLayout(context);
        fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fragmentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });





        ((FrameLayout) fragmentView).addView(listView);




        addResponseButton = new ImageView(context);
        addResponseButton.setBackgroundResource(R.drawable.floating);
        addResponseButton.setImageResource(R.drawable.floating_pencil);
        addResponseButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(addResponseButton, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(addResponseButton, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            addResponseButton.setStateListAnimator(animator);
            addResponseButton.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        ((FrameLayout) fragmentView).addView(addResponseButton);

        FrameLayout.LayoutParams layoutParams =  (FrameLayout.LayoutParams) addResponseButton.getLayoutParams();
        layoutParams.width = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.height = FrameLayout.LayoutParams.WRAP_CONTENT;
        layoutParams.rightMargin = AndroidUtilities.dp(LocaleController.isRTL ? 0 : 16);
        layoutParams.bottomMargin = AndroidUtilities.dp(16);
        layoutParams.gravity = (Gravity.RIGHT | Gravity.BOTTOM);
        addResponseButton.setLayoutParams(layoutParams);


        addResponseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                editTextList.add("");
                listAdapter.notifyDataSetChanged();

            }
        });






        fragmentView.setBackgroundColor(context.getResources().getColor(R.color.lightgrey_bg));

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();



       /* if (!animations) {
            firstNameField.requestFocus();
            AndroidUtilities.showKeyboard(firstNameField);
        }*/
    }


    private void saveTextResponse(ArrayList<String> editTextList) {
        int i=0;


       editor.clear();
        i=0;

        for (String editText : editTextList) {
            Log.d(Integer.toString(i),editText);
            editor.putString(Integer.toString(i), editText);
            editor.commit();
            i++;  }

        NotificationsController.setAndroidWearResponse(editTextList);
        finishFragment();
    }

    private ArrayList<String> getAndroidWearResponses (){


        ArrayList<String> responseList = new ArrayList<String>();

        keys = myPreferences.getAll();


        TreeSet<String> treeMap = new TreeSet<String>(keys.keySet());
        for (String setKey : treeMap) {
            String value = keys.get(setKey).toString();
            responseList.add(value);
        }


        return responseList;
    }




   /* @Override
    public void onOpenAnimationEnd() {
        firstNameField.requestFocus();
        AndroidUtilities.showKeyboard(firstNameField);
    }*/

    public class CustomAdapter extends  BaseAdapter {
        private Paint paint;
        private Context context;
        private ArrayList<String> editTextList;
        private LinearLayout.LayoutParams layoutParams;
        private Animation inAnimation;
        private Animation outAnimation;
        public CustomAdapter(Context context, ArrayList<String> editTextList) {
            this.context = context;
            this.editTextList = editTextList;
            paint = new Paint();
            paint.setColor(0xffd9d9d9);
            paint.setStrokeWidth(1);

            outAnimation = AnimationUtils.loadAnimation(context, R.anim.icon_anim_fade_out);



        }

        @Override
        public int getCount() {
            return editTextList.size();
        }

        @Override
        public String getItem(int i) {
            return editTextList.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        public void addItem (String text) {



        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {

            final ViewHolder vh;
            final int p = i;
            if(view==null){

                vh = new ViewHolder();
                LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = vi.inflate(R.layout.android_response_list, null);
                vh.editText = (EditText) view.findViewById(R.id.editText1);
                vh.deleteEditText = (ImageView) view.findViewById(R.id.deleteEditText);
                vh.listItem = (LinearLayout) view.findViewById(R.id.listItem);
                vh.deleteEditText.setBackgroundResource(R.drawable.floating);
                vh.deleteEditText.setImageResource(R.drawable.ic_close_white);
                vh.deleteEditText.setScaleType(ImageView.ScaleType.CENTER);
                if (Build.VERSION.SDK_INT >= 21) {
                    StateListAnimator animator = new StateListAnimator();
                    animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(vh.deleteEditText, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
                    animator.addState(new int[]{}, ObjectAnimator.ofFloat(vh.deleteEditText, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
                    vh.deleteEditText.setStateListAnimator(animator);
                    vh.deleteEditText.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setOval(0, 0, AndroidUtilities.dp(32), AndroidUtilities.dp(32));
                        }
                    });
                }


                vh.editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                vh.editText.setHintTextColor(0xff979797);
                vh.editText.setTextColor(0xff212121);

                vh.editText.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                vh.editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
                vh.editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
                AndroidUtilities.clearCursorDrawable(vh.editText);

                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) vh.editText.getLayoutParams();
                layoutParams = (LinearLayout.LayoutParams) vh.editText.getLayoutParams();
                layoutParams.topMargin = AndroidUtilities.dp(24);
                layoutParams.height = AndroidUtilities.dp(36);
                layoutParams.rightMargin = AndroidUtilities.dp(24);
                layoutParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
                vh.editText.setLayoutParams(layoutParams);
                vh.editText.setSelection(vh.editText.length());


                view.setTag(vh);
            }

            else {
                vh = (ViewHolder) view.getTag();

        }

            vh.listItem.setVisibility(View.VISIBLE);
             vh.editText.setText(getItem(i));
             vh.editText.addTextChangedListener(new TextWatcher() {
                 @Override
                 public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                 }

                 @Override
                 public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {

                 }

                 @Override
                 public void afterTextChanged(Editable editable) {
                     editTextList.set(p, vh.editText.getText().toString());
                 }
             });

            vh.deleteEditText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {



                    outAnimation.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            vh.listItem.setVisibility(View.GONE);
                            editTextList.remove(p);
                            notifyDataSetChanged();
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });

                    vh.listItem.startAnimation(outAnimation);


                }
            });

            return view;
        }


        public class ViewHolder {

            public EditText editText;
            public ImageView deleteEditText;
            public LinearLayout listItem;
        }
    }



}
