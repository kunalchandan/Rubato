package one.chandan.rubato.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import one.chandan.rubato.R;

public class NowPlayingWidgetConfigureActivity extends AppCompatActivity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_now_playing_config);

        Intent intent = getIntent();
        if (intent != null) {
            appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        RadioGroup sourceGroup = findViewById(R.id.widget_source_group);
        findViewById(R.id.widget_config_add).setOnClickListener(v -> {
            String source = WidgetPreferences.SOURCE_QUEUE;
            int checkedId = sourceGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.widget_source_recent) {
                source = WidgetPreferences.SOURCE_RECENT;
            } else if (checkedId == R.id.widget_source_none) {
                source = WidgetPreferences.SOURCE_NONE;
            }

            WidgetPreferences.setRecommendationSource(this, appWidgetId, source);
            WidgetUpdateHelper.requestUpdate(this);

            Intent result = new Intent();
            result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            setResult(RESULT_OK, result);
            finish();
        });
    }
}
