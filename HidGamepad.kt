package com.example.phonepad

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import java.util.concurrent.Executors

class PadState(
    var buttons: Int = 0, var hat: Int = 0,
    var lx: Int = 0, var ly: Int = 0, var rx: Int = 0, var ry: Int = 0,
    var lt: Int = 0, var rt: Int = 0
)

@SuppressLint("MissingPermission")
class HidGamepad(private val ctx: Context, private val onStatus: (String) -> Unit) {

    companion object {
        val DESC = byteArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1.toByte(), 0x01,
            0x85.toByte(), 0x01,
            // 16 buttons
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95.toByte(), 0x10, 0x81.toByte(), 0x02,
            // hat switch (0 = neutral, 1..8)
            0x05, 0x01, 0x09, 0x39, 0x15, 0x01, 0x25, 0x08, 0x35, 0x00,
            0x46, 0x3B, 0x01, 0x65, 0x14, 0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x42,
            // 4-bit padding
            0x75, 0x04, 0x95.toByte(), 0x01, 0x81.toByte(), 0x03,
            // X Y Z Rz sticks, signed 8-bit
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35,
            0x15, 0x81.toByte(), 0x25, 0x7F, 0x75, 0x08, 0x95.toByte(), 0x04, 0x81.toByte(), 0x02,
            // Rx Ry triggers, unsigned 8-bit
            0x09, 0x33, 0x09, 0x34, 0x15, 0x00, 0x26, 0xFF.toByte(), 0x00,
            0x75, 0x08, 0x95.toByte(), 0x02, 0x81.toByte(), 0x02,
            0xC0.toByte()
        )
    }

    private val adapter: BluetoothAdapter =
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val last = ByteArray(9)

    private val sdp = BluetoothHidDeviceAppSdpSettings(
        "PhonePad", "Gamepad", "PhonePad", BluetoothHidDevice.SUBCLASS2_GAMEPAD, DESC
    )

    private val cb = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onStatus(if (registered) "HID 已註冊,等待 B 連線" else "HID 未註冊")
        }
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> { host = device; onStatus("已連線:${device.name}") }
                BluetoothProfile.STATE_DISCONNECTED -> { if (host == device) host = null; onStatus("已斷線") }
            }
        }
    }

    fun start() {
        adapter.getProfileProxy(ctx, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = proxy as BluetoothHidDevice
                    hid!!.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(), cb)
                }
            }
            override fun onServiceDisconnected(profile: Int) { hid = null }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun bonded(): List<BluetoothDevice> = adapter.bondedDevices.toList()
    fun connect(d: BluetoothDevice) { hid?.connect(d) }

    fun send(s: PadState) {
        val h = host ?: return
        val b = byteArrayOf(
            (s.buttons and 0xFF).toByte(), ((s.buttons shr 8) and 0xFF).toByte(),
            (s.hat and 0x0F).toByte(),
            s.lx.toByte(), s.ly.toByte(), s.rx.toByte(), s.ry.toByte(),
            s.lt.toByte(), s.rt.toByte()
        )
        if (b.contentEquals(last)) return
        b.copyInto(last)
        hid?.sendReport(h, 1, b)
    }

    fun stop() {
        try { hid?.unregisterApp(); adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid) } catch (_: Exception) {}
    }
}
