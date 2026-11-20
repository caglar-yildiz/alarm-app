# BENG Alarm: Play Console answers (draft for your review)

Package `com.caglaryildiz.alarm`, versionCode 21 / 0.10.0. These answers are based on the code in this repo.
Nothing has been submitted.

## 1. Subscription `beng_premium` (Monetize > Products > Subscriptions)

The code (`Premium.kt`) expects exactly this. Any other ID means the app won't find the plans.

| Field | Value |
| --- | --- |
| Product ID | `beng_premium` |
| Name | BENG Premium |
| Base plan 1 | ID `yearly`, auto-renewing, billing period **1 year** (P1Y) |
| Base plan 2 | ID `monthly`, auto-renewing, billing period **1 month** (P1M) |
| Offers | **None.** The 7-day trial is handled inside the app, and the code only reads offers where `offerId == null` |
| Grace period / account hold | Play defaults are fine |
| Benefits (shown on Play) | Wake check · Shapes, QR and shake missions · Nature and extra-loud tones |

Activate both base plans after setting prices.

### Prices: still needed from you
Give me the base **USD** price for each plan. I'll apply the tiers below by country and round to local price points
(e.g. 2.99 / 19.99, ₺ 99,99, ¥ 400).

Proposed tiers (multiplier on your USD price, roughly following purchasing power):

| Tier | × | Countries |
| --- | --- | --- |
| A, highest | 1.20 | Switzerland, Norway, Denmark, Luxembourg, Iceland, Liechtenstein |
| B, base | 1.00 | United States, Canada, United Kingdom, Ireland, Australia, New Zealand, Germany, Austria, Netherlands, Belgium, Sweden, Finland, France, Singapore, Hong Kong, Israel, United Arab Emirates, Qatar, Kuwait |
| C | 0.80 | Japan, South Korea, Taiwan, Italy, Spain, Portugal, Saudi Arabia, Bahrain, Oman, Czechia, Slovenia, Estonia |
| D | 0.55 | Poland, Greece, Hungary, Romania, Croatia, Chile, Uruguay, Malaysia, Mexico, Costa Rica |
| E | 0.35 | Türkiye, Brazil, Argentina, Colombia, Peru, Thailand, South Africa, Kazakhstan |
| F, lowest | 0.25 | Indonesia, India, Vietnam, Philippines, Egypt, Pakistan, Bangladesh, Nigeria, Kenya, Morocco |
| Everything else | Play's automatic conversion of the base price |

Check: yearly should stay about 40–50% cheaper than 12 × monthly in every country, because the app shows
"Save X%" (`yearlySavingsPercent`).

## 2. App content

| Item | Answer |
| --- | --- |
| Privacy policy | `https://caglar-yildiz.github.io/alarm-app/` *(index.html in this repo. I couldn't check from here that GitHub Pages is on, so please confirm the URL)* |
| Ads | **No**, the app has no ads |
| App access | All functionality is available without special access. There's no login, and Premium can be tried through the in-app 7-day trial |
| Advertising ID | **No**, no ad SDK and no `AD_ID` permission |
| Government app | No |
| Financial features | None |
| Health | None |
| News app | No |

### Target audience and content
- Target age groups: **13–15, 16–17, 18 and over** (not 12 and under)
- Could the app unintentionally appeal to children? **No**
- Store presence: no "Teacher Approved" / Families

### Content rating (IARC questionnaire)
- Category: **Utility, Productivity, Communication, or Other**
- Violence: No · Sexuality: No · Bad language: No · Controlled substances: No · Crude humor: No
- Simulated gambling / real gambling: No
- Users can interact or exchange content: **No**
- Shares user's location with other users: **No**
- Allows purchase of digital goods: **Yes** (subscription)
- Unrestricted internet / web browser: **No**
- Expected result: Everyone / PEGI 3 / USK 0 / IARC 3+

### Data safety
| Question | Answer |
| --- | --- |
| Does the app collect or share any of the required user data types? | **Yes** (see the note on location below) |
| Is all data encrypted in transit? | **Yes**: the only network call is HTTPS to `api.met.no` (plus Google Play Billing) |
| Can users request deletion? | No developer-side storage, so there's nothing to delete. Choose "No" and explain that data stays on the device and is removed by uninstalling |
| **Location → Approximate location** | Collected: **Yes**; Shared: **No** (sent to MET Norway only when the user asks for weather); Processed ephemerally: **Yes**; Required or optional: **Optional**; Purpose: **App functionality** |
| Camera / photos | **Not collected.** The QR mission reads frames on the device only |
| Purchase history | **Not collected** by the developer. Google Play Billing handles payment, and the app only keeps a local "subscribed" flag |
| App activity, device IDs, personal info | Not collected |

> **Location note:** the weather tab sends the latitude and longitude of the province or district the user *chose*
> (not GPS) to MET Norway. Declaring it as approximate location is the safe answer. Answering "no data collected"
> could be defended, but a reviewer could flag it. Say if you prefer that.

### Permission declarations Play will probably ask for
- **Exact alarms** (`SCHEDULE_EXACT_ALARM`): core function = alarm clock
- **Full-screen intent** (`USE_FULL_SCREEN_INTENT`): declare "Alarm clock"
- **Foreground service, media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`): `RingService` plays the alarm sound.
  Play asks for a short video of the alarm ringing
- **Camera**: QR-code dismiss mission (no declaration form, but the privacy policy should mention it)

## 3. Privacy policy gap: fix before submitting Data safety
`index.html` doesn't mention: the MET Norway weather request (chosen place coordinates), camera use for QR
scanning (on-device only), Google Play Billing for subscriptions, or Android backup. The Data safety form has to
match the policy, so it should be updated first. I can make that edit.

## 4. License testing
Settings > License testing → add your Google account email (the one signed in to Play Console), response
`RESPOND_NORMALLY`.

## 5. Closed testing
Testing > Closed testing → Create track (e.g. "beta") → add a Google Group or an email list of testers (include
yourself) → choose countries → create a release with the signed AAB (versionCode 21 or higher).
New personal accounts need **12 testers opted in for 14 days** before production access.
