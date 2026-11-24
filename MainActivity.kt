package com.example.toibot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var commandEngine: ToibotCommandEngine
    private lateinit var miniOrbPreview: ToibotOrbView
    private lateinit var statusLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commandEngine = ToibotCommandEngine(this)

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#04090C"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 48, 40, 48)
        }

        miniOrbPreview = ToibotOrbView(this).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180)
            state = ToibotOrbView.OrbState.LISTENING
        }

        statusLabel = TextView(this).apply {
            text = "T.O.I.B.O.T Gibrid Tizim (Onlayn + Oflayn)"
            setTextColor(Color.parseColor("#2DD4BF"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 24)
        }

        val apiInput = EditText(this).apply {
            hint = "Gemini API kaliti (onlayn AI uchun ixtiyoriy)"
            setText(commandEngine.getApiKey())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val saveApiBtn = Button(this).apply {
            text = "API kalitni saqlash"
            setOnClickListener {
                commandEngine.saveApiKey(apiInput.text.toString())
                statusLabel.text = "API kalit saqlandi."
            }
        }

        val mixUrlInput = EditText(this).apply {
            hint = "YouTube miks havolasi (ixtiyoriy, masalan: youtube.com/watch?v=...)"
            setText(commandEngine.getCustomMixUrl())
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

        val saveMixBtn = Button(this).apply {
            text = "Mening miksim havolasini saqlash"
            setOnClickListener {
                commandEngine.saveCustomMixUrl(mixUrlInput.text.toString())
                statusLabel.text = "YouTube miks havolasi saqlandi."
            }
        }

        val overlayPermBtn = Button(this).apply {
            text = "1. Ekran ustida kichik sfera ruxsati"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    statusLabel.text = "Ekran usti ruxsati allaqachon berilgan."
                }
            }
        }

        val accPermBtn = Button(this).apply {
            text = "2. Skrinshot va avto-boshqaruv ruxsati"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val startToibotBtn = Button(this).apply {
            text = "T.O.I.B.O.T Doimiy Eshitishni Ishga Tushirish"
            setOnClickListener {
                requestAllPermissionsAndStart()
            }
        }

        val stopToibotBtn = Button(this).apply {
            text = "To'xtatish"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ToibotForegroundService::class.java))
                statusLabel.text = "T.O.I.B.O.T to'xtatildi."
            }
        }

        val featuresInfo = TextView(this).apply {
            text = """
                Qo'llab-quvvatlanadigan ovozli buyruqlar:
                • "YouTube'da ... qo'shig'ini qo'y" / "Birinchi videoni qo'y"
                • "YouTube'da mening miksimni qo'y"
                • "Alisherga qo'ng'iroq qil" (Lotin/Kirill/Ruscha kontaktlar)
                • "Алишер ga SMS yoz: ertaga ko'rishamiz" (tasdiqlashni kutadi)
                • "Telegramda xabar yoz: salom hammaga" (tasdiqlash bilan)
                • "e.maktab ga kir" / "Googledan ob-havoni qidir" / "YouTube qidir"
                • "Skrinshot ol" / "Orqaga qayt" / "Bosh ekran" / "Pastga tushir"
                • "Chiroqni yoq" / "Chiroqni o'chir" / "Vibratsiya"
                • "5 daqiqaga taymer qo'y" / "Budilnik 07:30"
                • "25 ga 15 ni qo'sh" / "Musiqani to'xtat" / "Ovozni balandlat"
                • "... eslab qol" / "Eslatmalarni ayt" / "Xotirani tozala"
            """.trimIndent()
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            setPadding(0, 32, 0, 0)
        }

        container.addView(miniOrbPreview)
        container.addView(statusLabel)
        container.addView(apiInput)
        container.addView(saveApiBtn)
        container.addView(mixUrlInput)
        container.addView(saveMixBtn)
        container.addView(overlayPermBtn)
        container.addView(accPermBtn)
        container.addView(startToibotBtn)
        container.addView(stopToibotBtn)
        container.addView(featuresInfo)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun requestAllPermissionsAndStart() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        } else {
            startToibotService()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            startToibotService()
        }
    }

    private fun startToibotService() {
        val intent = Intent(this, ToibotForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        statusLabel.text = "T.O.I.B.O.T ishga tushdi! Yuqoridagi mini-sfera faol."
    }
}
