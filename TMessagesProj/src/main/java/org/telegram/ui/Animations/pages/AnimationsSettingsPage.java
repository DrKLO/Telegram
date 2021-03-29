package org.telegram.ui.Animations.pages;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Cells.AnimationPropertiesCell;
import org.telegram.ui.Components.RecyclerListView;

public abstract class AnimationsSettingsPage {

    public final int type;
    public final String title;

    protected final AnimationsSettingsAdapter adapter = new AnimationsSettingsAdapter();
    private RecyclerListView.OnItemClickListener clickListener;

    public AnimationsSettingsPage(int type, String title) {
        this.type = type;
        this.title = title;
    }

    public View createView(Context context) {
        RecyclerListView recyclerView = new RecyclerListView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
        recyclerView.setDisallowInterceptTouchEvents(true);
        if (clickListener != null) {
            recyclerView.setOnItemClickListener(clickListener);
        }
        return recyclerView;
    }

    public void setOnItemClickListener(RecyclerListView.OnItemClickListener clickListener) {
        this.clickListener = clickListener;
    }
}