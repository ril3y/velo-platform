package io.freewheel.bridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BLE Heart Rate Monitor manager.
 * Scans for devices advertising HR Service (0x180D), connects to the first one found,
 * subscribes to HR Measurement characteristic (0x2A37), and broadcasts BPM updates.
 */
public class HrmManager {
    private static final String TAG = "HrmManager";

    // Standard BLE Heart Rate UUIDs
    private static final UUID HR_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HR_MEASUREMENT_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final long SCAN_DURATION_MS = 30_000;
    private static final long RESCAN_DELAY_MS = 10_000;

    public interface Listener {
        void onHeartRate(int bpm, String deviceName);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;

    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt connectedGatt;
    private volatile boolean scanning = false;
    private volatile boolean running = false;

    private volatile int currentBpm = 0;
    private volatile String connectedDeviceName = null;

    public HrmManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getCurrentBpm() { return currentBpm; }
    public String getConnectedDeviceName() { return connectedDeviceName; }

    public void start() {
        BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (btManager == null) {
            Log.w(TAG, "BluetoothManager not available");
            return;
        }
        adapter = btManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.w(TAG, "Bluetooth not enabled");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.w(TAG, "BLE scanner not available");
            return;
        }

        running = true;
        Log.i(TAG, "Starting HRM scan...");
        startScan();
    }

    public void stop() {
        running = false;
        stopScan();
        disconnect();
    }

    private void startScan() {
        if (!running || scanning) return;
        if (scanner == null) return;

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(HR_SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        try {
            scanner.startScan(filters, settings, scanCallback);
            scanning = true;
            Log.i(TAG, "BLE scan started (filtering for HR service)");

            // Stop scan after duration
            handler.postDelayed(scanTimeoutRunnable, SCAN_DURATION_MS);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start scan: " + e.getMessage());
        }
    }

    private void stopScan() {
        if (scanning && scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Exception e) {
                Log.w(TAG, "stopScan error: " + e.getMessage());
            }
            scanning = false;
            handler.removeCallbacks(scanTimeoutRunnable);
        }
    }

    private final Runnable scanTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (scanning) {
                Log.d(TAG, "Scan timeout — no HRM found, will retry");
                stopScan();
                if (running && connectedGatt == null) {
                    handler.postDelayed(() -> startScan(), RESCAN_DELAY_MS);
                }
            }
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device.getName();
            if (name == null) name = "HRM " + device.getAddress().substring(12);

            Log.i(TAG, "Found HRM: " + name + " (" + device.getAddress()
                    + ") RSSI=" + result.getRssi());

            // Connect to first found
            stopScan();
            connectToDevice(device);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed with error: " + errorCode);
            scanning = false;
        }
    };

    private void connectToDevice(BluetoothDevice device) {
        String name = device.getName();
        if (name == null) name = "HRM " + device.getAddress().substring(12);
        Log.i(TAG, "Connecting to HRM: " + name);

        connectedGatt = device.connectGatt(context, false, gattCallback);
    }

    private void disconnect() {
        if (connectedGatt != null) {
            connectedGatt.disconnect();
            connectedGatt.close();
            connectedGatt = null;
        }
        connectedDeviceName = null;
        currentBpm = 0;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String name = gatt.getDevice().getName();
            if (name == null) name = "HRM";

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to " + name + " — discovering services");
                connectedDeviceName = name;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Disconnected from " + name);
                connectedDeviceName = null;
                currentBpm = 0;

                if (connectedGatt != null) {
                    connectedGatt.close();
                    connectedGatt = null;
                }

                // Auto-reconnect
                if (running) {
                    Log.i(TAG, "Will rescan for HRM in " + (RESCAN_DELAY_MS / 1000) + "s");
                    handler.postDelayed(() -> startScan(), RESCAN_DELAY_MS);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: " + status);
                return;
            }

            BluetoothGattService hrService = gatt.getService(HR_SERVICE_UUID);
            if (hrService == null) {
                Log.e(TAG, "HR service not found on device");
                return;
            }

            BluetoothGattCharacteristic hrChar = hrService.getCharacteristic(HR_MEASUREMENT_UUID);
            if (hrChar == null) {
                Log.e(TAG, "HR measurement characteristic not found");
                return;
            }

            // Enable notifications
            gatt.setCharacteristicNotification(hrChar, true);

            BluetoothGattDescriptor cccd = hrChar.getDescriptor(CCCD_UUID);
            if (cccd != null) {
                cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(cccd);
                Log.i(TAG, "Subscribed to HR notifications from " + connectedDeviceName);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.i(TAG, "Descriptor write status=" + status
                    + (status == BluetoothGatt.GATT_SUCCESS ? " (OK)" : " (FAILED)"));
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (HR_MEASUREMENT_UUID.equals(characteristic.getUuid())) {
                int bpm = parseHeartRate(characteristic.getValue());
                currentBpm = bpm;
                Log.d(TAG, "HR: " + bpm + " bpm from " + connectedDeviceName);
                if (bpm > 0 && listener != null) {
                    listener.onHeartRate(bpm, connectedDeviceName);
                }
            }
        }
    };

    /**
     * Parse BLE Heart Rate Measurement (0x2A37).
     * Byte 0: flags — bit 0: 0=uint8, 1=uint16 for HR value
     * Byte 1 (or 1-2): heart rate
     */
    private static int parseHeartRate(byte[] data) {
        if (data == null || data.length < 2) return 0;
        int flags = data[0] & 0xFF;
        if ((flags & 0x01) == 0) {
            // uint8
            return data[1] & 0xFF;
        } else {
            // uint16 little-endian
            if (data.length < 3) return 0;
            return (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);
        }
    }
}
