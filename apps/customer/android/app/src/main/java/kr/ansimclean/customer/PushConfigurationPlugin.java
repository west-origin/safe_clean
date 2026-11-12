package kr.ansimclean.customer;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.firebase.FirebaseApp;

/** Check native Firebase configuration before calling the FCM plugin. */
@CapacitorPlugin(name = "PushConfiguration")
public class PushConfigurationPlugin extends Plugin {
    @PluginMethod
    public void check(PluginCall call) {
        boolean configured;
        try {
            FirebaseApp.getInstance();
            configured = true;
        } catch (IllegalStateException missingConfiguration) {
            configured = false;
        }
        JSObject result = new JSObject();
        result.put("configured", configured);
        call.resolve(result);
    }
}
