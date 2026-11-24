package com.example.toibot

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PendingConfirmation(
    val descriptionForSpeech: String,
    val executeAction: () -> String
)

class ToibotCommandEngine(private val context: Context) {

    var pendingAction: PendingConfirmation? = null
    private val memoryPrefs = context.getSharedPreferences("toibot_memory", Context.MODE_PRIVATE)

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun saveApiKey(key: String) {
        memoryPrefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getApiKey(): String = memoryPrefs.getString("gemini_api_key", "") ?: ""

    fun saveCustomMixUrl(url: String) {
        memoryPrefs.edit().putString("custom_mix_url", url.trim()).apply()
    }

    fun getCustomMixUrl(): String = memoryPrefs.getString("custom_mix_url", "") ?: ""

    fun processCommand(
        rawSpeech: String,
        onSpeak: (String) -> Unit,
        onConfirmationRequested: (String) -> Unit
    ) {
        val speech = rawSpeech.trim()
        val lower = speech.lowercase()

        // 1. Tasdiqlash kutilayotgan bo'lsa
        val currentPending = pendingAction
        if (currentPending != null) {
            when {
                lower.contains("ha") || lower.contains("tasdiqlayman") ||
                lower.contains("yubor") || lower.contains("mayli") ||
                lower.contains("да") || lower.contains("yes") -> {
                    pendingAction = null
                    val resultMsg = currentPending.executeAction()
                    onSpeak(resultMsg)
                    return
                }
                lower.contains("yo'q") || lower.contains("bekor") ||
                lower.contains("to'xta") || lower.contains("нет") || lower.contains("no") -> {
                    pendingAction = null
                    onSpeak("Bekor qilindi, T.O.I.B.O.T kutish rejimida.")
                    return
                }
            }
        }

        // 2. YouTube'da mening miksimni qo'yish
        if (lower.contains("mening miksim") || lower.contains("miksimni qo'y") ||
            lower.contains("miksimni yoq") || lower.contains("my mix")
        ) {
            val savedMixUrl = getCustomMixUrl()
            if (savedMixUrl.isNotBlank()) {
                openUrl(savedMixUrl)
                ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                onSpeak("Sizning shaxsiy miksingiz YouTube'da ishga tushirilmoqda.")
            } else {
                val mixQuery = URLEncoder.encode("My Mix music playlist", "UTF-8")
                openUrl("https://www.youtube.com/results?search_query=$mixQuery")
                ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                onSpeak("YouTube'da sizning miksingiz qidirilmoqda va birinchi natija ochilmoqda.")
            }
            return
        }

        // 3. YouTube'da qo'shiq yoki birinchi videoni topib qo'yish
        val isPlayRequest = lower.contains("qo'y") || lower.contains("quys") ||
            lower.contains("yoq") || lower.contains("ijro et") || lower.contains("play")
        if (lower.contains("youtube") && (isPlayRequest || lower.contains("qidir") || lower.contains("och"))) {
            val cleanedQuery = speech
                .replace("youtube", "", ignoreCase = true)
                .replace("da", "", ignoreCase = true)
                .replace("qo'shig'ini", "", ignoreCase = true)
                .replace("qo'shiq", "", ignoreCase = true)
                .replace("qushiq", "", ignoreCase = true)
                .replace("birinchi videoni", "", ignoreCase = true)
                .replace("videoni", "", ignoreCase = true)
                .replace("qo'y", "", ignoreCase = true)
                .replace("quys", "", ignoreCase = true)
                .replace("yoq", "", ignoreCase = true)
                .replace("qidir", "", ignoreCase = true)
                .replace("och", "", ignoreCase = true)
                .trim()

            if (cleanedQuery.isNotEmpty()) {
                val encoded = URLEncoder.encode(cleanedQuery, "UTF-8")
                openUrl("https://www.youtube.com/results?search_query=$encoded")
                if (isPlayRequest || lower.contains("birinchi")) {
                    ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                    onSpeak("YouTube'da $cleanedQuery topildi va birinchi video ishga tushirilmoqda.")
                } else {
                    onSpeak("YouTube'da $cleanedQuery qidirildi.")
                }
            } else {
                openUrl("https://www.youtube.com")
                if (isPlayRequest || lower.contains("birinchi")) {
                    ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                    onSpeak("YouTube ochildi va birinchi video tanlanmoqda.")
                } else {
                    onSpeak("YouTube ochildi.")
                }
            }
            return
        }

        // Agar foydalanuvchi shunchaki "birinchi videoni qo'y" yoki "... qo'shig'ini qo'y" desa
        if ((lower.contains("birinchi video") || lower.contains("qo'shiq") || lower.contains("qushiq")) && isPlayRequest) {
            val songQuery = speech
                .replace("birinchi videoni", "", ignoreCase = true)
                .replace("birinchi video", "", ignoreCase = true)
                .replace("qo'shig'ini", "", ignoreCase = true)
                .replace("qo'shiqni", "", ignoreCase = true)
                .replace("qo'shiq", "", ignoreCase = true)
                .replace("qushiq", "", ignoreCase = true)
                .replace("qo'y", "", ignoreCase = true)
                .replace("quys", "", ignoreCase = true)
                .replace("yoq", "", ignoreCase = true)
                .trim()

            if (songQuery.isNotEmpty()) {
                val encoded = URLEncoder.encode(songQuery, "UTF-8")
                openUrl("https://www.youtube.com/results?search_query=$encoded")
                ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                onSpeak("$songQuery qo'shig'i YouTube'da topilib, birinchi video qo'yilmoqda.")
            } else {
                val clicked = ToibotAccessibilityService.instance?.clickFirstClickableVideoCandidate() ?: false
                if (clicked) {
                    onSpeak("Birinchi video ishga tushirildi.")
                } else {
                    openUrl("https://www.youtube.com")
                    ToibotAccessibilityService.instance?.clickFirstVideoResultAfterDelay(2400L)
                    onSpeak("YouTube ochildi va birinchi video qo'yilmoqda.")
                }
            }
            return
        }

        // 4. Skrinshot olish
        if (lower.contains("skrinshot") || lower.contains("ekranni rasmga") || lower.contains("screenshot")) {
            val acc = ToibotAccessibilityService.instance
            if (acc != null) {
                acc.takeSystemScreenshot { ok ->
                    onSpeak(if (ok) "Skrinshot olindi." else "Skrinshot olib bo'lmadi.")
                }
            } else {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                onSpeak("Skrinshot olish uchun Maxsus imkoniyatlar bo'limidan T.O.I.B.O.T xizmatini yoqing.")
            }
            return
        }

        // 5. Ekran navigatsiyasi
        if (lower.contains("orqaga qayt") || lower.contains("nazad")) {
            val ok = ToibotAccessibilityService.instance?.goBack() ?: false
            onSpeak(if (ok) "Orqaga qaytdim." else "Maxsus xizmat faol emas.")
            return
        }
        if (lower.contains("bosh ekran") || lower.contains("domoy")) {
            val ok = ToibotAccessibilityService.instance?.goHome() ?: false
            onSpeak(if (ok) "Bosh ekranga qaytdim." else "Maxsus xizmat faol emas.")
            return
        }
        if (lower.contains("pastga tushir") || lower.contains("skroll")) {
            val ok = ToibotAccessibilityService.instance?.scrollDownActiveScreen() ?: false
            onSpeak(if (ok) "Sahifa pastga aylantirildi." else "Sahifani aylantirib bo'lmadi.")
            return
        }

        // 6. Google, e.maktab va boshqa veb-sahifalar
        if (lower.contains("e.maktab") || lower.contains("emaktab") || lower.contains("e maktab")) {
            openUrl("https://login.emaktab.uz")
            onSpeak("e.maktab sahifasi ochildi.")
            return
        }
        if (lower.contains("googledan") || lower.contains("google dan") || lower.contains("qidir")) {
            val query = speech
                .replace("googledan", "", ignoreCase = true)
                .replace("google dan", "", ignoreCase = true)
                .replace("qidir", "", ignoreCase = true)
                .trim()
            if (query.isNotEmpty()) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                openUrl("https://www.google.com/search?q=$encoded")
                onSpeak("Google orqali $query bo'yicha qidiruv ochildi.")
            } else {
                openUrl("https://www.google.com")
                onSpeak("Google ochildi.")
            }
            return
        }
        if (lower.contains("sayt och") || lower.contains("havola och")) {
            val urlMatch = Regex("(https?://\S+|[a-zA-Z0-9.-]+\.(uz|com|ru|org|net))").find(speech)?.value
            if (urlMatch != null) {
                val fullUrl = if (urlMatch.startsWith("http")) urlMatch else "https://$urlMatch"
                openUrl(fullUrl)
                onSpeak("$fullUrl sahifasi ochildi.")
                return
            }
        }

        // 7. Messenjerlarga xabar yozish (Telegram, WhatsApp)
        if ((lower.contains("telegram") || lower.contains("whatsapp") || lower.contains("vatsap")) &&
            (lower.contains("yoz") || lower.contains("xabar") || lower.contains("habar"))
        ) {
            handleMessengerCommand(speech, onSpeak, onConfirmationRequested)
            return
        }

        // 8. Oddiy SMS yuborish (tasdiqlash bilan)
        if (lower.contains("sms") || lower.contains("xabar yoz") || lower.contains("habar yoz")) {
            handleSmsCommand(speech, onSpeak, onConfirmationRequested)
            return
        }

        // 9. Qo'ng'iroq qilish (Lotin/Kirill/Ruscha kontakt qidiruvi)
        if (lower.contains("qo'ng'iroq") || lower.contains("qongiroq") ||
            lower.contains("telefon qil") || lower.contains("zvonok") || lower.contains("позвони")
        ) {
            handleCallCommand(speech, onSpeak, onConfirmationRequested)
            return
        }

        // 10. Fonar (Chiroq)
        if (lower.contains("fonar") || lower.contains("chiroq")) {
            val turnOn = lower.contains("yoq") || lower.contains("yondir")
            toggleFlashlight(turnOn)
            onSpeak(if (turnOn) "Chiroq yoqildi." else "Chiroq o'chirildi.")
            return
        }

        // 11. Vibratsiya
        if (lower.contains("vibratsiya") || lower.contains("titra")) {
            vibrateBriefly()
            onSpeak("Vibratsiya signali bajarildi.")
            return
        }

        // 12. Media boshqaruvi
        if (lower.contains("musiqani to'xtat") || lower.contains("pauza") || lower.contains("davom ettir")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            onSpeak("Media holati o'zgartirildi.")
            return
        }
        if (lower.contains("keyingi musiqa") || lower.contains("keyingi trek")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            onSpeak("Keyingi trekka o'tildi.")
            return
        }
        if (lower.contains("oldingi musiqa") || lower.contains("oldingi trek")) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            onSpeak("Oldingi trekka o'tildi.")
            return
        }

        // 13. Ovoz balandligi va ovoz rejimi
        if (lower.contains("ovozni balandlat") || lower.contains("ovozni ko'tar")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            onSpeak("Ovoz balandlatildi.")
            return
        }
        if (lower.contains("ovozni pasaytir") || lower.contains("ovozni tushir")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            onSpeak("Ovoz pasaytirildi.")
            return
        }
        if (lower.contains("jim rejimga o'tkaz") || lower.contains("bezovta qilinmasin")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
            onSpeak("Qurilma jim rejimga o'tkazildi.")
            return
        }
        if (lower.contains("oddiy ovoz rejimi")) {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            onSpeak("Oddiy ovoz rejimi tiklandi.")
            return
        }

        // 14. Ilovalarni ochish
        if (lower.contains("och") || lower.contains("ishga tushir")) {
            if (openAppBySpokenName(lower)) {
                onSpeak("Ilova ochildi.")
                return
            }
        }

        // 15. Vaqt, sana, batareya, internet holati
        if (lower.contains("soat") || lower.contains("vaqt")) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            onSpeak("Hozir soat $time.")
            return
        }
        if (lower.contains("sana") || lower.contains("bugun nechanchi")) {
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
            onSpeak("Bugungi sana $dateStr.")
            return
        }
        if (lower.contains("batareya") || lower.contains("quvvat")) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            onSpeak("Batareya quvvati $pct foiz.")
            return
        }
        if (lower.contains("internet bormi") || lower.contains("aloqa holati")) {
            onSpeak(if (isOnline()) "Internet aloqasi faol, onlayn rejim ishlamoqda." else "Internet yo'q, T.O.I.B.O.T oflayn rejimda ishlamoqda.")
            return
        }

        // 16. Taymer va budilnik qo'yish
        if (lower.contains("taymer")) {
            val mins = Regex("\d+").find(lower)?.value?.toIntOrNull() ?: 1
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, mins * 60)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onSpeak("$mins daqiqalik taymer o'rnatildi.")
            return
        }
        if (lower.contains("budilnik")) {
            val match = Regex("(\d{1,2})[:\s](\d{2})").find(lower)
            val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
            val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onSpeak("Budilnik soat $hour:${minute.toString().padStart(2, '0')} ga qo'yildi.")
            return
        }

        // 17. Tezkor kalkulyator (oflayn)
        if (lower.contains("qo'sh") || lower.contains("ayir") || lower.contains("ko'paytir") || lower.contains("bo'l") || lower.contains("hisobla")) {
            val nums = Regex("\d+(\.\d+)?").findAll(lower).mapNotNull { it.value.toDoubleOrNull() }.toList()
            if (nums.size >= 2) {
                val res = when {
                    lower.contains("qo'sh") || lower.contains("plus") -> nums[0] + nums[1]
                    lower.contains("ayir") || lower.contains("minus") -> nums[0] - nums[1]
                    lower.contains("ko'paytir") || lower.contains("karra") -> nums[0] * nums[1]
                    lower.contains("bo'l") -> if (nums[1] != 0.0) nums[0] / nums[1] else Double.NaN
                    else -> nums.sum()
                }
                if (!res.isNaN()) {
                    val formatted = if (res % 1.0 == 0.0) res.toLong().toString() else String.format(Locale.US, "%.2f", res)
                    onSpeak("Hisob natijasi: $formatted")
                    return
                }
            }
        }

        // 18. Uzoq muddatli xotira
        if (lower.contains("eslab qol")) {
            val fact = speech.replace("eslab qol", "", ignoreCase = true).trim()
            val oldNotes = memoryPrefs.getString("saved_notes", "") ?: ""
            memoryPrefs.edit().putString("saved_notes", "$oldNotes\n- $fact").apply()
            onSpeak("Xotiraga saqlandi: $fact")
            return
        }
        if (lower.contains("nimani eslab qolgansan") || lower.contains("eslatmalar")) {
            val notes = memoryPrefs.getString("saved_notes", "") ?: ""
            onSpeak(if (notes.isBlank()) "Hozircha saqlangan eslatmalar yo'q." else "Saqlangan ma'lumotlar: $notes")
            return
        }
        if (lower.contains("xotirani tozala")) {
            memoryPrefs.edit().remove("saved_notes").apply()
            onSpeak("Saqlangan eslatmalar tozalandi.")
            return
        }

        // 19. Gibrid AI miya (Onlayn Gemini API yoki Oflayn javob)
        if (isOnline() && getApiKey().isNotBlank()) {
            val aiReply = askGeminiOnline(speech)
            onSpeak(aiReply)
        } else {
            onSpeak("Oflayn rejimda buyruq qabul qilindi: $speech.")
        }
    }

    private fun handleCallCommand(
        speech: String,
        onSpeak: (String) -> Unit,
        onConfirmationRequested: (String) -> Unit
    ) {
        val cleaned = speech
            .replace("qo'ng'iroq qil", "", ignoreCase = true)
            .replace("qongiroq qil", "", ignoreCase = true)
            .replace("telefon qil", "", ignoreCase = true)
            .replace("ga", "", ignoreCase = true)
            .trim()

        val matches = ContactMatcher.findContacts(context, cleaned)
        if (matches.isEmpty()) {
            onSpeak("$cleaned ismli kontakt topilmadi.")
            return
        }
        val target = matches.first()
        val prompt = "${target.name} raqamiga qo'ng'iroq qilaymi? Tasdiqlang."
        pendingAction = PendingConfirmation(prompt) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${target.phoneNumber}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "${target.name} ga qo'ng'iroq qilinmoqda."
        }
        onConfirmationRequested(prompt)
    }

    private fun handleSmsCommand(
        speech: String,
        onSpeak: (String) -> Unit,
        onConfirmationRequested: (String) -> Unit
    ) {
        val parts = speech.split(":", "deb", limit = 2)
        val header = parts[0]
        val messageBody = if (parts.size > 1) parts[1].trim() else "Salom!"

        val namePart = header
            .replace("sms yoz", "", ignoreCase = true)
            .replace("sms yubor", "", ignoreCase = true)
            .replace("xabar yoz", "", ignoreCase = true)
            .replace("habar yoz", "", ignoreCase = true)
            .replace("ga", "", ignoreCase = true)
            .trim()

        val matches = ContactMatcher.findContacts(context, namePart)
        if (matches.isEmpty()) {
            onSpeak("$namePart ismli kontakt topilmadi.")
            return
        }
        val target = matches.first()
        val prompt = "${target.name} ga quyidagi SMS yuborilsinmi: $messageBody. Tasdiqlaysizmi?"
        pendingAction = PendingConfirmation(prompt) {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(target.phoneNumber, null, messageBody, null, null)
            "${target.name} ga SMS yuborildi."
        }
        onConfirmationRequested(prompt)
    }

    private fun handleMessengerCommand(
        speech: String,
        onSpeak: (String) -> Unit,
        onConfirmationRequested: (String) -> Unit
    ) {
        val isTelegram = speech.contains("telegram", ignoreCase = true)
        val appName = if (isTelegram) "Telegram" else "WhatsApp"
        val pkg = if (isTelegram) "org.telegram.messenger" else "com.whatsapp"

        val parts = speech.split(":", "deb", limit = 2)
        val messageText = if (parts.size > 1) parts[1].trim() else speech

        val prompt = "$appName orqali quyidagi matn yuborilsinmi: $messageText. Tasdiqlaysizmi?"
        pendingAction = PendingConfirmation(prompt) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageText)
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(sendIntent)
                "$appName oynasi ochildi va matn tayyorlandi."
            } catch (e: Exception) {
                "$appName ilovasi qurilmada topilmadi."
            }
        }
        onConfirmationRequested(prompt)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleFlashlight(enable: Boolean) {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull() ?: return
            cm.setTorchMode(cameraId, enable)
        } catch (_: Exception) {
        }
    }

    private fun vibrateBriefly() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(180)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun openAppBySpokenName(lower: String): Boolean {
        val intent = when {
            lower.contains("kamera") -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            lower.contains("telegram") -> context.packageManager.getLaunchIntentForPackage("org.telegram.messenger")
            lower.contains("whatsapp") || lower.contains("vatsap") -> context.packageManager.getLaunchIntentForPackage("com.whatsapp")
            lower.contains("youtube") -> context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            lower.contains("sozlamalar") -> Intent(Settings.ACTION_SETTINGS)
            lower.contains("wi-fi") || lower.contains("wifi") -> Intent(Settings.ACTION_WIFI_SETTINGS)
            lower.contains("bluetooth") -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> null
        } ?: return false

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    private fun askGeminiOnline(prompt: String): String {
        return try {
            val apiKey = getApiKey()
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 8000
                readTimeout = 12000
            }

            val systemStyle = "Sen T.O.I.B.O.T mustaqil gibrid yordamchisiz. Vazmin, qisqa, aqlli va o'zbek tilida javob ber."
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", "$systemStyle\nFoydalanuvchi: $prompt")
                    }))
                }))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            val responseText = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(responseText)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        } catch (e: Exception) {
            "Onlayn AI bilan bog'lanishda xatolik: ${e.message}"
        }
    }
}
