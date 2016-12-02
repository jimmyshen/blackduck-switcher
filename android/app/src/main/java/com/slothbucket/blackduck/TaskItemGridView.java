package com.slothbucket.blackduck;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import com.slothbucket.blackduck.client.BlackDuckService;
import com.slothbucket.blackduck.client.Constants;
import com.slothbucket.blackduck.client.RequestPayload;
import com.slothbucket.blackduck.client.ServiceRequest;
import com.slothbucket.blackduck.models.Task;

class TaskItemGridView extends GridView {
     private class ItemTouchListener implements View.OnTouchListener {
        private static final float DIFF_THRESHOLD = 100;
        private static final float VELOCITY_THRESHOLD = 1000;

        private final GestureDetector gestureDetector;
        private final GestureDetector.SimpleOnGestureListener gestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(
                        MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    float diffY = e2.getY() - e1.getY();
                    float diffX = e2.getX() - e1.getX();
                    if (Math.abs(diffX) < Math.abs(diffY) &&
                            Math.abs(diffY) >= DIFF_THRESHOLD &&
                            Math.abs(velocityY) >= VELOCITY_THRESHOLD) {
                        int itemId = getItemIdByCoordinate((int)e1.getX(), (int)e1.getY());
                        if (itemId >= 0) {
                            return (diffY < 0)
                                ? getTaskItemAdapter().onFlingItemDown(itemId)
                                : getTaskItemAdapter().onFlingItemUp(itemId);
                        }
                    }
                    return false;
                }
            };

        ItemTouchListener() {
            gestureDetector = new GestureDetector(getContext(), gestureListener);
        }

        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return gestureDetector.onTouchEvent(motionEvent);
        }
    }

    private static class ItemClickListener implements AdapterView.OnItemClickListener {
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

    public TaskItemGridView(Context context) {
        super(context);
        setListeners();
    }

    public TaskItemGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setListeners();
    }

    public TaskItemGridView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setListeners();
    }

    private void setListeners() {
        super.setOnTouchListener(new ItemTouchListener());
        super.setOnItemClickListener(new ItemClickListener());
    }

    @Override
    public void setOnTouchListener(OnTouchListener listener) {
        throw new UnsupportedOperationException("Cannot set OnTouchListener");
    }

    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
        throw new UnsupportedOperationException("Cannot set OnItemClickListener");
    }

    private TaskItemAdapter getTaskItemAdapter() {
        return (TaskItemAdapter) getAdapter();
    }

    private int getItemIdByCoordinate(int x, int y) {
        for (int i = 0; i < getChildCount(); i++) {
            View view = getChildAt(i);
            if (view != null && inView(x, y, view)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inView(int x, int y, View view) {
        return x <= view.getRight() && x >= view.getLeft() &&
                y <= view.getBottom() && y >= view.getTop();
    }
}
