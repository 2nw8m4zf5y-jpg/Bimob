package com.bimob.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round
import kotlin.math.sqrt

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("bimob_projects", MODE_PRIVATE) }
    private var projectName = "Новый проект"
    private val walls = mutableListOf<Wall>()
    private lateinit var canvasView: PlanCanvas
    private lateinit var status: TextView

    enum class DrawingTool { WALL, SELECT }

    data class Wall(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val thickness: Float = 0.2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun baseLayout(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 64, 24, 48)
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        return root
    }

    private fun showHome() {
        val root = baseLayout()
        val title = TextView(this).apply {
            text = "BIMOB"
            textSize = 30f
            setTextColor(Color.rgb(24, 39, 61))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 8)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Проектирование зданий • 2D / 3D"
            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        })
        addButton(root, "＋  Создать проект") { promptNewProject() }
        addButton(root, "Открыть проекты") { showProjects() }
        root.addView(TextView(this).apply {
            text = "Начинаем с точного 2D-плана. 3D-модуль, конструкции и расчёты будут добавляться поверх единой модели проекта."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(4, 24, 4, 0)
        })
        setContentView(root)
    }

    private fun promptNewProject() {
        val input = EditText(this).apply { hint = "Название проекта"; setText("Мой проект") }
        AlertDialog.Builder(this).setTitle("Новый проект").setView(input)
            .setPositiveButton("Создать") { _, _ ->
                projectName = input.text.toString().trim().ifBlank { "Мой проект" }
                walls.clear()
                openEditor()
            }.setNegativeButton("Отмена", null).show()
    }

    private fun showProjects() {
        val names = prefs.all.keys.sorted()
        if (names.isEmpty()) {
            AlertDialog.Builder(this).setMessage("Сохранённых проектов пока нет.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Проекты").setItems(names.toTypedArray()) { _, which ->
            projectName = names[which]
            loadProject(projectName)
            openEditor()
        }.setNegativeButton("Закрыть", null).show()
    }

    private fun saveProject() {
        val json = JSONObject().put("name", projectName).put("walls", JSONArray().apply {
            walls.forEach { w ->
                put(JSONObject().put("x1", w.x1).put("y1", w.y1).put("x2", w.x2).put("y2", w.y2).put("thickness", w.thickness))
            }
        })
        prefs.edit().putString(projectName, json.toString()).apply()
        status.text = "Сохранено: ${walls.size} стен"
    }

    private fun loadProject(name: String) {
        walls.clear()
        val raw = prefs.getString(name, null) ?: return
        try {
            val json = JSONObject(raw)
            val arr = json.optJSONArray("walls") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val w = arr.getJSONObject(i)
                walls.add(Wall(w.getDouble("x1").toFloat(), w.getDouble("y1").toFloat(),
                    w.getDouble("x2").toFloat(), w.getDouble("y2").toFloat(), w.optDouble("thickness", 0.2).toFloat()))
            }
        } catch (_: Exception) { }
    }

    private fun openEditor() {
        val root = baseLayout()
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = projectName; textSize = 20f; setTextColor(Color.rgb(25, 40, 60))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val save = Button(this).apply { text = "Сохранить"; setOnClickListener { saveProject() } }
        header.addView(save)
        root.addView(header)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addTool(tools, "Стена") { canvasView.tool = DrawingTool.WALL; status.text = "Проведи пальцем, чтобы поставить стену" }
        addTool(tools, "Выбор") { canvasView.tool = DrawingTool.SELECT; status.text = "Режим просмотра" }
        addTool(tools, "Очистить") {
            AlertDialog.Builder(this).setMessage("Удалить все стены на плане?")
                .setPositiveButton("Удалить") { _, _ -> walls.clear(); canvasView.invalidate(); status.text = "План очищен" }
                .setNegativeButton("Отмена", null).show()
        }
        root.addView(tools)

        canvasView = PlanCanvas(this, walls)
        root.addView(canvasView, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            text = "Сетка: 0,5 м • Привязка: 0,1 м • Стены: ${walls.size}"
            textSize = 13f; setTextColor(Color.DKGRAY); setPadding(4, 12, 4, 4)
        }
        root.addView(status)
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addTool(bottom, "← Проекты") { showHome() }
        addTool(bottom, "Параметры") {
            AlertDialog.Builder(this).setTitle("Параметры проекта")
                .setMessage("Единицы: метры\nТолщина стены по умолчанию: 200 мм\nВысота помещения: 2,7 м (пока справочная)")
                .setPositiveButton("OK", null).show()
        }
        root.addView(bottom)
        setContentView(root)
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = label; setOnClickListener { action() } },
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
    }
    private fun addTool(row: LinearLayout, label: String, action: () -> Unit) {
        row.addView(Button(this).apply { text = label; setOnClickListener { action() } },
            LinearLayout.LayoutParams(0, -2, 1f))
    }

    inner class PlanCanvas(activity: Activity, private val model: MutableList<Wall>) : View(activity) {
        var tool = DrawingTool.WALL
        private val gridPaint = Paint().apply { color = Color.rgb(229, 233, 238); strokeWidth = 1f }
        private val axisPaint = Paint().apply { color = Color.rgb(205, 212, 221); strokeWidth = 2f }
        private val wallPaint = Paint().apply { color = Color.rgb(38, 57, 78); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        private val previewPaint = Paint().apply { color = Color.rgb(46, 125, 220); strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }
        private val labelPaint = Paint().apply { color = Color.rgb(55, 75, 95); textSize = 26f; isAntiAlias = true }
        private val meterPaint = Paint().apply { color = Color.rgb(115, 130, 145); textSize = 20f; isAntiAlias = true }
        private val scale = 100f // 100 px per meter
        private var start: Pair<Float, Float>? = null
        private var end: Pair<Float, Float>? = null

        private fun snap(px: Float, py: Float): Pair<Float, Float> {
            val mx = ((px - width / 2f) / scale)
            val my = ((height / 2f - py) / scale)
            return (round(mx * 10f) / 10f) to (round(my * 10f) / 10f)
        }
        private fun screen(x: Float, y: Float): Pair<Float, Float> = (width / 2f + x * scale) to (height / 2f - y * scale)

        override fun onDraw(c: Canvas) {
            super.onDraw(c)
            c.drawColor(Color.WHITE)
            val step = scale * 0.5f
            var x = width / 2f % step
            while (x < width) { c.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
            var y = height / 2f % step
            while (y < height) { c.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
            c.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), axisPaint)
            c.drawLine(0f, height / 2f, width.toFloat(), height / 2f, axisPaint)
            c.drawText("0", width / 2f + 8, height / 2f - 8, meterPaint)
            model.forEach { drawWall(c, it, wallPaint) }
            if (start != null && end != null) {
                val a = start!!; val b = end!!
                val temporary = Wall(a.first, a.second, b.first, b.second)
                drawWall(c, temporary, previewPaint)
                val length = sqrt((b.first-a.first)*(b.first-a.first) + (b.second-a.second)*(b.second-a.second))
                val p = screen(b.first, b.second)
                c.drawText(String.format("%.2f м", length), p.first + 12, p.second - 12, labelPaint)
            }
        }

        private fun drawWall(c: Canvas, w: Wall, p: Paint) {
            val a = screen(w.x1, w.y1); val b = screen(w.x2, w.y2)
            c.drawLine(a.first, a.second, b.first, b.second, p)
            val length = sqrt((w.x2-w.x1)*(w.x2-w.x1) + (w.y2-w.y1)*(w.y2-w.y1))
            c.drawText(String.format("%.2f", length), (a.first+b.first)/2 + 8, (a.second+b.second)/2 - 8, labelPaint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (tool != DrawingTool.WALL) return true
            when(event.action) {
                MotionEvent.ACTION_DOWN -> { start = snap(event.x, event.y); end = start; invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { end = snap(event.x, event.y); invalidate(); return true }
                MotionEvent.ACTION_UP -> {
                    end = snap(event.x, event.y)
                    val a = start; val b = end
                    if (a != null && b != null && (a.first != b.first || a.second != b.second)) {
                        model.add(Wall(a.first, a.second, b.first, b.second))
                        status.text = "Стена добавлена • ${model.size} стен • длина с привязкой 10 см"
                    }
                    start = null; end = null; invalidate(); return true
                }
            }
            return true
        }
    }
}
