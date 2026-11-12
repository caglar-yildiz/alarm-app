# BENG

Tek seferlik ve haftalık alarmlar kurabileceğin yerel bir Android uygulaması. Alarmı 5 dakika erteleyebilir, istersen kapatmak için toplama sorusu çözebilirsin. Alarm ayarları telefonda saklanır.

Alarm düzenleyicisinde alarm adı, saat, tek seferlik tarih veya haftalık tekrar günleri, cihaz alarm sesi ve kapatma görevi ayrı ayrı değiştirilebilir.

## Kurulum

JDK 17 ve Android SDK Platform 35 ile projeyi Android Studio'da aç. Minimum desteklenen sürüm Android 8.0 (API 26).

Terminalden debug APK oluşturmak için:

```bash
./gradlew :app:assembleDebug
```

Windows'ta `gradlew.bat :app:assembleDebug` kullan. APK, `app/build/outputs/apk/debug/app-debug.apk` yoluna yazılır.

## Telefonda deneme

1. Uygulamayı aç. Ekranda görünüyorsa **Alarm iznini aç**, **Alarm bildirimlerini aç** ve **Kilit ekranında alarmı göster** adımlarını tamamla.
2. Bir dakika sonrasına, matematik sorusu açık tek seferlik alarm kur. Ekranı kilitle. Sesi ve alarm ekranını kontrol et.
3. Yanlış cevabın alarmı durdurmadığını, doğru cevabın sesi durdurduğunu ve tek seferlik alarmın devre dışı kaldığını kontrol et.
4. Alarmı yeniden kur ve ertele. Alarmın beş dakika sonra tekrar çaldığını ve bu ikinci çalmayı kapatabildiğini kontrol et.
5. Bugünün gününü seçerek tekrar eden alarm kur. Çaldıktan sonra açık kaldığını doğrula. Ardından alarm kurup telefonu yeniden başlat. Önce ekran kilidini açmadan, sonra pil tasarrufu açıkken dene.
6. İki alarmı aynı saate kur. İlkini kapattığında ikincisinin ekrana geldiğini kontrol et.

Alarm, etkileşim olmazsa 10 dakika sonra susar. Tam ekran izni kapalıysa ve bildirimler açıksa, alarm ekranını bildirimden açabilirsin. Samsung cihazda kilit ekranı ve yeniden başlatma senaryoları fiziksel cihazda doğrulanmalıdır.
