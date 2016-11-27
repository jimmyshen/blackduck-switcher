package com.slothbucket.blackduck;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.slothbucket.blackduck.client.BlackDuckService;
import com.slothbucket.blackduck.client.Constants;
import com.slothbucket.blackduck.client.RequestPayload;
import com.slothbucket.blackduck.client.ServiceRequest;
import com.slothbucket.blackduck.client.ServiceResponse;
import com.slothbucket.blackduck.common.FluentLog;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MainActivity extends AppCompatActivity {

    private static final FluentLog logger = FluentLog.loggerFor("blackduck", MainActivity.class);

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
    private ProgressDialog progressDialog;
    private ScheduledFuture<?> periodicRefreshTask;
    private BluetoothAdapter bluetoothAdapter;

    private final BroadcastReceiver blackduckServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Constants.ACTION_DEVICE_CONNECTED.equals(action)) {
                progressDialog.setMessage("Fetching running tasks...");
                onDeviceConnected();
            } else if (Constants.ACTION_DEVICE_ERROR.equals(action)) {
                String reason = intent.getStringExtra(Constants.EXTRA_ERROR_MESSAGE);
                progressDialog.setMessage(String.format("Bluetooth error: %s", reason));
            } else if (Constants.ACTION_SERVICE_RESPONSE.equals(action)) {
                ServiceResponse response =
                        intent.getParcelableExtra(Constants.ACTION_SERVICE_RESPONSE);

                String status = response.status();
                if (!"ok".equals(status)) {
                    String error = response.error();
                    if ("client-error".equals(status)) {
                        logger.atError().log("Received client error in response: %s", error);
                        progressDialog.setMessage("Client error encountered.");
                    } else if ("server-error".equals(status)) {
                        logger.atError().log("Received server error in response: %s", error);
                        progressDialog.setMessage("Server error encountered.");
                    } else {
                        logger.atError().log("Unexpected status: %s", status);
                    }
                    return;
                }

                switch (response.requestId()) {
                    case REQUEST_LIST_TASKS_INITIAL:
                        onInitialTaskLoad(response.payload().tasks());
                        break;
                    case REQUEST_FETCH_ICONS_INITIAL:
                        onInitialIconLoad(response.payload().icons());
                        break;
                    case REQUEST_LIST_TASKS_SYNC:
                        onTaskUpdates(response.payload().tasks());
                        break;
                    case REQUEST_FETCH_ICONS_SYNC:
                        onNewIcons(response.payload().icons());
                        break;
                    default:
                        logger.atWarning().log(
                            "Unhandled service response from request ID %d", response.requestId());
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
        intentFilter.addAction(Constants.ACTION_DEVICE_CONNECTED);
        intentFilter.addAction(Constants.ACTION_DEVICE_ERROR);
        intentFilter.addAction(Constants.ACTION_SERVICE_RESPONSE);
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(blackduckServiceReceiver, intentFilter);

        progressDialog = new ProgressDialog(this, ProgressDialog.STYLE_SPINNER);
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
            stopService(new Intent(this, BlackDuckService.class));
        }
    }

    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // TODO: Popup a useful message instead of doing nothing.
            logger.atError().log("No Bluetooth adapter available!");
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

        progressDialog.setTitle("Please wait while we connect to your computer.");
        progressDialog.setMessage("Connecting to Bluetooth service...");
        progressDialog.show();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(BT_DEVICE_MAC);
        if (device == null) {
            logger.atError().log("Failed to find remote device '%s'", BT_DEVICE_MAC);
            return;
        }

        Intent intent = new Intent(this, BlackDuckService.class);
        intent.setAction(Constants.ACTION_CONNECT_DEVICE);
        intent.putExtra(Constants.EXTRA_DEVICE, device);
        startService(intent);
    }

    private void onDeviceConnected() {
        ServiceRequest request =
            ServiceRequest.builder()
                .setRequestId(REQUEST_LIST_TASKS_INITIAL)
                .setCommand(Constants.COMMAND_LIST_TASKS)
                .setPayload(RequestPayload.empty())
                .build();
        BlackDuckService.sendRequest(this, request);
    }

    private void onInitialTaskLoad(Iterable<Task> tasks) {
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

        progressDialog.setMessage("Fetching task icons...");
        fetchNewIcons(iconIds);
    }

    private void onInitialIconLoad(Iterable<TaskIcon> taskIcons) {
        // Initialize icon cache.
        iconMap.clear();
        for (TaskIcon icon : taskIcons) {
            iconMap.put(icon.id(), icon);
        }

        // Schedule periodic refresh of tasks.
        schedulePeriodicTaskRefresher(5);
        refreshTaskDisplay();

        progressDialog.dismiss();
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
        List<String> newIconIds = getNewIconIds(iconIds);
        if (!newIconIds.isEmpty()) {
            fetchNewIcons(newIconIds);
        } else {
            refreshTaskDisplay();
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

        // TODO: Maybe only refresh tasks that are affected.
        refreshTaskDisplay();
    }

    private void fetchNewIcons(List<String> iconIds) {
        RequestPayload payload = RequestPayload.builder().setIconIds(iconIds).build();
        ServiceRequest request =
                ServiceRequest.builder()
                        .setRequestId(REQUEST_LIST_TASKS_INITIAL)
                        .setCommand(Constants.COMMAND_BATCHGET_ICONS)
                        .setPayload(payload)
                        .build();
        BlackDuckService.sendRequest(this, request);
    }

    private void schedulePeriodicTaskRefresher(int periodSeconds) {
        if (periodicRefreshTask != null) {
            logger.atError().log("Periodic refresh already scheduled!");
            return;
        }

        final Activity thisActivity = this;
        periodicRefreshTask = scheduler.scheduleWithFixedDelay(
            new Runnable() {
                @Override
                public void run() {
                    RequestPayload payload =
                        RequestPayload.builder().setLastUpdateTimestamp(maxTimestamp.get()).build();
                    ServiceRequest request =
                        ServiceRequest.builder()
                            .setRequestId(REQUEST_LIST_TASKS_SYNC)
                            .setCommand(Constants.COMMAND_LIST_UPDATED_TASKS)
                            .setPayload(payload)
                            .build();
                    BlackDuckService.sendRequest(thisActivity, request);
                }
            },
            periodSeconds,
            periodSeconds,
            TimeUnit.SECONDS);
    }

    private List<String> getNewIconIds(Iterable<String> iconIds) {
        ArrayList<String> newIconIds = new ArrayList<>();
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
                logger.atInfo().log("User enabled bluetooth request.");
                onBluetoothEnabled();
            } else {
                logger.atInfo().log("User rejected bluetooth request.");
            }
        }
    }
}
