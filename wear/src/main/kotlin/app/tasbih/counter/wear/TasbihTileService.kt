package app.tasbih.counter.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.tasbih.counter.shared.R as SharedR
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val TEAL = 0xFF7CC4BC.toInt()
private const val TRACK = 0xFF2B403C.toInt()
private const val SAND = 0xFFF0DCAE.toInt()
private const val CREAM = 0xFFF6EEDF.toInt()
private const val MUTED = 0xFFA7B5B0.toInt()

/** A tile, which is Wear OS's widget, showing the count and progress through the round. */
class TasbihTileService : TileService() {
    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                TileBuilders.Tile.Builder()
                    .setResourcesVersion(RESOURCES_VERSION)
                    .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(currentSnapshot())))
                    .build()
            )
            "tile"
        }

    override fun onTileResourcesRequest(
        request: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = CallbackToFutureAdapter.getFuture { completer ->
        completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
        "resources"
    }

    private fun layout(snapshot: CounterSnapshot): LayoutElementBuilders.LayoutElement {
        val open = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .build()
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(open).build())
                    .build()
            )
            .addContent(ring(360f, TRACK))
            .addContent(ring(360f * snapshot.progress, TEAL))
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(label(dhikrLabel(snapshot.tasbih), 18f, SAND))
                    .addContent(label(snapshot.count.toString(), 46f, CREAM))
                    .addContent(label(getString(SharedR.string.of_round, snapshot.roundSize), 14f, MUTED))
                    .build()
            )
            .build()
    }

    private fun ring(sweep: Float, color: Int) = LayoutElementBuilders.Arc.Builder()
        .setAnchorAngle(degrees(0f))
        .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
        .addContent(
            LayoutElementBuilders.ArcLine.Builder()
                .setLength(degrees(sweep))
                .setThickness(dp(8f))
                .setColor(argb(color))
                .build()
        )
        .build()

    private fun label(value: String, size: Float, color: Int) = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setMaxLines(1)
        .setFontStyle(
            LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setColor(argb(color))
                .build()
        )
        .build()
}
