# AndroRadio

Webradio Android (téléphone et tablette) — éditeur **plug-your-home**.
Interface en anglais, français et russe.

## Fonctions (v1.0)

- Recherche de stations dans l'annuaire libre [Radio Browser](https://www.radio-browser.info) : par nom, genre, pays de l'appareil ou monde entier.
- Lecture en arrière-plan (Media3 / ExoPlayer) : notification, écran verrouillé, boutons Bluetooth, pause si le casque est débranché, pause pendant les appels.
- Titre en cours (métadonnées ICY), logo de la station.
- Flux MP3, AAC, OGG, HLS ; flux `http://` acceptés.
- Reconnexion automatique (3 essais) si un flux décroche.
- Favoris et historique (50 dernières stations), stockés uniquement sur l'appareil.
- « Lecture » depuis un casque Bluetooth reprend la dernière station, même après redémarrage.
- Mise en page adaptative : barre de navigation sur téléphone, rail latéral et grille multi-colonnes sur tablette.
- Choix de la langue dans l'appli (Android 13+) ; avant, l'appli suit la langue du système.

## Pile technique

| | |
|---|---|
| Langage | Kotlin 2.2, Jetpack Compose (Material 3, couleurs dynamiques Android 12+) |
| Lecture | AndroidX Media3 1.7 (ExoPlayer, HLS, MediaSessionService, OkHttp) |
| Réseau | OkHttp + kotlinx.serialization |
| Images | Coil |
| SDK | minSdk 24 (Android 7.0), targetSdk / compileSdk 36 |
| Package | `com.plugyourhome.androradio` (`.debug` pour les builds de test) |

## Structure

```
app/src/main/java/com/plugyourhome/androradio/
├── AndroRadioApp.kt          Application : API + stockage partagés
├── MainActivity.kt
├── data/
│   ├── Station.kt            Modèle + conversion en MediaItem
│   ├── RadioBrowserApi.kt    Client Radio Browser (miroirs avec bascule)
│   └── StationStore.kt       Favoris / historique (JSON local)
├── playback/
│   ├── PlaybackService.kt    ExoPlayer + MediaSession (arrière-plan)
│   └── PlayerConnection.kt   MediaController → état pour l'interface
└── ui/
    ├── MainViewModel.kt
    ├── AndroRadioScreen.kt   Onglets, recherche, feuille « En cours »
    ├── Components.kt         Cartes, grille, mini-lecteur
    └── theme/Theme.kt
```

## Compiler

**Avec GitHub Actions** (aucune installation) : chaque push sur `main` compile un APK de test.
Onglet *Actions* → dernier build → artefact `androradio-debug-apk`.

**En local** : ouvrir le dossier dans Android Studio, ou :

```bash
./gradlew assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## Publier sur Google Play

1. Créer une clé de signature (une seule fois, à conserver précieusement) :
   ```bash
   keytool -genkeypair -v -keystore androradio-release.jks -alias androradio \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Dans le dépôt GitHub → *Settings* → *Secrets and variables* → *Actions*, ajouter :
   - `ANDRORADIO_KEYSTORE_BASE64` : sortie de `base64 -w0 androradio-release.jks`
   - `ANDRORADIO_KEYSTORE_PASSWORD`
   - `ANDRORADIO_KEY_ALIAS` : `androradio`
   - `ANDRORADIO_KEY_PASSWORD`
3. Le workflow produit alors aussi l'artefact `androradio-release` (`.aab` pour la Play Console).
4. Avant chaque nouvelle version : augmenter `versionCode` et `versionName` dans `app/build.gradle.kts`.

Icône 512 × 512 pour la fiche Play : `store/play_icon_512.png`.

### Fiche « Sécurité des données »

Aucune donnée collectée ni partagée : pas de compte, pas de publicité, pas d'analytics.
Les favoris et l'historique restent sur l'appareil. Les seules requêtes réseau vont vers
Radio Browser (annuaire) et vers les serveurs des radios écoutées.

## Feuille de route

- Minuterie de sommeil
- Ajout manuel d'une URL de flux
- Widget d'écran d'accueil, Android Auto
- Suggestions de stations selon l'historique
- Envoi d'une station vers la webradio maison (ESP32 / Raspberry Pi)

## Crédits

Annuaire des stations : [Radio Browser](https://www.radio-browser.info), base communautaire libre.
