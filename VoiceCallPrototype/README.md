# Ovozli qo‘ng‘iroq — Android prototipi

Bu loyiha Android telefonda **mikrofon → mahalliy effekt → quloqchin** zanjirini sinaydi. `Original`, `Yumshoq`, `Kuchli`, `Robot` mavjud. `Yumshoq` va `Kuchli` faqat amplituda effektlari; ular AI ovoz almashtirish yoki pitch shifting emas. Quloqchin taqing, aks holda akustik aks-sado yuz beradi.

**Raqam terish maydoni qo‘ng‘iroq qilmaydi.** Tugma ataylab bloklangan: Android oddiy SIM qo‘ng‘irog‘ining chiqayotgan ovoziga bu ilova orqali audio kiritib bo‘lmaydi. Oddiy +998 raqamga o‘zgargan ovozni uzatish uchun alohida sinovdan o‘tgan GSM/SIP gateway va audio marshrutlash kerak. `chan_mobile` / Bluetooth telefoni haqidagi oldingi taxmin ishlab turgan integratsiya deb qabul qilinmasin. Mahalliy tarifning gateway orqali ishlatishga ruxsati ham operator bilan tekshirilishi kerak.

## Qurish

Android Studio o‘rnating. `VoiceCallPrototype` papkasini oching, SDK Platform 35 va Android Gradle Plugin 8.7.3 ni Gradle Sync orqali yuklang, `Build > Build APK(s)` ni tanlang. Java 17 kerak. Jismoniy Android 8+ qurilmada sinang; mikrofonga ruxsat bering. Loyihada Gradle wrapper yo‘q: Android Studio o‘z Gradle muhitidan foydalanadi.

### Bulutda APK yig‘ish

Arxiv ichidagi `VoiceCallPrototype` va `.github` papkalarini birgalikda GitHub reposining **ildiziga** joylang. GitHub workflow fayli repo ildizidagi `.github/workflows/build-android.yml` manzilida bo‘lishi kerak. Repo `Actions` → `Build Android test APK` → `Run workflow` menyusidan ishga tushiring. Build muvaffaqiyatli tugasa, `ovoz-sinovi-debug-apk` artifactini yuklab oling va ichidagi `app-debug.apk`ni o‘zingizning Android qurilmangizga o‘rnating. Bu debug APK; rasmiy nashr uchun signing va release build alohida kerak.

## Keyingi bosqich uchun qabul mezoni

1. Quloqchinda barcha effektlar uzluksiz ishlashi va qurilma modelida kechikish o‘lchanishi.
2. Rozilik bilan olingan o‘zbekcha target ovozli RVC/Seed-VC modelini alohida Windows/Linux kompyuterda o‘lchash; mahalliy DSP effektini AI deb atamaslik.
3. SIP softphone → Asterisk → qonuniy GSM/SIP gateway zanjirida qo‘ng‘iroq va ikki tomonlama audioni sinash.
4. Shundan keyin Android mikrofoni → real vaqt media transporti → voice engine → gateway marshrutini ulash va tugmani yoqish.

Bu arxivda server, AI voice model, SIP akkaunt va APK yo‘q. Qo‘ng‘iroq ishlaydi degan da’vo qilinmaydi.
