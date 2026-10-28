package com.elsewhere.location.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.elsewhere.location.core.GeoPoint
import com.elsewhere.location.core.MapLayer
import org.osmdroid.api.IGeoPoint
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint as OsmGeoPoint

internal fun GeoPoint.toOsm(): OsmGeoPoint = OsmGeoPoint(latitude, longitude)

internal fun IGeoPoint.toCore(): GeoPoint = GeoPoint(latitude, longitude)

internal fun GeoPoint.forLayer(layer: MapLayer): OsmGeoPoint =
    com.elsewhere.location.core.ChineseGeo.toDisplay(this, layer).toOsm()

internal fun IGeoPoint.fromLayer(layer: MapLayer): GeoPoint =
    com.elsewhere.location.core.ChineseGeo.toWgs84(this.toCore(), layer)

internal fun mapTileSource(layer: MapLayer) = when (layer) {
    MapLayer.GAODE -> GaodeTileSource
    MapLayer.OSM -> OsmTileSource
}

/**
 * AMap's public raster tile endpoint is reached by query parameters rather than
 * the usual /z/x/y path, so osmdroid needs a tiny custom source.
 */
private object GaodeTileSource : OnlineTileSourceBase(
    "Gaode",
    3,
    19,
    256,
    "",
    arrayOf("https://webrd0"),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val host = ((x + y) % 4) + 1
        return "https://webrd0$host.is.autonavi.com/appmaptile" +
            "?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
    }
}

private val OsmTileSource: OnlineTileSourceBase = TileSourceFactory.MAPNIK

internal class WaypointDrawable(
    context: Context,
    private val number: Int,
    private val isStart: Boolean,
    private val isEnd: Boolean,
) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = Color.parseColor("#16231B")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 10f * density
    }
    private val sizePx = (28 * density).toInt()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val radius = sizePx / 2f - 2f * density
        fillPaint.color = when {
            isStart -> Color.parseColor("#16231B")
            isEnd -> Color.parseColor("#ED5A2D")
            else -> Color.parseColor("#FBF8EF")
        }
        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        textPaint.color = when {
            isStart || isEnd -> Color.parseColor("#FBF8EF")
            else -> Color.parseColor("#16231B")
        }
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), cx, baseline, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}

internal class MockBeaconDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
    }
    private val sizePx = (32 * density).toInt()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = sizePx / 2f - 3f * density
        fill.color = Color.parseColor("#22ED5A2D")
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = Color.parseColor("#FBF8EF")
        stroke.strokeWidth = 2.4f * density
        canvas.drawCircle(cx, cy, r * 0.64f, stroke)
        fill.color = Color.parseColor("#ED5A2D")
        canvas.drawCircle(cx, cy, r * 0.48f, fill)
        fill.color = Color.parseColor("#16231B")
        canvas.drawCircle(cx, cy, r * 0.14f, fill)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
}
