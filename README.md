# T.O.I.B.O.T Hybrid Assistant (v4.1)

Onlayn va oflayn ishlaydigan mustaqil **T.O.I.B.O.T** Android AI ilovasi.

## Yangi qo'shilgan media va YouTube funksiyalari:
- **Birinchi videoni / qo'shiqni avtomatik bosish:** `ToibotAccessibilityService` orqali YouTube qidiruv ochilgandan so'ng ekrandagi birinchi mos video kartochkasini bosishga harakat qiladi.
- **"Mening miksim" buyrug'i:** Ilova ichida saqlangan shaxsiy YouTube miks havolasini (yoki standart qidiruvni) ochib, birinchi videoni ishga tushiradi.

- **Asosan o'zbekcha oflayn STT model:** `app/src/main/assets/stt_model/` ichida faqat `vosk-model-small-uz-0.22` fayllari saqlab qolindi.

- **GitHub Actions avtomatik APK build:** `.github/workflows/build.yml` qo'shildi — repository'ga fayl yuklanganda (`main`/`master` branch yoki `Run workflow`) APK avtomatik yig'ilib, `TOIBOT-APK` artifact sifatida tayyorlanadi.
