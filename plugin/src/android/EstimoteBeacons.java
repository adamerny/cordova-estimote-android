/*
Android implementation of Cordova plugin for Estimote Beacons.

JavaDoc for Estimote Android API: https://estimote.github.io/Android-SDK/JavaDocs/
*/

package com.evothings;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.estimote.sdk.Beacon;
import com.estimote.sdk.BeaconManager;
import com.estimote.sdk.EstimoteSDK;
import com.estimote.sdk.MacAddress;
import com.estimote.sdk.Region;
import com.estimote.sdk.SystemRequirementsHelper;
import com.estimote.sdk.Utils;
import com.estimote.sdk.cloud.model.BeaconInfo;
import com.estimote.sdk.cloud.model.BeaconInfoSettings;
import com.estimote.sdk.connection.BeaconConnection;
import com.estimote.sdk.connection.DeviceConnection;
import com.estimote.sdk.connection.DeviceConnectionCallback;
import com.estimote.sdk.connection.DeviceConnectionProvider;
import com.estimote.sdk.connection.exceptions.DeviceConnectionException;
import com.estimote.sdk.connection.scanner.ConfigurableDevice;
import com.estimote.sdk.connection.scanner.ConfigurableDevicesScanner;
import com.estimote.sdk.connection.scanner.DeviceType;
import com.estimote.sdk.connection.settings.SettingCallback;
import com.estimote.sdk.connection.settings.Settings;
import com.estimote.sdk.connection.settings.SettingsReader;
import com.estimote.sdk.exception.EstimoteDeviceException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin class for the Estimote Beacon plugin.
 */
@SuppressWarnings("deprecation")
public class EstimoteBeacons extends CordovaPlugin {
    private static final String LOGTAG = "EstimoteBeacons";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static int VERBOSE_MODE = 1;

    private BeaconManager mBeaconManager;
    private EstimoteSDK mEstimoteSDK;
    private CordovaInterface mCordovaInterface;

    private ConfigurableDevicesScanner mDeviceScanner;
    private List<ConfigurableDevicesScanner.ScanResultItem> mScannedDevices;
    private DeviceConnectionProvider mDeviceConnectionProvider;
    private DeviceConnection mConnectedDevice;
    private LinkedHashMap mConnectedDeviceHashMap;
    private boolean mDeviceConnectionProviderIsConnected = false;
    private boolean mIsScanning = false;
    private boolean mIsFetchingSettings = false;

    private ArrayList<Beacon> mRangedBeacons;
    private BeaconConnected mConnectedBeacon;
    private boolean mIsConnected = false;


    // Maps and variables that keep track of Cordova callbacks.
    private HashMap<String, CallbackContext> mRangingCallbackContexts =
            new HashMap<String, CallbackContext>();
    private HashMap<String, CallbackContext> mMonitoringCallbackContexts =
            new HashMap<String, CallbackContext>();

    private CallbackContext mBluetoothStateCallbackContext;
    private CallbackContext mBeaconConnectionCallbackContext;
    private CallbackContext mBeaconDisconnectionCallbackContext;

    private CallbackContext mScanningCallbackContext;
    private CallbackContext mDeviceConnectionCallbackContext;

    private Context mApplicationContext;

    //   https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java#L72

    /**
     * Return VERBOSE_MODE
     */
    public static Boolean getVerboseMode() {
        return (VERBOSE_MODE == 1);
    }

    /**
     * Create JSON object representing a region.
     */
    private static JSONObject makeJSONRegion(Region region)
            throws JSONException {
        return makeJSONRegion(region, null);
    }

    /**
     * Create JSON object representing a region in the given state.
     */
    private static JSONObject makeJSONRegion(Region region, String state)
            throws JSONException {
        JSONObject json = new JSONObject();
        json.put("identifier", region.getIdentifier());
        json.put("uuid", region.getProximityUUID());
        json.put("major", region.getMajor());
        json.put("minor", region.getMinor());
        if (state != null) {
            json.put("state", state);
        }
        return json;
    }

    /**
     * Plugin initializer.
     */
    @Override
    public void pluginInitialize() {
        LogUtils.i(LOGTAG, "initialize");

        mCordovaInterface = cordova;
        mCordovaInterface.setActivityResultCallback(this);

        mApplicationContext = this.cordova.getActivity().getApplicationContext();

        if (mBeaconManager == null) {
            mBeaconManager = new BeaconManager(mApplicationContext);
        }

        mBeaconManager.setErrorListener(new BeaconManager.ErrorListener() {
            @Override
            public void onError(Integer errorId) {
                LogUtils.e(LOGTAG, "BeaconManager error: " + errorId);
            }
        });

        if (null == mDeviceScanner) {
            mDeviceScanner = new ConfigurableDevicesScanner(mApplicationContext);
        }

        mRangedBeacons = new ArrayList<Beacon>();
        mScannedDevices = new ArrayList<ConfigurableDevicesScanner.ScanResultItem>();
    }

    /**
     * Plugin reset.
     * Called when the WebView does a top-level navigation or refreshes.
     */
    @Override
    public void onReset() {
        LogUtils.i(LOGTAG, "onReset");

        disconnectBeaconManager();

        mRangingCallbackContexts = new HashMap<String, CallbackContext>();
        mMonitoringCallbackContexts = new HashMap<String, CallbackContext>();
    }

    /**
     * The final call you receive before your activity is destroyed.
     *  
     */
    public void onDestroy() {
        LogUtils.i(LOGTAG, "onDestroy");
        disconnectConnectedDevice();
        disconnectConnectedBeacon();
        disconnectDeviceConnectionProvider();
        disconnectBeaconManager();
    }

    /**
     * Disconnect from the device provider.
     *  
     */
    private void disconnectDeviceConnectionProvider() {
        if (mDeviceConnectionProvider != null && mDeviceConnectionProviderIsConnected) {
            mDeviceConnectionProvider.destroy();
            mDeviceConnectionProviderIsConnected = false;
        }
    }

    /**
     * Disconnect from the beacon manager.
     *  
     */
    private void disconnectBeaconManager() {
        if (mBeaconManager != null && mIsConnected) {
            mBeaconManager.disconnect();
            mIsConnected = false;
        }
    }

    /**
     * Entry point for JavaScript calls.
     */
    @Override
    public boolean execute(
            String action,
            CordovaArgs args,
            final CallbackContext callbackContext)
            throws JSONException {
        if ("beacons_startScanningDevices".equals(action)) {
            startScanningDevices(args, callbackContext);
        } else if ("beacons_stopScanningDevices".equals(action)) {
            stopScanningDevices(callbackContext);
        } else if ("beacons_connectToDevice".equals(action)) {
            connectToDevice(args, callbackContext);
        } else if ("beacons_disconnectConnectedDevice".equals(action)) {
            disconnectConnectedDevice(callbackContext);
        } else if ("beacons_startRangingBeaconsInRegion".equals(action)) {
            startRangingBeaconsInRegion(args, callbackContext);
        } else if ("beacons_stopRangingBeaconsInRegion".equals(action)) {
            stopRangingBeaconsInRegion(args, callbackContext);
        } else if ("beacons_startMonitoringForRegion".equals(action)) {
            startMonitoringForRegion(args, callbackContext);
        } else if ("beacons_stopMonitoringForRegion".equals(action)) {
            stopMonitoringForRegion(args, callbackContext);
        } else if ("beacons_setupAppIDAndAppToken".equals(action)) {
            setupAppIDAndAppToken(args, callbackContext);
        } else if ("beacons_connectToBeacon".equals(action)) {
            connectToBeacon(args, callbackContext);
        } else if ("beacons_disconnectConnectedBeacon".equals(action)) {
            disconnectConnectedBeacon(callbackContext);
        } else if ("beacons_writeConnectedProximityUUID".equals(action)) {
            writeConnectedProximityUUID(args, callbackContext);
        } else if ("beacons_writeConnectedMajor".equals(action)) {
            writeConnectedMajor(args, callbackContext);
        } else if ("beacons_writeConnectedMinor".equals(action)) {
            writeConnectedMinor(args, callbackContext);
        } else if ("bluetooth_bluetoothState".equals(action)) {
            checkBluetoothState(callbackContext);
        } else if ("toggleVerbose".equals(action)) {
            toggleVerbose(args, callbackContext);
        } else {
            return false;
        }
        return true;
    }

    /**
     * If Bluetooth is off, open a Bluetooth diaLogUtils.
     */
    private void checkBluetoothState(
            final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "checkBluetoothState");

        // Check that no Bluetooth state request is in progress.
        if (null != mBluetoothStateCallbackContext) {
            callbackContext.error("Bluetooth state request already in progress");
            return;
        }

        // Check if Bluetooth is available.
        if (!SystemRequirementsHelper.isBluetoothLeAvailable(mApplicationContext)) {
            sendResultForBluetoothEnabled(callbackContext);
        } else if (!SystemRequirementsHelper.isBluetoothEnabled(mApplicationContext)) { // Check if Bluetooth is enabled.
            // Open Bluetooth dialog on the UI thread.
            final CordovaPlugin self = this;
            mBluetoothStateCallbackContext = callbackContext;
            Runnable openBluetoothDialog = new Runnable() {
                public void run() {
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    mCordovaInterface.startActivityForResult(
                            self,
                            enableIntent,
                            REQUEST_ENABLE_BLUETOOTH);
                }
            };
            mCordovaInterface.getActivity().runOnUiThread(openBluetoothDialog);
        } else {
            // Bluetooth is enabled, return the result to JavaScript,
            sendResultForBluetoothEnabled(callbackContext);
        }
    }

    /**
     * Check if Bluetooth is enabled and return result to JavaScript.
     */
    private void sendResultForBluetoothEnabled(CallbackContext callbackContext) {
        if (!SystemRequirementsHelper.isBluetoothLeAvailable(mApplicationContext)) {
            callbackContext.success(-1);
        } else if (SystemRequirementsHelper.isBluetoothEnabled(mApplicationContext)) {
            callbackContext.success(1);
        } else {
            callbackContext.success(0);
        }
    }

    /**
     * Called when the Bluetooth dialog is closed.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        LogUtils.i(LOGTAG, "onActivityResult");
        if (REQUEST_ENABLE_BLUETOOTH == requestCode) {
            sendResultForBluetoothEnabled(mBluetoothStateCallbackContext);
            mBluetoothStateCallbackContext = null;
        }
    }

    /**
     * Start scanning for beacons. (Newer method than ranging)
     */
    private void startScanningDevices(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "startScanningDevices");

        JSONObject json;
        try {
            json = cordovaArgs.getJSONObject(0);
        } catch (JSONException e) {
            json = new JSONObject();
        }

        // Stop scanning before starting again
        if (mIsScanning)
            mDeviceScanner.stopScanning();

        configureDeviceScanner(json);

        mScanningCallbackContext = callbackContext;

        // Start scanning
        mDeviceScanner.scanForDevices(new PluginScanningListener());
        mIsScanning = true;
    }

    /**
     * Configure Device Scanner object
     */

    private void configureDeviceScanner(JSONObject json) {
        // Set if the scan should only show authorized beacons
        mDeviceScanner.setOwnDevicesFiltering(json.optBoolean("filterOwn", true));

        // Set scan frequency in milliseconds
        mDeviceScanner.setScanPeriodMillis(json.optLong("scanPeriod", 2000L));

        // Set allowed DeviceTypes
        List<DeviceType> allowedDeviceTypes = new ArrayList<DeviceType>();
        JSONArray providedDeviceTypes = json.optJSONArray("deviceTypes");
        if (null == providedDeviceTypes) {
            allowedDeviceTypes = Arrays.asList(DeviceType.values());
        } else {
            String deviceTypeName;
            for (int i = 0; i < providedDeviceTypes.length(); i++) {
                deviceTypeName = providedDeviceTypes.optString(i);
                if (null == deviceTypeName)
                    return;
                if (deviceTypeName.equalsIgnoreCase("LOCATION_BEACON"))
                    allowedDeviceTypes.add(DeviceType.LOCATION_BEACON);
                else if (deviceTypeName.equalsIgnoreCase("PROXIMITY_BEACON"))
                    allowedDeviceTypes.add(DeviceType.PROXIMITY_BEACON);
                else if (deviceTypeName.equalsIgnoreCase("NEARABLE"))
                    allowedDeviceTypes.add(DeviceType.NEARABLE);
            }
        }
        mDeviceScanner.setDeviceTypes(allowedDeviceTypes);
    }

    /**
     * Stop scanning for beacons. (Newer method than ranging)
     */
    private void stopScanningDevices(
            final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "stopScanningDevices");


        // Send empty payload
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        mScanningCallbackContext.sendPluginResult(result);

        mScanningCallbackContext = null;

        if (mIsScanning) {
            // Stop scanning.
            mDeviceScanner.stopScanning();
            mIsScanning = false;

            // Send back success.
            callbackContext.success();
            LogUtils.i(LOGTAG, "Scanning stopped");
        } else {
            callbackContext.error("Not scanning.");
        }
    }

    /**
     * Start ranging for beacons.
     */
    private void startRangingBeaconsInRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "startRangingBeaconsInRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        final Region region = createRegion(json);

        String key = regionHashMapKey(region);
        if (null != mRangingCallbackContexts.get(key)) {
            return;
        }

        // Add callback to hash map.
        mRangingCallbackContexts.put(key, callbackContext);

        // Create ranging listener.
        mBeaconManager.setRangingListener(new PluginRangingListener());


        EstimoteSDK.enableRangingAnalytics(json.optBoolean("analytics", false));
        LogUtils.i(LOGTAG, "Ranging Analytics = " + (EstimoteSDK.isRangingAnalyticsEnabled()));

        // If connected start ranging immediately, otherwise first connect.
        if (mIsConnected) {
            startRanging(region);
        } else {
            LogUtils.i(LOGTAG, "connect");
            mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    LogUtils.i(LOGTAG, "onServiceReady");
                    mIsConnected = true;
                    startRanging(region);
                }
            });
        }
    }

    /**
     * Helper method.
     */
    private void startRanging(Region region) {
        LogUtils.i(LOGTAG, "startRanging");
        mBeaconManager.startRanging(region);
    }

    /**
     * Stop ranging for beacons.
     */
    private void stopRangingBeaconsInRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "stopRangingBeaconsInRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        Region region = createRegion(json);

        // If ranging callback does not exist call error callback
        String key = regionHashMapKey(region);
        CallbackContext rangingCallback = mRangingCallbackContexts.get(key);
        if (null == rangingCallback) {
            callbackContext.error("Region not ranged");
            return;
        }

        // Remove ranging callback from hash map.
        mRangingCallbackContexts.remove(key);

        // Clear ranging callback on JavaScript side.
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        rangingCallback.sendPluginResult(result);

        // Stop ranging if connected.
        if (mIsConnected) {
            LogUtils.i(LOGTAG, "stopRanging");

            // Stop ranging.
            mBeaconManager.stopRanging(region);

            // Send back success.
            callbackContext.success();
        } else {
            callbackContext.error("Not connected");
        }
    }

    /**
     * Start monitoring for region.
     */
    private void startMonitoringForRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "startMonitoringForRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        final Region region = createRegion(json);

        EstimoteSDK.enableMonitoringAnalytics(json.optBoolean("analytics", false));
        LogUtils.i(LOGTAG, "Monitoring Analytics = " + (EstimoteSDK.isMonitoringAnalyticsEnabled()));

        String key = regionHashMapKey(region);
        if (null != mMonitoringCallbackContexts.get(key)) {
            LogUtils.i(LOGTAG, "Monitor already active for this region. Re-registering");
            // Remove monitoring callback from hash map.
            mMonitoringCallbackContexts.remove(key);
        }

        // Add callback to hash map.
        mMonitoringCallbackContexts.put(key, callbackContext);

        // Create monitoring listener.
        mBeaconManager.setMonitoringListener(new PluginMonitoringListener());

        // If connected start monitoring immediately, otherwise first connect.
        if (mIsConnected) {
            startMonitoring(region);
        } else {
            LogUtils.i(LOGTAG, "connect");
            mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    LogUtils.i(LOGTAG, "onServiceReady");
                    mIsConnected = true;
                    startMonitoring(region);
                }
            });
        }
    }

    /**
     * Helper method.
     */
    private void startMonitoring(Region region) {
        LogUtils.i(LOGTAG, "startMonitoring");
        mBeaconManager.startMonitoring(region);
    }

    /**
     * Stop monitoring for region.
     */
    private void stopMonitoringForRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "stopMonitoringForRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        Region region = createRegion(json);

        // If monitoring callback does not exist call error callback
        String key = regionHashMapKey(region);
        CallbackContext monitoringCallback = mMonitoringCallbackContexts.get(key);
        if (null == monitoringCallback) {
            callbackContext.error("Region not monitored");
            return;
        }

        // Remove monitoring callback from hash map.
        mMonitoringCallbackContexts.remove(key);

        // Clear monitoring callback on JavaScript side.
        PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
        result.setKeepCallback(false);
        monitoringCallback.sendPluginResult(result);

        // Stop monitoring if connected.
        if (mIsConnected) {
            LogUtils.i(LOGTAG, "stopMonitoring");

            // Stop monitoring.
            mBeaconManager.stopMonitoring(region);

            // Send back success.
            callbackContext.success();
        } else {
            callbackContext.error("Not connected");
        }
    }

    /**
     * Toggle verbose mode
     */
    private void toggleVerbose(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "toggleVerbose");

        Boolean verboseMode;
        try {
            verboseMode = cordovaArgs.getBoolean(0);
        } catch (JSONException e) {
            verboseMode = (VERBOSE_MODE == 0);
        }

        VERBOSE_MODE = verboseMode ? 1 : 0;

        EstimoteSDK.enableDebugLogging(verboseMode);

        Log.i(LOGTAG, "VERBOSE_MODE = " + (VERBOSE_MODE));
        if (null != callbackContext) {
            PluginResult r = new PluginResult(PluginResult.Status.OK, verboseMode);
            callbackContext.sendPluginResult(r);
        }
    }

    /**
     * Authenticate with Estimote Cloud
     */
    private void setupAppIDAndAppToken(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "setupAppIDAndAppToken");

        if (mEstimoteSDK == null) {
            mEstimoteSDK = new EstimoteSDK();

            String appID = cordovaArgs.getString(0);
            String appToken = cordovaArgs.getString(1);
            EstimoteSDK.initialize(mApplicationContext, appID, appToken);

            PluginResult r = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(r);
        } else {
            callbackContext.error("Already authenticated to Estimote Cloud");
        }
    }

    /**
     * Find device in scannedDevices, with MAC address
     */
    private ConfigurableDevicesScanner.ScanResultItem findDevice(String macAddress) {
        LogUtils.i(LOGTAG, "findDevice(String)");
        MacAddress mac;
        try {
            mac = MacAddress.fromString(macAddress);
        } catch (NullPointerException e) {
            return null;
        }

        for (ConfigurableDevicesScanner.ScanResultItem d : mScannedDevices) {
            if (d.device.macAddress.equals(mac)) {
                return d;
            }
        }

        return null;
    }

    /**
     * Find beacon in rangedBeacons, with MAC address
     */
    private Beacon findBeacon(String macAddress) {
        LogUtils.i(LOGTAG, "findBeacon(String)");
        MacAddress mac;
        try {
            mac = MacAddress.fromString(macAddress);
        } catch (NullPointerException e) {
            return null;
        }

        for (Beacon beacon : mRangedBeacons) {
            if (beacon.getMacAddress().equals(mac)) {
                return beacon;
            }
        }

        return null;
    }

    /**
     * Find beacon in rangedBeacons, with region params
     */
    private Beacon findBeacon(String proximityUUID, int major, int minor) {
        LogUtils.i(LOGTAG, "findBeacon(String, int, int)");
        for (Beacon beacon : mRangedBeacons) {
            if (beacon.getProximityUUID().toString().equals(proximityUUID) &&
                    beacon.getMajor() == major &&
                    beacon.getMinor() == minor) {
                return beacon;
            }
        }

        return null;
    }

    /**
     * Find beacon in rangedBeacons, from JSON
     */
    private Beacon findBeacon(JSONObject json) {
        String macAddress = json.optString("macAddress", "");

        if (!macAddress.equals("")) {
            return findBeacon(macAddress);
        } else {
            String proximityUUID = json.optString("proximityUUID", "");
            int major = json.optInt("major", -1);
            int minor = json.optInt("minor", -1);

            if (!proximityUUID.equals("") && major > -1 && minor > -1) {
                return findBeacon(proximityUUID, major, minor);
            }
        }

        return null;
    }

    /**
     * Connect to device
     */
    private void connectToDevice(ConfigurableDevice device) {
        mConnectedDevice = mDeviceConnectionProvider.getConnection(device);
        mConnectedDevice.connect(new DeviceConnectionCallback() {
            @Override
            public void onConnected() {
                LogUtils.i(LOGTAG, "onConnected: " + mConnectedDevice.settings.beacon.proximityUUID().toString());
                fetchSettings();
            }

            @Override
            public void onDisconnected() {
                LogUtils.i(LOGTAG, "onDisconnected");
            }

            @Override
            public void onConnectionFailed(DeviceConnectionException e) {
                String msg = (e.getMessage() == null) ? "There was an error connecting to this device." : (e.getMessage());
                LogUtils.i(LOGTAG, "onConnectionFailed: " + msg);
                if (null != mDeviceConnectionCallbackContext) {
                    mDeviceConnectionCallbackContext.error(msg);
                }
            }
        });
    }

    /**
     * Connect to device
     */
    private void connectToDevice(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "connectToDevice");

        JSONObject json;
        try {
            json = cordovaArgs.getJSONObject(0);
        } catch (JSONException e) {
            json = new JSONObject();
        }

        if (null == mDeviceConnectionProvider) {
            mDeviceConnectionProvider = new DeviceConnectionProvider(mApplicationContext);
        }

        final ConfigurableDevicesScanner.ScanResultItem d = findDevice(json.optString("macAddress"));

        if (null == d) {
            callbackContext.error("Could not find device");
            return;
        }

        if (mConnectedDevice != null) {
            disconnectConnectedDevice();
        }

        mDeviceConnectionCallbackContext = callbackContext;

        if (mDeviceConnectionProviderIsConnected) {
            connectToDevice(d.device);
        } else {
            mDeviceConnectionProvider.connectToService(new DeviceConnectionProvider.ConnectionProviderCallback() {
                @Override
                public void onConnectedToService() {
                    mDeviceConnectionProviderIsConnected = true;
                    connectToDevice(d.device);
                }
            });
        }
    }

    /**
     * Disconnect connected device
     */
    private void disconnectConnectedDevice() {
        LogUtils.i(LOGTAG, "disconnectConnectedDevice");

        if (mConnectedDevice != null && mConnectedDevice.isConnected()) {
            mConnectedDevice.close();
            mConnectedDevice = null;
        }
    }

    /**
     * Disconnect connected device, c/o Cordova
     */
    private void disconnectConnectedDevice(final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "disconnectConnectedDevice (cordova)");

        mBeaconDisconnectionCallbackContext = callbackContext;
        disconnectConnectedDevice();
    }

    /**
     * Connect to beacon
     */
    private void connectToBeacon(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "connectToBeacon");

        JSONObject json = cordovaArgs.getJSONObject(0);

        Beacon beacon = findBeacon(json);
        if (beacon == null) {
            callbackContext.error("could not find beacon");
            return;
        }

        // beacons are jealous creatures and don't like competition
        if (mConnectedBeacon != null &&
                !mConnectedBeacon.getBeacon().getMacAddress().equals(beacon.getMacAddress())) {
            disconnectConnectedBeacon();
        }

        mBeaconConnectionCallbackContext = callbackContext;

        // Create error listener.
        mBeaconManager.setErrorListener(new PluginErrorListener(callbackContext));

        mConnectedBeacon = new BeaconConnected(
                mApplicationContext,
                beacon,
                new PluginConnectingListener()
        );

        mConnectedBeacon.authenticate();
    }

    /**
     * Disconnect connected beacon
     */
    private void disconnectConnectedBeacon() {
        LogUtils.i(LOGTAG, "disconnectConnectedBeacon");

        if (mConnectedBeacon != null && mConnectedBeacon.isConnected()) {
            mConnectedBeacon.close();
            mConnectedBeacon = null;
        }
    }

    /**
     * Disconnect connected beacon, c/o Cordova
     */
    private void disconnectConnectedBeacon(final CallbackContext callbackContext) {
        LogUtils.i(LOGTAG, "disconnectConnectedBeacon (cordova)");

        mBeaconDisconnectionCallbackContext = callbackContext;
        disconnectConnectedBeacon();
    }

    /**
     * Write Proximity UUID to connected beacon
     */
    private void writeConnectedProximityUUID(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "writeConnectedProximityUUID");

        if (mConnectedBeacon != null && mConnectedBeacon.isConnected()) {
            String uuid = cordovaArgs.getString(0);
            UUID _uuid = null;

            LogUtils.i(LOGTAG, uuid);
            LogUtils.i(LOGTAG, mConnectedBeacon.getBeacon().getProximityUUID().toString());
            LogUtils.i(LOGTAG, String.valueOf(uuid.equals(mConnectedBeacon.getBeacon().getProximityUUID().toString())));

            // already correct, skip
            if (uuid.equals(mConnectedBeacon.getBeacon().getProximityUUID().toString())) {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(r);
            }

            try {
                _uuid = UUID.fromString(uuid);
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }


            BeaconConnection.WriteCallback writeCallback;
            writeCallback = new BeaconConnection.WriteCallback() {
                @Override
                public void onSuccess() {
                    PluginResult r = new PluginResult(PluginResult.Status.OK);
                    callbackContext.sendPluginResult(r);
                }

                @Override
                public void onError(EstimoteDeviceException e) {
                    callbackContext.error(e.getMessage());
                }
            };

            mConnectedBeacon.edit()
                    .set(mConnectedBeacon.proximityUuid(), _uuid)
//					.set(mConnectedBeacon.major(), newMajor)
//					.set(mConnectedBeacon.minor(), newMinor)
                    .commit(writeCallback);

//            mConnectedBeacon.writeProximityUuid(uuid, writeCallback);
        }
    }

    /**
     * Write Major to connected beacon
     */
    private void writeConnectedMajor(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "writeConnectedMajor");

        if (mConnectedBeacon != null && mConnectedBeacon.isConnected()) {
            int major = cordovaArgs.getInt(0);

            // already correct, skip
            if (major == mConnectedBeacon.getBeacon().getMajor()) {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(r);
            }

            if (major == 0) {
                callbackContext.error("major cannot be 0");
                return;
            }

            BeaconConnection.WriteCallback writeCallback;
            writeCallback = new BeaconConnection.WriteCallback() {
                @Override
                public void onSuccess() {
                    PluginResult r = new PluginResult(PluginResult.Status.OK);
                    callbackContext.sendPluginResult(r);
                }

                @Override
                public void onError(EstimoteDeviceException e) {
                    callbackContext.error(e.getMessage());
                }
            };

            mConnectedBeacon.edit()
//					.set(mConnectedBeacon.proximityUuid(), _uuid)
                    .set(mConnectedBeacon.major(), major)
//					.set(mConnectedBeacon.minor(), newMinor)
                    .commit(writeCallback);

//            mConnectedBeacon.writeMajor(major, writeCallback);
        }
    }

    /**
     * Write Minor to connected beacon
     */
    private void writeConnectedMinor(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException {
        LogUtils.i(LOGTAG, "writeConnectedMinor");

        if (mConnectedBeacon != null && mConnectedBeacon.isConnected()) {
            int minor = cordovaArgs.getInt(0);

            // already correct, skip
            if (minor == mConnectedBeacon.getBeacon().getMinor()) {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                callbackContext.sendPluginResult(r);
            }

            if (minor == 0) {
                callbackContext.error("minor cannot be 0");
                return;
            }

            BeaconConnection.WriteCallback writeCallback;
            writeCallback = new BeaconConnection.WriteCallback() {
                @Override
                public void onSuccess() {
                    PluginResult r = new PluginResult(PluginResult.Status.OK);
                    callbackContext.sendPluginResult(r);
                }

                @Override
                public void onError(EstimoteDeviceException e) {
                    callbackContext.error(e.getMessage());
                }
            };

            mConnectedBeacon.edit()
//					.set(mConnectedBeacon.proximityUuid(), _uuid)
//					.set(mConnectedBeacon.major(), newMajor)
                    .set(mConnectedBeacon.minor(), minor)
                    .commit(writeCallback);

//            mConnectedBeacon.writeMinor(minor, writeCallback);
        }
    }

    /**
     * Create JSON object representing beacon info.
     * <p/>
     * beaconInfo format:
     * {
     * region: region,
     * beacons: array of beacon
     * }
     */
    private JSONObject makeJSONBeaconInfo(Region region, List<Beacon> beacons)
            throws JSONException {
        // Create JSON object.
        JSONObject json = new JSONObject();
        json.put("region", makeJSONRegion(region));
        json.put("beacons", makeJSONBeaconArray(beacons));
        return json;
    }

    /**
     * Error handler for Settings.
     */
    private void onSettingsFailure(Exception e) {
        if (null != mDeviceConnectionCallbackContext)
            mDeviceConnectionCallbackContext.error(e.getMessage() == null ? "There was an issue fetching settings for the connected device." : e.getMessage());
        mConnectedDeviceHashMap.clear();
        disconnectConnectedDevice();
        mIsFetchingSettings = false;
    }

    /**
     * Fetch RSSI then the rest of the settings.
     */
    private void fetchSettings() {
        if (mIsFetchingSettings) {
            if (null != mDeviceConnectionCallbackContext)
                mDeviceConnectionCallbackContext.error("Already fetching settings for the connected device.");
            return;
        }
        if (null == mConnectedDevice) {
            onSettingsFailure(new Exception("There is no device connected."));
            return;
        }

        if (null == mConnectedDeviceHashMap) {
            mConnectedDeviceHashMap = new LinkedHashMap();
        }
        mConnectedDeviceHashMap.clear();

        // Get the RSSI, then the rest of the settings
        mConnectedDevice.readRssi(new SettingCallback<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                // Store the RSSI value
                mConnectedDeviceHashMap.put("rssi", integer);

                // Fetch the rest of the settings
                fetchSettings(new SettingCallback<Map<Object, Object>>() {
                    @Override
                    public void onSuccess(Map<Object, Object> objectObjectMap) {
                        // Add the new settings to the hashmap
                        mConnectedDeviceHashMap.putAll((LinkedHashMap) objectObjectMap);

                        // Create JSON beacon info object.
                        JSONObject json;
                        try {
                            json = makeJSONConnectedDevice();
                        } catch (Exception e) {
                            onSettingsFailure(e);
                            return;
                        }

                        // Send result to JavaScript.
                        PluginResult r = new PluginResult(PluginResult.Status.OK, json);
                        r.setKeepCallback(true);
                        mScanningCallbackContext.sendPluginResult(r);
                    }

                    @Override
                    public void onFailure(DeviceConnectionException e) {
                        onSettingsFailure(e);
                    }
                });
            }

            @Override
            public void onFailure(DeviceConnectionException e) {
                onSettingsFailure(e);
            }
        });
    }

    /**
     * Fetch LinkedHashMap of settings for the connected device.
     */
    private void fetchSettings(final SettingCallback<Map<Object, Object>> callback) {
        if (null == mConnectedDevice) {
            onSettingsFailure(new Exception("There is no device connected."));
            return;
        }
        Settings settings = mConnectedDevice.settings;

        // TODO: Make this a dynamic config from JS plugin
        SettingsReader reader = new SettingsReader(mConnectedDevice);
        reader.get("beacon.enabled", settings.beacon.enable());
        reader.get("beacon.proximityUUID", settings.beacon.proximityUUID());
        reader.get("beacon.major", settings.beacon.major());
        reader.get("beacon.minor", settings.beacon.minor());
        reader.get("beacon.transmitPower", settings.beacon.transmitPower());
        reader.get("beacon.advertisingInterval", settings.beacon.advertisingInterval());
        reader.get("indoorLocation.name", settings.deviceInfo.indoorLocation.name());
        reader.get("uptime", settings.other.uptime());
        reader.get("power.batteryVoltage", settings.power.batteryVoltage());
        reader.get("power.batteryPercentage", settings.power.batteryPercentage());
        reader.get("power.estimatedBatteryLifetime", settings.power.estimatedBatteryLifetime());
        reader.get("nfc.data", settings.nfc.data());
        reader.get("sensors.ambientLight", settings.sensors.light.ambientLight());
        reader.get("sensors.temperature", settings.sensors.temperature.temperature());
        reader.get("location.advertisingInterval", settings.estimote.location.advertisingInterval());
        reader.get("location.transmitPower", settings.estimote.location.transmitPower());
        reader.get("telemetry.advertisingInterval", settings.estimote.telemetry.advertisingInterval());
        reader.get("telemetry.transmitPower", settings.estimote.telemetry.transmitPower());
        reader.get("deviceInfo.bootloader", settings.deviceInfo.bootloader());
        reader.get("deviceInfo.firmware", settings.deviceInfo.firmware());
        reader.get("deviceInfo.formFactor", settings.deviceInfo.formFactor());
//        reader.get("deviceInfo.geoLocation", settings.deviceInfo.geoLocation());
        reader.get("deviceInfo.hardware", settings.deviceInfo.hardware());
        reader.get("deviceInfo.name", settings.deviceInfo.name());
        reader.get("deviceInfo.tags", settings.deviceInfo.tags());
        reader.read(callback);
    }

    /**
     * Create JSON object representing the connected device.
     */
    private JSONObject makeJSONConnectedDevice()
            throws Exception {
        if (null == mConnectedDevice) {
            throw new Exception("There is no device connected.");
        }

        return new JSONObject(mConnectedDeviceHashMap);
    }

    /**
     * Create JSON array representing a beacon list.
     */
    private JSONArray makeJSONBeaconArray(List<Beacon> beacons)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (Beacon b : beacons) {
            // Compute proximity value.
            Utils.Proximity proximityValue = Utils.computeProximity(b);
            int proximity = 0; // Unknown.
            if (Utils.Proximity.IMMEDIATE == proximityValue) {
                proximity = 1;
            } else if (Utils.Proximity.NEAR == proximityValue) {
                proximity = 2;
            } else if (Utils.Proximity.FAR == proximityValue) {
                proximity = 3;
            }

            // Compute distance value.
            double distance = Utils.computeAccuracy(b);

            // Normalize UUID.
            String uuid = Utils.normalizeProximityUUID(b.getProximityUUID().toString());

            // Construct JSON object for beacon.
            JSONObject json = new JSONObject();
            json.put("major", b.getMajor());
            json.put("minor", b.getMinor());
            json.put("rssi", b.getRssi());
            json.put("measuredPower", b.getMeasuredPower());
            json.put("proximityUUID", uuid);
            json.put("proximity", proximity);
            json.put("distance", distance);
//			json.put("name", b.getName());
            json.put("macAddress", b.getMacAddress());
            jsonArray.put(json);
        }
        return jsonArray;
    }

    /**
     * Create JSON array representing a device list.
     */
    private JSONArray makeJSONDeviceArray(List<ConfigurableDevicesScanner.ScanResultItem> devices)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();
        ConfigurableDevice b;
        for (ConfigurableDevicesScanner.ScanResultItem device : devices) {
            b = device.device;
            if (null == b) continue;

            // Compute proximity value.
            Utils.Proximity proximityValue = Utils.computeProximity(device);
            int proximity = 0; // Unknown.
            if (Utils.Proximity.IMMEDIATE == proximityValue) {
                proximity = 1;
            } else if (Utils.Proximity.NEAR == proximityValue) {
                proximity = 2;
            } else if (Utils.Proximity.FAR == proximityValue) {
                proximity = 3;
            }

            // Compute distance value.
            double distance = Utils.computeAccuracy(device);

            // Normalize UUID.
//            String uuid = Utils.normalizeProximityUUID(b.deviceId.toString());

            // Construct JSON object for beacon.
            JSONObject json = new JSONObject();
//            json.put("major", b.getMajor());
//            json.put("minor", b.getMinor());
//            json.put("rssi", b.getRssi());
//            json.put("measuredPower", b.getMeasuredPower());
//            json.put("proximityUUID", uuid);
            json.put("deviceId", b.deviceId.toHexString());
            json.put("proximity", proximity);
            json.put("distance", distance);
            json.put("type", b.type.toString());
            json.put("macAddress", b.macAddress);
            jsonArray.put(json);
        }
        return jsonArray;
    }

    private String regionHashMapKey(String uuid, Integer major, Integer minor) {
        if (uuid == null) {
            uuid = "0";
        }

        if (major == null) {
            major = 0;
        }

        if (minor == null) {
            minor = 0;
        }

        // use % for easier decomposition
        return uuid + "%" + major + "%" + minor;
    }

    private String regionHashMapKey(Region region) {
        String uuid = (region.getProximityUUID() == null) ? null : region.getProximityUUID().toString();
        Integer major = region.getMajor();
        Integer minor = region.getMinor();

        return regionHashMapKey(uuid, major, minor);
    }

    /**
     * Create a Region object from Cordova arguments.
     */
    private Region createRegion(JSONObject json) throws IllegalArgumentException, NullPointerException {
        // null ranges all regions, if unset
        String uuid = json.optString("uuid", null);
        Integer major = optUInt16Null(json, "major");
        Integer minor = optUInt16Null(json, "minor");

        String identifier = json.optString(
                "identifier",
                regionHashMapKey(uuid, major, minor)
        );

        UUID _uuid;

        try {
            _uuid = UUID.fromString(uuid);
        } catch (NullPointerException e) {
            _uuid = null;
        } catch (IllegalArgumentException e) {
            _uuid = null;
        }

        return new Region(identifier, _uuid, major, minor);
    }

    /**
     * Returns the value mapped by name if it exists and is a positive integer
     * no larger than 0xFFFF.
     * Returns null otherwise.
     */
    private Integer optUInt16Null(JSONObject json, String name) {
        int i = json.optInt(name, -1);
        if (i < 0 || i > (0xFFFF)) {
            return null;
        } else {
            return i;
        }
    }

    /**
     * Listener for scanning events.
     */
    private class PluginScanningListener implements ConfigurableDevicesScanner.ScannerCallback {
        @Override
        public void onDevicesFound(List<ConfigurableDevicesScanner.ScanResultItem> devices) {
            LogUtils.i(LOGTAG, "onDevicesFound");

            if (null == mScanningCallbackContext) {
                LogUtils.e(LOGTAG, "onDevicesFound no callback found");
                return;
            }

            try {
                mScannedDevices.clear();
                mScannedDevices.addAll(devices);

                // Create JSON beacon info object.
                JSONArray json = makeJSONDeviceArray(devices);

                // Send result to JavaScript.
                PluginResult r = new PluginResult(PluginResult.Status.OK, json);
                r.setKeepCallback(true);
                mScanningCallbackContext.sendPluginResult(r);
            } catch (JSONException e) {
                LogUtils.e(LOGTAG, "onDevicesFound error:", e);
            }
        }
    }

    /**
     * Listener for ranging events.
     */
    private class PluginRangingListener implements BeaconManager.RangingListener {
        @Override
        public void onBeaconsDiscovered(Region region, List<Beacon> beacons) {
            // Note that results are not delivered on UI thread.

            LogUtils.i(LOGTAG, "onBeaconsDiscovered");

            try {
                // store in plugin
                mRangedBeacons.clear();
                mRangedBeacons.addAll(beacons);

                // Find region callback.
                String key = regionHashMapKey(region);
                CallbackContext rangingCallback = mRangingCallbackContexts.get(key);
                if (null == rangingCallback) {
                    // No callback found.
                    LogUtils.e(LOGTAG,
                            "onBeaconsDiscovered no callback found for key: " + key);
                    return;
                }

                // Create JSON beacon info object.
                JSONObject json = makeJSONBeaconInfo(region, beacons);

                // Send result to JavaScript.
                PluginResult r = new PluginResult(PluginResult.Status.OK, json);
                r.setKeepCallback(true);
                rangingCallback.sendPluginResult(r);
            } catch (JSONException e) {
                LogUtils.e(LOGTAG, "onBeaconsDiscovered error:", e);
            }
        }
    }

    /**
     * Listener for error events.
     */
    class PluginErrorListener implements BeaconManager.ErrorListener {
        final CallbackContext mErrorCallbackContext;

        public PluginErrorListener(CallbackContext _callbackContext) {
            super();
            mErrorCallbackContext = _callbackContext;
        }

        @Override
        public void onError(Integer eInt) {
            LogUtils.i(LOGTAG, "onError: " + (eInt));
            if (null != mErrorCallbackContext) {
                mErrorCallbackContext.error(eInt);
            }
        }
    }

    /**
     * Listener for monitoring events.
     */
    class PluginMonitoringListener implements BeaconManager.MonitoringListener {
        @Override
        public void onEnteredRegion(Region region, List<Beacon> beacons) {
            // Note that results are not delivered on UI thread.

            LogUtils.i(LOGTAG, "onEnteredRegion");

            sendRegionInfo(region, "inside");
        }

        @Override
        public void onExitedRegion(Region region) {
            // Note that results are not delivered on UI thread.
            LogUtils.i(LOGTAG, "onExitedRegion");

            sendRegionInfo(region, "outside");
        }

        private void sendRegionInfo(Region region, String state) {
            try {
                // Find region callback.
                String key = regionHashMapKey(region);
                CallbackContext monitoringCallback = mMonitoringCallbackContexts.get(key);
                if (null == monitoringCallback) {
                    // No callback found.
                    LogUtils.e(LOGTAG, "sendRegionInfo no callback found for key: " + key);
                    return;
                }

                // Create JSON region info object with the given state.
                JSONObject json = makeJSONRegion(region, state);

                // Send result to JavaScript.
                PluginResult r = new PluginResult(PluginResult.Status.OK, json);
                r.setKeepCallback(true);
                monitoringCallback.sendPluginResult(r);
            } catch (JSONException e) {
                LogUtils.e(LOGTAG, "sendRegionInfo error:", e);
            }
        }
    }

    /**
     * Listener for beacon connection events
     */
    private class PluginConnectingListener implements BeaconConnection.ConnectionCallback {
        @Override
        public void onAuthorized(BeaconInfo beaconInfo) {
            LogUtils.i(this.toString(), "onAuthorized");

        }

        @Override
        public void onConnected(BeaconInfo beaconInfo) {
            LogUtils.i(this.toString(), "onConnected");
            CallbackContext callback = mBeaconConnectionCallbackContext;

            if (callback == null) {
                return;
            }

            try {
                JSONObject json = new JSONObject();

                // add beaconInfo
                json.put(
                        "batteryLifeExpectancyInDays",
                        beaconInfo.batteryLifeExpectancyInDays
                );
                json.put("color", beaconInfo.color.toString());
                json.put("macAddress", beaconInfo.macAddress);
                json.put("major", beaconInfo.major);
                json.put("minor", beaconInfo.minor);
                json.put("name", beaconInfo.name);
                json.put("uuid", beaconInfo.uuid);

                LogUtils.i(LOGTAG, "2");
                // add beaconInfo.settings
                BeaconInfoSettings settings = beaconInfo.settings;
                JSONObject jsonSettings = new JSONObject();
                jsonSettings.put(
                        "advertisingIntervalMillis",
                        settings.advertisingIntervalMillis
                );
                jsonSettings.put("batteryLevel", settings.batteryLevel);
                jsonSettings.put(
                        "broadcastingPower",
                        settings.broadcastingPower
                );
                jsonSettings.put("firmware", settings.firmware);
                jsonSettings.put("hardware", settings.hardware);

                LogUtils.i(LOGTAG, "3");
                // finish up response param
                json.put("settings", jsonSettings);

                LogUtils.i(LOGTAG, "4");
                LogUtils.i(LOGTAG, json.toString());
                // pass back to web
                PluginResult r = new PluginResult(PluginResult.Status.OK, json);
                callback.sendPluginResult(r);
            } catch (JSONException e) {
                LogUtils.i(LOGTAG, "inError");
                String msg;
                msg = "connection succeeded, could not marshall object: ";
                msg = msg.concat(e.getMessage());

                callback.error(msg);
            }

            // cleanup
            mBeaconConnectionCallbackContext = null;
        }

        @Override
        public void onAuthenticationError(EstimoteDeviceException e) {
            LogUtils.i(this.toString(), "onAuthenticationError");
            CallbackContext callback = mBeaconConnectionCallbackContext;

            if (callback == null) {
                return;
            }

            // pass back to js
            callback.error(e.getMessage());

            LogUtils.e(this.toString(), "" + e.errorCode);

            // print stacktrace to android logs
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            LogUtils.e(LOGTAG, sw.toString());

            // cleanup
            mBeaconConnectionCallbackContext = null;
        }

        @Override
        public void onDisconnected() {
            LogUtils.i(this.toString(), "onDisconnected");
            CallbackContext callback = mBeaconDisconnectionCallbackContext;

            if (callback == null) {
                return;
            }

            PluginResult r = new PluginResult(PluginResult.Status.OK);
            callback.sendPluginResult(r);

            // cleanup
            mBeaconDisconnectionCallbackContext = null;
        }
    }

    @SuppressWarnings("deprecation")
    public class BeaconConnected extends BeaconConnection {
        final private Beacon mBeacon;

        public BeaconConnected(
                Context context,
                Beacon beacon,
                BeaconConnection.ConnectionCallback connectionCallback
        ) {
            super(context, beacon, connectionCallback);
            this.mBeacon = beacon;
        }

        public Beacon getBeacon() {
            return mBeacon;
        }
    }
}
