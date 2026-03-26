<ENGLISH>

🎧 Ziker — Project Overview
I bought this magnificent pair of headphones back in 2015—a design so unique and captivating!
I found them again a few years later, after unfortunately having to abandon them because I no longer had a phone compatible with the legacy app. I absolutely wanted to use them with my current primary device.

So, in 2025—12 years later—I decided to use my technical knowledge and AI to breathe life back into this "King of Music"!

💡 The Concept
To start, I needed a name. It had to be distinct enough not to infringe on Parrot's identity; therefore, I couldn't include "Parrot" or "Zik" in the name.

I chose Ziker, a contraction of "Zik" and "er" because it’s short, simple, and effective.

As for the logo, it's very straightforward: a profile view of the Parrot Zik 3. It represents the concept well without using the exact design for obvious legal reasons... The Bluetooth logo and a double circle evoke the synchronization between the app and the headset.

🎯 Project Goals
Ziker is an application for modern Android phones (Android 7 to 16) designed to revive the Parrot Zik 3 (and Zik 2).
The goal is to recreate the original application almost in its entirety, re-implementing as many hardware-supported features as possible (EQ, ANC, Concert Hall, presets, sensors, system settings), with a behavior as close to the original as possible.

✅ What has been achieved:
A significant part of the work involved continuous comparison between the firmware's actions, the headset's responses, and the original app's behavior to extract as much technical material as possible. I had to restart several times, perform multiple logcats of the apps (both mine and Parrot's), and carry out countless installations, uninstalls, and device switches to see this project through!

In total, over 75 hours of hard work were spent on phones, the headset (whose aesthetics suffered a bit during the process...), VS Code, and its AI agents.

❌ What had to be removed:
Noise Map: Offline for a long time.

Online Accounts: Everything related to account creation or Cloud functions.

Flight Mode: The function was operational, but the headset struggled to "wake up" after deactivation, so I decided to remove it.

🔬 Methodology & Reverse Engineering
I began by gathering essential elements: the original app (v1.91 from 2018), the latest firmware (3.0.7), and my Ivory Zik 3.

The process followed three major steps:

Analysis (Galaxy Note 3 - Stock & Rooted):
Using this device, I analyzed Bluetooth logs between the headset and the phone by "playing" with every button and gauge. This allowed me to create a complete "technical dictionary": specifications, connection algorithms, and functional behaviors, all validated by firmware dissection.

Design (Galaxy S22 Ultra - Custom ROM & Rooted):
I tasked VS Code's AI agents to create my UI based on screenshots taken from the original app.
Not without difficulty, it took several hours to achieve the result we have today. The same applies to the app's core functions—notably the EQ disc, which gave me quite a bit of trouble!

Final Validation (Galaxy S25 Ultra - Stock):
This device served as the final platform to install the release and perform full final testing (the app you see here).

🚀 What this project demonstrates
This project proves the feasibility of advanced functional reconstruction around a proprietary product, without access to the original source code, thanks to a disciplined reverse engineering methodology and empirical hardware validation.

You are free to test the application with your Zik 1, Zik 2, and Zik 3.

Technical Documentation
UUIDs & Channel Opening (Connection)
UUIDs (Trial Order):

Standard SPP: 00001101-0000-1000-8000-00805F9B34FB (Universal fallback)

Parrot Zik 3: 8b6814d3-6ce7-4498-9700-9312c1711f64

Parrot Zik 2: 8b6814d3-6ce7-4498-9700-9312c1711f63

Socket: createInsecureRfcommSocketToServiceRecord(UUID) → close socket in catch block if failed.

Mandatory Handshake:

Sent: 00 03 00

Response: 00 03 02 (byte 2 = firmware version)

Rule: Do not send anything before the handshake; wait ~300ms after the response.

Message Format (Technical Reminders)
Request: [LEN_HI][LEN_LO][0x80] + "GET /api/path?args" ; LEN = payload.size + 3 (big‑endian). No CRLF.

Response: [LEN_HI][LEN_LO][0x80][0x01][0x01][PAY_LEN_HI][PAY_LEN_LO] + XML_UTF8.

Note: Multiple XMLs can be concatenated → parse via accumulation. Always format floats with .0 (e.g., 5.0).

1) Noise Control (ANC)
Endpoints:

GET /api/audio/noise_control/get → <noise_control ... value="0|1|2"/>

GET /api/audio/noise_control/set?arg=anc&value=<0|1|2> — e.g., value=2 = Strong ANC

GET /api/audio/noise_control/auto_nc/set?arg=true|false — toggles auto NC

Values: 0 = Off, 1 = Low, 2 = Strong.

Client Usage: Wait for XML confirmation before updating ancMode/ancEnabled StateFlows.

2) Thumb EQ
Endpoints:

GET /api/audio/thumb_equalizer/value/get → <thumb_equalizer arg="v1,v2,v3,v4,v5,x,y"/>

GET /api/audio/thumb_equalizer/value/set?arg=v1,v2,v3,v4,v5,x,y

Format:

v1..v5 = 5-band gains (Double) → range -6.0 to +6.0

x,y = Joystick position (Int) → 0 to 100

Example: "-1.5,2.5,5.5,5.0,4.0,55,36"

Legacy: Supports CSV 5-value formats for Zik 2.

3) Concert Hall (Sound Effects)
Endpoints:

GET /api/audio/sound_effect/room_size/set?arg=<room> — silent|living|jazz|concert

GET /api/audio/sound_effect/angle/set?arg=<angle> — discrete values 30|60|90|120|150|180

Deactivation: arg=silent.

4) Switches (Practical Examples)
ANC: value=0 → OFF; value=1|2 → ON.

Concert Hall: room_size=silent → OFF.

EQ: No binary switch; considered OFF if v1..v5 are all 0.0.

5) Firmware & Battery
Firmware: Byte 2 of handshake (0x02 = Zik 3, 0x00 = Zik 2). Use GET /api/system/device_type/get for confirmation.

Battery: GET /api/system/battery/get → <battery percent="NN" state="charging|in_use"/>.

Polling: Battery check immediately upon connection, then a 15s loop for battery, ANC, EQ, and Metadata.

6) Robustness & Parsing
Concatenated Messages: Accumulate bytes, use LEN_HI/LO to split messages, parse XML, then consume buffer.

Timing: Crucial 300ms delay after handshake before sending commands.

<FRENCH>

# 🎧 Ziker — Présentation du projet

J'avais acheté en 2015 ce magnifique casque, au design si particulier et passionnant !
Je l'ai retrouvé quelques années plus tard, après l'avoir malheureusement abandonné car je n'avais plus de téléphone compatible avec l'ancienne application et je voulais absolument m'en servir avec mon téléphone principal.

C'est donc en 2025, soit 12 ans plus tard, que je me décide à utiliser mes connaissances et l'IA afin de redonner vie à ce roi de la musique !

## 💡 Le Concept
Pour commencer, j'avais besoin d'un nom. Il fallait qu'il ne soit pas trop représentatif afin de ne pas usurper l'identité de Parrot ; je ne devais donc pas inclure "Parrot" ni "Zik" dedans.

Je l'ai appelé **Ziker**, une contraction de "Zik" et le "er" parce que je trouve ça court, simple et efficace.

Pour le logo, c'est très simple : le Parrot Zik 3 dessiné de profil. Il offre une bonne représentation de l'idée sans en avoir le design exact pour des raisons juridiques évidentes... Le logo Bluetooth et un double cercle invoquent la synchronisation entre l'app et le casque.

## 🎯 Objectifs du projet
Ziker est une application pour téléphones Android récents d'Android 7 à 16 dont le but est de redonner vie au Parrot Zik 3 (et 2). 
Le but est de retrouver l’application d’origine quasiment dans son intégralité, en réimplémentant le maximum de fonctions réellement supportées par le casque (**EQ, ANC, Concert Hall, presets, capteurs, réglages système**), avec un comportement au plus proche de l’original.

### ✅ Ce qui a été réalisé :
Une part importante du travail a consisté à comparer en continu ce que fait le firmware, les réponses du casque ainsi que le comportement avec l'app d'origine pour tirer un maximum de matière techniques pour travailler. J'ai du m'y reprendre à plusieurs fois, faire plusieurs logcat de l'app (la mienne et celle de Parrot), effectuer un nombre incalculable d'installation, désinstallation, switch entre les appareil afin d'arriver au bout du projet!

Au total, plus de 75H de travail acharné sur les telephones, le casque (dont l'éstétique de ce ceernier a mal vécu...),  VS Code, et ses agents AI.

### ❌ Ce qui a du être retiré :
* **Carte du bruit** : Hors ligne depuis longtemps.
* **Comptes en ligne** : Tout ce qui est lié à la création d'un compte ou aux fonctions Cloud.
* **Le mode Avion** : La fonction était fonctionnelle mais le casque avait beaucoup de mal à "revenir à lui" après désactivation, je l'ai donc supprimée.

## 🔬 Méthodologie et Reverse Engineering

J'ai commencé par rassembler les éléments essentiels : l'application originale (v1.91 de 2018) et le dernier firmware (3.0.7), ainsi que mon Zik3 ivoire.

**Le processus a suivi trois étapes majeures :**

1. **L'Analyse (Galaxy Note 3 - Stock & Rooté)** : 
   Grâce à cet appareil, j'ai analysé les logs Bluetooth entre le casque et le téléphone en "jouant" avec chaque bouton/gauge. J'ai ainsi pu créer un "dictionnaire technique" complet : spécifications, algorithmes de connexion et comportements des fonctions et du casque, le tout valider grace à la disection du firmware.
   
2. **La Conception (Galaxy S22 Ultra - Rom Custom & Rooté)** : 
   J'ai demandé aux agents IA de VS Code de créer mon UI en se basant sur les captures effectuées de l'app orignale.
   Non sans mal, j'ai pris plusieurs heures pour obtenir le resultat que nous avons aujourd'hui. 
   Pareils pour obtenir le bon fonctionnement des fonctions de l'app, notamment le disque de l'EQ, qui m'a donné pas mal de file à retordre! 

3. **La Validation Finale (Galaxy S25 Ultra - Stock)** : 
   Cet appareil m'a servi de version finale pour installer la "release" et effectuer les derniers tests complets. C'est l'application que vous avez ici.


## 🚀 Ce que ce projet démontre
Ce projet prouve la faisabilité d’une reconstruction fonctionnelle avancée autour d’un produit propriétaire, sans accès au code source original, grâce à une méthodologie de reverse engineering disciplinée et une validation empirique sur matériel.


---
*Vous êtes libres de tester l'application avec vos Zik 1, Zik 2 et Zik 3.*


-----------------------------------------
Partie technique:

UUIDs & ouverture du canal (Connexion)

UUIDs (ordre d’essai):
SPP standard: 00001101-0000-1000-8000-00805F9B34FB (fallback universel)

Parrot Zik 3: 8b6814d3-6ce7-4498-9700-9312c1711f64
Parrot Zik 2: 8b6814d3-6ce7-4498-9700-9312c1711f63

Socket: createInsecureRfcommSocketToServiceRecord(UUID) → fermer le socket dans le catch si échec.

Handshake obligatoire:
Envoi: 00 03 00
Réponse: 00 03 02 (byte 2 = version firmware)

Règle: ne rien envoyer avant handshake ; attendre ~300 ms après réponse.

Format messages (rappels techniques)

Requête: [LEN_HI][LEN_LO][0x80] + "GET /api/chemin?args" ; LEN = payload.size + 3 (big‑endian). 
Pas de CRLF.

Réponse: [LEN_HI][LEN_LO][0x80][0x01][0x01][PAY_LEN_HI][PAY_LEN_LO] + XML_UTF8. 

Plusieurs XML peuvent être concaténés → parser par accumulation.

Bonnes pratiques: formater les entiers flottants avec .0 (ex. 5.0).

1) Contrôle du bruit (ANC)

Endpoints:
GET /api/audio/noise_control/get → <noise_control ... value="0|1|2"/>
GET /api/audio/noise_control/set?arg=anc&value=<0|1|2> — ex. value=2 = ANC fort
GET /api/audio/noise_control/auto_nc/set?arg=true|false — active/désactive auto NC

Valeurs observées:
value=0 → off, value=1 → faibles, value=2 → fort
auto_nc = true/false

Usage côté client:
Envoyer la requête encodée dans le header + 0x80.
Après envoi, attendre la réponse XML confirmant l’état puis mettre à jour ancMode/ancEnabled StateFlow.

Notes: ne pas envoyer avant handshake ; l’état enabled peut être récupéré via GET /api/audio/noise_control/enabled/get.

2) Égaliseur (Thumb EQ)

Endpoints:

GET /api/audio/thumb_equalizer/value/get → <thumb_equalizer arg="v1,v2,v3,v4,v5,x,y"/>
GET /api/audio/thumb_equalizer/value/set?arg=v1,v2,v3,v4,v5,x,y

Format & significations:
v1..v5 = gains 5 bandes (double) → plage observée -6.0 .. +6.0
x,y = position joystick (int) → 0 .. 100

Exemple capturé: "-1.5,2.5,5.5,5.0,4.0,55,36"

Compatibilité: support de formats legacy (CSV 5 valeurs pour Zik2).

Usage côté client:
Toujours formatter les gains comme doubles (ex. 5.0).
Envoyer set puis lire la réponse XML pour synchroniser thumbEq StateFlow.

Recommandation UI: conserver bouton/apparence ON/OFF via présence de valeurs non nulles ; pas d’endpoint explicite ON/OFF — l’égaliseur est défini par ses valeurs.

3) Concert Hall (effets sonores)

Endpoints:
GET /api/audio/sound_effect/room_size/set?arg=<room> — silent|living|jazz|concert
GET /api/audio/sound_effect/angle/set?arg=<angle> — valeurs discrètes 30|60|90|120|150|180

Comportement: valeurs discrètes (paliers fixes), pas continues.

Désactivation: arg=silent → effet désactivé.

Usage client: envoyer set puis vérifier réponse XML pour confirmer roomSize mis à jour.

4) Interrupteurs ON/OFF (exemples pratiques)

ANC: value=0 → OFF ; value=1|2 → ON (niveaux). auto_nc = true|false.

Concert Hall: room_size=silent → OFF ; autres valeurs → ON (choix de preset).

Égaliseur: pas d’interrupteur binaire ; on considère OFF si v1..v5 tous à 0.0.

Commandes broadcast (tests): la spec liste des broadcasts ADB pour action rapide (ex. ACTION_ANC_ON/OFF) pour tests locaux.

5) Version firmware

Handshake rapide: byte 2 de la réponse handshake (00 03 02) indique version (ex. 0x02 = Zik 3, 0x00 = Zik 2).

Endpoint utile: GET /api/system/device_type/get → <device_type value="..."/> (donne type/appareil).

Conclusion: vérifier byte handshake + éventuellement device_type pour déterminer génération/firmware.

6) Batterie & fréquence d’actualisation

Endpoint: GET /api/system/battery/get → <battery percent="NN" state="charging|in_use"/>

Polling: à la connexion → envoyer packetForBattery() immédiatement ; puis boucle toutes les 15 s via pollState() qui récupère battery, noise_control, thumb_eq, track_metadata.

Usage client: mettre à jour battery et isCharging StateFlows à chaque réponse.

7) Polling général & StateFlows

Séquence: connexion → battery+noise_control immédiats → après 3 s démarrage de la boucle 15 s.

StateFlows exposés: battery, isCharging, ancMode, ancEnabled, thumbEq, roomSize, trackTitle, isConnected, searchStatus.

8) Robustesse & parsing

Messages concaténés: accumuler les octets, lire LEN_HI/LO pour découper chaque message, parser XML puis consommer le buffer.

Socket handling: déclarer var s: BluetoothSocket? = null avant try et socket.close() dans catch.

Timing: attendre ~300 ms après handshake avant la première commande.
