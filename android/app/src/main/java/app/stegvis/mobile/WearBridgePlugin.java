package app.stegvis.mobile;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;

/** Sends the login token to the paired Wear OS watch over the Wear Data Layer (path /stegvis/token). An empty token clears it on the watch. */
@CapacitorPlugin(name = "WearBridge")
public class WearBridgePlugin extends Plugin {

    @PluginMethod
    public void setToken(PluginCall call) {
        PutDataMapRequest request = PutDataMapRequest.create("/stegvis/token");
        request.getDataMap().putString("token", call.getString("token", ""));
        request.getDataMap().putLong("sent", System.currentTimeMillis()); // forces a change event on every update
        request.setUrgent();
        Wearable.getDataClient(getContext()).putDataItem(request.asPutDataRequest())
            .addOnSuccessListener(item -> call.resolve())
            .addOnFailureListener(e -> call.reject("wear"));
    }
}
