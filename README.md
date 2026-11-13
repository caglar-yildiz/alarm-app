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

## 0.8: Görev önizlemesi ve şekil görevi

Görev seçildikten sonra **Görevi önizle** seçilen matematik zorluğunu ve soru
sayısını da kullanarak gerçek görev bileşenini açar. **Alarmı sesli önizle**
seçilen alarm sesini en fazla 30 saniye çalar. Önizlemeden çıkıldığında ses
ve sensör dinleyicisi durur. Bu akış alarm kaydetmez, ertelemez ve uyanıklık
kontrolü başlatmaz. Premium görevleri denemek ücretsizdir; kaydederken
abonelik kontrol edilir. Şekilleri bul görevinde dört farklı hedef şekil,
4 × 4 tabloda soldan sağa sırayla seçilir; yanlış kutu ilerletmez.

## Google Play aboneliğini açma ve yayın

1. Play Console'da ödeme profilini tamamla. Uygulama paket kimliği
   `com.caglaryildiz.alarm` olmalı. Önizleme APK'sının `.editorpreview`
   paketi bu uygulamanın abonelik ürünlerini sorgulayamaz.
2. **Play ile para kazanma > Ürünler > Abonelikler** altında `beng_premium`
   oluştur. `monthly` kimlikli, aylık ve otomatik yenilenen temel plan ekle.
   Ülke/bölge ve fiyatları belirleyip planı etkinleştir. Bu sürüm yalnızca
   standart aylık planı sunar; deneme/indirim tekliflerini sunmaz.
3. Fiyat kodda sabit değildir. Google Play'in yerelleştirilmiş fiyatı,
   aylık dönem ve otomatik yenileme açıklaması ödeme düğmesinden önce
   gösterilir. Ürün sorgusu başarısızsa ödeme düğmesi açılmaz; yeniden
   yükleme sunulur. Bekleyen ödemeler erişim açmaz. Satın alımlar geri
   yüklenebilir, etkin abonelik Play üzerinden yönetilip iptal edilebilir.
4. Yayın anahtarınla imzalanmış AAB'yi iç test kanalına yükle; test hesabını
   hem test kanalına hem lisans test kullanıcılarına ekle. Uygulamayı Play
   üzerinden kur. Onay, reddetme, bekleyen ödeme, iptal, yenileme, süre
   dolması ve yeniden kurulum/geri yükleme senaryolarını dene.
5. Mağaza kaydı, gizlilik politikası, veri güvenliği, içerik derecelendirmesi,
   hedef kitle ve kullanılan izinlere ilişkin Console görevlerini tamamla.
   Üretim erişimi ve inceleme tamamlanmadan herkese açık yayın gerçekleşmez.
   13 Kasım 2023 sonrasında açılan kişisel hesaplarda üretim başvurusu için
   en az 12 test kullanıcısının kesintisiz 14 günlük kapalı testi gerekebilir.

**Mevcut sınır:** abonelik sorgulama ve satın alma onayı istemcidedir.
Sunucuda Google Play `purchases.subscriptionsv2.get` doğrulaması, gerçek
zamanlı bildirimler (RTDN), iade/iptal durum takibi henüz entegre değildir.
Çevrimdışıyken son erişim durumu korunur; süresi dolan abonelik, başarılı
Play sorgusuna kadar önbellekte etkin kalabilir. Ücretli üretim dağıtımından
önce bu erişim politikasını ve sunucu doğrulamasını tamamlamak önerilir.
Uygulama içi kullanıcı hesabı gerekmemesi, Play satın alma doğrulamasını
ortadan kaldırmaz. Debug test erişimi release sürümünde bulunmaz.

Kaynaklar:
- https://support.google.com/googleplay/android-developer/answer/140504
- https://developer.android.com/google/play/billing/test
- https://developer.android.com/google/play/billing/security
- https://support.google.com/googleplay/android-developer/answer/14151465
