package app.stegvis.wear

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileBuilders

private const val TILE_RES_VERSION = "1"
private const val FRESH_MS = 15 * 60_000L

/** Tile: thin 270 degree arc with today's steps in the middle. Reads the same stored value as the app. */
class StepTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { c -> c.set(buildTile()); "tile" }

    private fun buildTile(): TileBuilders.Tile {
        val services = Services(this)
        val steps = services.vault.todaySteps(System.currentTimeMillis())
        val goal = services.vault.goal
        val progress = goalProgress(steps, goal)
        val ring = LayoutElementBuilders.ArcLine.Builder()
            .setLength(degrees(270f * progress.coerceAtLeast(0.001f)))
            .setThickness(dp(6f))
            .setColor(argb(0xFFFF745C.toInt()))
            .build()
        val track = LayoutElementBuilders.ArcLine.Builder()
            .setLength(degrees(270f))
            .setThickness(dp(6f))
            .setColor(argb(0xFF303042.toInt()))
            .build()
        val arc = LayoutElementBuilders.Arc.Builder()
            .setAnchorAngle(degrees(135f))
            .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
            .addContent(track)
            .addContent(ring)
            .build()
        val label = LayoutElementBuilders.Text.Builder()
            .setText(localized().getString(app.stegvis.wear.R.string.tile_label))
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder().setSize(sp(12f)).setColor(argb(0xFFAAA8B9.toInt())).build())
            .build()
        val number = LayoutElementBuilders.Text.Builder()
            .setText(formatSteps(steps))
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(26f))
                    .setColor(argb(0xFFF4F1F6.toInt()))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .build(),
            )
            .build()
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(label)
            .addContent(number)
            .build()
        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setClickable(ModifiersBuilders.Clickable.Builder().setId("open").setOnClick(
                    androidx.wear.protolayout.ActionBuilders.LaunchAction.Builder().setAndroidActivity(
                        androidx.wear.protolayout.ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName).setClassName(MainActivity::class.java.name).build()).build()).build())
                .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(0xFF191925.toInt())).build()).build())
            .addContent(arc)
            .addContent(column)
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(TILE_RES_VERSION)
            .setFreshnessIntervalMillis(FRESH_MS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { c -> c.set(ResourceBuilders.Resources.Builder().setVersion(TILE_RES_VERSION).build()); "res" }
}
