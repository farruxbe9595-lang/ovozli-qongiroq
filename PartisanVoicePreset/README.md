# Ovoz: Telegram uchun ayol ovozi rejimi

2026-09-25 holati: bu **manba kodi o‘zgarishi**, o‘rnatiladigan APK emas.

## Tiklangan loyiha

Maqsad: Android telefonda Telegram orqali jonli gaplashganda ovozni ayol ovoziga yaqinlashtirish. Bir kishilik qo‘ng‘iroq va guruh ovozli suhbatini alohida tekshirish kerak. Foydalanuvchi pullik daqiqalik xizmatni tanlamagan.

Oldingi loyiha: https://github.com/farruxbe9595-lang/ovozli-qongiroq

U yerdagi VoiceCallPrototype faqat mikrofon effektini quloqchinda eshittiradi; Telegram qo‘ng‘irog‘iga ulanmagan. Mazkur o‘zgarish Partisan Telegram 4.4.5 asosida tayyorlandi:

https://github.com/wrwrabbit/Partisan-Telegram-Android/tree/513865a0b8a1e521eb58403c1b787c5845c28ffc

## Tayyorlangan o‘zgarish

- Ovoz sozlamalarida alohida `Женский голос` / `Female voice` rejimi.
- Ovoz balandligi va tembri uchun ikkita besh bosqichli boshqaruv.
- Boshlang‘ich balandlik 1.6×, tembr 1.2×. Bu sinash uchun tanlangan qiymatlar; tabiiy ayol ovozini kafolatlamaydi.
- Tasodifiy ovoz pasaytirish va talaffuzni buzuvchi effektlar ushbu rejimda o‘chadi.
- WORLD o‘rta sifatli ishlov berish ishlatiladi. Bu AI ovoz klonlash modeli emas.
- Rejim o‘chirilganda oldingi sifat va tasodifiy ovoz parametrlari qaytariladi.
- O‘zgarish keyingi qo‘ng‘iroqqa tatbiq etilishi menyuda ko‘rsatiladi.
- Inglizcha va ruscha menyu matnlari qo‘shildi. Asl tasodifiy rejim standart holat bo‘lib qoladi.

## Tekshiruv

Haqiqiy Settings, Generator va CachedProvider Java klasslari kompilyatsiya qilinib, xotiradagi sozlama saqlash o‘rnini bosuvchi test muhiti bilan tekshirildi. 7880 tekshiruv o‘tdi: eski generator bilan 1200 kombinatsiya bo‘yicha tenglik, yangi rejimning barqarorligi, parametr chegaralari, avvalgi sifatga qaytish va amaldagi qo‘ng‘iroq sozlamalari nusxasining o‘zgarmasligi.

Yangi Android matn resurslari AAPT2 orqali kompilyatsiyadan o‘tdi. Patch asl fayllar nusxasiga `git apply --check` va `git apply` bilan qo‘llanib, natija o‘zgartirilgan manbalar bilan solishtirildi.

To‘liq Android ilovasi yig‘ilmadi. Android sozlamalarining diskda saqlanishi, ekrandagi ko‘rinish, native audio sifati, kechikish, batareya sarfi va haqiqiy qo‘ng‘iroqlar sinalmagan. To‘liq manba kodini yuklash tugallanmagan. Paket to‘liq Telegram loyihasi yoki APK o‘rnini bosmaydi.

## Qo‘llash va keyingi ish

`female-voice.patch` aynan yuqoridagi commit uchun. `original/` asl uchta Java faylini, `modified/` esa beshta yakuniy o‘zgargan/yangi faylni saqlaydi.

To‘liq Partisan Telegram loyihasi va submodullar olinib, yuqoridagi commit tanlangandan keyin loyiha ildizida:

```text
git apply --check /absolute/path/female-voice.patch
git apply /absolute/path/female-voice.patch
```

Keyin upstream README bo‘yicha Android build muhiti va ilova konfiguratsiyasi tayyorlanadi. Unda Android SDK 36, NDK 27.2.12479018 va shaxsiy build konfiguratsiyasi ko‘rsatilgan. APK yig‘ilib, telefonda avval ovoz yozish sinovi, so‘ng bir kishilik va guruh qo‘ng‘irog‘i tekshiriladi. Mavjud akkauntni yoki ilovani o‘chirish bu paketning bir qismi emas.

Joriy upstream qo‘ng‘iroq ovoziga ishlov beruvchi hook `org/webrtc/voiceengine/WebRtcAudioRecord.java` ichida. Barcha audio yo‘llari shu hookdan o‘tishi hali tasdiqlanmagan. Shu patch qo‘ng‘iroq transportini o‘zgartirmaydi; tayyor APK guruh chatida ishlaydi degan xulosa chiqarib bo‘lmaydi.

Testlarni qayta ishlatish (Python 3 va JDK 17 kerak):

```text
python tests/run_tests.py --drafts . --runtime /absolute/path/to/test-work
```

GitHub reposiga bu safar yozilmadi, yangi APK chiqarilmadi. Kirish kodi yoki hisob paroli bu paketga kiritilmagan.

## Manbalar va litsenziya

- Upstream kod: https://github.com/wrwrabbit/Partisan-Telegram-Android
- Ovoz moduli izohi: https://github.com/wrwrabbit/Partisan-Telegram-Android/wiki/Voice-Changing
- Upstream `LICENSE` paket ichida berilgan. O‘zgartirishlar ham shu GPL-2.0 litsenziyasi ostida taqdim etiladi.
