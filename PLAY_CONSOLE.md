# Publier AndroRadio sur Google Play — pas à pas

Tout ce qui est préparé dans le dépôt, et les réponses à donner dans la Play Console.

## 1. Ce qui est déjà prêt dans le dépôt

| Élément | Emplacement |
|---|---|
| Politique de confidentialité (EN/FR/RU) | `PRIVACY.md` — URL : https://github.com/rkroman-86/AndroRadio/blob/main/PRIVACY.md |
| Titre, description courte, description complète, notes de version | `fastlane/metadata/android/{en-US,fr-FR,ru-RU}/` |
| Icône 512 × 512 | `fastlane/metadata/android/en-US/images/icon.png` |
| Bannière (feature graphic) 1024 × 500 | `fastlane/metadata/android/en-US/images/featureGraphic.png` |
| Écran « À propos » dans l'appli (crédits Radio Browser, MusicBrainz, Cover Art Archive, lien confidentialité) | bouton ⓘ en haut à droite |
| Build release minifié (R8) vérifié à chaque push | GitHub Actions |

## 2. Règles des services utilisés — état

**Radio Browser** (https://docs.radio-browser.info) — données dans le domaine public.
- User-Agent descriptif `AndroRadio/version` sur chaque requête ✅
- Serveurs trouvés par DNS de `all.api.radio-browser.info` côté client, `/json/servers` seulement en repli ✅
- Compteur de clics `/json/url/{uuid}` appelé à chaque lancement de station (y compris ⏮ ⏭ et reprise) ✅
- Crédit visible : écran « À propos » + description de la fiche ✅
- Optionnel : signaler l'appli à l'auteur de Radio Browser pour figurer dans la liste des applis sur radio-browser.info.

**MusicBrainz / Cover Art Archive** (pochettes, remplace iTunes)
- User-Agent avec contact `AndroRadio/version ( https://github.com/rkroman-86/AndroRadio )` ✅
- Au plus 1 requête par seconde, résultats en cache ✅
- Une seule recherche par changement de morceau ✅

## 3. Signature de la version Play

1. Créer la clé (une seule fois, à sauvegarder hors du dépôt — sa perte empêche toute mise à jour) :
   ```bash
   keytool -genkeypair -v -keystore androradio-release.jks -alias androradio \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. GitHub → *Settings* → *Secrets and variables* → *Actions* :
   `ANDRORADIO_KEYSTORE_BASE64` (sortie de `base64 -w0 androradio-release.jks`),
   `ANDRORADIO_KEYSTORE_PASSWORD`, `ANDRORADIO_KEY_ALIAS` (`androradio`), `ANDRORADIO_KEY_PASSWORD`.
3. Au prochain push, l'artefact `androradio-release` contient le `.aab` à envoyer.
4. Dans la Play Console, accepter **Play App Signing** (Google garde la clé de distribution, ta clé sert de clé d'importation).
5. À chaque nouvelle version : augmenter `versionCode` et `versionName` dans `app/build.gradle.kts`, et ajouter `fastlane/metadata/android/<langue>/changelogs/<versionCode>.txt`.

## 4. Fiche Play Store

- **Nom de l'appli** : `AndroRadio – Internet Radio` (fr : `AndroRadio – Radio Internet`, ru : `AndroRadio – интернет-радио`)
- **Catégorie** : Musique et audio
- **Coordonnées** : adresse e-mail de contact (obligatoire, visible publiquement), site web optionnel (le dépôt GitHub)
- **Langue par défaut** : anglais (en-US) ; ajouter les traductions fr-FR et ru-RU depuis `fastlane/`.

### Captures d'écran — déjà générées, sans aucune marque de tiers
Les captures sont produites automatiquement (stations, logos et pochettes fictifs dessinés pour l'appli) par le workflow « Store screenshots », à chaque changement d'interface :

| Format Play | Dossier (par langue) | Taille |
|---|---|---|
| Téléphone | `fastlane/metadata/android/<langue>/images/phoneScreenshots/` | 1233 × 2460 |
| Tablette 7" | `…/sevenInchScreenshots/` | 1200 × 1920 |
| Tablette 10" | `…/tenInchScreenshots/` | 1600 × 2560 |

Langues : `en-US`, `fr-FR`, `ru-RU`. Trois écrans : accueil, favoris, recherche.
Pour les régénérer à la main : onglet *Actions* → *Store screenshots* → *Run workflow*.
Ne pas utiliser de captures réelles avec de vraies stations (logos, pochettes et noms de marques).

## 5. « Contenu de l'appli » — réponses à donner

| Rubrique | Réponse |
|---|---|
| Politique de confidentialité | URL de `PRIVACY.md` (ci-dessus) |
| Publicités | Non, l'appli ne contient pas de publicité |
| Accès à l'appli | Toutes les fonctionnalités sont accessibles sans restriction |
| Public cible | 18 ans et plus (les flux sont diffusés par des tiers, non contrôlés) |
| Appli d'actualités | Non |
| Fonctionnalités financières / santé / gouvernement | Aucune |
| Types de service de premier plan | `mediaPlayback` : « Lecture audio continue d'une radio choisie par l'utilisateur, écran éteint, avec commandes dans la notification. » Préparer une courte vidéo (YouTube non répertoriée) : lancer une station, verrouiller l'écran, la lecture continue, commandes dans la notification. |
| Classification du contenu (questionnaire IARC) | Catégorie « Toutes les autres applis ». Pas de violence, sexualité, jeux d'argent ni achats. Pas d'échanges entre utilisateurs. Répondre honnêtement à la question sur les contenus non contrôlés : l'appli diffuse des flux de stations tierces. |

### Sécurité des données
L'éditeur ne collecte rien, mais l'appli **transmet** des données à des services tiers. Réponse recommandée (prudente) :

- **Votre appli collecte-t-elle ou partage-t-elle des données ?** Oui
- **Chiffrement en transit** : Oui (HTTPS vers Radio Browser, MusicBrainz, Cover Art Archive)
- **Suppression des données** : pas de compte ni de stockage côté éditeur (indiquer qu'aucune donnée n'est conservée par l'éditeur)
- **Type de données** : *Activité dans l'appli → Historique des recherches dans l'appli*
  - Collectée : oui — Partagée : non (envoi à Radio Browser déclenché par l'utilisateur, exemption « action de l'utilisateur »)
  - Traitée de façon éphémère : non (on ne contrôle pas les journaux du service tiers)
  - Collecte facultative : oui (uniquement si l'utilisateur fait une recherche)
  - Finalité : Fonctionnalité de l'appli
- Rien d'autre : pas de localisation (le pays vient de la langue du téléphone, pas du GPS), pas d'identifiants, pas de contacts, pas de données d'usage.

*Variante* : beaucoup d'applis radio déclarent « aucune donnée collectée ». C'est défendable, mais la déclaration ci-dessus correspond exactement à ce que fait l'appli.

## 6. Test fermé avant la production

Pour les comptes développeur **personnels** récents, Google exige un test fermé avec au moins 12 testeurs inscrits pendant 14 jours d'affilée avant de pouvoir passer en production (même règle que pour Lucky Slot si ton compte est concerné).
1. *Tests → Test fermé* : créer une piste, y envoyer le `.aab`.
2. Ajouter les testeurs (liste d'e-mails ou Google Group), leur envoyer le lien d'inscription.
3. Après 14 jours, demander l'accès à la production depuis le tableau de bord.

## 7. Après publication

- Si un diffuseur demande le retrait de sa station : la corriger ou la signaler dans Radio Browser (l'appli ne fait que lire l'annuaire).
- Les APK de test (`dev`) restent signés avec la clé de test du dépôt et s'installent à côté de la version Play (`com.plugyourhome.androradio.debug`).
