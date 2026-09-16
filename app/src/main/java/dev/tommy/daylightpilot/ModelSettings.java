package dev.tommy.daylightpilot;

import android.content.Context;
import android.content.SharedPreferences;

final class ModelSettings {
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("model_preferences", Context.MODE_PRIVATE);
    }
    static ModelOptions read(Context context) {
        SharedPreferences prefs = preferences(context);
        return new ModelOptions(prefs.getString("model", "gpt-5.6-luna"), prefs.getString("effort", "low"));
    }
    static void save(Context context, ModelOptions.Model model, ModelOptions.Effort effort) {
        preferences(context).edit().putString("model", model.id)
            .putString("effort", model.normalize(effort).id).apply();
    }
}
