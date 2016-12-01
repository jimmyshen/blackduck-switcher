package com.slothbucket.blackduck.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class TaskStateManager {

    private static final Comparator<Task> TASK_BY_APPNAME =
        new Comparator<Task>() {
            @Override
            public int compare(Task t1, Task t2) {
                return t1.applicationName().compareTo(t2.applicationName());
            }
        };

    private final Executor executor = Executors.newFixedThreadPool(2);
    private final Map<String, Task> tasks = new HashMap<>();
    private final Map<String, TaskIcon> taskIcons = new HashMap<>();
    private final AtomicLong maxTimestamp = new AtomicLong(0);

    public int getTaskCount() {
        synchronized (tasks) {
            return tasks.size();
        }
    }

    public List<Task> getTasks() {
        List<Task> results = new ArrayList<>();
        synchronized (tasks) {
            results.addAll(tasks.values());
        }
        return results;
    }

    public List<Task> getTasksSortedByAppName() {
        List<Task> tasks = getTasks();
        Collections.sort(tasks, TASK_BY_APPNAME);
        return tasks;
    }

    public List<TaskIcon> getTaskIcons() {
        List<TaskIcon> results = new ArrayList<>();
        synchronized (taskIcons) {
            results.addAll(taskIcons.values());
        }
        return results;
    }

    public TaskIcon getTaskIconById(String iconId) {
        synchronized (taskIcons) {
            return taskIcons.get(iconId);
        }
    }

    public List<String> getMissingTaskIconIds(Iterable<String> iconIds) {
        List<String> results = new ArrayList<>();
        synchronized (taskIcons) {
            for (String iconId : iconIds) {
                if (!taskIcons.containsKey(iconId)) {
                    results.add(iconId);
                }
            }
        }
        return results;
    }

    public long getMaxTimestamp() {
        return maxTimestamp.get();
    }

    public void updateTasksAsync(final Iterable<Task> newTasks) {
        executor.execute(
            new Runnable() {
                @Override
                public void run() {
                    long oldMaxTimestamp = maxTimestamp.get();
                    long newMaxTimestamp = oldMaxTimestamp;
                    synchronized (tasks) {
                        for (Task task : newTasks) {
                            String taskId = task.id();
                            if (!tasks.containsKey(taskId) || task.newerThan(tasks.get(taskId))) {
                                tasks.put(taskId, task);
                            }

                            if (task.lastUpdateTimestamp() > newMaxTimestamp) {
                                newMaxTimestamp = task.lastUpdateTimestamp();
                            }
                        }
                    }

                    maxTimestamp.compareAndSet(oldMaxTimestamp, newMaxTimestamp);
                }
            });
    }

    public void updateTaskIconsAsync(final Iterable<TaskIcon> newTaskIcons) {
        executor.execute(
            new Runnable() {
                @Override
                public void run() {
                    synchronized (taskIcons) {
                        for (TaskIcon icon : newTaskIcons) {
                            String iconId = icon.id();
                            if (!taskIcons.containsKey(iconId)) {
                                taskIcons.put(iconId, icon);
                            }
                        }
                    }
                }
            });
    }
}
