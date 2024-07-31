package com.example.myapplication;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_LOCATION = 2;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private TextView statusTextView, device1NameTextView;
    private EditText editText;
    private TextToSpeech textToSpeech;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isConnected = false;

    private final ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private BluetoothDevice selectedDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        device1NameTextView = findViewById(R.id.device1NameTextView);
        editText = findViewById(R.id.editText);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Bluetooth not supported");
            statusTextView.setText("Bluetooth not supported");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // Set up buttons
        Button connectButton = findViewById(R.id.connectButton);
        Button disconnectButton = findViewById(R.id.disconnectButton);
        Button sendButton = findViewById(R.id.sendButton);
        Button speechButton = findViewById(R.id.speechButton);

        connectButton.setOnClickListener(v -> startScanning());
        disconnectButton.setOnClickListener(v -> disconnectFromDevice());
        sendButton.setOnClickListener(v -> sendMessage());
        speechButton.setOnClickListener(v -> speakMessage());

        initializeTextToSpeech();
    }

    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.d(TAG, "Language not supported");
                }
            } else {
                Log.d(TAG, "TextToSpeech initialization failed");
            }
        });
    }

    private void startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.getBluetoothLeScanner().startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    if (device != null && !discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        Log.d(TAG, "Found device: " + device.getName() + " (" + device.getAddress() + ")");
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e(TAG, "Scan failed with error code: " + errorCode);
                }
            });

            // After a short delay, show the device selection dialog
            handler.postDelayed(this::showDeviceSelectionDialog, 1000);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_LOCATION);
        }
    }

    private void showDeviceSelectionDialog() {
        if (discoveredDevices.isEmpty()) {
            statusTextView.setText("No devices found");
            return;
        }

        // Convert discovered devices to a list of names for display
        String[] deviceNames = new String[discoveredDevices.size()];
        for (int i = 0; i < discoveredDevices.size(); i++) {
            deviceNames[i] = discoveredDevices.get(i).getName() + " (" + discoveredDevices.get(i).getAddress() + ")";
        }

        // Show a dialog to select a device
        new AlertDialog.Builder(this)
                .setTitle("Select Device")
                .setItems(deviceNames, (dialog, which) -> {
                    selectedDevice = discoveredDevices.get(which);
                    connectToDevice(selectedDevice);
                })
                .show();
    }

    private void connectToDevice(BluetoothDevice device) {
        if (device == null) return;

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices();
                handler.post(() -> {
                    statusTextView.setText("Connected to: " + gatt.getDevice().getName());
                    device1NameTextView.setText("Device Name: " + gatt.getDevice().getName());
                });
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post(() -> statusTextView.setText("Disconnected"));
                isConnected = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                isConnected = true;
            } else {
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Handle characteristic read here
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic written successfully");
            } else {
                Log.e(TAG, "Failed to write characteristic");
            }
        }
    };

    private void sendMessage() {
        String message = editText.getText().toString();
        if (message.isEmpty() || bluetoothGatt == null || !isConnected) {
            statusTextView.setText("No message to send or not connected");
            return;
        }

        // Replace with actual service and characteristic UUIDs
        UUID serviceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"); // Example UUID
        UUID characteristicUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"); // Example UUID

        BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
        if (service == null) {
            statusTextView.setText("Service not found");
            Log.e(TAG, "Service UUID not found: " + serviceUUID.toString());
            return;
        }

        BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUUID);
        if (characteristic == null) {
            statusTextView.setText("Characteristic not found");
            Log.e(TAG, "Characteristic UUID not found: " + characteristicUUID.toString());
            return;
        }

        characteristic.setValue(message.getBytes());
        boolean success = bluetoothGatt.writeCharacteristic(characteristic);

        if (success) {
            statusTextView.setText("Message sent successfully");
        } else {
            statusTextView.setText("Failed to send message");
        }
    }

    private void speakMessage() {
        String message = editText.getText().toString();
        if (textToSpeech != null && !message.isEmpty()) {
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void disconnectFromDevice() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
            statusTextView.setText("Disconnected");
            isConnected = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        disconnectFromDevice();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                statusTextView.setText("Permission required for Bluetooth scanning");
            }
        }
    }
}
