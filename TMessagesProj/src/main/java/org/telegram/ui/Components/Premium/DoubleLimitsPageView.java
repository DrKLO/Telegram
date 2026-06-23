package org.Tajgram.ui.Components.Premium;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.UserConfig;
import org.Tajgram.ui.ActionBar.Theme;

public class DoubleLimitsPageView extends BaseListPageView {

    DoubledLimitsBottomSheet.Adapter adapter;

    public DoubleLimitsPageView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    @Override
    public RecyclerView.Adapter createAdapter() {
        adapter = new DoubledLimitsBottomSheet.Adapter(UserConfig.selectedAccount, true, resourcesProvider);
        adapter.containerView = this;
        return adapter;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        adapter.measureGradient(getContext(), getMeasuredWidth(), getMeasuredHeight());
    }
}
