package com.slothbucket.blackduck;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.slothbucket.blackduck.common.Preconditions;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;
import com.slothbucket.blackduck.models.TaskStateManager;

class TaskItemAdapter extends BaseAdapter {

    private final Context context;
    private final TaskStateManager taskStateManager;

    TaskItemAdapter(Context context, TaskStateManager taskStateManager) {
        this.context = context;
        this.taskStateManager = Preconditions.checkNotNull(taskStateManager);
    }

    @Override
    public int getCount() {
        return taskStateManager.getTasks().size();
    }

    @Override
    public Object getItem(int i) {
        return taskStateManager.getTasks().get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LinearLayout linearLayout;
        TextView textView;
        ImageView imageView;

        if (view == null) {
            linearLayout = new LinearLayout(context);

            imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setScaleX(1.5f);
            imageView.setScaleY(1.5f);

            textView = new TextView(context);
            textView.setPadding(10, 10, 10, 10);
            textView.setWidth(320);
            textView.setHeight(48);
            textView.setTextSize(24);

            linearLayout.addView(imageView);
            linearLayout.addView(textView);
        } else {
            linearLayout = (LinearLayout) view;
            imageView = (ImageView) linearLayout.getChildAt(0);
            textView = (TextView) linearLayout.getChildAt(1);
        }

        Task task = (Task) getItem(i);
        TaskIcon icon = taskStateManager.getTaskIconById(task.iconId());
        if (icon != null) {
            imageView.setImageBitmap(icon.getPixelsAsBitmap());
        }
        textView.setText(getDisplayText(task));

        return linearLayout;
    }

    private static String getDisplayText(Task task) {
        String title = task.title();
        if (title.length() > 12) {
            title = String.format("%s...", title.substring(0, 12));
        }
        return String.format("%s - %s", task.applicationName(), title);
    }
}
