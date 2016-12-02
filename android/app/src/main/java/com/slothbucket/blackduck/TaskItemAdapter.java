package com.slothbucket.blackduck;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.slothbucket.blackduck.client.BlackDuckService;
import com.slothbucket.blackduck.client.Constants;
import com.slothbucket.blackduck.client.RequestPayload;
import com.slothbucket.blackduck.client.ServiceRequest;
import com.slothbucket.blackduck.common.FluentLog;
import com.slothbucket.blackduck.common.Preconditions;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;
import com.slothbucket.blackduck.models.TaskStateManager;

class TaskItemAdapter extends BaseAdapter {
    private static final FluentLog logger = FluentLog.loggerFor("blackduck", TaskItemAdapter.class);

    private final Context context;
    private final TaskStateManager taskStateManager;
    private final LayoutInflater inflater;

    TaskItemAdapter(Context context, TaskStateManager taskStateManager) {
        super();
        this.context = context;
        this.taskStateManager = Preconditions.checkNotNull(taskStateManager);
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return taskStateManager.getTaskCount();
    }

    @Override
    public Object getItem(int i) {
        return taskStateManager.getTasksSortedByAppName().get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        TaskIconView iconView;
        TextView titleView;

        if (view == null) {
            // Construct view from layout XML.
            view = inflater.inflate(R.layout.taskgrid_item, viewGroup, false);
            view.setTag(R.id.task_icon, view.findViewById(R.id.task_icon));
            view.setTag(R.id.task_title, view.findViewById(R.id.task_title));
        }

        iconView = (TaskIconView) view.getTag(R.id.task_icon);
        titleView = (TextView) view.getTag(R.id.task_title);

        Task task = (Task) getItem(i);
        TaskIcon icon = taskStateManager.getTaskIconById(task.iconId());
        if (icon != null) {
            // TODO: Set a default bitmap resource when icon is not available.
            iconView.setImageBitmap(icon.getPixelsAsBitmap());
        }
        titleView.setText(getDisplayText(task));
        return view;
    }

    boolean onFlingItemUp(int itemId) {
        Task task = (Task) getItem(itemId);
        if (task != null) {
            scaleTask(task.id(), "maximize");
            return true;
        }
        return false;
    }

    boolean onFlingItemDown(int itemId) {
        Task task = (Task) getItem(itemId);
        if (task != null) {
            scaleTask(task.id(), "unmaximize");
            return true;
        }
        return false;
    }

    private void scaleTask(String taskId, String scaleAction) {
         ServiceRequest serviceRequest =
            ServiceRequest.builder()
                .setRequestId(RequestConstants.REQUEST_SCALE_TASK)
                    .setCommand(Constants.COMMAND_SCALE_TASK)
                    .setPayload(
                        RequestPayload.builder()
                        .setTaskId(taskId)
                        .setScaleAction(scaleAction)
                        .build())
                .build();
        BlackDuckService.sendRequest(context, serviceRequest);
    }

    private static String getDisplayText(Task task) {
        String title = task.title();
        if (title.length() > 12) {
            title = String.format("%s...", title.substring(0, 12));
        }
        return String.format("%s - %s", task.applicationName(), title);
    }
}
