package dev.tommy.daylightpilot;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

/** Invisible launcher entry point; Poppy has no background/home screen. */
public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PilotService service = PilotService.current();
        finish();
        if (service != null) {
            service.capture();
        } else {
            // Android must grant accessibility access before Poppy can capture a screen.
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }
}
