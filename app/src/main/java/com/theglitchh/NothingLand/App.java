package com.theglitchh.NothingLand;

import android.app.Application;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Nothing red by default. Material You (wallpaper colours) only when the user
        // switches on "Material You colours" in the app settings.
        DynamicColors.applyToActivitiesIfAvailable(this, new DynamicColorsOptions.Builder()
                .setPrecondition((activity, theme) -> materialYouEnabled(activity))
                .build());
    }

    /** Shared with the island: the "Material You colours" setting (off by default). */
    public static boolean materialYouEnabled(android.content.Context ctx) {
        return ctx.getSharedPreferences(ctx.getPackageName(), MODE_PRIVATE)
                .getBoolean("wallpaper_color", false);
    }
}
