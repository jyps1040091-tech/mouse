package com.example.phonepad

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.min

class PadView(ctx: Context) : View(ctx) {

    class Item(val id: String, val stick: Boolean, var x: Float, var y: Float, var size: Float)

    var items = mutableListOf<Item>()
    var editMode = false
        set(v) { field = v; releaseAll(); invalidate() }
    var selected: Item? = null
    var onSelect: ((Item?) -> Unit)? = null
    var onState: ((PadState) -> Unit)? = null

    private val pointerItem = HashMap<Int, Item>()
    private val offset = HashMap<Int, PointF>()
    private val pressed = HashSet<String>()
    private val stickVec = HashMap<String, PointF>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    init { items = defaultLayout() }

    fun defaultLayout() = mutableListOf(
        Item("LS", true, 0.14f, 0.62f, 0.34f),
        Item("RS", true, 0.62f, 0.72f, 0.28f),
        Item("UP", false, 0.14f, 0.30f, 0.11f), Item("DOWN", false, 0.14f, 0.44f, 0.11f),
        Item("LEFT", false, 0.06f, 0.37f, 0.11f), Item("RIGHT", false, 0.22f, 0.37f, 0.11f),
        Item("A", false, 0.90f, 0.62f, 0.14f), Item("B", false, 0.96f, 0.48f, 0.14f),
        Item("X", false, 0.84f, 0.46f, 0.14f), Item("Y", false, 0.90f, 0.32f, 0.14f),
        Item("LB", false, 0.08f, 0.10f, 0.13f), Item("LT", false, 0.22f, 0.10f, 0.13f),
        Item("RB", false, 0.92f, 0.10f, 0.13f), Item("RT", false, 0.78f, 0.10f, 0.13f),
        Item("BACK", false, 0.42f, 0.12f, 0.09f), Item("START", false, 0.54f, 0.12f, 0.09f)
    )

    fun toJson(): String {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("stick", it.stick)
            .put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("s", it.size.toDouble())) }
        return a.toString()
    }

    fun fromJson(s: String) {
        try {
            val a = JSONArray(s); val l = mutableListOf<Item>()
            for (i in 0 until a.length()) { val o = a.getJSONObject(i)
                l.add(Item(o.getString("id"), o.getBoolean("stick"),
                    o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("s").toFloat())) }
            if (l.isNotEmpty()) items = l
        } catch (_: Exception) {}
        invalidate()
    }

    private fun dia(i: Item) = i.size * min(width, height)
    private fun cx(i: Item) = i.x * width
    private fun cy(i: Item) = i.y * height

    private fun hit(x: Float, y: Float): Item? =
        items.reversed().firstOrNull { hypot(x - cx(it), y - cy(it)) <= dia(it) / 2 * 1.15f }

    private fun releaseAll() {
        pointerItem.clear(); pressed.clear(); stickVec.clear(); offset.clear(); emit()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex; val id = e.getPointerId(i)
                val hitItem = hit(e.getX(i), e.getY(i))
                if (hitItem != null) {
                    pointerItem[id] = hitItem
                    if (editMode) {
                        selected = hitItem; onSelect?.invoke(hitItem)
                        offset[id] = PointF(cx(hitItem) - e.getX(i), cy(hitItem) - e.getY(i))
                    } else update(hitItem, e.getX(i), e.getY(i))
                } else if (editMode) { selected = null; onSelect?.invoke(null) }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i); val m = pointerItem[id] ?: continue
                if (editMode) {
                    val o = offset[id] ?: PointF()
                    m.x = ((e.getX(i) + o.x) / width).coerceIn(0.03f, 0.97f)
                    m.y = ((e.getY(i) + o.y) / height).coerceIn(0.05f, 0.95f)
                } else update(m, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                pointerItem.remove(id)?.let { if (!editMode) release(it) }
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        if (!editMode) emit()
        invalidate()
        return true
    }

    private fun update(m: Item, x: Float, y: Float) {
        if (m.stick) {
            val r = dia(m) / 2
            var dx = (x - cx(m)) / r; var dy = (y - cy(m)) / r
            val len = hypot(dx, dy); if (len > 1f) { dx /= len; dy /= len }
            stickVec[m.id] = PointF(dx, dy)
        } else pressed.add(m.id)
    }

    private fun release(m: Item) { if (m.stick) stickVec.remove(m.id) else pressed.remove(m.id) }

    private fun emit() {
        val s = PadState()
        var up = false; var dn = false; var lf = false; var rt = false
        for (id in pressed) when (id) {
            "A" -> s.buttons = s.buttons or (1 shl 0)
            "B" -> s.buttons = s.buttons or (1 shl 1)
            "X" -> s.buttons = s.buttons or (1 shl 2)
            "Y" -> s.buttons = s.buttons or (1 shl 3)
            "LB" -> s.buttons = s.buttons or (1 shl 4)
            "RB" -> s.buttons = s.buttons or (1 shl 5)
            "BACK" -> s.buttons = s.buttons or (1 shl 6)
            "START" -> s.buttons = s.buttons or (1 shl 7)
            "LT" -> s.lt = 255
            "RT" -> s.rt = 255
            "UP" -> up = true
            "DOWN" -> dn = true
            "LEFT" -> lf = true
            "RIGHT" -> rt = true
        }
        s.hat = when {
            up && rt -> 2; dn && rt -> 4; dn && lf -> 6; up && lf -> 8
            up -> 1; rt -> 3; dn -> 5; lf -> 7; else -> 0
        }
        stickVec["LS"]?.let { s.lx = (it.x * 127).toInt(); s.ly = (it.y * 127).toInt() }
        stickVec["RS"]?.let { s.rx = (it.x * 127).toInt(); s.ry = (it.y * 127).toInt() }
        onState?.invoke(s)
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(Color.parseColor("#101418"))
        for (m in items) {
            val x = cx(m); val y = cy(m); val r = dia(m) / 2
            val sel = editMode && m === selected
            if (m.stick) {
                p.style = Paint.Style.FILL; p.color = Color.parseColor("#22FFFFFF"); c.drawCircle(x, y, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = if (sel) 6f else 3f
                p.color = if (sel) Color.YELLOW else Color.GRAY; c.drawCircle(x, y, r, p)
                val v = stickVec[m.id]
                p.style = Paint.Style.FILL; p.color = Color.parseColor("#88FFFFFF")
                c.drawCircle(x + (v?.x ?: 0f) * r, y + (v?.y ?: 0f) * r, r * 0.4f, p)
            } else {
                val on = pressed.contains(m.id)
                p.style = Paint.Style.FILL
                p.color = if (on) Color.parseColor("#CCFFFFFF") else Color.parseColor("#55FFFFFF")
                c.drawCircle(x, y, r, p)
                if (sel) { p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = Color.YELLOW; c.drawCircle(x, y, r, p) }
                p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textAlign = Paint.Align.CENTER
                p.textSize = r * 0.6f; c.drawText(m.id, x, y + p.textSize / 3, p)
            }
        }
    }
}
