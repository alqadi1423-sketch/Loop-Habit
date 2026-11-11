package app.daab.habits.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.daab.habits.data.Db
import app.daab.habits.ui.DaabTheme

/** Shown when the square is placed on the home screen: choose what it displays. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        setResult(Activity.RESULT_CANCELED, result)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val habits = Db.get(this).habits()
        val groups = Db.get(this).groups()

        fun choose(habitId: Long, groupId: Long = 0) {
            HabitWidget.bind(this, widgetId, habitId, groupId)
            HabitWidget.render(this, AppWidgetManager.getInstance(this), widgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        setContent {
            DaabTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState())) {
                        Text(
                            "ماذا يعرض المربع؟",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(20.dp)
                        )
                        Column(Modifier.fillMaxWidth().clickable { choose(HabitWidget.ALL_HABITS) }.padding(20.dp)) {
                            Text("كل العادات", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "يعرض عادة اليوم التي لم تنجز، وبعد تعليمها تظهر التالية",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                        for (g in groups) {
                            Column(Modifier.fillMaxWidth().clickable { choose(HabitWidget.ALL_HABITS, g.id) }.padding(20.dp)) {
                                Text("مجموعة " + g.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "يعرض عادة الوقت الحالي من المجموعة",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
                        for (h in habits) {
                            Text(
                                h.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth().clickable { choose(h.id) }.padding(20.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
