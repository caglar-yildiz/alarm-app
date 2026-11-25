# BENG — iOS

Android uygulamasının (`app/`) iPhone sürümü. SwiftUI ile yazıldı, alarmlar için Apple'ın **AlarmKit**
çerçevesini kullanır; bu yüzden **iOS 26 ve üstü** gerekir. Aynı 5 sekme (Alarmlar, Hatırlatıcı, Hava,
Araçlar, Premium), aynı görevler, aynı 14 dil ve aynı gece gökyüzü tasarımı vardır.

## Klasörler

| Klasör | İçerik |
| --- | --- |
| `Core/` | Uygulama, widget ve kilit ekranı düğmelerinin paylaştığı kod: alarm modeli, tatil/atlama mantığı, hatırlatıcılar, resmi tatiller, istatistik, hava durumu ayrıştırıcı, il/ilçe araması, dil, **AlarmKit zamanlayıcısı** (`Scheduler.swift`). |
| `BENG/` | Uygulama: SwiftUI ekranları (`UI/`), StoreKit 2 aboneliği ve ses (`Services/`), kaynaklar (sesler, `places_tr.tsv`, simge, çeviriler). |
| `BENGWidget/` | Ana ekran widget'ı (sonraki alarm + "20 dk şekerleme" / "30 dk sonra alarm" düğmeleri) ve AlarmKit'in Live Activity görünümü. |
| `BENGTests/` | Android birim testlerinin Swift karşılıkları. |
| `tools/convert_strings.py` | Android `strings.xml` dosyalarından iOS `Localizable.strings`/`.stringsdict` üretir. |

## Kurulum

1. Mac'te **Xcode 26** ve [XcodeGen](https://github.com/yonaskolb/XcodeGen) gerekir:
   ```sh
   brew install xcodegen
   cd ios && xcodegen generate
   open BENG.xcodeproj
   ```
2. Xcode'da **BENG** ve **BENGWidget** hedeflerinde *Signing & Capabilities* altında takımını seç
   (ya da `project.yml` içindeki `DEVELOPMENT_TEAM` değerini doldur). İki hedef de
   `group.com.caglaryildiz.alarm` App Group'unu kullanır; Apple Developer hesabında bu grubu oluştur.
3. Gerçek bir iPhone'da çalıştır. Simülatörde arayüz çalışır ama AlarmKit alarmları ve kamera
   gerçek cihazda denenmelidir.

Çeviriler Android'den gelir. Android'de bir metni değiştirdiğinde:

```sh
python3 ios/tools/convert_strings.py
```

Yalnızca iOS'ta farklı olan metinler (App Store, Apple Hesabı, AlarmKit izni…) `tools/ios_strings.json`
dosyasındadır.

GitHub Actions'taki **iOS app** iş akışı (`.github/workflows/ios.yml`) her `ios/` değişikliğinde projeyi
macOS'ta üretir, derler ve testleri simülatörde çalıştırır.

## Alarm nasıl çalar? (Android'den farkları)

iOS, uygulamanın arka planda kendi ekranını açmasına veya sesi kilitlemesine izin vermez. Bunun yerine:

- **AlarmKit** alarmları sessiz modda ve Odak açıkken de tam ekran, sesli çalar. Kilit ekranında iki düğme
  vardır: **Durdur** ve **Görev** (görevsiz alarmlarda **Ertele**).
- **Görev** düğmesi uygulamayı görev ekranında açar; alarm sesi uygulama içinde (kademeli ses ve titreşimle)
  görev bitene kadar devam eder.
- **Kaçış yok:** Kilit ekranında görev yapılmadan **Durdur**'a basılırsa ya da görev ekranından çıkılırsa
  alarm **1 dakika sonra yeniden çalar** (Android'deki "ekrandan kaçılamaz" davranışının karşılığı). İlk
  çalmadan 30 dakika sonra bu bırakılır ve gün "kaçırıldı" sayılır.
- iOS alarm çalarken uygulama kodu çalıştırmaz. Bu yüzden tek seferlik alarmın kapanması, kullanılmış
  atlama/erteleme kayıtlarının silinmesi gibi işler uygulama açılınca, bir alarm düğmesine basılınca veya
  arka plan yenilemesinde yapılır. Planın tamamı her değişiklikte `Scheduler` tarafından yeniden hesaplanır
  ve sistemdeki alarmlar buna eşitlenir.
- Tekrarlayan alarmlar sistemin haftalık tekrarını kullanır, uygulama hiç açılmasa da çalar. **Sonrakini atla**
  veya **tatil modu** varken o alarm, istisnadan sonraki iki haftayı da kapsayan tek tek alarmlara çevrilir.
- Ses seviyesini kilitleme (Android'deki %60 alt sınır) iOS'ta mümkün değildir; AlarmKit sistem alarm sesini kullanır.
- Pil/üretici ayarları kartı iOS'ta gereksizdir; yerine **AlarmKit izni** ve **bildirim izni** kartı vardır.

## Özellikler (Android ile aynı)

- **Görevler:** matematik (zorluk, 1–5 soru), hafıza dizisi, yazma; Premium: salla (ivmeölçer), QR kod
  (kamera), şekil bulma, yürüme (Hareket ve Fitness izni; adım sayar).
- Görev önizlemesi, gün/tarih seçimi, yazarak saat/tarih girme, yörünge saat kadranı, 9 yerleşik ses
  (6'sı Premium), kademeli ses.
- **Sonrakini atla** (kart menüsü ve alarmdan 2 saat önceki "Bu sefer atla" bildirimi), **tatil modu**.
- **Uyanıklık kontrolü** (Premium): alarm kapandıktan sonra seçilen sürede "Uyanık mısın?" bildirimi;
  60 saniyede onaylanmazsa alarm yeniden çalar.
- **Sabah özeti** (Premium ile sesli okuma: `AVSpeechSynthesizer`), **istatistikler**, **yatma vakti hatırlatıcısı**,
  **kronometre**, **sayaçlar**, **dil seçimi** (uygulama içinden, 14 dil).
- **Hatırlatıcılar** (önceden haber ve "alarm gibi çal" Premium), **resmi tatiller** (Nager.Date; Türkiye için
  Diyanet tarihleri) ve **özel günler**.
- **Hava durumu** (MET Norway, Türkiye'nin 81 il ve 974 ilçesi uygulamada; yurt dışı için Apple'ın coğrafi kodlayıcısı).
- **Widget** ve **değerlendirme isteği** (StoreKit `requestReview`, aynı kurallar).

iOS bir uygulamada en fazla 64 bekleyen bildirime izin verir; en yakın 60 bildirim (hatırlatıcılar, tatiller,
uyanıklık kontrolü, sayaçlar, yatma vakti) planlanır ve uygulama açıldıkça yenilenir.

## Premium (App Store)

App Store Connect'te **BENG Premium** abonelik grubu ve iki otomatik yenilenen abonelik oluştur:

| Ürün kimliği | Süre |
| --- | --- |
| `com.caglaryildiz.alarm.premium.yearly` | 1 yıl |
| `com.caglaryildiz.alarm.premium.monthly` | 1 ay |

**7 günlük ücretsiz deneme:** Android'deki kartsız deneme App Store kurallarına (3.1.1/3.1.2) uymaz.
iOS'ta deneme, iki aboneliğe eklenen **1 haftalık ücretsiz tanıtım teklifi** (introductory offer) ile verilir;
uygun kullanıcılara Premium ekranında "7 günlük ücretsiz denemeyi başlat" düğmesi çıkar. Deneme Apple
Hesabı başına birdir ve Apple tarafından takip edilir.

Yerel test için `BENG/BENG.storekit` dosyası şemaya bağlıdır (Xcode'dan çalıştırınca sahte fiyatlar ve satın
alma çalışır). Debug derlemelerde Premium ekranındaki test düğmesi ödeme olmadan özellikleri açar.

Satın alma doğrulaması StoreKit 2'nin imzalı işlemleriyle cihazda yapılır; sunucu gerekmez. Abonelik
yönetimi uygulama içinden `manageSubscriptionsSheet` ile açılır.

## App Store'a göndermeden önce

- App Store Connect'te uygulama kaydı (`com.caglaryildiz.alarm`), App Group ve abonelikler.
- Gizlilik etiketleri: veri toplanmaz; hava durumu için konum yerine seçilen şehrin koordinatı MET Norway'e
  gönderilir. Gizlilik politikası: https://caglar-yildiz.github.io/alarm-app/
- Ekran görüntüleri ve 14 dildeki açıklamalar `play-console/store-listing.md` metinlerinden uyarlanabilir.
- Uygulama arka planda ses için `audio` modunu yalnızca görev ekranında çalan alarm sesi için kullanır; inceleme
  notunda bunu belirt.
