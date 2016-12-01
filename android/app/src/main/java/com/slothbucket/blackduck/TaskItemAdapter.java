package com.slothbucket.blackduck;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.slothbucket.blackduck.client.BlackDuckService;
import com.slothbucket.blackduck.client.Constants;
import com.slothbucket.blackduck.client.RequestPayload;
import com.slothbucket.blackduck.client.ServiceRequest;
import com.slothbucket.blackduck.common.Preconditions;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;
import com.slothbucket.blackduck.models.TaskStateManager;

class TaskItemAdapter extends BaseAdapter {
    static class TaskItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            TaskItemAdapter adapter = (TaskItemAdapter) adapterView.getAdapter();
            Task task = (Task) adapter.getItem(i);

            if (task != null) {
                ServiceRequest serviceRequest =
                    ServiceRequest.builder()
                        .setRequestId(RequestConstants.REQUEST_ACTIVATE_TASK)
                        .setCommand(Constants.COMMAND_ACTIVATE_TASK)
                        .setPayload(
                            RequestPayload.builder()
                                .setTaskId(task.id())
                                .build())
                        .build();
                BlackDuckService.sendRequest(view.getContext(), serviceRequest);
            }
        }
    }

    private final TaskStateManager taskStateManager;
    private final LayoutInflater inflater;

    TaskItemAdapter(Context context, TaskStateManager taskStateManager) {
        this.taskStateManager = Preconditions.checkNotNull(taskStateManager);
        this.inflater = LayoutInflater.from(context);
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
            iconView.setImageBitmap(icon.getPixelsAsBitmap());
        }
        titleView.setText(getDisplayText(task));
        return view;
    }

    private static String getDisplayText(Task task) {
        String title = task.title();
        if (title.length() > 12) {
            title = String.format("%s...", title.substring(0, 12));
        }
        return String.format("%s - %s", task.applicationName(), title);
    }
}
