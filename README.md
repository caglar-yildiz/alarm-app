# BENG

Tek seferlik ve haftalık alarmlar kurabileceğin yerel bir Android uygulaması. Alarmı 5 dakika erteleyebilir, istersen kapatmak için toplama sorusu çözebilirsin. Alarm ayarları telefonda saklanır.

Alarm düzenleyicisinde alarm adı, saat, tek seferlik tarih veya haftalık tekrar günleri, cihaz alarm sesi ve kapatma görevi ayrı ayrı değiştirilebilir.

Alarm çalarken alarm ses seviyesi kısılamaz veya kapatılamaz: ses tuşları alarm ekranında yok sayılır, başka bir yoldan kısılırsa ses geri yükseltilir. Ses, en az azami seviyenin %60'ında çalar; alarm bittiğinde kullanıcının önceki ses seviyesi geri gelir.

Alarm ekranından kaçılamaz: alarm çalarken ana ekrana, Ayarlar'a geçilir ya da ekran kapatılırsa `RingService` alarm ekranını her 5 saniyede bir geri getirir (kilitli veya kapalı ekranda tam ekran bildirimle açılır, açık telefonda Android arka plandan ekran açmaya izin vermediği için üstten bildirim olarak çıkar). Telefon yeniden başlatılır veya uygulama güncellenirse çalan alarm (`RingRecord`) birkaç saniye sonra yeniden çalar; ilk çalmadan 30 dakika sonra bu artık yapılmaz.

## Özellikler

- **Kapatma görevleri:** toplama, hafıza dizisi, yazma; Premium: telefonu sallama, QR kod, şekil bulma ve **yürüme** (20–100 adım; fiziksel etkinlik izni gerekir, yoksa alarm matematiğe döner). Adımlar hem adım dedektöründen hem adım sayacından okunur ve büyük olan kullanılır: dedektör birçok telefonda ilk adımları ve elde tutulurken atılan adımları kaçırır.
- **Sonrakini atla:** alarm kartındaki ⋮ menüsünden ya da alarmdan 2 saat önce gelen sessiz "Bu sefer atla" bildiriminden (`UpcomingAlarm`). Atlanan gün kartta küçük bir etiket olarak görünür; etikete dokununca geri alınır. Bildirim, tek seferlik alarmı kapatır.
- **Tatil modu (Araçlar):** seçilen ilk ve son gün (dahil) arasında tekrarlanan alarmlar çalmaz, sonra kendiliğinden döner; tek seferlik alarmlar yine çalar. Açıkken "Sonraki alarm" kartında bir çip görünür.
- **Sabah özeti:** alarm kapatılınca selam, saat, bugünün hava durumu, zamanında kalkma serisi ve uyanıklık kontrolü bilgisi gösterilir (`BriefingActivity`). Premium ile telefonun metin okuma sesiyle okunur.
- **İstatistikler:** her alarmın nasıl bittiği (kapatıldı, ertelendi, çalıp bitti, uyanıklık kontrolü tekrar çaldı) yalnızca telefonda saklanır (`WakeStats`). Erteleme olmadan kapatılan gün “zamanında” sayılır; alarm olmayan günler seriyi bozmaz.
- **Yatma vakti hatırlatıcısı:** bir sonraki gerçek alarma ve uyku hedefine göre bildirim (`Bedtime`).
- **Ana ekran widget'ı:** sonraki alarm ve tek dokunuşla 20 dk şekerleme / 30 dk sonra alarm (`NextAlarmWidget`). Bu hızlı alarmlar çaldıktan sonra listeden silinir.
- **Güvenilirlik kartı:** pil kısıtlaması açıksa veya telefon Xiaomi, Huawei, Honor, OPPO/realme, vivo, OnePlus ya da Samsung ise ilgili ayar ekranını açar (`Reliability`). Pil izni istenmez; kullanıcı sistem listesinden değiştirir.
- **Değerlendirme isteği:** en az 3 günlük zamanında kalkma serisinden sonra, kurulumdan 4 gün geçmişse ve en fazla 120 günde bir Google Play değerlendirme penceresi istenir (`ReviewPrompt`).

## Diller

Uygulama 14 dil destekler. Diller, uygulama harcamasında öne çıkan pazarlara göre seçildi:

| Dil | Klasör | Başlıca pazarlar |
| --- | --- | --- |
| İngilizce (varsayılan) | `values` | ABD, Birleşik Krallık, Kanada, Avustralya |
| Türkçe | `values-tr` | Türkiye |
| Almanca | `values-de` | Almanya, İsviçre, Avusturya |
| Fransızca | `values-fr` | Fransa, Kanada, İsviçre |
| İspanyolca | `values-es` | Meksika, İspanya |
| İtalyanca | `values-it` | İtalya |
| Portekizce (Brezilya) | `values-pt-rBR` | Brezilya |
| Felemenkçe | `values-nl` | Hollanda |
| Japonca | `values-ja` | Japonya |
| Korece | `values-ko` | Güney Kore |
| Geleneksel Çince | `values-zh-rTW` | Tayvan, Hong Kong |
| Arapça (sağdan sola) | `values-ar` | Suudi Arabistan, BAE |
| Tayca | `values-th` | Tayland |
| Endonezce | `values-in` | Endonezya | Dil, **Araçlar > Dil** kutucuğundan veya Android 13+ cihazlarda sistem ayarlarındaki uygulama dili ekranından seçilir. Yeni dil eklemek için `res/values-<dil>/strings.xml` dosyasını ekle, `res/xml/locales_config.xml` ve `AppLanguage.supported` listesine dil kodunu yaz. Uygulama içinden dil değişebildiği için AAB'de dil bölmesi kapalıdır.

## Kurulum

JDK 17 ve Android SDK Platform 36 ile projeyi Android Studio'da aç. Minimum desteklenen sürüm Android 8.0 (API 26).

Terminalden debug APK oluşturmak için:

```bash
./gradlew :app:assembleDebug
```

Windows'ta `gradlew.bat :app:assembleDebug` kullan. APK, `app/build/outputs/apk/debug/app-debug.apk` yoluna yazılır.

## Play'e sürüm çıkarmak

GitHub'da **Actions → Build Play Store bundle → Run workflow** (yalnızca `main`). `versionCode` elle
değiştirilmez: iş akışı her çalıştırmada `100 + çalıştırma numarası` verir, yani her yükleme bir öncekinden
büyüktür. İstersen "version_name" kutusuna Play'de görünecek sürüm adını yaz (ör. `1.0.0`); boş bırakırsan
`app/build.gradle.kts` içindeki ad kullanılır. Kullanılan numara iş özetinde ve indirilen dosyanın adında
(`BENG-play-release-<versionCode>`) yazar. Yerel derlemeler `22` / `0.11.0` kullanır; elle denemek için
`./gradlew -PversionCode=150 -PversionName=1.0.0 :app:bundleRelease`.

## Veriler ve güncellemeler

- **Yedekleme:** Alarmlar, ayarlar, dil, uyanıklık kontrolü, sayaçlar, hava durumu konumu ve
  deneme kaydı Android'in yedeklemesine dahildir (`res/xml/backup_rules.xml`,
  `res/xml/data_extraction_rules.xml`). Uygulama silinip Play'den yeniden kurulduğunda ya da
  yeni telefona geçildiğinde geri gelir. Geri yükleme bitince `BengBackupAgent` alarmları,
  uygulama açılmadan yeniden kurar. Premium önbelleği yedeklenmez; abonelik Play'den okunur.
- **Güncelleme ve imza:** Android, imzası farklı bir APK'yı eski uygulamanın üstüne kurmaz.
  Bu durumda uygulamayı silmek gerekir ve veriler gider. GitHub Actions'taki debug APK
  (`.github/workflows/main.yml`) her çalıştırmada yeni bir anahtar üretiyordu. Artık
  `DEBUG_KEYSTORE_BASE64` secret'ındaki sabit anahtarla imzalanır; secret yoksa iş uyarı verir.
  Kurulum için Android Studio'nun kullandığı `~/.android/debug.keystore` dosyasının Base64
  karşılığını **Settings → Secrets and variables → Actions** altında `DEBUG_KEYSTORE_BASE64`
  adıyla ekle. Böylece Android Studio ve CI derlemeleri aynı imzayı taşır. macOS/Linux:

  ```sh
  base64 < ~/.android/debug.keystore | tr -d '\n'
  ```

  Windows PowerShell:

  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore"))
  ```

  Telefondaki eski, farklı imzalı sürümü bir kez silmek gerekir; sonraki CI derlemeleri
  verileri koruyarak üstüne kurulur. Play sürümü ayrı (yükleme) anahtarıyla imzalandığı için
  debug APK ile Play sürümü birbirinin üstüne kurulamaz.

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

Play Console'da `com.caglaryildiz.alarm` için `beng_premium` aboneliği ve iki temel plan
tanımlanır: `yearly` (yıllık, P1Y) ve `monthly` (aylık, P1M). Planlar otomatik yenilenir.
Premium ekranında iki plan yan yana çıkar; yıllık plan önden seçilidir ve aylığa göre
tasarruf oranı ("%35 tasarruf") ile aylık karşılığı Play fiyatından hesaplanır. Play'de
yalnızca biri tanımlıysa yalnızca o gösterilir. Fiyat ve para birimi Google Play'den alınır.

### Kartsız 7 günlük deneme

Abone olmayan kullanıcı **Ücretsiz denemeyi başlat** ile Premium'u 7 gün kart ve hesap
olmadan açar (`FreeTrial.kt`). Play'de deneme teklifi tanımlanmaz; deneme uygulama içindedir.

- Deneme her telefon ve Google hesabı için bir kezdir. Başlangıç zamanı cihazda (Auto
  Backup'a dahil) ve Google Block Store'da tutulur; en erken kayıt geçerlidir. Bu yüzden
  silip yeniden kurmak ya da yedekten geri yüklemek yeni deneme açmaz.
- Saat geri alınırsa süre uzamaz.
- Kayıtta telefonun ANDROID_ID özeti de saklanır; ileride bir sunucu kontrolü eklenirse kullanılır.
- Sunucu olmadığı için bu yöntem "en iyi çaba"dır. Play Hizmetleri verisini silen, yedeklemeyi
  kapatan ya da fabrika ayarlarına dönen kullanıcı yeniden deneyebilir.
- Play Console **Veri güvenliği** formunda "Cihaz veya diğer kimlikler" (dolandırıcılığı önleme
  amacıyla, cihazda) beyan edilmelidir.
- Deneme bitince Premium görevler matematiğe, Premium sesler ücretsiz sese döner ve
  uyanıklık kontrolü durur. Satın alma ve geri yükleme Play
hesabıyla yapılır. Lisans testinde Play Console'daki paket kimliğiyle
imzalanmış bir iç test sürümü kullanılır. Yerel test APK'sında Premium
ekranındaki test düğmesi ödeme olmadan özellikleri açar.

Premium üyeler **Araçlar > Uyanıklık kontrolü** kutucuğundan kontrolü tüm alarmlar için
açabilir; durum ana ekrandaki "Sonraki alarm" kartında da görünür. Alarm kapatıldığında
seçilen gecikme sonunda bildirim gösterilir. 60 saniye içinde onay
gelmezse alarm yeniden çalar. Test ederken bildirim ve kesin alarm
izinlerini ver, kilit ekranında hem onay hem yanıtsız senaryoyu dene.

## 0.8: Görev önizlemesi ve şekil görevi

**Alarmı ve görevi önizle** seçilen görevi açar ve alarm sesini en fazla
30 saniye çalar. Matematik önizlemesi seçilen zorluk ve soru sayısını kullanır. Önizlemeden çıkıldığında ses
ve sensör dinleyicisi durur. Bu akış alarm kaydetmez, ertelemez ve uyanıklık
kontrolü başlatmaz. Premium görevleri denemek ücretsizdir; kaydederken
abonelik kontrol edilir. Şekilleri bul görevinde dört farklı hedef şekil,
4 × 4 tabloda soldan sağa sırayla seçilir; yanlış kutu ilerletmez.

## Hatırlatıcı sekmesi

Alt menüdeki **Hatırlatıcı** sekmesinde iki bölüm var: kullanıcının kendi hatırlatıcıları ve resmi günler.

**Kendi hatırlatıcıların** (`Reminders.kt`, `ReminderEditorActivity.kt`)

- Hatırlatıcının başlığı, isteğe bağlı notu, tarihi ve saati vardır.
- Tekrar seçenekleri: bir kez, her gün, haftanın seçili günleri, her ay ya da her yıl. Ayın 31'i
  kısa aylarda ayın son gününe, 29 Şubat artık olmayan yıllarda 28 Şubat'a kayar.
- Varsayılan olarak bildirim gösterir. Saatinde çalışması için kesin alarm izni kullanılır; izin
  yoksa Android'in izin verdiği en yakın zamanda gelir.
- Premium ✦ özellikler:
  - **Önceden haber:** 1 gün ya da 1 hafta önce, o günün bildirimine ek olarak.
  - **Alarm gibi çal:** Alarm ekranını sesle açar; ses kilidi ve 5 dakika erteleme de çalışır.
- Hatırlatıcılar yedeklemeye dahildir. Yeniden başlatmada, saat dilimi değişince ve yedekten
  geri yüklenince yeniden kurulur.
- Sıradaki hatırlatıcı, ana ekrandaki "Sonraki alarm" kartında da görünür.

**Resmi günler** (`Holidays.kt`)

- Kullanıcı açtığında ülkesinin ülke geneli resmi tatillerinde seçilen saatte bildirim gelir.
  Varsayılan ülke dil ayarından alınır.
- Veriler ücretsiz [Nager.Date](https://date.nager.at) servisinden (anahtarsız) yıllık olarak çekilir,
  cihazda saklanır ve ayda bir yenilenir.
- Kurban Bayramı gibi çok günlü tatiller tek kayıt olarak gösterilir ("3 gün").
- Türkiye'nin dini bayramlarında Nager.Date yalnızca tahmin verir (2027 Ramazan Bayramı'nı bir gün
  geç gösteriyor). Diyanet'in yayımladığı yıllarda (şimdilik 2026–2027) arife dahil resmi tarihler
  kullanılır; sonraki yıllar "tahmini" diye işaretlenir. Diyanet yeni yılın takvimini yayımlayınca
  `HolidayParser.TURKEY_BAYRAMS` tablosuna eklenmeli.
- Premium ✦ özellikler:
  - **Bir gün önce de hatırlat.**
  - **Özel günler:** Sevgililer Günü; Anneler Günü ve Babalar Günü her ülkenin kendi tarihiyle
    (örneğin Türkiye'de mayısın 2. ve haziranın 3. pazarı, İngiltere'de Mothering Sunday, Almanya'da
    Vatertag); Türkiye'de Öğretmenler Günü.
- Nager.Date şu an Suudi Arabistan, BAE, Tayland ve Tayvan'ı kapsamıyor. Bu ülkelerde "veri yok"
  mesajı çıkar, özel günler yine çalışır.

## Hava durumu sekmesi

Alt menüdeki **Hava** sekmesi ücretsizdir; konum izni istemez. Seçim yapıldıktan sonra o anki hava,
günün önerisi, rüzgar/nem/yağış, sonraki 24 saat ve 7 günlük tahmin gösterilir.

**Yer araması** yazarken sonuç verir:

- Türkiye'nin 81 ili ve 974 ilçesi koordinatlarıyla uygulamaya gömülüdür (`assets/places_tr.tsv`,
  [GeoNames](https://www.geonames.org/), CC BY 4.0). Bu kısım internetsiz çalışır.
- Arama Türkçe harflerden ve büyük/küçük harften bağımsızdır: "istan", "İstan" ve "ıstan" aynı sonucu verir.
- "istan" yazınca önce **İstanbul**, sonra **İstanbul / Esenyurt** gibi ilçeler nüfusa göre listelenir.
- "esen" doğrudan ilçeleri bulur; "istanbul esen" ya da "İstanbul/Esen" aramayı daraltır.
- Yerel sonuç az olduğunda (ör. yurt dışı şehirler) Android Geocoder'ı otomatik devreye girer.
- Mantık `PlaceSearch.kt` içinde, testleri `PlaceSearchTest.kt` içindedir.
- Veriyi yeniden üretmek için GeoNames `TR.zip` dosyasındaki `ADM1`/`ADM2` kayıtları ve il/ilçe
  merkezleri (`PPLA`, `PPLA2`) kullanılır.

Tahmin verileri [MET Norway](https://api.met.no/) Locationforecast 2.0'dan
gelir: ücretsizdir, ticari uygulamalarda kullanılabilir (CC BY 4.0, ekranda kaynak belirtilir).
MET kurallarına göre istekler uygulamayı tanıtan bir `User-Agent` ile gönderilir, yanıt `Expires`
süresi dolana kadar önbellekten okunur ve `If-Modified-Since` kullanılır. Saatler cihazın saat
diliminde gösterilir. Google hizmetleri olmayan cihazlarda (ör. bazı Huawei modelleri) yalnızca
yurt dışı araması çalışmayabilir; Türkiye il ve ilçeleri her cihazda bulunur.

Telefonda deneme: **Hava** sekmesini aç, bir şehir ara ve seç. Uçak modunda son tahminin ve
"İnternet bağlantısı yok" uyarısının göründüğünü, **Yenile** ve **Şehri değiştir** düğmelerinin
çalıştığını kontrol et.

## Google Play aboneliğini açma ve yayın

1. Play Console'da ödeme profilini tamamla. Uygulama paket kimliği
   `com.caglaryildiz.alarm` olmalı. Önizleme APK'sının `.editorpreview`
   paketi bu uygulamanın abonelik ürünlerini sorgulayamaz.
2. **Play ile para kazanma > Ürünler > Abonelikler** altında `beng_premium`
   oluştur. `yearly` kimlikli yıllık (P1Y) ve `monthly` kimlikli aylık (P1M),
   otomatik yenilenen iki temel plan ekle. Ülke/bölge ve fiyatları belirleyip
   planları etkinleştir. Yıllık fiyatı aylığın yaklaşık 7–8 katı tutmak önerilir.
   Bu sürüm teklif (deneme/indirim) kullanmaz; 7 günlük deneme uygulama içindedir.
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

### GitHub Actions ile imzalı AAB oluşturma

1. GitHub'da **Settings → Environments** bölümünden `play-signing` ortamını
   oluştur. Dağıtım dalı kuralını yalnızca `main` dalına izin verecek şekilde
   ayarla.
2. Ortamın **Environment secrets** bölümüne aşağıdaki değerleri ekle.
   Daha önce Play Console'a paket yüklediysen aynı yükleme anahtarını kullan.

   | Secret | Değer |
   | --- | --- |
   | `PLAY_UPLOAD_KEYSTORE_BASE64` | Yükleme anahtarı dosyasının Base64 karşılığı |
   | `PLAY_UPLOAD_STORE_PASSWORD` | Anahtar deposu şifresi |
   | `PLAY_UPLOAD_KEY_ALIAS` | Anahtar takma adı |
   | `PLAY_UPLOAD_KEY_PASSWORD` | Anahtarın şifresi |

   İlk yüklemeyse JDK 17 ile bir yükleme anahtarı oluşturabilirsin:

   ```sh
   keytool -genkeypair -keystore beng-upload.jks -storetype JKS -alias beng-upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   Windows PowerShell'de Base64 değerini almak için:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\beng-upload.jks).Path))
   ```

   Anahtarı ve şifreleri güvenli bir yerde yedekle. Base64 değerini yalnızca
   ilgili GitHub secret alanına yapıştır.

3. Workflow `main` dalına girdikten sonra **Actions → Build Play Store bundle
   → Run workflow** üzerinden `main` dalıyla çalıştır. Başarılı çalışmanın
   **Artifacts** bölümünden `BENG-play-release` arşivini indir ve içindeki
   `BENG-play-release.aab` dosyasını Play Console'da test sürümüne yükle.
   Sonraki Play yüklemeleri için `versionCode` değerini artır.
