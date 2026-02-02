package one.chandan.rubato.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class NowPlayingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdateHelper.updateNowPlayingWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        if (appWidgetIds == null) return;
        for (int widgetId : appWidgetIds) {
            WidgetPreferences.clear(context, widgetId);
        }
    }
}
