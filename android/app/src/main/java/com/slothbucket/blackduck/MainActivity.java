package com.slothbucket.blackduck;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BlackDuck:MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LIST_TASKS_INITIAL = 2;
    private static final int REQUEST_LIST_TASKS_SYNC = 3;
    private static final int REQUEST_FETCH_ICONS_INITIAL = 4;
    private static final int REQUEST_FETCH_ICONS_SYNC = 5;
    private static final int REQUEST_ACTIVATE_TASK = 6;

    // TODO: Implement automatic device discovery (SDP keeps cycling my adapter!).
    private static final String BT_DEVICE_MAC = "00:02:5B:05:7A:CA";

    private final ConcurrentHashMap<String, Task> taskMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TaskIcon> iconMap = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicLong maxTimestamp = new AtomicLong();
    private ScheduledFuture<?> periodicRefreshTask;
    private BluetoothAdapter bluetoothAdapter;

    private final BroadcastReceiver localBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BlackDuckApiService.Constants.ACTION_DEVICE_CONNECTED.equals(action)) {
                BlackDuckApiService.listTasks(getParent(), REQUEST_LIST_TASKS_INITIAL);
            } else {
                int requestId =
                    intent.getIntExtra(BlackDuckApiService.Constants.EXTRA_REQUEST_ID, 0);

                if (requestId == REQUEST_LIST_TASKS_INITIAL &&
                    BlackDuckApiService.Constants.ACTION_TASKS_RESPONSE.equals(action)){
                    onInitialTaskLoad(
                        intent.<Task>getParcelableArrayListExtra(
                            BlackDuckApiService.Constants.EXTRA_TASKS));
                } else if (requestId == REQUEST_FETCH_ICONS_INITIAL &&
                    BlackDuckApiService.Constants.ACTION_ICONS_RESPONSE.equals(action)) {
                    onInitialIconLoad(
                        intent.<TaskIcon>getParcelableArrayListExtra(
                                BlackDuckApiService.Constants.ACTION_ICONS_RESPONSE));
                } else if (requestId == REQUEST_LIST_TASKS_SYNC &&
                    BlackDuckApiService.Constants.ACTION_TASKS_RESPONSE.equals(action)) {
                    onTaskUpdates(
                        intent.<Task>getParcelableArrayListExtra(
                            BlackDuckApiService.Constants.EXTRA_TASKS));
                } else if (requestId == REQUEST_FETCH_ICONS_SYNC &&
                    BlackDuckApiService.Constants.ACTION_ICONS_RESPONSE.equals(action)) {
                    onNewIcons(
                        intent.<TaskIcon>getParcelableArrayListExtra(
                                BlackDuckApiService.Constants.ACTION_ICONS_RESPONSE));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configure local broadcast listener.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BlackDuckApiService.Constants.ACTION_DEVICE_CONNECTED);
        intentFilter.addAction(BlackDuckApiService.Constants.ACTION_TASKS_RESPONSE);
        intentFilter.addAction(BlackDuckApiService.Constants.ACTION_ICONS_RESPONSE);
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(localBroadcastReceiver, intentFilter);

        initializeBluetooth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (periodicRefreshTask != null) {
            periodicRefreshTask.cancel(true);
            periodicRefreshTask = null;
        }

        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            BlackDuckApiService.disconnectDevice(this);
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // TODO: Popup a useful message instead of doing nothing.
        } else if (!bluetoothAdapter.isEnabled()) {
            Intent requestBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(requestBtIntent, REQUEST_ENABLE_BT);
        } else {
            onBluetoothEnabled();
        }
    }

    private void onBluetoothEnabled() {
        // Cancel discovery to decrease load on adapter.
        bluetoothAdapter.cancelDiscovery();

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(BT_DEVICE_MAC);
        if (device == null) {
            Log.e(TAG, String.format("Failed to find remote device '%s'", BT_DEVICE_MAC));
        }
        BlackDuckApiService.connectDevice(this, device);
    }

    private void onInitialTaskLoad(Iterable<Task> tasks) {
        taskMap.clear();
        HashSet<String> iconIds = new HashSet<>();
        long max = 0;
        for (Task task : tasks) {
            taskMap.put(task.id(), task);
            iconIds.add(task.iconId());
            if (task.lastUpdateTimestamp() > max) {
                max = task.lastUpdateTimestamp();
            }
        }
        maxTimestamp.set(max);

        BlackDuckApiService.batchGetIcons(this, REQUEST_FETCH_ICONS_INITIAL, iconIds);
    }

    private void onInitialIconLoad(Iterable<TaskIcon> taskIcons) {
        // Initialize icon cache.
        iconMap.clear();
        for (TaskIcon icon : taskIcons) {
            iconMap.put(icon.id(), icon);
        }

        // Schedule periodic refresh of tasks.
        schedulePeriodicTaskRefresher(5);

        // TODO: Render the task list for the first time!
    }

    private void onTaskUpdates(Iterable<Task> tasks) {
        // Initialize tasks data and fetch icon data.
        taskMap.clear();
        ArrayList<String> iconIds = new ArrayList<>();
        long max = 0;
        for (Task task : tasks) {
            taskMap.put(task.id(), task);
            iconIds.add(task.iconId());
            if (task.lastUpdateTimestamp() > max) {
                max = task.lastUpdateTimestamp();
            }
        }
        maxTimestamp.set(max);

        // Fetch any new icons.
        Set<String> newIconIds = getNewIconIds(iconIds);
        if (!newIconIds.isEmpty()) {
            BlackDuckApiService.batchGetIcons(this, REQUEST_FETCH_ICONS_SYNC, newIconIds);
        } else {
            // TODO: Refresh task display.
        }

        // TODO: Schedule task/icon garbage sweep when we hit thresholds:
        // Collect tasks that aren't open and haven't been updated for over N seconds.
        // Prune icons that are no longer referenced.
    }

    private void onNewIcons(Iterable<TaskIcon> taskIcons) {
        // Initialize icon cache.
        for (TaskIcon icon : taskIcons) {
            iconMap.put(icon.id(), icon);
        }

        // TODO: Render the task list! (maybe only if open tasks are affected)
    }

    private void schedulePeriodicTaskRefresher(int periodSeconds) {
        if (periodicRefreshTask != null) {
            Log.e(TAG, "Periodic refresh already scheduled!");
            return;
        }

        final Activity thisActivity = this;
        periodicRefreshTask = scheduler.scheduleWithFixedDelay(
            new Runnable() {
                @Override
                public void run() {
                    BlackDuckApiService.listUpdatedTasks(
                        thisActivity, REQUEST_LIST_TASKS_SYNC, maxTimestamp.get());
                }
            },
            periodSeconds,
            periodSeconds,
            TimeUnit.SECONDS);
    }

    private Set<String> getNewIconIds(Iterable<String> iconIds) {
        HashSet<String> newIconIds = new HashSet<>();
        for (String iconId : iconIds) {
            if (!iconMap.containsKey(iconId)) {
                newIconIds.add(iconId);
            }
        }

        return newIconIds;
    }

    private void refreshTaskDisplay() {
        // TODO: Implement.
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "User enabled bluetooth request.");
                onBluetoothEnabled();
            } else {
                Log.d(TAG, "User rejected bluetooth request.");
            }
        }
    }
}
