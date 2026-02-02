package one.chandan.rubato.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

public class CircleNowPlayingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        WidgetUpdateHelper.updateCircleWidgets(context, appWidgetManager, appWidgetIds);
    }
}
