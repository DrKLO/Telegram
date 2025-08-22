package org.telegram.ui;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class StoryCellFactory extends UItem.UItemFactory<SharedPhotoVideoCell2> {
    static {
        setup(new StoryCellFactory());
    }

    private SharedPhotoVideoCell2.SharedResources sharedResources;

    @Override
    public SharedPhotoVideoCell2 createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
        if (sharedResources == null) {
            sharedResources = new SharedPhotoVideoCell2.SharedResources(context, resourcesProvider);
        }

        SharedPhotoVideoCell2 cell = new SharedPhotoVideoCell2(context, sharedResources, currentAccount);
        cell.setCheck2();
        cell.isStory = true;

        return cell;
    }

    @Override
    public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
        SharedPhotoVideoCell2 cell = (SharedPhotoVideoCell2) view;
        MessageObject message = (MessageObject) item.object;

        cell.setMessageObject(message, item.parentSpanCount);
        cell.setChecked(item.checked, false);

        ((SharedPhotoVideoCell2) view).setReordering(item.reordering, false);
    }

    @Override
    public void attachedView(View view, UItem item) {
        ((SharedPhotoVideoCell2) view).setReordering(item.reordering, false);
    }

    public static UItem asStory(int tab, MessageObject story, int parentColumns, boolean checkable) {
        final UItem item = UItem.ofFactory(StoryCellFactory.class).setSpanCount(1);
        item.intValue = tab;
        item.object = story;
        item.longValue = story != null && story.storyItem != null ? story.storyItem.id : -1;
        item.collapsed = checkable;
        item.parentSpanCount = parentColumns;

        return item;
    }

    @Override
    public boolean equals(UItem a, UItem b) {
        if (a.accent != b.accent) return false;
        if (a.checked != b.checked) return false;

        return a.longValue == b.longValue; // todo improve
    }
}
