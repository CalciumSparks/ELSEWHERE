/*
 * ELSEWHERE — Frida sensor bridge
 *
 * This script is injected into a target process by an external Frida client:
 * - frida-jdwp-loader over JDWP for a debuggable app (no root, no repackaging);
 * - a root frida-server (any app);
 * - a Frida Gadget build of the target (repackaged).
 * It intercepts SystemSensorManager dispatches and rewrites the values that
 * the target's SensorEventListeners receive.
 *
 * It does not inject into the process by itself. Shizuku has no ptrace
 * permission over third-party apps. See README for the required environment.
 *
 * Config is replaced by ELSEWHERE before delivery:
 *   mode, speedMps, sensors, amplitude
 */

'use strict';

const CONFIG = __ELSEWHERE_CONFIG__;

const TYPE = {
    ACCELEROMETER: 1,
    GYROSCOPE: 4,
    GRAVITY: 9,
    LINEAR_ACCELERATION: 10,
    STEP_DETECTOR: 18,
    STEP_COUNTER: 19
};

const SENSOR_KEY = {};
SENSOR_KEY[TYPE.ACCELEROMETER] = 'accelerometer';
SENSOR_KEY[TYPE.GYROSCOPE] = 'gyroscope';
SENSOR_KEY[TYPE.GRAVITY] = 'gravity';
SENSOR_KEY[TYPE.LINEAR_ACCELERATION] = 'linearAcceleration';
SENSOR_KEY[TYPE.STEP_COUNTER] = 'stepCounter';
SENSOR_KEY[TYPE.STEP_DETECTOR] = 'stepDetector';

const handles = {};
const warnedHandles = {};
const startedAtMs = Date.now();
let generatedSteps = 0;
let eventCount = 0;
const LOG_TAG = 'ELSEWHERE-SENSOR';

function log(message) {
    try {
        const Log = Java.use('android.util.Log');
        Log.i(LOG_TAG, String(message));
    } catch (ignored) {
        // The Java bridge may not be available before boot; console is a
        // useful fallback when a Frida CLI is attached.
    }
    try {
        console.log('[elsewhere] ' + message);
    } catch (ignored) {
        // In frida-jdwp-loader script mode, logcat is the usable output.
    }
}

function emit(event, payload) {
    const message = Object.assign({ event: event }, payload || {});
    try {
        send(message);
    } catch (ignored) {
        // Autonomous JDWP/Gadget modes have no attached message handler.
    }
    if (event === 'ready' || event === 'error' || event === 'unmapped-sensor') {
        log(JSON.stringify(message));
    }
}

function sensorEnabled(type) {
    const key = SENSOR_KEY[type];
    if (!key) return false;
    return !CONFIG.sensors || CONFIG.sensors[key] !== false;
}

function cadence() {
    const mode = String(CONFIG.mode || 'WALK').toUpperCase();
    const speed = Number(CONFIG.speedMps) || 1.35;
    if (mode === 'CYCLE') return 0;
    if (mode === 'RUN') return Math.max(2.7, speed * 0.82);
    return Math.max(1.6, speed * 1.35);
}

function elapsedSeconds() {
    return Math.max(0, (Date.now() - startedAtMs) / 1000);
}

function phase() {
    return elapsedSeconds() * (0.75 + cadence() * 0.14);
}

function warnOnce(handle, values) {
    if (warnedHandles[handle]) return;
    warnedHandles[handle] = true;
    emit('unmapped-sensor', {
        handle: handle,
        valueCount: values.length,
        note: 'Could not resolve the Sensor type for this handle; values were left untouched.'
    });
}

function findSensor(queue, handle) {
    const attempts = [
        function () { return queue.mManager.value.mSensors.value.get(handle); },
        function () { return queue.mManager.value.mSensorListByHandle.value.get(handle); },
        function () { return queue.mSensors.value.get(handle); },
        function () { return queue.mSensorsEvents.value.get(handle).sensor.value; }
    ];
    for (let i = 0; i < attempts.length; i += 1) {
        try {
            const sensor = attempts[i]();
            if (sensor !== null && sensor !== undefined) return sensor;
        } catch (ignored) {
            // Field names vary between Android releases. Try the next path.
        }
    }
    return null;
}

function sensorType(queue, handle, values) {
    if (handles[handle] !== undefined) return handles[handle];
    const sensor = findSensor(queue, handle);
    if (sensor !== null) {
        try {
            const type = sensor.getType();
            handles[handle] = type;
            return type;
        } catch (ignored) {
            // Fall through to the value-shape fallback.
        }
    }

    // The registration hook normally maps handles before dispatch. This
    // fallback keeps step sensors useful even if a vendor field differs.
    if (values.length === 1) {
        const value = Number(values[0]);
        const type = value > 4 ? TYPE.STEP_COUNTER : TYPE.STEP_DETECTOR;
        handles[handle] = type;
        return type;
    }
    warnOnce(handle, values);
    return null;
}

function setValue(values, index, value) {
    if (index < values.length) values[index] = value;
}

function applyValues(type, values) {
    if (!sensorEnabled(type)) return;
    const t = phase();
    const amplitude = Number(CONFIG.amplitude) || 1.0;
    const stepSpeed = cadence();

    switch (type) {
        case TYPE.ACCELEROMETER: {
            if (values.length < 3) return;
            setValue(values, 0, (0.32 * Math.sin(t) + 0.08 * Math.sin(t * 3.7)) * amplitude);
            setValue(values, 1, (0.16 * Math.cos(t * 1.3)) * amplitude);
            setValue(values, 2, (9.81 + 0.34 * Math.sin(t * 2.1)) * amplitude);
            break;
        }
        case TYPE.GYROSCOPE: {
            if (values.length < 3) return;
            setValue(values, 0, 0.018 * Math.sin(t * 0.7) * amplitude);
            setValue(values, 1, 0.014 * Math.cos(t * 0.9) * amplitude);
            setValue(values, 2, (stepSpeed > 0 ? 0.035 * Math.sin(t * 2.3) : 0.006 * Math.sin(t)) * amplitude);
            break;
        }
        case TYPE.GRAVITY: {
            if (values.length < 3) return;
            setValue(values, 0, 0.0);
            setValue(values, 1, 0.0);
            setValue(values, 2, 9.81);
            break;
        }
        case TYPE.LINEAR_ACCELERATION: {
            if (values.length < 3) return;
            setValue(values, 0, (0.28 * Math.sin(t * 2.3)) * amplitude);
            setValue(values, 1, (0.12 * Math.cos(t * 1.7)) * amplitude);
            setValue(values, 2, (0.18 * Math.sin(t * 4.1)) * amplitude);
            break;
        }
        case TYPE.STEP_COUNTER: {
            const steps = Math.floor(elapsedSeconds() * stepSpeed);
            generatedSteps = Math.max(generatedSteps, steps);
            setValue(values, 0, generatedSteps);
            break;
        }
        case TYPE.STEP_DETECTOR: {
            setValue(values, 0, 1.0);
            break;
        }
        default:
            break;
    }
    eventCount += 1;
}

function hookRegistration(SystemSensorManager) {
    const method = SystemSensorManager.registerListenerImpl;
    if (!method || !method.overloads) return;
    method.overloads.forEach(function (overload) {
        const original = overload;
        overload.implementation = function () {
            try {
                for (let i = 0; i < arguments.length; i += 1) {
                    const candidate = arguments[i];
                    if (candidate === null || candidate === undefined) continue;
                    try {
                        if (candidate.getClass().getName() !== 'android.hardware.Sensor') continue;
                        const type = candidate.getType();
                        let handle = -1;
                        try { handle = candidate.getHandle(); } catch (ignored) {}
                        if (handle < 0) {
                            try { handle = candidate.mHandle.value; } catch (ignored) {}
                        }
                        if (handle >= 0) handles[handle] = type;
                    } catch (ignored) {
                        // Not every argument is a Sensor.
                    }
                }
            } catch (ignored) {
                // Never break the target because of a mapping failure.
            }
            return original.apply(this, arguments);
        };
    });
}

function hookDispatch(SystemSensorManager) {
    const Queue = Java.use('android.hardware.SystemSensorManager$SensorEventQueue');
    const method = Queue.dispatchSensorEvent;
    if (!method || !method.overloads) {
        emit('error', { message: 'dispatchSensorEvent has no overloads' });
        return;
    }
    method.overloads.forEach(function (overload) {
        const types = overload.argumentTypes.map(function (type) { return type.className; });
        const valueIndex = types.indexOf('[F');
        const handleIndex = types.indexOf('int');
        if (valueIndex < 0) return;
        const original = overload;
        overload.implementation = function () {
            try {
                const handle = handleIndex >= 0 ? Number(arguments[handleIndex]) : -1;
                const values = arguments[valueIndex];
                if (handle >= 0 && values) {
                    const type = sensorType(this, handle, values);
                    if (type !== null) applyValues(type, values);
                }
            } catch (ignored) {
                // A hook must not destabilize the host app.
            }
            return original.apply(this, arguments);
        };
    });
}

function boot() {
    if (!Java.available) {
        // frida-jdwp-loader can load the Gadget during early app startup, before
        // the Java bridge is ready. Wait instead of failing the whole script.
        setTimeout(boot, 200);
        return;
    }
    Java.perform(function () {
        try {
            const SystemSensorManager = Java.use('android.hardware.SystemSensorManager');
            hookRegistration(SystemSensorManager);
            hookDispatch(SystemSensorManager);
            emit('ready', {
                android: Java.androidVersion,
                process: Process.id,
                mode: CONFIG.mode,
                speedMps: CONFIG.speedMps,
                sensors: CONFIG.sensors,
                note: 'Sensor values are rewritten at SystemSensorManager dispatch.'
            });
        } catch (error) {
            emit('error', { message: String(error) });
        }
    });
}

boot();
