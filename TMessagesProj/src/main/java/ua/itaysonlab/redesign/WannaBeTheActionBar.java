package ua.itaysonlab.redesign;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.BaseFragment;

public abstract class WannaBeTheActionBar extends FrameLayout {
    public BaseFragment parentFragment;

    public WannaBeTheActionBar(@NonNull Context context) {
        super(context);
    }

    public WannaBeTheActionBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public WannaBeTheActionBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public WannaBeTheActionBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
