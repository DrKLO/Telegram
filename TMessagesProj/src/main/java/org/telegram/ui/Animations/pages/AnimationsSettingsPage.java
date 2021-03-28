package org.telegram.ui.Animations.pages;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.ui.Animations.AnimationsSettingsAdapter;
import org.telegram.ui.Components.RecyclerListView;

public abstract class AnimationsSettingsPage {

    public final int type;
    public final String title;

    protected final AnimationsSettingsAdapter adapter;
    private final RecyclerListView recyclerView;

    public AnimationsSettingsPage(Context context, int type, String title) {
        this.type = type;
        this.title = title;

        adapter = new AnimationsSettingsAdapter();

        recyclerView = new RecyclerListView(context);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setItemAnimator(null);
    }

    public View getView() {
        return recyclerView;
    }

    public void setOnItemClickListener(RecyclerListView.OnItemClickListener clickListener) {
        recyclerView.setOnItemClickListener(clickListener);
    }
}