package org.Tajgram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.Tajgram.ui.ActionBar.BaseFragment;
import org.Tajgram.ui.Components.SizeNotifierFrameLayout;

public class EmptyBaseFragment extends BaseFragment {

    @Override
    public View createView(Context context) {
        return fragmentView = new SizeNotifierFrameLayout(context);
    }

}
