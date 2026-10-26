# Regélő – Android alkalmazás (v1.3.0)

Magyar írói és olvasói platform (Wattpad / Inkitt jellegű), Kotlin + Jetpack Compose.

- Csomagnév: `hu.regelo.app`
- Backend: Supabase (Postgres + RLS, Auth, Storage, Realtime), projekt: `pqmfezkvaslqedalwwnn` (Frankfurt)
- Push: Firebase Cloud Messaging (`regelo` projekt) + `send-community-push` Edge Function
- Offline-first: minden írás azonnal a telefonra mentődik (Room), a felhőbe a háttérben szinkronizál (WorkManager)

## Fordítás
Android Studio › Open › ez a mappa › (Gradle sync) › Build › Build App Bundle(s) / APK(s) › Build APK(s).
Az APK: `app/build/outputs/apk/debug/app-debug.apk`

## Android-kiadás és aláírás
Ez a repository natív Android/Jetpack Compose alkalmazás; iOS-, Expo-, Capacitor-, React Native- vagy Flutter-projekt nincs benne.
Google Playre aláírt Android App Bundle-t (AAB) tölts fel, ne fejlesztői debug APK-t.

### Aláírt AAB Android Studióval
1. Nyisd meg a repository gyökérmappáját Android Studióban, várd meg a Gradle szinkronizálását, és futtasd a teszteket.
2. A **Build › Generate Signed Bundle / APK… › Android App Bundle** útvonalon válaszd a `release` build variantot.
3. Első kiadáskor hozz létre külön feltöltési (upload) kulcsot és keystore-t. A fájl, a jelszavak és az alias
   biztonságos, Git-en kívüli helyre kerüljenek; a Google Play App Signing bekapcsolása ajánlott.
4. Készítsd el az AAB-t, ellenőrizd a Play Console belső tesztcsatornáján, majd csak sikeres ellenőrzés után
   indítsd a szélesebb körű kiadást.
5. Minden új feltöltés előtt növeld az `app/build.gradle.kts` fájlban a `versionCode` értékét. Jelenlegi értéke
   `130`; ez azonos vagy kisebb értékkel nem tölthető fel frissítésként.

### Parancssoros/CI aláírás
Biztonságos build/CI titokként add meg mind a négy környezeti változót:
`REGELO_STORE_FILE`, `REGELO_STORE_PASSWORD`, `REGELO_KEY_ALIAS`, `REGELO_KEY_PASSWORD`. Például a fájl elérési
útja `C:\secure\regelo-upload.jks` lehet. A jelszavakat ne írd parancssorba, scriptbe vagy repository-fájlba.
Ezután futtasd:

```powershell
.\gradlew.bat :app:bundleRelease
```

A kimenet helye: `app\build\outputs\bundle\release\app-release.aab`. Vagy mind a négy változó legyen beállítva,
vagy egyik sem; részleges beállításnál a Gradle szándékosan hibával leáll. Változók nélkül az `assembleRelease`
ellenőrző build lefut, de nem állít elő Playre feltölthető, a saját kiadási kulcsoddal aláírt csomagot.
Az Android Gradle build nem használ `.env` fájlt. A Supabase URL és publishable klienskulcs nem titok; adatvédelmüket
a szerveroldali hitelesítés és RLS-szabályok adják. Service-role kulcsot tilos a mobilalkalmazásba tenni.

### Play Console kiadás előtti kapu
- Ellenőrizd és publikáld az adatkezelési tájékoztató és támogatás URL-jét, valamint a Data safety adatlapot.
- Adj meg működő, alkalmazáson kívüli webes fióktörlési kérelem URL-t is. Az alkalmazáson belüli törlőgomb önmagában
  nem teljesíti ezt a Play-követelményt; ilyen publikus URL jelenleg nincs azonosítva.
- Az alkalmazás API 37-et céloz. A `versionCode` frissítése és az aláírt bundle ellenőrzése még szükséges a kiadáshoz.
- A completion RPC-t használó kliens kiadása előtt a cél Supabase-környezeten legyen alkalmazva a
  `supabase/2026-10-26_app_v1_4.sql` migráció.

## Adatbázis
A `supabase/` mappában lévő SQL fájlok idempotensek (többször is futtathatók) a Supabase SQL Editorban.
A kliens frissítésének kiadása előtt futtasd a `supabase/2026-10-26_app_v1_4.sql` migrációt a v1.3 után;
az idempotens olvasásbefejezés RPC-t igényli. A hozzá tartozó adatbázis-tesztsor:
`supabase/tests/reading_state_v1_4.sql`.

## Mentés
Ez a mappa a teljes forráskód. Készíts róla rendszeresen másolatot (pl. OneDrive vagy GitHub privát tároló),
hogy egy véletlen törlés ne okozzon gondot.

## Kapcsolat
Hivatalos e-mail: regeloapp@gmail.com · Hivatalos profil az appban: @regelohivatalos (moderátor, hírei minden felhasználónak mennek).

## Változások
- **v1.4 backend-kiegészítés** – A könyv befejezésének mentése szerveroldali, idempotens művelet,
  amely a budapesti naptári évet és az adatbázisban szereplő publikált/befejezett állapotot használja.
  A rate limit párhuzamos kéréseknél is atomikusan érvényesül. A kliens nem ment tokent titkosítatlanul,
  az adat-export a teljes, szerveroldali GDPR-exportot használja, és a Room frissítés nem törli csendben
  az ismeretlen sémájú helyi adatokat.
- **1.3.0** – Hivatalos kiadványok, adatvédelem és biztonság:
  a Regélő négy, folyamatosan bővülő hivatalos „könyve” (Hírek, Kihívások, Heti válogatás, Kávéház) – az új részeket a szerver
  automatikusan teszi közzé (hírből, kihívásból, heti válogatásból, beszélgetésindítóból); „A Regélőtől” sáv a Könyvtárban
  (olvasatlan részek száma, saját pozíció; Beállítások › Tartalom alatt kikapcsolható), saját borító, „Folyamatosan bővül” jelölés,
  feliratkozás az új részekre (push is), a legfrissebb részek az adatlap elején; a hivatalos kiadványok nem értékelhetők, és nem
  ismétlődnek a listákban; belső hivatkozások (regelo://book/…, regelo://profile/…) a szövegekben.
  Állapotkezelési javítások: az olvasási pozíció a rész azonosítóját is tárolja (nem csúszik el, ha egy korábbi rész eltűnik);
  a végére ért olvasás visszakerül a „Folytatás” sorba, ha új rész jelenik meg; a kihívások határidejét a szerver órája dönti el;
  a szerver által visszavont történet helyi másolata is törlődik; az üzenetváltás akadozó élő kapcsolatnál is frissül.
  Adatvédelem és biztonság: a bejelentkezési tokenek Android Keystore-ral (AES-256-GCM) titkosítva, a biztonsági mentésből kizárva;
  Beállítások › Adatvédelem › Adataim letöltése (JSON, GDPR); a fióktörlés a feltöltött képeket is törli (delete-account Edge Function);
  csak web- és e-mail-linkek nyílnak meg; szerveroldali szövegtisztítás, gyakoriságkorlát (komment, üzenet, körbejegyzés, jelentés, követés, adatexport),
  szerepkörök (adminisztrátor, moderátor, hivatalos szerkesztő), adatmegőrzési idők (hibanapló 90 nap, törölt kommentek 30 nap, megtekintések 13 hónap);
  kíméletes frissítési kérés, ha a telepített verzió már nem támogatott. SQL: `supabase/2026-10-15_app_v1_3.sql`,
  jogosultsági tesztsor: `supabase/tests/rls_battery_v1_3.sql`, teljes sémakép: `supabase/schema-snapshot-2026-10-15.sql`,
  Edge Function: `supabase/functions/delete-account`.
- **1.2.1** – Teljes átvizsgálás és hibajavítás:
  az időzített történeteket a szerver jelenteti meg a megadott napon (magyar idő szerint, 10 percenként ellenőrizve), akkor is, ha az app nincs megnyitva;
  a komment- és válaszértesítés az olvasót a hozzászólás bekezdésénél nyitja meg; a törölt kommentek válaszai „elrejtett” szál alatt maradnak láthatók;
  kijelentkezés előtt figyelmeztetés, ha valami még nincs feltöltve; értékelés törölhető; a fejezetek sorszáma és az „Itt tartasz” jelölés pontos;
  az olvasási kihívásban egy újraolvasás a következő évben külön számít; üzenetküldés akkor is befejeződik, ha közben elhagyod a képernyőt;
  a borítókivágás nem akasztja meg a felületet; táblagépen és fekvő módban helyesebb elrendezés; biztonságosabb keresés és fájlimportálás;
  adatbázis-gyorsítás (indexek, egyszerűbb jogosultsági szabályok), szigorított függvényjogosultságok; frissített AndroidX-könyvtárak,
  lint-hibák javítva. SQL: `supabase/2026-10-14_app_v1_2_1.sql`.
- **1.2.0** – Közösségi és olvasói bővítés:
  értesítés (push is) a követett szerzők új fejezeteiről és új történeteiről, valamint az elmentett / olvasott történetek új fejezeteiről;
  aktivitásfolyam a Közösség › Újdonságok fülön (új fejezetek, új történetek, hírek, vélemények, kihívások);
  „Neked ajánljuk” a Felfedezésben; olvasókörök (Közösség › Körök: közös olvasmány, témák, válaszok, némítás, tagkezelés, jelentés);
  írói kihívások (Közösség › Kihívások: meghirdetés moderátorként, nevezés publikált művel, titkos szavazás, eredményhirdetés);
  olvasóban fókusz („Ne zavarj”) mód, kijelző ébren tartása, bekezdés-kiemelések és saját jegyzetek (hosszú nyomás), felolvasás magyar hangon;
  Könyvtár › Jegyzeteim; olvasási kihívás (éves cél, végigolvasott könyvek); jelvények a profilon;
  18+ tartalmak csak életkor-megerősítés után (Beállítások › Tartalom). SQL: `supabase/2026-10-14_app_v1_2.sql`,
  Edge Function: `supabase/functions/send-community-push`.
- **1.1.1** – Új alkalmazásikon: dőlt Lora „R” rozsdaszínben, tintakék alapon (adaptív vektoros ikon + témás ikon Android 13+, új értesítési ikon). Áruházi ikon: `docs/brand/regelo-ikon-512.png`.
- **1.1.0** – Írói eszközök: saját mű exportja PDF-be (A5, könyvszerű tördelés, borító, tartalomjegyzék),
  EPUB e-könyvbe (borító, képek, tartalomjegyzék) és Word/DOCX-be (címsorstílusok, oldaltörés fejezetenként) –
  mentés a telefonra/Drive-ra vagy megosztás (Írás › ⋮ › Exportálás, illetve a történet szerkesztőjében).
  Új statisztika: olvasók, végigolvasók, napi megtekintések diagramja (30 nap), fejezetenkénti „hol tartanak az olvasók”
  kimutatás a lemorzsolódás jelzésével, fejezetkedvelések és kommentek. SQL: `supabase/2026-10-13_app_v1_1.sql`.
- **1.0.2** – Teljes átvizsgálás és javítások: a háttérben érkezett értesítésre koppintva a megfelelő képernyő nyílik
  (üzenet, profil, történet); a kijelentkezés/fióktörlés mindig végigfut (nem marad helyi adat); lejárt e-mailes link
  esetén érthető üzenet; jelszó-visszaállításnál a helyes link küldhető újra; fejezet megnyitása/bezárása már nem
  „frissíti” feleslegesen a könyvet; időzített megjelenéskor a fejezetek is publikussá válnak; dátumválasztó az időzítéshez;
  fejezetszerkesztőben látszik a piszkozat/publikus állapot és van „Publikálás” gomb; a fejezet végére érve 100% lesz
  az olvasási előrehaladás; a „Legjobbra értékelt” rendezés figyelembe veszi a keresést és a szűrőket, és a lapozás
  nem áll le idő előtt; az üzenetváltásban a legújabb üzenetek látszanak; az értesítések megnyitáskor olvasottá válnak;
  a profilnál a linkek ellenőrzése és borítókép-eltávolítás; a visszajelzések (snackbar) nem tartják fel a navigációt;
  egyéb apróságok (eltávolítás gomb a „folytatás” kártyán, gombok tördelése kis kijelzőn, elavult ikon cseréje).
- **1.0.1** – Felhasználókeresés (Felfedezés › Felhasználók, Közösség › keresés ikon; név vagy @felhasználónév, ékezet nélkül is),
  hivatalos fiók jelvénnyel, hír közzététele a profilról (hivatalos fióknál mindenkinek), hírek a profilon,
  kattintható linkek és e-mail-címek a bemutatkozásban, Beállítások › Kapcsolat és visszajelzés, hivatalos e-mail a jogi szövegekben.
  SQL: `supabase/2026-10-13_app_v1_0_1.sql`.
- **1.0.0** – Az app újraépítése a meglévő Supabase- és Firebase-projektre.
