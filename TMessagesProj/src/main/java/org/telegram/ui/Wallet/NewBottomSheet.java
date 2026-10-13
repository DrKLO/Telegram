package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;

import java.util.ArrayList;

public class NewBottomSheet extends BottomSheetWithRecyclerListView implements Theme.Colorable {

    private CharSequence title;
    private UniversalAdapter adapter;
    private ViewGroup container;
    private final Paint navigationBarPaint = new Paint();
    private float sheetTop;

    public NewBottomSheet(Context context, ViewGroup container, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, true, false, false, resourcesProvider);

        this.topPadding = 0.1f;
        this.container = container;

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        updateColors();

        adapter.update(false);
    }

    protected int getSheetBackgroundColor() {
        return getThemedColor(Theme.key_windowBackgroundGray);
    }

    @Override
    public void updateColors() {
        final int color = getSheetBackgroundColor();
        navigationBarPaint.setColor(color);
        setBackgroundColor(color);
        fixNavigationBar(color);
        if (this.container != null) {
            this.container.invalidate();
        }
    }

    @Override
    public void onSheetTop(float top) {
        super.onSheetTop(top);
        sheetTop = top;
        if (topBulletinContainer != null) {
            final float sheetTop = containerView.getY() + top + backgroundPaddingTop;
            final float minBottom = AndroidUtilities.statusBarHeight + dp(56) + topBulletinContainer.getHeight();
            topBulletinContainer.setTranslationY(Math.max(sheetTop, minBottom) - topBulletinContainer.getBottom());
        }
    }

    @Override
    protected void mainContainerDispatchDraw(Canvas canvas) {
        final float bottom = getContainer().getHeight();
        final float top = Math.max(bottom - AndroidUtilities.navigationBarHeight,
                containerView.getY() + sheetTop + backgroundPaddingTop);
        if (top >= bottom) {
            return;
        }
        canvas.drawRect(
                0,
                top,
                getContainer().getWidth(),
                bottom,
                navigationBarPaint
        );
    }

    @Override
    public void setOverlayNavBarColor(int color) {
        super.setOverlayNavBarColor(color);
        // The sheet draws this background so it can follow the opening and closing animations.
        AndroidUtilities.setNavigationBarColor(this, Color.TRANSPARENT, false);
    }

    @Override
    public void show() {
        if (getWindow() != null) {
            AndroidUtilities.enableEdgeToEdge(getWindow());
        }
        super.show();

        final int color = getSheetBackgroundColor();
        setOverlayNavBarColor(color);
        AndroidUtilities.setLightNavigationBar(this, AndroidUtilities.computePerceivedBrightness(color) > .721f);
        if (getWindow() != null) {
            getWindow().getDecorView().requestApplyInsets();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return title;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        return adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(container));
    }

}
