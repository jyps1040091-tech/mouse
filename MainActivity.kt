package com.example.phonepad

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var pad: PadView
    private lateinit var hid: HidGamepad
    private lateinit var status: TextView
    private lateinit var seek: SeekBar
    private lateinit var editRow: LinearLayout

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hid.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        pad = PadView(this)
        getSharedPreferences("pad", MODE_PRIVATE).getString("layout", null)?.let { pad.fromJson(it) }

        status = TextView(this).apply { setTextColor(0xFFFFFFFF.toInt()); text = "啟動中…"; setPadding(16, 8, 16, 8) }
        hid = HidGamepad(this) { msg -> runOnUiThread { status.text = msg } }
        pad.onState = { hid.send(it) }

        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("可被搜尋") {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300))
            })
            addView(btn("連線") {
                val l = hid.bonded()
                AlertDialog.Builder(this@MainActivity).setTitle("選擇 B 手機")
                    .setItems(l.map { it.name ?: it.address }.toTypedArray()) { _, i -> hid.connect(l[i]) }.show()
            })
            addView(btn("編輯") {
                pad.editMode = !pad.editMode
                editRow.visibility = if (pad.editMode) View.VISIBLE else View.GONE
            })
        }

        seek = SeekBar(this).apply {
            min = 6; max = 45; progress = 14
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    if (fromUser) pad.selected?.let { it.size = v / 100f; pad.invalidate() }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        pad.onSelect = { sel -> if (sel != null) seek.progress = (sel.size * 100).toInt() }

        editRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(TextView(this@MainActivity).apply { text = "大小"; setTextColor(0xFFFFFFFF.toInt()) })
            addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
            addView(btn("儲存") {
                getSharedPreferences("pad", MODE_PRIVATE).edit().putString("layout", pad.toJson()).apply()
                Toast.makeText(this@MainActivity, "已儲存", Toast.LENGTH_SHORT).show()
            })
            addView(btn("重設") { pad.items = pad.defaultLayout(); pad.invalidate() })
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0x66000000)
            addView(bar); addView(editRow)
        }
        val root = FrameLayout(this).apply {
            addView(pad, FrameLayout.LayoutParams(-1, -1))
            addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        }
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 31) {
            perms.launch(arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_SCAN))
        } else hid.start()
    }

    override fun onDestroy() { hid.stop(); super.onDestroy() }
}
