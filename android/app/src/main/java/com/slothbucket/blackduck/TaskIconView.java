package com.slothbucket.blackduck;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.slothbucket.blackduck.common.FluentLog;

public class TaskIconView extends ImageView {
    private static final FluentLog logger = FluentLog.loggerFor("blackduck", TaskIconView.class);

    public TaskIconView(Context context) {
        super(context);
    }

    public TaskIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TaskIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onMeasure(int measuredWidth, int measuredHeight) {
        super.onMeasure(measuredWidth, measuredHeight);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }
}

