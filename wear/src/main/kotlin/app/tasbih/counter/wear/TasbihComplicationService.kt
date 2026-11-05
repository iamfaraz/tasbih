package app.tasbih.counter.wear

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import app.tasbih.counter.shared.Tasbih
import app.tasbih.counter.shared.R as SharedR

/** Puts the current count on a watch face. Tapping it opens Tasbih. */
class TasbihComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        build(request.complicationType, currentSnapshot(), openApp())

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, CounterSnapshot(Tasbih.SUBHAN_ALLAH, 12, 12, 33), tapAction = null)

    private fun build(type: ComplicationType, snapshot: CounterSnapshot, tapAction: PendingIntent?): ComplicationData? {
        val count = text(snapshot.count.toString())
        val name = dhikrLatinLabel(snapshot.tasbih)
        val description = text(getString(SharedR.string.complication_description, name, snapshot.count))
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(count, description)
                .setTitle(text(getString(SharedR.string.app_name)))
                .setTapAction(tapAction)
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(text("$name · ${snapshot.count}"), description)
                .setTapAction(tapAction)
                .build()
            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = snapshot.filled.toFloat(),
                min = 0f,
                max = snapshot.roundSize.toFloat(),
                contentDescription = description,
            )
                .setText(count)
                .setTapAction(tapAction)
                .build()
            else -> null
        }
    }

    private fun text(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
