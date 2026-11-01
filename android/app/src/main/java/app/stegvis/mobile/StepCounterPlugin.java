package app.stegvis.mobile;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

/** Reads the phone's hardware step counter: total steps since boot. The sensor keeps counting while the app is closed, so the app only has to read it. */
@CapacitorPlugin(name = "StepCounter", permissions = { @Permission(strings = { Manifest.permission.ACTIVITY_RECOGNITION }, alias = "activity") })
public class StepCounterPlugin extends Plugin {

    @PluginMethod
    public void getTotal(final PluginCall call) {
        if (android.os.Build.VERSION.SDK_INT >= 29 && getPermissionState("activity") != PermissionState.GRANTED) {
            requestPermissionForAlias("activity", call, "afterPermission");
            return;
        }
        read(call);
    }

    @PermissionCallback
    private void afterPermission(PluginCall call) {
        if (getPermissionState("activity") == PermissionState.GRANTED) read(call);
        else call.reject("permission");
    }

    private void read(final PluginCall call) {
        final SensorManager sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (sensor == null) {
            call.reject("unavailable");
            return;
        }
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] done = { false };
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (done[0]) return;
                done[0] = true;
                sm.unregisterListener(this);
                JSObject result = new JSObject();
                result.put("total", (double) event.values[0]);
                call.resolve(result);
            }

            @Override
            public void onAccuracyChanged(Sensor s, int accuracy) {}
        };
        if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            call.reject("unavailable");
            return;
        }
        handler.postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            sm.unregisterListener(listener);
            call.reject("timeout");
        }, 4000);
    }
}
