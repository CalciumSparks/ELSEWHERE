package com.elsewhere.location.xposed;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.Context;
import android.hardware.Sensor;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

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

            log("installed hooks for " + lpparam.packageName
                + " process=" + lpparam.processName);
        } catch (Throwable error) {
            log("hook installation failed: " + error);
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
            boolean stepDetector
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
        }

        static Config defaults() {
            return new Config(
                true, "WALK", 1.35, 1.0,
                true, true, true, true, true, true
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
                    sensor(sensors, "stepDetector")
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
}
