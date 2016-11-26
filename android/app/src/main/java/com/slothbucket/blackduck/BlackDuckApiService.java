package com.slothbucket.blackduck;

import android.app.IntentService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.auto.value.AutoValue;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages interaction with BlackDuck task service over a persistent Bluetooth connection.
 */
public class BlackDuckApiService extends IntentService {
    public static final class Constants {
        private Constants() {}

        // Actions
        static final String ACTION_TASKS_RESPONSE = pkgAction("TASKS_RESPONSE");
        static final String ACTION_ICONS_RESPONSE = pkgAction("ICONS_RESPONSE");

        // Extras
        static final String EXTRA_REQUEST_ID = pkgExtra("REQUEST_ID");
        static final String EXTRA_TASKS = pkgExtra("TASKS");
        static final String EXTRA_TASK_ICONS = pkgExtra("TASK_ICONS");
    }

    private static class InternalConstants {
        static final String ACTION_CONNECT_DEVICE = pkgAction("CONNECT_DEVICE");
        static final String ACTION_LIST_TASKS = pkgAction("LIST_TASKS");
        static final String ACTION_LIST_UPDATED_TASKS = pkgAction("LIST_UPDATED_TASKS");
        static final String ACTION_BATCHGET_ICONS = pkgAction("BATCHGET_ICONS");
        static final String ACTION_ACTIVATE_TASK = pkgAction("ACTIVATE_TASK");

        static final String EXTRA_DEVICE = pkgAction("DEVICE");
        static final String EXTRA_LAST_UPDATE_TIMESTAMP = pkgExtra("LAST_UPDATE_TIMESTAMP");
        static final String EXTRA_ICON_IDS = pkgExtra("ICON_IDS");
        static final String EXTRA_TASK_ID = pkgExtra("TASK_ID");
    }

    private static String pkgAction(String name) {
        return "com.slothbucket.blackduck.action." + name;
    }

    private static String pkgExtra(String name) {
        return "com.slothbucket.blackduck.extra." + name;
    }

    private static final AtomicLong REQUEST_ID_SEQUENCE = new AtomicLong();
    private static final String TAG = "BlackDuckApiService";
    private static final UUID SERVICE_UUID = UUID.fromString("7f759fe2-b22a-11e6-ba35-37c9859e1514");

    @AutoValue
    abstract static class ServiceRequest {
        abstract long requestId();
        abstract String commandName();
        abstract byte[] data();

        long dataLength() {
            return data().length;
        }

        static ServiceRequest create(long requestId, String commandName, byte[] data) {
            // TODO: Check if AutoValue performs a defensive copy on its own.
            return new AutoValue_BlackDuckApiService_ServiceRequest(
                requestId, commandName, Arrays.copyOf(data, data.length));
        }
    }

    private static class ServiceConnection extends Thread {

        private final Queue<ServiceRequest> pendingTasks = new ConcurrentLinkedQueue<>();
        private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
        private final BluetoothDevice device;
        private final LocalBroadcastManager broadcastManager;
        @Nullable private BluetoothSocket socket;

        ServiceConnection(BluetoothDevice device, LocalBroadcastManager broadcastManager) {
            this.device = device;
            this.broadcastManager = broadcastManager;
        }

        @Override
        public void run() {
            socket = connectDevice();
            if (socket != null) {
                try {
                    ServiceRequest task = pendingTasks.poll();
                    Log.d(TAG, String.format(
                        "Sending request %d (%d bytes).", task.requestId(), task.dataLength()));
                    socket.getOutputStream().write(task.data());

                    JsonNode rootNode = objectMapper.reader(
                        objectMapper.getNodeFactory()).readTree(socket.getInputStream());

                    dispatchResponse(task.requestId(), task.commandName(), rootNode);
                } catch (IOException e) {
                    Log.e(TAG,
                        "I/O exception occurred while communicating over Bluetooth socket.", e);
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // fml
                    }
                }
            }
        }

        private void dispatchResponse(long requestId, String command, JsonNode rootNode) {
            if (!isValidResponseHeader(rootNode)) {
                return;
            }

            Intent intent = new Intent();
            intent.putExtra(Constants.EXTRA_REQUEST_ID, requestId);

            if ("list_tasks".equals(command) || "list_updated_tasks".equals(command)) {
                intent.setAction(Constants.ACTION_TASKS_RESPONSE);
                ArrayList<Task> tasks = new ArrayList<>();
                try {
                    for (JsonNode node : rootNode.get("tasks")) {
                        tasks.add(objectMapper.treeToValue(node, Task.class));
                    }
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Failed to process 'tasks' list in response.", e);
                }
                intent.putParcelableArrayListExtra(Constants.EXTRA_TASKS, tasks);
            } else if ("batchget_icons".equals(command)) {
                intent.setAction(Constants.ACTION_ICONS_RESPONSE);
                ArrayList<TaskIcon> icons = new ArrayList<>();
                try {
                    for (JsonNode node : rootNode.get("icons")) {
                        icons.add(objectMapper.treeToValue(node, TaskIcon.class));
                    }
                } catch (JsonProcessingException e) {
                    Log.e(TAG, "Failed to process 'icons' list in response.", e);
                }
                intent.putParcelableArrayListExtra(Constants.EXTRA_TASK_ICONS, icons);
            } else if ("activate_task".equals(command)) {
                return;
            } else {
                Log.w(TAG, String.format("Unhandled command response: %s", command));
                return;
            }

            broadcastManager.sendBroadcast(intent);
        }

        private boolean isValidResponseHeader(JsonNode rootNode) {
            String status = rootNode.get("status").asText("wtf");
            if (!"ok".equals(status)) {
                if ("client-error".equals(status)) {
                    String error = rootNode.get("client-error").asText("(not set)");
                    Log.e(TAG, String.format("Received client error response: %s", error));
                } else if ("server-error".equals(status)) {
                    String error = rootNode.get("client-error").asText("(not set)");
                    Log.e(TAG, String.format("Received server error response: %s", error));
                } else {
                    Log.wtf(TAG, "Unexpected response from server.");
                }

                return false;
            }

            return true;
        }

        void sendRequest(long requestId, String command, byte[] data) {
            pendingTasks.offer(ServiceRequest.create(requestId, command, data));
        }

        @Nullable
        private BluetoothSocket connectDevice() {
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                socket.connect();
                return socket;
            } catch (IOException e) {
                Log.e(TAG,
                    String.format("Failed to connect to service on device %s ('%s')",
                            device.getAddress(), device.getName()));
            }

            return null;
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());
    @Nullable private ServiceConnection connection;

    public BlackDuckApiService() {
        super("BlackDuckApiService");
    }

    /**
     * Connect to task service being hosted on the given device.
     *
     * <p>Be sure to call {@link BluetoothAdapter#cancelDiscovery()} to reduce load on the adapter.
     */
    public static void connectDevice(Context context, BluetoothDevice device) {
        Intent intent = new Intent(context, BlackDuckApiService.class);
        intent.setAction(InternalConstants.ACTION_CONNECT_DEVICE);
        intent.putExtra(InternalConstants.EXTRA_DEVICE, device);
        context.startService(intent);
    }

    /**
     * Lists all the tasks available via the BlackDuck service.
     */
    public static long listTasks(Context context) {
        long requestId = REQUEST_ID_SEQUENCE.getAndIncrement();
        Intent intent = new Intent(context, BlackDuckApiService.class);
        intent.setAction(InternalConstants.ACTION_LIST_TASKS);
        intent.putExtra(Constants.EXTRA_REQUEST_ID, requestId);
        context.startService(intent);
        return requestId;
    }

    /**
     * Lists all tasks that have changed since the given timestamp.
     *
     * <p><b>NOTE:</b> Always use timestamps from the service instead of ones generated by
     * client-side clock.
     */
    public static long listUpdatedTasks(Context context, long lastUpdateTimestamp) {
        long requestId = REQUEST_ID_SEQUENCE.getAndIncrement();
        Intent intent = new Intent(context, BlackDuckApiService.class);
        intent.setAction(InternalConstants.ACTION_LIST_TASKS);
        intent.putExtra(Constants.EXTRA_REQUEST_ID, requestId);
        context.startService(intent);
        return requestId;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final String action = intent.getAction();
        if (InternalConstants.ACTION_CONNECT_DEVICE.equals(action)) {
            BluetoothDevice device = intent.getParcelableExtra(InternalConstants.EXTRA_DEVICE);
            onConnectDeviceAction(device);
        } else {
            long requestId = intent.getLongExtra(Constants.EXTRA_REQUEST_ID, 0);
            if (requestId <= 0) {
                throw new IllegalArgumentException("Invalid request ID provided.");
            }

            if (InternalConstants.ACTION_LIST_TASKS.equals(action)) {
                onListTasksAction(requestId);
            } else if (InternalConstants.ACTION_LIST_UPDATED_TASKS.equals(action)) {
                onListUpdatedTasksAction(
                        requestId,
                        intent.getLongExtra(InternalConstants.EXTRA_LAST_UPDATE_TIMESTAMP, 0));
            } else if (InternalConstants.ACTION_BATCHGET_ICONS.equals(action)) {
                onBatchGetIconsAction(
                        requestId,
                        intent.getStringArrayListExtra(InternalConstants.EXTRA_ICON_IDS));
            } else if (InternalConstants.ACTION_ACTIVATE_TASK.equals(action)) {
                onActivateTask(requestId, intent.getStringExtra(InternalConstants.EXTRA_TASK_ID));
            }
        }
    }

    private void onConnectDeviceAction(BluetoothDevice device) {
        if (connection != null) {
            throw new IllegalStateException(
                "Cannot connect to service: there is an existing connection!");
        }

        connection = new ServiceConnection(device, LocalBroadcastManager.getInstance(this));
        connection.start();
    }

    private void onListTasksAction(long requestId) {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        }

        ObjectNode rootNode =
            objectMapper.getNodeFactory().objectNode().put("command", "list_tasks");
        dispatchRequest(requestId, "list_tasks", rootNode);
    }

    private void onListUpdatedTasksAction(long requestId, long lastUpdateTimestamp) {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        } else if (lastUpdateTimestamp <= 0) {
            throw new IllegalArgumentException("Invalid last update timestamp given.");
        }

        ObjectNode rootNode =
            objectMapper.getNodeFactory().objectNode().put("command", "list_updated_tasks");
        rootNode.putObject("payload").put("last_update_ts", lastUpdateTimestamp);
        dispatchRequest(requestId, "list_updated_tasks", rootNode);
    }

    private void onBatchGetIconsAction(long requestId, ArrayList<String> iconIds) {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        } else if (iconIds == null || iconIds.isEmpty()) {
            throw new IllegalArgumentException("Null or empty icon IDs given.");
        }

        ObjectNode rootNode =
            objectMapper.getNodeFactory().objectNode().put("command", "batchget_icons");

        ArrayNode iconIdsNode = rootNode.putObject("payload").putArray("icon_ids");
        for (String iconId : iconIds) {
           iconIdsNode.add(iconId);
        }
        dispatchRequest(requestId, "batchget_icons", rootNode);
    }

    private void onActivateTask(long requestId, String taskId) {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        } else if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("Null or empty task ID given.");
        }

        ObjectNode rootNode =
            objectMapper.getNodeFactory().objectNode().put("command", "activate_task");
        rootNode.putObject("payload").put("task_id", taskId);
        dispatchRequest(requestId, "activate_task", rootNode);
    }

    private void dispatchRequest(long requestId, String command, ObjectNode rootNode) {
        if (connection == null) {
            throw new IllegalStateException("Cannot list tasks: no connection was established.");
        }

        try {
            connection.sendRequest(
                requestId, command, objectMapper.writeValueAsBytes(rootNode));
        } catch (JsonProcessingException e) {
            Log.e(TAG, String.format("Failed to serialize '%s' request.", command), e);
        }
    }
}
