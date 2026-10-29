package com.elsewhere.location.xposed;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.hardware.Sensor;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * ELSEWHERE Xposed sensor module for LSPatch / VirtualXposed.
 *
 * This is intentionally a classic de.robv.android.xposed module (API 82), so
 * LSPatch can embed it into a patched app without any root or system framework.
 *
 * The module is loaded by LSPatch inside the patched target process. It hooks
 * SystemSensorManager.$SensorEventQueue.dispatchSensorEvent and rewrites the
 * float[] values before the app's SensorEventListener receives them.
 */
public final class SensorHook implements IXposedHookLoadPackage {
    private static final String TAG = "ELSEWHERE-XPOSED";
    private static final String CONFIG_KEY = "elsewhere_sensor_config";
    private static final int TYPE_ACCELEROMETER = 1;
    private static final int TYPE_GYROSCOPE = 4;
    private static final int TYPE_GRAVITY = 9;
    private static final int TYPE_LINEAR_ACCELERATION = 10;
    private static final int TYPE_STEP_DETECTOR = 18;
    private static final int TYPE_STEP_COUNTER = 19;

    private static final ConcurrentHashMap<Integer, Integer> handles = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Boolean> warnedHandles = new ConcurrentHashMap<>();

    private static volatile Config config;
    private static volatile long startedAtMs;
    private static volatile long lastConfigReadMs = 0L;
    private static volatile int generatedSteps = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null || lpparam.classLoader == null) return;
            startedAtMs = SystemClock.elapsedRealtime();
            config = Config.defaults();

            Class<?> manager = Class.forName(
                "android.hardware.SystemSensorManager",
                false,
                lpparam.classLoader
            );
            XposedBridge.hookAllMethods(manager, "registerListenerImpl", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    mapSensors(param.args);
                }
            });

            Class<?> queue = Class.forName(
                "android.hardware.SystemSensorManager$SensorEventQueue",
                false,
                lpparam.classLoader
            );
            XposedBridge.hookAllMethods(queue, "dispatchSensorEvent", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    rewriteDispatch(param);
                }
            });

            installWirelessHooks(lpparam.classLoader);
            log("installed hooks for " + lpparam.packageName
                + " process=" + lpparam.processName);
        } catch (Throwable error) {
            log("hook installation failed: " + error);
        }
    }

    /**
     * The same module carries the virtual radio environment. The target app's
     * WiFiInfo / scan results / BluetoothAdapter / TelephonyManager reads are
     * rewritten from the global config. Hooking the getters (instead of
     * returning a fake system service) keeps the rest of the framework intact.
     */
    private static void installWirelessHooks(ClassLoader classLoader) {
        // Wi-Fi connection fields.
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getSSID",
            c -> c.environment.wifi.enabled,
            c -> quote(c.environment.wifi.ssid));
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getBSSID",
            c -> c.environment.wifi.enabled,
            c -> c.environment.wifi.bssid);
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getMacAddress",
            c -> c.environment.wifi.enabled,
            c -> c.environment.wifi.bssid);
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getRssi",
            c -> c.environment.wifi.enabled,
            c -> c.environment.wifi.rssi);
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getFrequency",
            c -> c.environment.wifi.enabled,
            c -> frequencyFromChannel(c.environment.wifi.channel));
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getLinkSpeed",
            c -> c.environment.wifi.enabled,
            c -> 866);
        hookResult(classLoader, "android.net.wifi.WifiInfo", "getNetworkId",
            c -> c.environment.wifi.enabled,
            c -> 1);

        // Wi-Fi state + one synthetic scan result.
        hookResult(classLoader, "android.net.wifi.WifiManager", "isWifiEnabled",
            c -> c.environment.wifi.enabled,
            c -> Boolean.TRUE);
        hookResult(classLoader, "android.net.wifi.WifiManager", "getWifiState",
            c -> c.environment.wifi.enabled,
            c -> WifiManager.WIFI_STATE_ENABLED);
        hookResult(classLoader, "android.net.wifi.WifiManager", "getScanResults",
            c -> c.environment.wifi.enabled,
            SensorHook::fakeScanResults);

        // Bluetooth adapter identity/state.
        hookResult(classLoader, "android.bluetooth.BluetoothAdapter", "getName",
            c -> c.environment.bluetooth.enabled,
            c -> c.environment.bluetooth.name);
        hookResult(classLoader, "android.bluetooth.BluetoothAdapter", "getAddress",
            c -> c.environment.bluetooth.enabled,
            c -> c.environment.bluetooth.address);
        hookResult(classLoader, "android.bluetooth.BluetoothAdapter", "isEnabled",
            c -> c.environment.bluetooth.enabled,
            c -> Boolean.TRUE);
        hookResult(classLoader, "android.bluetooth.BluetoothAdapter", "getState",
            c -> c.environment.bluetooth.enabled,
            c -> BluetoothAdapter.STATE_ON);

        // Cell operator and legacy cell identity. getAllCellInfo is handled
        // best-effort with a reflected CellInfoLte; see fakeCellInfoList().
        hookResult(classLoader, "android.telephony.TelephonyManager", "getNetworkOperator",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.mcc + c.environment.cell.mnc);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getSimOperator",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.mcc + c.environment.cell.mnc);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getNetworkOperatorName",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.carrier);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getSimOperatorName",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.carrier);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getNetworkType",
            c -> c.environment.cell.enabled,
            SensorHook::networkTypeInt);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getDataNetworkType",
            c -> c.environment.cell.enabled,
            SensorHook::networkTypeInt);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getPhoneType",
            c -> c.environment.cell.enabled,
            c -> TelephonyManager.PHONE_TYPE_GSM);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getSimState",
            c -> c.environment.cell.enabled,
            c -> TelephonyManager.SIM_STATE_READY);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getCellLocation",
            c -> c.environment.cell.enabled,
            SensorHook::fakeCellLocation);
        hookResult(classLoader, "android.telephony.TelephonyManager", "getAllCellInfo",
            c -> c.environment.cell.enabled,
            SensorHook::fakeCellInfoList);

        // Android 15 keeps some CellIdentityLte fields hidden; spoofing the
        // public getters is version-proof and also fixes the synthetic object
        // when its private fields cannot be written.
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getMcc",
            c -> c.environment.cell.enabled,
            c -> parseInt(c.environment.cell.mcc, 460));
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getMnc",
            c -> c.environment.cell.enabled,
            c -> parseInt(c.environment.cell.mnc, 0));
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getCi",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.cellId);
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getTac",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.areaCode);
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getMccString",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.mcc);
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getMncString",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.mnc);
        hookResult(classLoader, "android.telephony.CellIdentityLte", "getMobileNetworkOperator",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.mcc + c.environment.cell.mnc);
        hookResult(classLoader, "android.telephony.CellSignalStrengthLte", "getRsrp",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.signalDbm);
        hookResult(classLoader, "android.telephony.CellSignalStrengthLte", "getDbm",
            c -> c.environment.cell.enabled,
            c -> c.environment.cell.signalDbm);
        hookResult(classLoader, "android.telephony.CellSignalStrengthLte", "getLevel",
            c -> c.environment.cell.enabled,
            c -> 2);
    }

    private static void hookResult(
        ClassLoader classLoader,
        String className,
        String methodName,
        Predicate<Config> enabled,
        Function<Config, Object> value
    ) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            XposedBridge.hookAllMethods(type, methodName, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Config current = currentConfig();
                        if (enabled.test(current)) {
                            param.setResult(value.apply(current));
                        }
                    } catch (Throwable error) {
                        log("wireless hook " + methodName + " failed: " + error);
                    }
                }
            });
        } catch (Throwable error) {
            log("wireless hook skipped " + className + "." + methodName + ": " + error);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static int frequencyFromChannel(int channel) {
        if (channel == 14) return 2484;
        if (channel >= 1 && channel <= 13) return 2407 + channel * 5;
        if (channel >= 32 && channel <= 177) return 5000 + channel * 5;
        return 5745;
    }

    private static List<?> fakeScanResults(Config c) {
        try {
            Class<?> scanResultType = Class.forName("android.net.wifi.ScanResult", false, Config.class.getClassLoader());
            Constructor<?> constructor = scanResultType.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object scan = constructor.newInstance();
            setField(scanResultType, scan, "SSID", c.environment.wifi.ssid);
            setField(scanResultType, scan, "BSSID", c.environment.wifi.bssid);
            setField(scanResultType, scan, "capabilities", "[WPA2-PSK-CCMP][ESS]");
            setField(scanResultType, scan, "level", c.environment.wifi.rssi);
            setField(scanResultType, scan, "frequency", frequencyFromChannel(c.environment.wifi.channel));
            return Collections.singletonList(scan);
        } catch (Throwable error) {
            log("fake ScanResult unavailable: " + error);
            return Collections.emptyList();
        }
    }

    private static Object fakeCellLocation(Config c) {
        try {
            GsmCellLocation location = new GsmCellLocation();
            location.setLacAndCid(c.environment.cell.areaCode, c.environment.cell.cellId);
            return location;
        } catch (Throwable error) {
            log("fake GsmCellLocation failed: " + error);
            return null;
        }
    }

    private static List<?> fakeCellInfoList(Config c) {
        try {
            ClassLoader loader = Config.class.getClassLoader();
            Object cell = newHidden("android.telephony.CellInfoLte", loader);
            Object identity = newHidden("android.telephony.CellIdentityLte", loader);
            Object signal = newHidden("android.telephony.CellSignalStrengthLte", loader);
            if (cell == null || identity == null || signal == null) return Collections.emptyList();

            Class<?> identityType = identity.getClass();
            // mcc/mnc are spoofed through CellIdentityLte getters; their
            // private field names differ across Android releases.
            setField(identityType, identity, "mCi", c.environment.cell.cellId);
            setField(identityType, identity, "mTac", c.environment.cell.areaCode);

            Class<?> signalType = signal.getClass();
            setField(signalType, signal, "mRsrp", c.environment.cell.signalDbm);
            setField(signalType, signal, "mRssi", c.environment.cell.signalDbm + 20);
            setField(signalType, signal, "mLevel", 2);

            Class<?> cellType = cell.getClass();
            setField(cellType, cell, "mCellIdentityLte", identity);
            setField(cellType, cell, "mCellSignalStrengthLte", signal);
            return Collections.singletonList(cell);
        } catch (Throwable error) {
            log("fake CellInfo unavailable: " + error);
            return Collections.emptyList();
        }
    }

    private static Object newHidden(String className, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable error) {
            log("hidden constructor unavailable " + className + ": " + error);
            return null;
        }
    }

    private static void setField(Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable error) {
            log("field unavailable " + type.getSimpleName() + "." + name + ": " + error);
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int networkTypeInt(Config c) {
        String type = c.environment.cell.networkType == null
            ? "LTE"
            : c.environment.cell.networkType.toUpperCase();
        switch (type) {
            case "NR":
            case "5G":
                return 20;
            case "HSPAP":
                return 15;
            case "UMTS":
            case "3G":
                return 3;
            case "EDGE":
                return 2;
            case "GSM":
                return 1;
            case "CDMA":
                return 4;
            case "LTE":
            default:
                return 13;
        }
    }

    private static void mapSensors(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (!(arg instanceof Sensor)) continue;
            Sensor sensor = (Sensor) arg;
            int handle = sensorHandle(sensor);
            if (handle >= 0) {
                handles.put(handle, sensor.getType());
            }
        }
    }

    private static void rewriteDispatch(XC_MethodHook.MethodHookParam param) {
        try {
            Object[] args = param.args;
            if (args == null) return;
            int handle = -1;
            float[] values = null;
            for (Object arg : args) {
                if (arg instanceof float[]) {
                    values = (float[]) arg;
                    break;
                }
            }
            for (Object arg : args) {
                if (arg instanceof Integer) {
                    handle = (Integer) arg;
                    break;
                }
            }
            if (values == null || handle < 0) return;

            Config current = currentConfig();
            if (!current.enabled) return;

            Integer type = handles.get(handle);
            if (type == null) {
                type = inferType(values);
            }
            if (type == null) {
                if (warnedHandles.putIfAbsent(handle, Boolean.TRUE) == null) {
                    log("unmapped sensor handle=" + handle + " values=" + values.length);
                }
                return;
            }
            applyValues(type, values, current);
        } catch (Throwable error) {
            // A sensor hook must never break the host application.
            log("dispatch error: " + error);
        }
    }

    private static Integer inferType(float[] values) {
        if (values.length == 1) {
            return values[0] > 4f ? TYPE_STEP_COUNTER : TYPE_STEP_DETECTOR;
        }
        return null;
    }

    private static void applyValues(int type, float[] values, Config c) {
        if (!enabled(type, c)) return;
        double t = phase(c);
        double amplitude = c.amplitude;
        double stepSpeed = cadence(c);
        switch (type) {
            case TYPE_ACCELEROMETER:
                if (values.length < 3) return;
                values[0] = (float) ((0.32 * Math.sin(t) + 0.08 * Math.sin(t * 3.7)) * amplitude);
                values[1] = (float) ((0.16 * Math.cos(t * 1.3)) * amplitude);
                values[2] = (float) ((9.81 + 0.34 * Math.sin(t * 2.1)) * amplitude);
                break;
            case TYPE_GYROSCOPE:
                if (values.length < 3) return;
                values[0] = (float) (0.018 * Math.sin(t * 0.7) * amplitude);
                values[1] = (float) (0.014 * Math.cos(t * 0.9) * amplitude);
                values[2] = (float) ((stepSpeed > 0 ? 0.035 * Math.sin(t * 2.3) : 0.006 * Math.sin(t)) * amplitude);
                break;
            case TYPE_GRAVITY:
                if (values.length < 3) return;
                values[0] = 0f;
                values[1] = 0f;
                values[2] = 9.81f;
                break;
            case TYPE_LINEAR_ACCELERATION:
                if (values.length < 3) return;
                values[0] = (float) (0.28 * Math.sin(t * 2.3) * amplitude);
                values[1] = (float) (0.12 * Math.cos(t * 1.7) * amplitude);
                values[2] = (float) (0.18 * Math.sin(t * 4.1) * amplitude);
                break;
            case TYPE_STEP_COUNTER:
                generatedSteps = Math.max(generatedSteps, (int) Math.floor(elapsedSeconds() * stepSpeed));
                values[0] = generatedSteps;
                break;
            case TYPE_STEP_DETECTOR:
                values[0] = 1f;
                break;
            default:
                break;
        }
    }

    private static boolean enabled(int type, Config c) {
        switch (type) {
            case TYPE_ACCELEROMETER: return c.accelerometer;
            case TYPE_GYROSCOPE: return c.gyroscope;
            case TYPE_GRAVITY: return c.gravity;
            case TYPE_LINEAR_ACCELERATION: return c.linearAcceleration;
            case TYPE_STEP_COUNTER: return c.stepCounter;
            case TYPE_STEP_DETECTOR: return c.stepDetector;
            default: return false;
        }
    }

    private static double cadence(Config c) {
        if ("CYCLE".equals(c.mode)) return 0.0;
        if ("RUN".equals(c.mode)) return Math.max(2.7, c.speedMps * 0.82);
        return Math.max(1.6, c.speedMps * 1.35);
    }

    private static double elapsedSeconds() {
        return Math.max(0.0, (SystemClock.elapsedRealtime() - startedAtMs) / 1000.0);
    }

    private static double phase(Config c) {
        return elapsedSeconds() * (0.75 + cadence(c) * 0.14);
    }

    private static int sensorHandle(Sensor sensor) {
        try {
            Method method = Sensor.class.getDeclaredMethod("getHandle");
            method.setAccessible(true);
            Object value = method.invoke(sensor);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {
            // Fall through to the field.
        }
        try {
            Field field = Sensor.class.getDeclaredField("mHandle");
            field.setAccessible(true);
            Object value = field.get(sensor);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) {
            // The registration hook still provides a best-effort path.
        }
        return -1;
    }

    private static Config currentConfig() {
        long now = SystemClock.elapsedRealtime();
        if (config != null && now - lastConfigReadMs < 2_000L) {
            return config;
        }
        lastConfigReadMs = now;
        Config next = config;
        try {
            Application app = AndroidAppHelper.currentApplication();
            if (app != null) {
                String raw = Settings.Global.getString(app.getContentResolver(), CONFIG_KEY);
                if (raw != null && !raw.isEmpty()) {
                    next = Config.parse(raw);
                }
            }
        } catch (Throwable ignored) {
            // Defaults are safe when no global config is available.
        }
        config = next;
        return next;
    }

    private static void log(String message) {
        String line = "[ELSEWHERE-XPOSED] " + message;
        Log.i(TAG, line);
        try {
            XposedBridge.log(line);
        } catch (Throwable ignored) {
            // XposedBridge is provided by LSPatch; keep plain logcat as fallback.
        }
    }

    private static final class Config {
        final boolean enabled;
        final String mode;
        final double speedMps;
        final double amplitude;
        final boolean accelerometer;
        final boolean gyroscope;
        final boolean gravity;
        final boolean linearAcceleration;
        final boolean stepCounter;
        final boolean stepDetector;
        final Environment environment;

        Config(
            boolean enabled,
            String mode,
            double speedMps,
            double amplitude,
            boolean accelerometer,
            boolean gyroscope,
            boolean gravity,
            boolean linearAcceleration,
            boolean stepCounter,
            boolean stepDetector,
            Environment environment
        ) {
            this.enabled = enabled;
            this.mode = mode;
            this.speedMps = speedMps;
            this.amplitude = amplitude;
            this.accelerometer = accelerometer;
            this.gyroscope = gyroscope;
            this.gravity = gravity;
            this.linearAcceleration = linearAcceleration;
            this.stepCounter = stepCounter;
            this.stepDetector = stepDetector;
            this.environment = environment;
        }

        static Config defaults() {
            return new Config(
                true, "WALK", 1.35, 1.0,
                true, true, true, true, true, true,
                Environment.defaults()
            );
        }

        static Config parse(String raw) {
            try {
                JSONObject root = new JSONObject(raw);
                JSONObject sensors = root.optJSONObject("sensors");
                return new Config(
                    root.optBoolean("enabled", true),
                    root.optString("mode", "WALK").toUpperCase(),
                    root.optDouble("speedMps", 1.35),
                    root.optDouble("amplitude", 1.0),
                    sensor(sensors, "accelerometer"),
                    sensor(sensors, "gyroscope"),
                    sensor(sensors, "gravity"),
                    sensor(sensors, "linearAcceleration"),
                    sensor(sensors, "stepCounter"),
                    sensor(sensors, "stepDetector"),
                    Environment.parse(root.optJSONObject("environment"))
                );
            } catch (Throwable error) {
                log("invalid config; using defaults: " + error);
                return defaults();
            }
        }

        private static boolean sensor(JSONObject object, String key) {
            return object == null || object.optBoolean(key, true);
        }
    }

    private static final class Environment {
        final Wifi wifi;
        final Bluetooth bluetooth;
        final Cell cell;

        Environment(Wifi wifi, Bluetooth bluetooth, Cell cell) {
            this.wifi = wifi;
            this.bluetooth = bluetooth;
            this.cell = cell;
        }

        static Environment defaults() {
            return new Environment(
                Wifi.defaults(),
                Bluetooth.defaults(),
                Cell.defaults()
            );
        }

        static Environment parse(JSONObject object) {
            if (object == null) return defaults();
            return new Environment(
                Wifi.parse(object.optJSONObject("wifi")),
                Bluetooth.parse(object.optJSONObject("bluetooth")),
                Cell.parse(object.optJSONObject("cell"))
            );
        }
    }

    private static final class Wifi {
        final boolean enabled;
        final String ssid;
        final String bssid;
        final int rssi;
        final int channel;

        Wifi(boolean enabled, String ssid, String bssid, int rssi, int channel) {
            this.enabled = enabled;
            this.ssid = ssid;
            this.bssid = bssid;
            this.rssi = rssi;
            this.channel = channel;
        }

        static Wifi defaults() {
            return new Wifi(false, "SEVEN-ELEVEN-5G", "A4:2B:8C:19:7D:02", -57, 149);
        }

        static Wifi parse(JSONObject object) {
            if (object == null) return defaults();
            return new Wifi(
                object.optBoolean("enabled", false),
                object.optString("ssid", "SEVEN-ELEVEN-5G"),
                object.optString("bssid", "A4:2B:8C:19:7D:02"),
                object.optInt("rssi", -57),
                object.optInt("channel", 149)
            );
        }
    }

    private static final class Bluetooth {
        final boolean enabled;
        final String name;
        final String address;
        final int rssi;

        Bluetooth(boolean enabled, String name, String address, int rssi) {
            this.enabled = enabled;
            this.name = name;
            this.address = address;
            this.rssi = rssi;
        }

        static Bluetooth defaults() {
            return new Bluetooth(false, "AirPods Pro", "3C:2A:4F:81:9B:20", -62);
        }

        static Bluetooth parse(JSONObject object) {
            if (object == null) return defaults();
            return new Bluetooth(
                object.optBoolean("enabled", false),
                object.optString("name", "AirPods Pro"),
                object.optString("address", "3C:2A:4F:81:9B:20"),
                object.optInt("rssi", -62)
            );
        }
    }

    private static final class Cell {
        final boolean enabled;
        final String carrier;
        final String mcc;
        final String mnc;
        final int areaCode;
        final int cellId;
        final String networkType;
        final int signalDbm;

        Cell(
            boolean enabled,
            String carrier,
            String mcc,
            String mnc,
            int areaCode,
            int cellId,
            String networkType,
            int signalDbm
        ) {
            this.enabled = enabled;
            this.carrier = carrier;
            this.mcc = mcc;
            this.mnc = mnc;
            this.areaCode = areaCode;
            this.cellId = cellId;
            this.networkType = networkType;
            this.signalDbm = signalDbm;
        }

        static Cell defaults() {
            return new Cell(false, "中国移动", "460", "00", 21987, 3735921, "LTE", -91);
        }

        static Cell parse(JSONObject object) {
            if (object == null) return defaults();
            return new Cell(
                object.optBoolean("enabled", false),
                object.optString("carrier", "中国移动"),
                object.optString("mcc", "460"),
                object.optString("mnc", "00"),
                object.optInt("areaCode", 21987),
                object.optInt("cellId", 3735921),
                object.optString("networkType", "LTE"),
                object.optInt("signalDbm", -91)
            );
        }
    }
}
