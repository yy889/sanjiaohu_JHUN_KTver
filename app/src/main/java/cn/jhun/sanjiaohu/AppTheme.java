package cn.jhun.sanjiaohu;

import android.app.Activity;
import android.content.Context;
import android.view.View;

/**
 * Android bridge between the persisted light/dark preference and the pure ThemePalette math.
 * Kept separate so ThemePalette stays free of Android imports and reusable from the Java tests.
 */
public final class AppTheme {
    static final String PREFS="settings",DARK_KEY="darkMode";
    private AppTheme(){}

    public static boolean isDark(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(DARK_KEY,false);
    }

    public static void setDark(Context context,boolean dark){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(DARK_KEY,dark).apply();
    }

    public static ThemePalette from(Context context,int primary){return new ThemePalette(primary,isDark(context));}

    /** Status and navigation bars follow the palette, including the icon polarity. */
    public static void applySystemBars(Activity activity,ThemePalette theme){
        activity.getWindow().setStatusBarColor(theme.surface);
        activity.getWindow().setNavigationBarColor(theme.surface);
        // The static theme in res/values/styles.xml is always light, because the switch is a
        // manual preference rather than a values-night resource. The window background has to be
        // painted here or every activity flashes white before its content view is attached.
        activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(theme.surface));
        View decor=activity.getWindow().getDecorView();
        int flags=decor.getSystemUiVisibility();
        if(theme.dark)flags&=~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        else flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }
}
