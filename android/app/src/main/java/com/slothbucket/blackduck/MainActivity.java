package com.slothbucket.blackduck;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.GridView;

import com.slothbucket.blackduck.client.BlackDuckService;
import com.slothbucket.blackduck.client.Constants;
import com.slothbucket.blackduck.client.RequestPayload;
import com.slothbucket.blackduck.client.ServiceRequest;
import com.slothbucket.blackduck.client.ServiceResponse;
import com.slothbucket.blackduck.common.FluentLog;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;
import com.slothbucket.blackduck.models.TaskStateManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final FluentLog logger = FluentLog.loggerFor("blackduck", MainActivity.class);

    // TODO: Implement automatic device discovery (SDP keeps cycling my adapter!).
    private static final String BT_DEVICE_MAC = "00:02:5B:05:7A:CA";

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final TaskStateManager taskStateManager = new TaskStateManager();
    private ProgressDialog progressDialog;
    private ScheduledFuture<?> periodicRefreshTask;
    private BluetoothAdapter bluetoothAdapter;

    private final BroadcastReceiver serviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            logger.atDebug().log("Received action %s", action);
            if (Constants.ACTION_DEVICE_CONNECTED.equals(action)) {
                progressDialog.setMessage("Fetching running tasks...");
                onDeviceConnected();
            } else if (Constants.ACTION_DEVICE_ERROR.equals(action)) {
                String reason = intent.getStringExtra(Constants.EXTRA_ERROR_MESSAGE);
                progressDialog.setMessage(String.format("Bluetooth error: %s", reason));
            } else if (Constants.ACTION_SERVICE_RESPONSE.equals(action)) {
                ServiceResponse response =
                    intent.getParcelableExtra(Constants.EXTRA_SERVICE_RESPONSE);

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
                    case RequestConstants.REQUEST_LIST_TASKS_INITIAL:
                        onListTasksResults(response.payload().tasks(), true);
                        break;
                    case RequestConstants.REQUEST_FETCH_ICONS_INITIAL:
                        onBatchGetIconResults(response.payload().icons(), true);
                        break;
                    case RequestConstants.REQUEST_LIST_TASKS_SYNC:
                        onListTasksResults(response.payload().tasks(), false);
                        break;
                    case RequestConstants.REQUEST_FETCH_ICONS_SYNC:
                        onBatchGetIconResults(response.payload().icons(), false);
                        break;
                    case RequestConstants.REQUEST_ACTIVATE_TASK:
                        logger.atDebug().log("Task activation succeeded.");
                        break;
                    case RequestConstants.REQUEST_SCALE_TASK:
                        logger.atDebug().log("Task scaling succeeded.");
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
            .registerReceiver(serviceReceiver, intentFilter);

        // Configure grid view.
        TaskItemGridView taskItemGridView = (TaskItemGridView) findViewById(R.id.task_grid);
        taskItemGridView.setNumColumns(
            getNumColumnsForOrientation(getResources().getConfiguration().orientation));
        taskItemGridView.setAdapter(new TaskItemAdapter(this, taskStateManager));

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
            startActivityForResult(requestBtIntent, RequestConstants.REQUEST_ENABLE_BT);
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
                .setRequestId(RequestConstants.REQUEST_LIST_TASKS_INITIAL)
                .setCommand(Constants.COMMAND_LIST_TASKS)
                .setPayload(RequestPayload.empty())
                .build();
        BlackDuckService.sendRequest(this, request);
    }

    private void onListTasksResults(Iterable<Task> tasks, boolean isInitialLoad) {
        Set<String> iconIds = getIconIdsFromTasks(tasks);
        taskStateManager.updateTasksAsync(tasks);

        if (isInitialLoad) {
            if (!iconIds.isEmpty()) {
                progressDialog.setMessage("Fetching task icons...");
                batchGetIcons(RequestConstants.REQUEST_FETCH_ICONS_INITIAL, iconIds);
            } else {
                progressDialog.dismiss();
                refreshTaskDisplay();
            }
        } else {
             // Fetch any new icons.
            List<String> newIconIds = taskStateManager.getMissingTaskIconIds(iconIds);
            if (!newIconIds.isEmpty()) {
                batchGetIcons(RequestConstants.REQUEST_FETCH_ICONS_SYNC, newIconIds);
            } else {
                refreshTaskDisplay();
            }
        }
    }

    private void onBatchGetIconResults(Iterable<TaskIcon> taskIcons, boolean isInitialLoad) {
        taskStateManager.updateTaskIconsAsync(taskIcons);

        logger.atDebug().log("Processing icon results.");
        if (isInitialLoad) {
            schedulePeriodicTaskRefresher(5);
            refreshTaskDisplay();
            progressDialog.dismiss();
        } else {
            refreshTaskDisplay();
        }
    }

    private void batchGetIcons(int requestId, Iterable<String> iconIds) {
        ArrayList<String> iconIdsList = new ArrayList<>();
        addAllIterableToCollection(iconIdsList, iconIds);

        RequestPayload payload = RequestPayload.builder().setIconIds(iconIdsList).build();
        ServiceRequest request =
                ServiceRequest.builder()
                        .setRequestId(requestId)
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

        periodicRefreshTask = scheduler.scheduleWithFixedDelay(
            new Runnable() {
                @Override
                public void run() {
                    long maxTimestamp = taskStateManager.getMaxTimestamp();
                    RequestPayload payload =
                        RequestPayload.builder().setLastUpdateTimestamp(maxTimestamp).build();
                    ServiceRequest request =
                        ServiceRequest.builder()
                            .setRequestId(RequestConstants.REQUEST_LIST_TASKS_SYNC)
                            .setCommand(Constants.COMMAND_LIST_UPDATED_TASKS)
                            .setPayload(payload)
                            .build();
                    BlackDuckService.sendRequest(MainActivity.this, request);
                }
            },
            periodSeconds,
            periodSeconds,
            TimeUnit.SECONDS);
    }

    private void refreshTaskDisplay() {
        GridView taskGridView = (GridView) findViewById(R.id.task_grid);
        ((TaskItemAdapter) taskGridView.getAdapter()).notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RequestConstants.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                logger.atInfo().log("User enabled bluetooth request.");
                onBluetoothEnabled();
            } else {
                logger.atInfo().log("User rejected bluetooth request.");
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        GridView taskGrid = (GridView) findViewById(R.id.task_grid);
        taskGrid.setNumColumns(getNumColumnsForOrientation(newConfig.orientation));
    }

    private static int getNumColumnsForOrientation(int orientation) {
        return (orientation == Configuration.ORIENTATION_LANDSCAPE) ? 6 : 3;
    }

    private static <T> void addAllIterableToCollection(
            Collection<T> collection, Iterable<T> newItems) {
        for (T item : newItems) {
            collection.add(item);
        }
    }

    private static Set<String> getIconIdsFromTasks(Iterable<Task> tasks) {
        Set<String> iconIds = new HashSet<>();
        for (Task task : tasks) {
            iconIds.add(task.iconId());
        }
        return iconIds;
    }
}
