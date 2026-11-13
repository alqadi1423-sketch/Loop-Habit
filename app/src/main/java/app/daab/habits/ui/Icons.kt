package app.daab.habits.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun stroked(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw
        )
    }.build()

/** An open book, for the Quran page. */
val BookIcon: ImageVector = stroked("book") {
    moveTo(12f, 6f)
    curveTo(10f, 4.5f, 7f, 4f, 4f, 4f)
    verticalLineTo(18f)
    curveTo(7f, 18f, 10f, 18.5f, 12f, 20f)
    curveTo(14f, 18.5f, 17f, 18f, 20f, 18f)
    verticalLineTo(4f)
    curveTo(17f, 4f, 14f, 4.5f, 12f, 6f)
    close()
    moveTo(12f, 6f)
    verticalLineTo(20f)
}

/** A string of beads, for the adhkar page. */
val BeadsIcon: ImageVector = stroked("beads") {
    val points = listOf(12f to 4f, 17.5f to 6.5f, 19.5f to 12f, 17.5f to 17.5f, 12f to 20f, 6.5f to 17.5f, 4.5f to 12f, 6.5f to 6.5f)
    for ((x, y) in points) {
        moveTo(x - 1.6f, y)
        arcToRelative(1.6f, 1.6f, 0f, true, false, 3.2f, 0f)
        arcToRelative(1.6f, 1.6f, 0f, true, false, -3.2f, 0f)
    }
}

/** Rising bars, for the counters page. */
val BarsIcon: ImageVector = stroked("bars") {
    moveTo(5f, 20f)
    verticalLineTo(11f)
    moveTo(12f, 20f)
    verticalLineTo(4f)
    moveTo(19f, 20f)
    verticalLineTo(14f)
}

/** Two upright bars, for pausing recitation. */
val PauseIcon: ImageVector = stroked("pause") {
    moveTo(9f, 6f)
    verticalLineTo(18f)
    moveTo(15f, 6f)
    verticalLineTo(18f)
}

/** An arrow into a tray, for downloading. */
val DownloadIcon: ImageVector = stroked("download") {
    moveTo(12f, 4f)
    verticalLineTo(15f)
    moveTo(7.5f, 10.5f)
    lineTo(12f, 15f)
    lineTo(16.5f, 10.5f)
    moveTo(5f, 19f)
    horizontalLineTo(19f)
}
