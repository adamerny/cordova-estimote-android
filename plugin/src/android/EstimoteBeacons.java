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
import com.estimote.sdk.connection.scanner.ConfigurableDevice;
import com.estimote.sdk.connection.scanner.ConfigurableDevicesScanner;
import com.estimote.sdk.connection.scanner.DeviceType;
import com.estimote.sdk.exception.EstimoteDeviceException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Plugin class for the Estimote Beacon plugin.
 */
public class EstimoteBeacons extends CordovaPlugin
{
    private static final String LOGTAG = "EstimoteBeacons";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static int VERBOSE_MODE = 1;

    private BeaconManager     mBeaconManager;
    private EstimoteSDK       mEstimoteSDK;
    private CordovaInterface  mCordovaInterface;

    private ConfigurableDevicesScanner mDeviceScanner;
    private List<ConfigurableDevicesScanner.ScanResultItem> mScannedDevices;
//    private DeviceConnected  mConnectedDevice;

    private ArrayList<Beacon> mRangedBeacons;
    private BeaconConnected   mConnectedBeacon;
    private boolean           mIsConnected = false;


    // Maps and variables that keep track of Cordova callbacks.
    private HashMap<String, CallbackContext> mRangingCallbackContexts =
            new HashMap<String, CallbackContext>();
    private HashMap<String, CallbackContext> mMonitoringCallbackContexts =
            new HashMap<String, CallbackContext>();

    private CallbackContext   mBluetoothStateCallbackContext;
    private CallbackContext   mBeaconConnectionCallback;
    private CallbackContext   mBeaconDisconnectionCallback;

    private CallbackContext   mScanningCallbackContext;

    private Context mApplicationContext;

    // todo: consider using pluginInitialize instead, per Cordova recommendation
    //   https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java#L60-L61
    /**
     * Plugin initialiser.
     */
    @Override
    public void initialize(final CordovaInterface cordova, CordovaWebView webView)
    {
        LogUtils.i(LOGTAG, "initialize");

        super.initialize(cordova, webView);

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
      */
    public void onDestroy() {
        LogUtils.i(LOGTAG, "onDestroy");
        disconnectConnectedBeacon();
        disconnectBeaconManager();
    }

    /**
     * Disconnect from the beacon manager.
      */
    private void disconnectBeaconManager() {
        if (mBeaconManager != null && mIsConnected) {
            mBeaconManager.disconnect();
            mIsConnected = false;
        }
    }
    /**
     * Return VERBOSE_MODE
     */
    public static Boolean getVerboseMode() {
        return (VERBOSE_MODE == 1);
    }

    /**
     * Entry point for JavaScript calls.
     */
    @Override
    public boolean execute(
            String action,
            CordovaArgs args,
            final CallbackContext callbackContext)
            throws JSONException
    {
        if ("beacons_startScanningDevices".equals(action)) {
            startScanningDevices(args, callbackContext);
        }
        else if ("beacons_stopScanningDevices".equals(action)) {
            stopScanningDevices(args, callbackContext);
        }
        else if ("beacons_startRangingBeaconsInRegion".equals(action)) {
            startRangingBeaconsInRegion(args, callbackContext);
        }
        else if ("beacons_stopRangingBeaconsInRegion".equals(action)) {
            stopRangingBeaconsInRegion(args, callbackContext);
        }
        else if ("beacons_startMonitoringForRegion".equals(action)) {
            startMonitoringForRegion(args, callbackContext);
        }
        else if ("beacons_stopMonitoringForRegion".equals(action)) {
            stopMonitoringForRegion(args, callbackContext);
        }
        else if ("beacons_setupAppIDAndAppToken".equals(action)) {
            setupAppIDAndAppToken(args, callbackContext);
        }
        else if ("beacons_connectToBeacon".equals(action)) {
            connectToBeacon(args, callbackContext);
        }
        else if ("beacons_disconnectConnectedBeacon".equals(action)) {
            disconnectConnectedBeacon(args, callbackContext);
        }
        else if ("beacons_writeConnectedProximityUUID".equals(action)) {
            writeConnectedProximityUUID(args, callbackContext);
        }
        else if ("beacons_writeConnectedMajor".equals(action)) {
            writeConnectedMajor(args, callbackContext);
        }
        else if ("beacons_writeConnectedMinor".equals(action)) {
            writeConnectedMinor(args, callbackContext);
        }
        else if ("bluetooth_bluetoothState".equals(action)) {
            checkBluetoothState(args, callbackContext);
        }
        else if ("toggleVerbose".equals(action)) {
            toggleVerbose(args, callbackContext);
        }
        else {
            return false;
        }
        return true;
    }

    /**
     * If Bluetooth is off, open a Bluetooth diaLogUtils.
     */
    private void checkBluetoothState(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "checkBluetoothState");

        // Check that no Bluetooth state request is in progress.
        if (null != mBluetoothStateCallbackContext) {
            callbackContext.error("Bluetooth state request already in progress");
            return;
        }

        // Check if Bluetooth is available.
        if (!SystemRequirementsHelper.isBluetoothLeAvailable(mApplicationContext)) {
            sendResultForBluetoothEnabled(callbackContext);
        }
        else if (!SystemRequirementsHelper.isBluetoothEnabled(mApplicationContext)) { // Check if Bluetooth is enabled.
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
        }
        else {
            // Bluetooth is enabled, return the result to JavaScript,
            sendResultForBluetoothEnabled(callbackContext);
        }
    }

    /**
     * Check if Bluetooth is enabled and return result to JavaScript.
     */
    public void sendResultForBluetoothEnabled(CallbackContext callbackContext)
    {
        if (!SystemRequirementsHelper.isBluetoothLeAvailable(mApplicationContext)) {
            callbackContext.success(-1);
        }
        else if (SystemRequirementsHelper.isBluetoothEnabled(mApplicationContext)) {
            callbackContext.success(1);
        }
        else {
            callbackContext.success(0);
        }
    }

    /**
     * Called when the Bluetooth dialog is closed.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
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
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "startScanningDevices");

        JSONObject json;
        try {
            json = cordovaArgs.getJSONObject(0);
        }
        catch (JSONException e) {
            json = new JSONObject();
        }

        if (null == mDeviceScanner) {
            mDeviceScanner = new ConfigurableDevicesScanner(mApplicationContext);
        }

        // Stop scanning before starting again
        // TODO: See if there is a way to check if "isScanning"
        mDeviceScanner.stopScanning();

        configureDeviceScanner(json);

        mScanningCallbackContext = callbackContext;

        // Start scanning
        mDeviceScanner.scanForDevices(new PluginScanningListener());
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
        }
        else {
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
     * Helper method. (Newer method than ranging)
     */
    private void startScanning(Region region, CallbackContext callbackContext)
    {
        LogUtils.i(LOGTAG, "startScanning");
        mBeaconManager.startRanging(region);
    }

    /**
     * Stop scanning for beacons. (Newer method than ranging)
     */
    private void stopScanningDevices(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "stopScanningDevices");

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
        }
        else {
            callbackContext.error("Not connected");
        }
    }

    /**
     * Start ranging for beacons.
     */
    private void startRangingBeaconsInRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "startRangingBeaconsInRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        final Region region = createRegion(json);

        // TODO: How to handle case when region already ranged?
        // Stop ranging then start again?
        // Currently, if ranging callback already exists we
        // do nothing, just return.
        String key = regionHashMapKey(region);
        if (null != mRangingCallbackContexts.get(key)) {
            return;
        }

        // Add callback to hash map.
        mRangingCallbackContexts.put(key, callbackContext);

        // Create ranging listener.
        mBeaconManager.setRangingListener(new PluginRangingListener());


        // TODO: Determine if analytics should be disabled by default
        mEstimoteSDK.enableRangingAnalytics(json.optBoolean("analytics", false));
        LogUtils.i(LOGTAG, "Ranging Analytics = " + (mEstimoteSDK.isRangingAnalyticsEnabled()));

        // If connected start ranging immediately, otherwise first connect.
        if (mIsConnected) {
            startRanging(region, callbackContext);
        }
        else {
            LogUtils.i(LOGTAG, "connect");

            // todo: consider whether this holds up to several startRanging(...)
            //   calls before onServiceReady() fires
            mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    LogUtils.i(LOGTAG, "onServiceReady");
                    mIsConnected = true;
                    startRanging(region, callbackContext);
                }
            });
        }
    }

    /**
     * Helper method.
     */
    private void startRanging(Region region, CallbackContext callbackContext)
    {
        LogUtils.i(LOGTAG, "startRanging");
        mBeaconManager.startRanging(region);
    }

    /**
     * Stop ranging for beacons.
     */
    private void stopRangingBeaconsInRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
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
        }
        else {
            callbackContext.error("Not connected");
        }
    }

    /**
     * Start monitoring for region.
     */
    private void startMonitoringForRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "startMonitoringForRegion");

        JSONObject json = cordovaArgs.getJSONObject(0);

        final Region region = createRegion(json);

        // TODO: Determine if analytics should be disabled by default
        mEstimoteSDK.enableMonitoringAnalytics(json.optBoolean("analytics", false));
        LogUtils.i(LOGTAG, "Monitoring Analytics = " + (mEstimoteSDK.isMonitoringAnalyticsEnabled()));


        // TODO: How to handle case when region already monitored?
        // Stop monitoring then start again?
        // Currently, if monitoring callback already exists we
        // do nothing, just return.
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
            startMonitoring(region, callbackContext);
        }
        else {
            LogUtils.i(LOGTAG, "connect");
            mBeaconManager.connect(new BeaconManager.ServiceReadyCallback() {
                @Override
                public void onServiceReady() {
                    LogUtils.i(LOGTAG, "onServiceReady");
                    mIsConnected = true;
                    startMonitoring(region, callbackContext);
                }
            });
        }
    }

    /**
     * Helper method.
     */
    private void startMonitoring(Region region, CallbackContext callbackContext)
    {
        LogUtils.i(LOGTAG, "startMonitoring");
        mBeaconManager.startMonitoring(region);
    }

    /**
     * Stop monitoring for region.
     */
    private void stopMonitoringForRegion(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
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
        }
        else {
            callbackContext.error("Not connected");
        }
    }

    /**
     * Toggle verbose mode
     */
    private void toggleVerbose(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "toggleVerbose");

        Boolean verboseMode;
        try {
            verboseMode = cordovaArgs.getBoolean(0);
        } catch (JSONException e) {
            verboseMode = (VERBOSE_MODE == 0);
        }

        VERBOSE_MODE = verboseMode ? 1 : 0;

        mEstimoteSDK.enableDebugLogging(verboseMode);

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
            throws JSONException
    {
        LogUtils.i(LOGTAG, "setupAppIDAndAppToken");

        if (mEstimoteSDK == null) {
            mEstimoteSDK = new EstimoteSDK();

            String appID = cordovaArgs.getString(0);
            String appToken = cordovaArgs.getString(1);
            mEstimoteSDK.initialize(mApplicationContext, appID, appToken);

            PluginResult r = new PluginResult(PluginResult.Status.OK);
            callbackContext.sendPluginResult(r);
        } else {
            // todo consider including helpful info e.g. appID
            callbackContext.error("already authenticated to Estimote Cloud");
        }
    }

    /**
     * Find beacon in rangedBeacons, with MAC address
     */
    private Beacon findBeacon(String macAddress) {
        LogUtils.i(LOGTAG, "findBeacon(String)");
        MacAddress mac;
        try {
            mac = MacAddress.fromString(macAddress);
        }
        catch(NullPointerException e) {
            return null;
        }

        for (Iterator<Beacon> i = mRangedBeacons.iterator(); i.hasNext();) {
            Beacon beacon = i.next();
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
        for (Iterator<Beacon> i = mRangedBeacons.iterator(); i.hasNext();) {
            Beacon beacon = i.next();
            if (beacon.getProximityUUID().equals(proximityUUID) &&
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
    private Beacon findBeacon(JSONObject json) throws JSONException {
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

    // todo: consider mac address only version?
    /**
     * Connect to beacon
     */
    private void connectToBeacon(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
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

        mBeaconConnectionCallback = callbackContext;

        // Create error listener.
        mBeaconManager.setErrorListener(new PluginErrorListener(callbackContext));

        mConnectedBeacon = new BeaconConnected(
                mApplicationContext,
                beacon,
                new PluginConnectingListener()
        );

        mConnectedBeacon.authenticate();

        return;
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
    private void disconnectConnectedBeacon(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "disconnectConnectedBeacon (cordova)");

        mBeaconDisconnectionCallback = callbackContext;
        disconnectConnectedBeacon();
    }

    /**
     * Write Proximity UUID to connected beacon
     */
    private void writeConnectedProximityUUID(
            CordovaArgs cordovaArgs,
            final CallbackContext callbackContext)
            throws JSONException
    {
        LogUtils.i(LOGTAG, "writeConnectedProximityUUID");

        if (mConnectedBeacon != null && mConnectedBeacon.isConnected()) {
            String uuid = cordovaArgs.getString(0);
            UUID _uuid = null;

            LogUtils.i(LOGTAG, uuid);
            LogUtils.i(LOGTAG, mConnectedBeacon.getBeacon().getProximityUUID().toString());
            LogUtils.i(LOGTAG, String.valueOf(uuid.equals(mConnectedBeacon.getBeacon().getProximityUUID())));

            // already correct, skip
            if (uuid.equals(mConnectedBeacon.getBeacon().getProximityUUID())) {
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
            throws JSONException
    {
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
            throws JSONException
    {
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
     *
     * beaconInfo format:
     * {
     *	 region: region,
     *	 beacons: array of beacon
     * }
     */
    private JSONObject makeJSONBeaconInfo(Region region, List<Beacon> beacons)
            throws JSONException
    {
        // Create JSON object.
        JSONObject json = new JSONObject();
        json.put("region", makeJSONRegion(region));
        json.put("beacons", makeJSONBeaconArray(beacons));
        return json;
    }

    /**
     * Create JSON object representing a region.
     */
    private static JSONObject makeJSONRegion(Region region)
            throws JSONException
    {
        return makeJSONRegion(region, null);
    }

    /**
     * Create JSON object representing a region in the given state.
     */
    private static JSONObject makeJSONRegion(Region region, String state)
            throws JSONException
    {
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
     * Create JSON object representing a beacon list.
     */
    private JSONArray makeJSONBeaconArray(List<Beacon> beacons)
            throws JSONException
    {
        JSONArray jsonArray = new JSONArray();
        for (Beacon b : beacons) {
            // Compute proximity value.
            Utils.Proximity proximityValue = Utils.computeProximity(b);
            int proximity = 0; // Unknown.
            if (Utils.Proximity.IMMEDIATE == proximityValue) { proximity = 1; }
            else if (Utils.Proximity.NEAR == proximityValue) { proximity = 2; }
            else if (Utils.Proximity.FAR == proximityValue) { proximity = 3; }

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
     * Create JSON object representing a device list.
     */
    private JSONArray makeJSONDeviceArray(List<ConfigurableDevicesScanner.ScanResultItem> devices)
            throws JSONException
    {
        JSONArray jsonArray = new JSONArray();
        ConfigurableDevice b;
        for (ConfigurableDevicesScanner.ScanResultItem device : devices) {
            b = device.device;
            if (null == device) continue;

            // Compute proximity value.
            Utils.Proximity proximityValue = Utils.computeProximity(device);
            int proximity = 0; // Unknown.
            if (Utils.Proximity.IMMEDIATE == proximityValue) { proximity = 1; }
            else if (Utils.Proximity.NEAR == proximityValue) { proximity = 2; }
            else if (Utils.Proximity.FAR == proximityValue) { proximity = 3; }

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

    private String regionHashMapKey(Region region)
    {
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

        UUID _uuid = null;

        try {
            _uuid = UUID.fromString(uuid);
        }
        catch (NullPointerException e) {
            _uuid = null;
        }
        catch (IllegalArgumentException e) {
            _uuid = null;
        }

        return new Region(identifier, _uuid, major, minor);
    }

    /**
     * Create a Region object from HashMap key.
     */
    private Region createRegion(String key) throws IllegalArgumentException, NullPointerException {
        String[] regionValues = key.split("%");
        String uuid = regionValues[0];
        int major = Integer.parseInt(regionValues[1]);
        int minor = Integer.parseInt(regionValues[2]);

        UUID _uuid = null;

        try {
            _uuid = UUID.fromString(uuid);
        }
        catch (NullPointerException e) {
            _uuid = null;
        }
        catch (IllegalArgumentException e) {
            _uuid = null;
        }

        return new Region(key, _uuid, major, minor);
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
        }
        else {
            return new Integer(i);
        }
    }

    /**
     * Listener for scanning events.
     */
    class PluginScanningListener implements ConfigurableDevicesScanner.ScannerCallback {
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
            }
            catch(JSONException e) {
                LogUtils.e(LOGTAG, "onDevicesFound error:", e);
            }
        }
    }

    /**
     * Listener for ranging events.
     */
    class PluginRangingListener implements BeaconManager.RangingListener {
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
            }
            catch(JSONException e) {
                LogUtils.e(LOGTAG, "onBeaconsDiscovered error:", e);
            }
        }
    }

    /**
     * Listener for error events.
     */
    class PluginErrorListener implements BeaconManager.ErrorListener {
        CallbackContext mErrorCallbackContext;
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
            }
            catch(JSONException e) {
                LogUtils.e(LOGTAG, "sendRegionInfo error:", e);
            }
        }
    }

    /**
     * Listener for beacon connection events
     */
    class PluginConnectingListener implements BeaconConnection.ConnectionCallback
    {
        @Override public void onAuthorized(BeaconInfo beaconInfo) {
            LogUtils.i(this.toString(), "onAuthorized");
            return;
        }

        @Override public void onConnected(BeaconInfo beaconInfo) {
            LogUtils.i(this.toString(), "onConnected");
            CallbackContext callback = mBeaconConnectionCallback;

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
            mBeaconConnectionCallback = null;
        }

        @Override public void onAuthenticationError(EstimoteDeviceException e) {
            LogUtils.i(this.toString(), "onAuthenticationError");
            CallbackContext callback = mBeaconConnectionCallback;

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
            mBeaconConnectionCallback = null;
        }

        @Override public void onDisconnected() {
            LogUtils.i(this.toString(), "onDisconnected");
            CallbackContext callback = mBeaconDisconnectionCallback;

            if (callback == null) {
                return;
            }

            PluginResult r = new PluginResult(PluginResult.Status.OK);
            callback.sendPluginResult(r);

            // cleanup
            mBeaconDisconnectionCallback = null;
        }
    }

//    public class DeviceConnected extends DeviceConnection {
//        private Beacon mBeacon;
//
//        public DeviceConnected(
//                Context context,
//                Beacon beacon,
//                BeaconConnection.ConnectionCallback connectionCallback
//        ) {
//            super(context, beacon, connectionCallback);
//            this.mBeacon = beacon;
//        }
//
//        public Beacon getBeacon() {
//            return mBeacon;
//        }
//    }

    @SuppressWarnings("deprecation")
    public class BeaconConnected extends BeaconConnection {
        private Beacon mBeacon;

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
