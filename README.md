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

### Çalan alarma yeniden erişim

- Ekran açıkken bir alarm çaldır. Bildirimi tek başına kaydırırsan, Android kaldırmaya izin verdiğinde bildirimin geri gelmesini bekle. Bildirimdeki **Alarmı aç** düğmesini kullan.
- Bildirime erişemiyorsan BENG'i aç. Ana ekranın en üstündeki **Çalan alarmı aç** düğmesiyle mevcut göreve dön.
- Alarmı kapatınca bildirimin kaybolduğunu ve yeniden görünmediğini kontrol et.

## Premium ve uyanıklık kontrolü

Play Console'da `com.caglaryildiz.alarm` için `beng_premium` aboneliği ve
`monthly` kimlikli aylık, otomatik yenilenen temel plan tanımlanır. Fiyat
ve para birimi Google Play'den alınır. Satın alma ve geri yükleme Play
hesabıyla yapılır. Lisans testinde Play Console'daki paket kimliğiyle
imzalanmış bir iç test sürümü kullanılır. Yerel test APK'sında Premium
ekranındaki test düğmesi ödeme olmadan özellikleri açar.

Uyanıklık sekmesindeki ayar tüm alarmlara uygulanır. Alarm kapatıldığında
seçilen gecikme sonunda bildirim gösterilir. 60 saniye içinde onay
gelmezse alarm yeniden çalar. Test ederken bildirim ve kesin alarm
izinlerini ver, kilit ekranında hem onay hem yanıtsız senaryoyu dene.
