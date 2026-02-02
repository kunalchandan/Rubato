package one.chandan.rubato.widget;

import android.content.Context;
import android.content.SharedPreferences;

public final class WidgetPreferences {
    private static final String PREFS_NAME = "widget_prefs";
    private static final String KEY_SOURCE_PREFIX = "widget_source_";

    public static final String SOURCE_QUEUE = "queue";
    public static final String SOURCE_RECENT = "recent";
    public static final String SOURCE_NONE = "none";

    private WidgetPreferences() {
    }

    public static void setRecommendationSource(Context context, int widgetId, String source) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SOURCE_PREFIX + widgetId, source).apply();
    }

    public static String getRecommendationSource(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SOURCE_PREFIX + widgetId, SOURCE_QUEUE);
    }

    public static void clear(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_SOURCE_PREFIX + widgetId).apply();
    }
}
