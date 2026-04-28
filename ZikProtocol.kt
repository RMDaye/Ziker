package com.rmdaye.ziker

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object ZikProtocol {
    const val TYPE_MES_DATA: Byte = 0x80.toByte()
    const val TYPE_MES_OPEN_SESSION: Byte = 0x00

    const val UUID_ZIK_2 = "8b6814d3-6ce7-4498-9700-9312c1711f63"
    const val UUID_ZIK_3 = "8b6814d3-6ce7-4498-9700-9312c1711f64"
    /** UUID SPP standard Bluetooth (fallback universel) */
    const val UUID_SPP    = "00001101-0000-1000-8000-00805F9B34FB"

    const val DEVICE_MAC = ""
    const val DEVICE_SERIAL = ""
    // Ne pas coder en dur la version firmware par défaut — laisser vide et lire dynamiquement
    const val DEVICE_FIRMWARE = ""
    const val DEVICE_PRODUCT = "zik3"

    object Api {
        const val AUDIO_NOISE_CONTROL_SET = "/api/audio/noise_control/set"
        const val AUDIO_NOISE_CONTROL_GET = "/api/audio/noise_control/get"
        const val AUDIO_NOISE_CONTROL_ENABLED_GET = "/api/audio/noise_control/enabled/get"
        const val AUDIO_NOISE_CONTROL_ENABLED_SET = "/api/audio/noise_control/enabled/set"
        const val AUDIO_PRESET_ACTIVATE = "/api/audio/preset/activate"
        const val AUDIO_PARAM_EQ_VALUE_SET = "/api/audio/param_equalizer/value/set"
        const val AUDIO_THUMB_EQ_VALUE_GET = "/api/audio/thumb_equalizer/value/get"
        const val AUDIO_THUMB_EQ_VALUE_SET = "/api/audio/thumb_equalizer/value/set"
        const val AUDIO_EQUALIZER_ENABLED_GET = "/api/audio/equalizer/enabled/get"
        const val AUDIO_EQUALIZER_ENABLED_SET = "/api/audio/equalizer/enabled/set"
        const val AUDIO_SOUND_EFFECT_ROOM_SIZE_SET = "/api/audio/sound_effect/room_size/set"
        const val AUDIO_SOUND_EFFECT_ANGLE_SET = "/api/audio/sound_effect/angle/set"
        const val AUDIO_SOUND_EFFECT_MODE_GET = "/api/audio/sound_effect/mode/get"
        const val AUDIO_SOUND_EFFECT_ENABLED_SET = "/api/audio/sound_effect/enabled/set"
        const val AUDIO_SOUND_EFFECT_GET = "/api/audio/sound_effect/get"
        const val BATTERY_GET = "/api/system/battery/get"
        const val AUDIO_ANC_PHONE_MODE_SET = "/api/audio/noise_control/phone_mode/set"
        // Nouveaux endpoints capturés par Frida (session 2)
        const val AUDIO_NOISE_CONTROL_AUTO_NC_SET = "/api/audio/noise_control/auto_nc/set"
        const val AUDIO_SMART_AUDIO_TUNE_SET = "/api/audio/smart_audio_tune/set"
        const val SYSTEM_DEVICE_TYPE_GET = "/api/system/device_type/get"
        const val SYSTEM_FRIENDLY_NAME_GET = "/api/bluetooth/friendlyname/get"
        const val SYSTEM_FRIENDLY_NAME_SET = "/api/bluetooth/friendlyname/set"
        const val AUDIO_PRESET_CURRENT_GET = "/api/audio/preset/current/get"
        const val AUDIO_PRESET_SET         = "/api/audio/preset/set"
        const val AUDIO_SOURCE_GET = "/api/audio/source/get"
        const val AUDIO_TRACK_METADATA_GET = "/api/audio/track/metadata/get"
        // Chemins confirmés logcat S22 Ultra :
        // - GET état porté (pi value / is_worn) → ancillary_sensor/presence/get
        // - SET activer/désactiver détection de port → head_detection/enabled/set
        const val SYSTEM_PRESENCE_SENSOR_GET   = "/api/system/ancillary_sensor/presence/get"
        const val SYSTEM_HEAD_DETECTION_GET    = "/api/system/head_detection/enabled/get"
        const val SYSTEM_PRESENCE_SENSOR_SET   = "/api/system/head_detection/enabled/set"
        const val AUDIO_SOUND_EFFECT_ENABLED_GET = "/api/audio/sound_effect/enabled/get"
        /** Activation / désactivation Concert Hall (chemin dédié Zik 3) */
        const val AUDIO_CONCERT_HALL_ENABLED_SET = "/api/audio/sound_effect/enabled/set"
        /** [VERIFIED NOTE3] Lecture / écriture auto-connexion */
        const val AUTO_CONNECTION      = "/api/system/auto_connection/enabled/get"
        const val AUTO_CONNECTION_SET  = "/api/system/auto_connection/enabled/set"
        /** [VERIFIED NOTE3] Lecture / écriture TTS (annonce vocale) */
        const val TTS_GET              = "/api/software/tts/get"
        const val TTS_SET              = "/api/software/tts/set"
        /** [VERIFIED NOTE3] Écriture timeout auto-extinction (arg=minutes, 0=désactivé) */
        const val SYSTEM_AUTO_OFF_SET  = "/api/system/auto_power_off/set"
    }

    /** Tailles de salle Concert Hall (valeurs complètes vérifiées GitHub + Frida) */
    object RoomSize {
        const val SILENT  = "silent"
        const val LIVING  = "living"
        const val JAZZ    = "jazz"
        const val CONCERT = "concert"
        val values = listOf(SILENT, LIVING, JAZZ, CONCERT)
    }

    /**
     * Modes réduction de bruit capturés par reverse engineering (Frida) sur l'app officielle.
     * Protocole exact : /api/audio/noise_control/set?arg=<id>&value=<val>  (SANS préfixe GET)
     * OFF passif réel : /api/audio/noise_control/enabled/set?arg=false
     */
    enum class NoiseControlMode(val arg: String, val value: Int) {
        OFF("off", 0),          // ANC désactivé    — capturé : arg=off&value=0
        ANC("anc", 2),          // Réduction active — capturé : arg=anc&value=2
        ANC_LOW("anc", 1),      // Réduction faible — capturé : arg=anc&value=1
        STREET("aoc", 2),       // Transparence     — capturé : arg=aoc&value=2
        STREET_LOW("aoc", 1),   // Transparence faible — capturé : arg=aoc&value=1

        // ─── DÉCOUVERTE PLF 3.07 — GenericXO DSP, CONFIG number="6" ──────────────────
        // Le firmware embarque un profil biquad nommé "ANC ON LouReed" (codename interne Parrot).
        // Mapping DSP : CONFIG 0=ANC_OFF_Music, 1=ANC_ON_Music, 4=BYPASS, 5=ANC_OFF_LouReed, 6=ANC_ON_LouReed
        // L'arg API probable serait arg=anc&value=3 ou arg=lou&value=1 — NON VÉRIFIÉ sur matériel.
        // Décommenter uniquement après validation Frida/btsnoop sur casque physique.
        // LOU_REED("anc", 3),   // Profil biquad LouReed (arg=anc&value=3 — à confirmer)
    }

    enum class Command(val path: String) {
        ANC_SET(Api.AUDIO_NOISE_CONTROL_SET),
        BATTERY_GET(Api.BATTERY_GET),
        EQ_SET(Api.AUDIO_PARAM_EQ_VALUE_SET),
        PRESET_ACTIVATE(Api.AUDIO_PRESET_ACTIVATE),
        CONCERT_HALL_SET("/api/audio/sound_effect/angle/set")
    }

    /**
     * Sons système embarqués dans le firmware zik_3_release_3.07.plf (section RIFF/WAV).
     * Ces sons sont joués par le casque lui-même — ils ne sont PAS accessibles depuis
     * l'app Android. Nommage conservé pour cohérence sémantique des retours UI/haptics :
     *   DRIP  = connexion Bluetooth établie
     *   GLASS = confirmation / action réussie
     *   ON    = casque allumé / ANC activé
     *   OFF   = casque éteint / mode passif
     *   OK    = commande acceptée par le firmware
     *   KO    = commande rejetée / erreur protocole
     */
    object SystemSoundName {
        const val DRIP  = "drip"   // sounds/prompt/drip.wav  — BT connecté
        const val GLASS = "glass"  // sounds/prompt/glass.wav — confirmation
        const val ON    = "on"     // sounds/prompt/on.wav    — allumage / ANC ON
        const val OFF   = "off"    // sounds/prompt/off.wav   — extinction / ANC OFF
        const val OK    = "ok"     // sounds/prompt/ok.wav    — commande acceptée
        const val KO    = "ko"     // sounds/prompt/ko.wav    — erreur / refus
    }

    fun buildGetPayload(path: String, args: String? = null): ByteArray {
        // PROTOCOLE PARROT ZIK : payload = chemin seul, SANS préfixe "GET".
        // Logcat S22 Ultra confirmé : les trames avec "GET " provoquent
        // <answer path="GET /api/..." error="true"> — le firmware ne reconnaît pas le mot-clé.
        // Le champ longueur (2 octets big-endian) = payload.size + 3 (header inclus).
        //
        // [VALIDATION Note3] : vérifier si l'app Note 3 envoie :
        //   a) chemin pur       : "/api/audio/noise_control/set?arg=anc&value=2"
        //   b) préfixe "GET "   : "GET /api/audio/..." (format app officielle Parrot v1.71)
        //   c) enveloppe XML    : "<zik version=\"1.0\"><query>/api/...</query></zik>" (rare)
        // Si le Note 3 utilise le format b), activer ici en préfixant par "GET ".
        val full = if (args.isNullOrEmpty()) path else "$path$args"
        return full.toByteArray(Charsets.UTF_8)
    }

    fun wrapPacket(payload: ByteArray, type: Byte = TYPE_MES_DATA): ByteArray {
        // Format Parrot Zik confirmé (captures Frida S22 Ultra + btsnoop) :
        //   [len_hi][len_lo][type=0x80][payload UTF-8]
        //   len = payload.size + 3  (header inclus, big-endian)
        //
        // [VALIDATION Note3] : vérifier dans les trames brutes :
        //   1. L'octet 3 (index 2) est-il toujours 0x80 (TYPE_MES_DATA) ?  → si oui, conforme.
        //   2. L'octet 1-2 correspond-il à payload.size + 3 ?             → si oui, conforme.
        //   3. Y a-t-il un entête supplémentaire avant le 0x80 ?          → si oui, adapter.
        //   Commande Note 3 attendue : $ parse_note3_hci.py <fichier>  (section VALIDATION ci-dessus)
        val totalLen = (payload.size + 3) and 0xFFFF
        val header = ByteArray(3)
        header[0] = ((totalLen shr 8) and 0xFF).toByte()
        header[1] = (totalLen and 0xFF).toByte()
        header[2] = type
        return header + payload
    }

    fun packetForBattery(): ByteArray = wrapPacket(buildGetPayload(Api.BATTERY_GET))

    // ─── Commandes ANC exactes (protocole vérifié par capture Frida) ───────────

    /** ANC activé  → GET /api/audio/noise_control/set?arg=anc&value=2 */
    fun packetForAncOn(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_SET, "?arg=anc&value=2"))

    /** ANC désactivé → GET /api/audio/noise_control/set?arg=off&value=0 */
    fun packetForAncOff(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_SET, "?arg=off&value=0"))

    /** Mode Street (transparence) → GET /api/audio/noise_control/set?arg=aoc&value=2 */
    fun packetForAncStreet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_SET, "?arg=aoc&value=2"))

    /** Construit un paquet pour n'importe quel mode de NoiseControlMode */
    fun packetForNoiseControlMode(mode: NoiseControlMode): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_SET, "?arg=${mode.arg}&value=${mode.value}"))

    /** Ancienne API — garde la compatibilité avec le code existant */
    fun packetForAnc(argAncValue: Int): ByteArray {
        val args = "?arg=anc&value=$argAncValue"
        return wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_SET, args))
    }

    fun packetForNoiseControlEnable(enabled: Boolean): ByteArray {
        val args = "?arg=${enabled}"
        return wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_ENABLED_SET, args))
    }

    /** Lecture état ANC — chemin exact capturé par Frida : /enabled/get (pas /get seul) */
    fun packetForNoiseControlGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_ENABLED_GET))

    /** Lecture état complet du noise control (chemin court) */
    fun packetForNoiseControlStateGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_GET))

    fun packetForEqualizerEnable(enabled: Boolean): ByteArray {
        val args = "?arg=${enabled}"
        return wrapPacket(buildGetPayload(Api.AUDIO_EQUALIZER_ENABLED_SET, args))
    }

    fun packetForThumbEqualizerSet(argString: String): ByteArray {
        val args = "?arg=$argString"
        return wrapPacket(buildGetPayload(Api.AUDIO_THUMB_EQ_VALUE_SET, args))
    }

    fun packetForRoomSize(arg: String): ByteArray = wrapPacket(buildGetPayload(Api.AUDIO_SOUND_EFFECT_ROOM_SIZE_SET, "?arg=$arg"))

    fun packetForAngle(arg: Int): ByteArray = wrapPacket(buildGetPayload(Api.AUDIO_SOUND_EFFECT_ANGLE_SET, "?arg=$arg"))

    /**
     * Valeurs EQ thumb complètes (format arg csv Zik 3).
     * v1..v5 : gains de bande (Double, ex. -1.5, 2.5)
     * x, y   : position joystick (Int, ex. 55, 36)
     */
    data class ThumbEqValues(
        val v1: Double, val v2: Double, val v3: Double,
        val v4: Double, val v5: Double,
        val x: Double, val y: Double
    ) {
        /**
         * Reconstruit l'arg CSV : "v1,v2,v3,v4,v5,r,theta"
         * Format identique Parrot natif : gains en 0.5dB (%.1f), r/theta entiers (%.0f).
         * Exemple : "1.5,0.5,0.0,0.5,1.5,26,240"
         */
        fun toArgString(): String {
            fun fmtG(d: Double): String = "%.1f".format(d)
            fun fmtC(d: Double): String = "%.0f".format(d)
            return "${fmtG(v1)},${fmtG(v2)},${fmtG(v3)},${fmtG(v4)},${fmtG(v5)},${fmtC(x)},${fmtC(y)}"
        }
    }

    // ─── Commandes EQ thumb (Zik 3) ──────────────────────────────────────────
    /**
     * GET /api/audio/thumb_equalizer/value/get
     * Réponse : <thumb_equalizer arg="v1,v2,v3,v4,v5,x,y"/> ou r="XX" theta="YY"
     */
    fun packetForThumbEqGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_THUMB_EQ_VALUE_GET))

    /**
     * SET EQ thumb Zik 3 — format vérifié GitHub + Frida :
     *   GET /api/audio/thumb_equalizer/value/set?arg=v1,v2,v3,v4,v5,x,y
     * Exemple : packetForThumbEqManual(-1.5, 2.5, 5.5, 5.0, 4.0, 55, 36)
     */
    fun packetForThumbEqManual(
        v1: Double, v2: Double, v3: Double, v4: Double, v5: Double,
        x: Double, y: Double
    ): ByteArray = wrapPacket(
        buildGetPayload(
            Api.AUDIO_THUMB_EQ_VALUE_SET,
            "?arg=${ThumbEqValues(v1, v2, v3, v4, v5, x, y).toArgString()}"
        )
    )

    /** Surcharge pratique depuis un ThumbEqValues */
    fun packetForThumbEqManual(eq: ThumbEqValues): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_THUMB_EQ_VALUE_SET, "?arg=${eq.toArgString()}"))

    // ─── Auto Noise Control ───────────────────────────────────────────────────
    /** GET /api/audio/noise_control/auto_nc/set?arg=true|false */
    fun packetForAutoNc(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_NOISE_CONTROL_AUTO_NC_SET, "?arg=$enabled"))
    // ─── Preset ────────────────────────────────────────────────────────────────────
    /**
     * GET /api/audio/preset/set?arg=off
     * Désactive tout preset actif — passe l'EQ en mode plat passif.
     */
    fun packetForPresetOff(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_PRESET_SET, "?arg=off"))

    /**
     * GET /api/audio/preset/set?arg=<name>
     * Active un preset EQ nommé (pops, vocal, crystal, club, punchy, deep).
     * Doit être précédé de packetForEqualizerEnable(true).
     */
    fun packetForPreset(name: String): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_PRESET_SET, "?arg=$name"))
    // ─── Smart Audio Tune ─────────────────────────────────────────────────────
    /** GET /api/audio/smart_audio_tune/set?arg=true|false */
    fun packetForSmartAudioTune(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_SMART_AUDIO_TUNE_SET, "?arg=$enabled"))

    // ─── EQ Paramétrique (ProducerMode) ──────────────────────────────────────
    /**
     * GET /api/audio/param_equalizer/value/set?arg=f1,f2,f3,f4,f5,q1,q2,q3,q4,q5,g1,g2,g3,g4,g5
     * Fréquences fixes (Hz) : 70, 381, 960, 2419, 11000
     * Facteurs Q fixes : 0.3, 0.8, 1.0, 0.8, 0.3
     * Gains (dB, float) : -12.0 .. +12.0
     */
    fun packetForParamEq(gains: List<Float>): ByteArray {
        val g = gains.map { it.coerceIn(-12f, 12f) }
        val arg = "70,381,960,2419,11000,0.3,0.8,1.0,0.8,0.3," +
                  String.format(Locale.US, "%.1f,%.1f,%.1f,%.1f,%.1f", g[0], g[1], g[2], g[3], g[4])
        return wrapPacket(buildGetPayload(Api.AUDIO_PARAM_EQ_VALUE_SET, "?arg=$arg&producer=1"))
    }

    // ─── Concert Hall ─────────────────────────────────────────────────────────
    /** GET /api/audio/concert_hall/enabled/set?arg=true|false */
    fun packetForConcertHallEnabled(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_CONCERT_HALL_ENABLED_SET, "?arg=$enabled"))

    /**
     * Parse le pourcentage de batterie depuis une réponse XML du casque.
     * N'opère QUE sur les trames contenant une balise <battery ; les autres retournent null.
     * Regex robuste : percent="(\d{1,3})"
     */
    fun parseBatteryPercentFromXml(xml: String?): Int? {
        if (xml == null) return null
        if (!xml.contains("/battery/") && !xml.contains("<battery")) return null
        return Regex("""percent="(\d{1,3})"""")
            .find(xml)?.groups?.get(1)?.value?.toIntOrNull()
            ?.takeIf { it in 0..100 }
    }

    /**
     * Parse le mode ANC depuis une réponse XML du casque.
     * Supports attribute type="anc|off|aoc" (SET response) and enabled="true|false" (GET response).
     */
    fun parseAncModeFromXml(xml: String?): NoiseControlMode? {
        if (xml == null) return null
        if (!xml.contains("noise_control")) return null
        // Priorité 1 : attribut type="..." + value (réponse à un SET ou GET complet)
        val typeMatch = Regex("""<noise_control[^>]*\btype="([^"]+)"""")
            .find(xml)?.groups?.get(1)?.value
        val valueMatch = Regex("""<noise_control[^>]*\bvalue="(\d+)"""")
            .find(xml)?.groups?.get(1)?.value?.toIntOrNull()
        if (typeMatch != null) {
            // Chercher la correspondance exacte arg+value si disponible
            if (valueMatch != null) {
                NoiseControlMode.values().firstOrNull { it.arg == typeMatch && it.value == valueMatch }
                    ?.let { return it }
            }
            // Sinon, arg seul
            NoiseControlMode.values().firstOrNull { it.arg == typeMatch }?.let { return it }
        }
        // Priorité 2 : attribut enabled="true|false" (réponse à GET /enabled/get)
        Regex("""<noise_control[^>]*\benabled="(true|false)"""")
            .find(xml)?.groups?.get(1)?.value?.let { v ->
                return if (v == "true") NoiseControlMode.ANC else NoiseControlMode.OFF
            }
        return null
    }

    /**
     * Extrait le niveau de réduction de bruit réel en dB depuis la réponse XML
     * de /api/audio/noise_control/get.
     * Formats attendus : noise_reduction="-25" ou noise_amplification="6"
     * Retourne null si absent.
     */
    fun parseNoiseReductionDbFromXml(xml: String?): Int? {
        if (xml == null) return null
        // Réduction (ANC) : généralement négatif
        Regex("""\bnoise_reduction="(-?\d+)"""")
            .find(xml)?.groups?.get(1)?.value?.toIntOrNull()?.let { return it }
        // Amplification (Street) : généralement positif
        Regex("""\bnoise_amplification="(-?\d+)"""")
            .find(xml)?.groups?.get(1)?.value?.toIntOrNull()?.let { return it }
        return null
    }

    /**
     * Parse les paramètres EQ thumb depuis une réponse XML du casque.
     * Supporte deux formats :
     *   1. arg="v1,v2,v3,v4,v5,x,y"   — réponse SET / GET Zik 3 (format CSV 7 valeurs)
     *   2. r="XX" theta="YY"           — format GET legacy (converti en ThumbEqValues partiel)
     * Retourne null si absente ou malformée.
     */
    fun parseThumbEqFromXml(xml: String?): ThumbEqValues? {
        if (xml == null) return null
        if (!xml.contains("thumb_equalizer")) return null

        // Format 1 : arg="v1,v2,v3,v4,v5,x,y"
        Regex("""<thumb_equalizer[^>]*\barg="([^"]+)"""")
            .find(xml)?.groups?.get(1)?.value?.let { raw ->
                val parts = raw.split(",")
                if (parts.size == 7) {
                    val v1 = parts[0].toDoubleOrNull() ?: return@let
                    val v2 = parts[1].toDoubleOrNull() ?: return@let
                    val v3 = parts[2].toDoubleOrNull() ?: return@let
                    val v4 = parts[3].toDoubleOrNull() ?: return@let
                    val v5 = parts[4].toDoubleOrNull() ?: return@let
                    val x  = parts[5].toDoubleOrNull() ?: return@let
                    val y  = parts[6].toDoubleOrNull() ?: return@let
                    return ThumbEqValues(v1, v2, v3, v4, v5, x, y)
                }
                // Cas Zik 2 : 5 valeurs seulement (pas de x,y) → on complète avec 0
                if (parts.size >= 5) {
                    val v1 = parts[0].toDoubleOrNull() ?: return@let
                    val v2 = parts[1].toDoubleOrNull() ?: return@let
                    val v3 = parts[2].toDoubleOrNull() ?: return@let
                    val v4 = parts[3].toDoubleOrNull() ?: return@let
                    val v5 = parts[4].toDoubleOrNull() ?: return@let
                    return ThumbEqValues(v1, v2, v3, v4, v5, 0.0, 0.0)
                }
            }

        // Format 2 : r="XX" theta="YY" (GET legacy — on les place en x,y, bandes à 0)
        val r     = Regex("""\br="(\d+)"""").find(xml)?.groups?.get(1)?.value?.toDoubleOrNull()
        val theta = Regex("""\btheta="(\d+)"""").find(xml)?.groups?.get(1)?.value?.toDoubleOrNull()
        if (r != null && theta != null) {
            return ThumbEqValues(0.0, 0.0, 0.0, 0.0, 0.0, r, theta)
        }

        return null
    }

    /**
     * Parse la valeur auto_nc depuis une réponse XML.
     * Retourne true/false, ou null si absent.
     */
    fun parseAutoNcFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("auto_nc")) return null
        return Regex("""\bauto_nc="(true|false)"""")
            .find(xml)?.groups?.get(1)?.value?.let { it == "true" }
    }

    // ─── Track Metadata ───────────────────────────────────────────────────────
    fun packetForTrackMetadataGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_TRACK_METADATA_GET))

    fun packetForEqualizerEnabledGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_EQUALIZER_ENABLED_GET))

    fun packetForSoundEffectEnabledGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_SOUND_EFFECT_ENABLED_GET))

    fun packetForPresenceSensorGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.SYSTEM_PRESENCE_SENSOR_GET))

    fun packetForPresenceSensor(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.SYSTEM_PRESENCE_SENSOR_SET, "?arg=$enabled"))

    /** [VERIFIED NOTE3] Écriture timeout auto-extinction (0=désactivé, valeur=minutes). */
    fun packetForAutoPowerOff(minutes: Int): ByteArray =
        wrapPacket(buildGetPayload(Api.SYSTEM_AUTO_OFF_SET, "?arg=$minutes"))

    /** Parse la version firmware depuis une réponse XML.
     *  Format attendu : <version sip6="3.07" pic="32" tts="fr-FR"/>
     *  Retourne la valeur sip6, ou null si absente. */
    fun parseFirmwareVersionFromXml(xml: String?): String? {
        if (xml == null) return null
        if (!xml.contains("sip6") && !xml.contains("version")) return null
        return Regex("""\bsip6="([^"]+)"""").find(xml)?.groups?.get(1)?.value
    }

    /** Parse le timeout d'arrêt automatique depuis une réponse XML.
     *  Format attendu : <auto_power_off value="0|5|10|20|30"/>
     *  Retourne la valeur entière (0 = désactivé), ou null si absente. */
    fun parseAutoPowerOffFromXml(xml: String?): Int? {
        if (xml == null) return null
        if (!xml.contains("auto_power_off")) return null
        return Regex("""<auto_power_off[^>]*\bvalue="(\d+)"""").find(xml)
            ?.groups?.get(1)?.value?.toIntOrNull()
    }

    /** [VERIFIED NOTE3] Lecture état auto-connexion */
    fun packetForAutoConnectionGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.AUTO_CONNECTION))

    /** [VERIFIED NOTE3] Écriture état auto-connexion */
    fun packetForAutoConnection(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.AUTO_CONNECTION_SET, "?arg=$enabled"))

    /** Parse l'état auto-connexion depuis une réponse XML.
     *  Format attendu : <auto_connection enabled="true|false"/> */
    fun parseAutoConnectionFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("auto_connection")) return null
        return Regex("""<auto_connection[^>]*\benabled="(true|false)"""").find(xml)
            ?.groups?.get(1)?.value?.let { it == "true" }
    }

    /** [VERIFIED NOTE3] Lecture état TTS (annonce vocale de l'appelant) */
    fun packetForTtsGet(): ByteArray =
        wrapPacket(buildGetPayload(Api.TTS_GET))

    /** [VERIFIED NOTE3] Écriture état TTS */
    fun packetForTts(enabled: Boolean): ByteArray =
        wrapPacket(buildGetPayload(Api.TTS_SET, "?arg=$enabled"))

    /** Parse l'état TTS depuis une réponse XML.
     *  Format attendu : <tts enabled="true|false"/> */
    fun parseTtsEnabledFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("tts")) return null
        return Regex("""<tts[^>]*\benabled="(true|false)"""").find(xml)
            ?.groups?.get(1)?.value?.let { it == "true" }
    }
    /** Parse le nom Bluetooth convivial depuis une réponse XML.
     *  Format attendu : <bluetooth friendlyname="Parrot ZIK 3"/> */
    fun parseFriendlyNameFromXml(xml: String?): String? {
        if (xml == null) return null
        if (!xml.contains("friendlyname")) return null
        return Regex("""friendlyname="([^"]+)""").find(xml)?.groups?.get(1)?.value
    }

    /** Parse le mode téléphonique ANC depuis une réponse XML.
     *  Format attendu : phone_mode="anc|aoc|street|off" */
    fun parsePhoneModeFromXml(xml: String?): String? {
        if (xml == null) return null
        if (!xml.contains("phone_mode")) return null
        return Regex("""phone_mode="([^"]+)""").find(xml)?.groups?.get(1)?.value
    }

    /** Écriture mode téléphonique ANC (pendant les appels) */
    fun packetForPhoneMode(mode: String): ByteArray =
        wrapPacket(buildGetPayload(Api.AUDIO_ANC_PHONE_MODE_SET, "?arg=$mode"))
    /**
     * Parse les métadonnées de la piste en cours.
     * Format : <metadata playing="true" title="..." artist="..." album="..." genre=""/>
     * Retourne Triple(playing, title, artist) ou null si absent.
     */
    fun parseTrackMetadataFromXml(xml: String?): Triple<Boolean, String, String>? {
        if (xml == null) return null
        if (!xml.contains("metadata")) return null
        val title  = Regex("""\btitle="([^"]*)"""" ).find(xml)?.groups?.get(1)?.value ?: return null
        val artist = Regex("""\bartist="([^"]*)"""" ).find(xml)?.groups?.get(1)?.value ?: ""
        val playing = Regex("""\bplaying="(true|false)"""").find(xml)?.groups?.get(1)?.value == "true"
        return Triple(playing, title, artist)
    }

    /**
     * Parse l'état de charge depuis une réponse XML batterie.
     * Format : <battery state="charging" .../>
     * Retourne true si en charge, false si in_use, null si absent.
     */
    fun parseChargingFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("<battery") && !xml.contains("/battery/")) return null
        return Regex("""\bstate="([^"]+)"""")
            .find(xml)?.groups?.get(1)?.value?.let { it == "charging" }
    }

    /** Parse l'état enabled de l'égaliseur depuis une réponse XML. */
    fun parseEqualizerEnabledFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("equalizer")) return null
        return Regex("""<equalizer[^>]*\benabled="(true|false)"""").find(xml)?.groups?.get(1)?.value?.let { it == "true" }
    }

    /** Parse l'état enabled du sound effect (Concert Hall) depuis une réponse XML. */
    fun parseSoundEffectEnabledFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        if (!xml.contains("sound_effect")) return null
        return Regex("""<sound_effect[^>]*\benabled="(true|false)"""").find(xml)?.groups?.get(1)?.value?.let { it == "true" }
    }

    /** Parse l'état du capteur de détection de port depuis une réponse XML.
     *  Chemins acceptés (ordre priorité) :
     *    1. head_detection (nouveau firmware S22 Ultra, confirmé logcat)
     *    2. ancillary_sensor/presence
     *    3. proximity_sensor / presence_sensor (anciens firmwares)
     */
    fun parsePresenceSensorFromXml(xml: String?): Boolean? {
        if (xml == null) return null
        val relevant = xml.contains("head_detection") ||
                       xml.contains("ancillary_sensor") ||
                       xml.contains("presence") ||
                       xml.contains("proximity_sensor") ||
                       xml.contains("presence_sensor")
        if (!relevant) return null
        // Priorité : balise <head_detection enabled="..." /> ou <presence enabled="..." />
        Regex("""<(?:head_detection|presence)[^>]*\benabled="(true|false)"""").find(xml)
            ?.groups?.get(1)?.value?.let { return it == "true" }
        // Fallback générique
        return Regex("""\benabled="(true|false)"""").find(xml)?.groups?.get(1)?.value?.let { it == "true" }
    }

    fun packetForPresetActivate(id: Int, enable: Int): ByteArray {
        val args = "?id=$id&enable=$enable"
        return wrapPacket(buildGetPayload(Api.AUDIO_PRESET_ACTIVATE, args))
    }

    fun packetForEqSet(eqString: String): ByteArray {
        val args = "?arg=$eqString"
        return wrapPacket(buildGetPayload(Api.AUDIO_PARAM_EQ_VALUE_SET, args))
    }

    data class InMessageData(
        val zzFirst: Int,
        val type: Byte,
        val x: Int,
        val y: Int,
        val zz: Int,
        val data: ByteArray
    ) {
        fun dataAsString(): String = data.toString(Charsets.UTF_8)
    }

    fun parseInMessages(stream: ByteArray): List<InMessageData> {
        val list = mutableListOf<InMessageData>()
        if (stream.isEmpty()) return list
        var data = stream
        while (data.isNotEmpty()) {
            if (data.size < 7) break
            val zzFirst = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            val type = data[2]
            val x = data[3].toInt() and 0xFF
            val y = data[4].toInt() and 0xFF
            val zz = ((data[5].toInt() and 0xFF) shl 8) or (data[6].toInt() and 0xFF)

            val payloadLen = if (data.size >= zzFirst) (zzFirst - 7) else (data.size - 7)
            val payload = if (payloadLen > 0 && data.size >= 7 + payloadLen) {
                data.copyOfRange(7, 7 + payloadLen)
            } else if (payloadLen > 0 && data.size > 7) {
                data.copyOfRange(7, data.size)
            } else ByteArray(0)

            list.add(InMessageData(zzFirst, type, x, y, zz, payload))

            if (data.size > zzFirst) {
                data = data.copyOfRange(zzFirst, data.size)
            } else {
                break
            }
        }
        return list
    }

    fun extractBatteryLevelFromText(text: String?): Int? {
        if (text == null) return null
        val rx1 = Regex("<level>(\\d{1,3})</level>")
        rx1.find(text)?.groups?.get(1)?.value?.let { return it.toIntOrNull() }
        val rx2 = Regex("(\\d{1,3})%")
        rx2.find(text)?.groups?.get(1)?.value?.let { return it.toIntOrNull() }
        val rx3 = Regex("\\b(\\d{1,3})\\b")
        rx3.find(text)?.groups?.get(1)?.value?.let {
            val v = it.toIntOrNull() ?: return null
            if (v in 0..100) return v
        }
        return null
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // MATRICE DE VÉRITÉ — SESSION GALAXY NOTE 3  (trames HCI à confirmer)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * MATRICE DE VÉRITÉ — Source : btsnoop_note3_pure.log (Note 3 SM-N9005, Android 5.0)
     * Appareil : Parrot ZIK 3 — firmware 3.07 — 385 trames — 20:51:50→20:55:49
     * Pollution Samsung : AUCUNE (SDHMS/SoundAlive absent) — [VERIFIED NOTE3]
     *
     * PRÉFIXE GET : l'app officielle envoie "GET /api/..." (hex 47 45 54 20).
     * Notre impl envoie le chemin nu "/api/..." — les deux sont acceptés par le firmware.
     * Format valeur : chemin /api/... sans préfixe (notre format natif).
     */
    val NATIVE_COMMAND_MAP: MutableMap<String, String> = mutableMapOf(
        // ── Handshake Système (#001–#030) ────────────────────────────────────
        "SYSTEM_DEVICE_TYPE_GET"        to "/api/system/device_type/get",            // [VERIFIED NOTE3] #001 → value="5"
        "SYSTEM_COLOR_GET"              to "/api/system/color/get?version=2",        // [VERIFIED NOTE3] #003 → value="9"
        "SOFTWARE_VERSION_GET"          to "/api/software/version/get",              // [VERIFIED NOTE3] #005 → sip6="3.07" pic="32" tts="fr-FR"
        "FEATURES_GET"                  to "/api/features/get",                      // [VERIFIED NOTE3] #007 → set1="131071" set2="0"
        "BLUETOOTH_NAME_GET"            to "/api/bluetooth/friendlyname/get",        // [VERIFIED NOTE3] #009 → "Parrot ZIK 3"
        "BATTERY_GET"                   to "/api/system/battery/get",                // [VERIFIED NOTE3] #011 → state="in_use" percent="90"
        "SYSTEM_TEXTURE_GET"            to "/api/system/texture/get",                // [VERIFIED NOTE3] #013 → value="3"
        "SYSTEM_PI_GET"                 to "/api/system/pi/get",                     // [VERIFIED NOTE3] #023
        "AUTO_CONNECTION_GET"           to "/api/system/auto_connection/enabled/get",// [VERIFIED NOTE3] #041 → enabled="true"
        "AUTO_POWER_OFF_GET"            to "/api/system/auto_power_off/get",         // [VERIFIED NOTE3] #043 → value="0"
        "HEAD_DETECTION_GET"            to "/api/system/head_detection/enabled/get", // [VERIFIED NOTE3] #045 → enabled="true"
        "BT_ADDRESS_GET"                to "/api/system/bt_address/get",             // [VERIFIED NOTE3] #059

        // ── Handshake ANC (#019–#023) ────────────────────────────────────────
        "ANC_ENABLED_GET"               to "/api/audio/noise_control/enabled/get",   // [VERIFIED NOTE3] #019 → enabled="true"
        "ANC_STATE_GET"                 to "/api/audio/noise_control/get",           // [VERIFIED NOTE3] #021 → type="anc" value="2" auto_nc="false"
        "ANC_PHONE_MODE_GET"            to "/api/audio/noise_control/phone_mode/get",// [VERIFIED NOTE3] #039 → phone_mode="aoc"

        // ── ANC SET (trames #077–#133) ───────────────────────────────────────
        "ANC_ENABLED_SET_OFF"           to "/api/audio/noise_control/enabled/set?arg=false",// [VERIFIED NOTE3] #077
        "ANC_ENABLED_SET_ON"            to "/api/audio/noise_control/enabled/set?arg=true", // [VERIFIED NOTE3] #089
        "ANC_ON"                        to "/api/audio/noise_control/set?arg=anc&value=2",  // [VERIFIED NOTE3] #117 + #130
        "ANC_ON_LOW"                    to "/api/audio/noise_control/set?arg=anc&value=1",  // [VERIFIED NOTE3] #113
        "ANC_STREET"                    to "/api/audio/noise_control/set?arg=aoc&value=2",  // [VERIFIED NOTE3] #097 + #119
        "ANC_STREET_LOW"                to "/api/audio/noise_control/set?arg=aoc&value=1",  // [VERIFIED NOTE3] #101–#107
        "ANC_OFF"                       to "/api/audio/noise_control/set?arg=off&value=0",  // [VERIFIED NOTE3] #109 + #125
        "AUTO_NC_SET_ON"                to "/api/audio/noise_control/auto_nc/set?arg=true", // [VERIFIED NOTE3] #121 — NOUVELLE commande
        "AUTO_NC_SET_OFF"               to "/api/audio/noise_control/auto_nc/set?arg=false",// [VERIFIED NOTE3] #134 — NOUVELLE commande

        // ── Handshake Égaliseur (#015, #027) ────────────────────────────────
        "EQ_ENABLED_GET"                to "/api/audio/equalizer/enabled/get",       // [VERIFIED NOTE3] #015 → enabled="true"
        "EQ_THUMB_GET"                  to "/api/audio/thumb_equalizer/value/get",   // [VERIFIED NOTE3] #027 → r="1" theta="0"
        "EQ_THUMB_SET"                  to "/api/audio/thumb_equalizer/value/set",   // [VERIFIED NOTE3] #140–#299 ×80
        "EQ_ENABLED_SET_OFF"            to "/api/audio/equalizer/enabled/set?arg=false",    // [VERIFIED NOTE3] #075
        "EQ_ENABLED_SET_ON"             to "/api/audio/equalizer/enabled/set?arg=true",     // [VERIFIED NOTE3] #085 + #138

        // ── Handshake Audio / Preset (#025–#067) ────────────────────────────
        "SOUND_EFFECT_ENABLED_GET"      to "/api/audio/sound_effect/enabled/get",    // [VERIFIED NOTE3] #017 → enabled="false"
        "SOUND_EFFECT_GET"              to "/api/audio/sound_effect/get",             // [VERIFIED NOTE3] #033 → room_size="jazz"
        "SOUND_EFFECT_MODE_GET"         to "/api/audio/sound_effect/mode/get",        // [VERIFIED NOTE3] #300 + #372 → mode="stereo"
        "SOUND_EFFECT_ENABLED_SET_ON"   to "/api/audio/sound_effect/enabled/set?arg=true",  // [VERIFIED NOTE3] #081 + #302
        "SOUND_EFFECT_ENABLED_SET_OFF"  to "/api/audio/sound_effect/enabled/set?arg=false", // [VERIFIED NOTE3] #083 + #380
        "SOUND_EFFECT_ROOM_SIZE_CONCERT" to "/api/audio/sound_effect/room_size/set?arg=concert",// [VERIFIED NOTE3] #304 — NOUVELLE
        "SOUND_EFFECT_ROOM_SIZE_JAZZ"   to "/api/audio/sound_effect/room_size/set?arg=jazz",   // [VERIFIED NOTE3] #326 — NOUVELLE
        "SOUND_EFFECT_ROOM_SIZE_LIVING" to "/api/audio/sound_effect/room_size/set?arg=living", // [VERIFIED NOTE3] #344 — NOUVELLE
        "SOUND_EFFECT_ROOM_SIZE_SILENT" to "/api/audio/sound_effect/room_size/set?arg=silent", // [VERIFIED NOTE3] #360 — NOUVELLE
        "SOUND_EFFECT_ANGLE_30"         to "/api/audio/sound_effect/angle/set?arg=30",  // [VERIFIED NOTE3] #318 — NOUVELLE
        "SOUND_EFFECT_ANGLE_60"         to "/api/audio/sound_effect/angle/set?arg=60",  // [VERIFIED NOTE3] #308 — NOUVELLE
        "SOUND_EFFECT_ANGLE_90"         to "/api/audio/sound_effect/angle/set?arg=90",  // [VERIFIED NOTE3] #314 — NOUVELLE
        "SOUND_EFFECT_ANGLE_120"        to "/api/audio/sound_effect/angle/set?arg=120", // [VERIFIED NOTE3] #316 — NOUVELLE
        "SOUND_EFFECT_ANGLE_150"        to "/api/audio/sound_effect/angle/set?arg=150", // [VERIFIED NOTE3] #318 — NOUVELLE
        "SOUND_EFFECT_ANGLE_180"        to "/api/audio/sound_effect/angle/set?arg=180", // [VERIFIED NOTE3] #324 — NOUVELLE
        "PRESET_CURRENT_GET"            to "/api/audio/preset/current/get",          // [VERIFIED NOTE3] #025 → id="0"
        "PRESET_BYPASS_GET"             to "/api/audio/preset/bypass/get",           // [VERIFIED NOTE3] #049 → bypass="false"
        "PRESET_CANCEL_PRODUCER"        to "/api/audio/preset/cancel_producer",      // [VERIFIED NOTE3] #057
        "PRESET_CLEAR_ALL"              to "/api/audio/preset/clear_all",            // [VERIFIED NOTE3] #065 → counter="0"
        "AUDIO_SOURCE_GET"              to "/api/audio/source/get",                  // [VERIFIED NOTE3] #029 → type="a2dp"
        "TRACK_METADATA_GET"            to "/api/audio/track/metadata/get",          // [VERIFIED NOTE3] #031 + NOTIFY
        "AUDIO_DELAY_GET"               to "/api/audio/delay/get",                   // [VERIFIED NOTE3] #055 → value="40"

        // ── Misc (#035–#053) ─────────────────────────────────────────────────
        "FLIGHT_MODE_GET"               to "/api/flight_mode/get",                   // [VERIFIED NOTE3] #035 → enabled="false"
        "SMART_AUDIO_TUNE_GET"          to "/api/audio/smart_audio_tune/get",        // [VERIFIED NOTE3] #037 → enabled="false"
        "APPLI_VERSION_SET"             to "/api/appli_version/set?arg=1.71",        // [VERIFIED NOTE3] #047 → <answer/>
        "TTS_GET"                       to "/api/software/tts/get",                  // [VERIFIED NOTE3] #051 → enabled="true"

        // ── Compte (#061–#063) ───────────────────────────────────────────────
        "ACCOUNT_USERNAME_GET"          to "/api/account/username/get",              // [VERIFIED NOTE3] #061
        // "ACCOUNT_USERNAME_SET"       to "/api/account/username/set?arg=",         // [OBSOLETE] #063 → error="true" if arg empty
    )

    /**
     * Chemins XML confirmés — Source : btsnoop_note3_pure.log (Note 3 SM-N9005, fw 3.07)
     * [VERIFIED NOTE3] = chemin capturé et vérifié dans le BTSnoop Note 3 pur.
     */
    object XmlPath {
        // ── ANC ────────────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Activation niveau ANC / mode (type + value) */
        const val ANC_SET              = "/api/audio/noise_control/set"
        /** [VERIFIED NOTE3] Lecture état activé/désactivé ANC */
        const val ANC_ENABLED_GET      = "/api/audio/noise_control/enabled/get"
        /** [VERIFIED NOTE3] Écriture état activé/désactivé ANC */
        const val ANC_ENABLED_SET      = "/api/audio/noise_control/enabled/set"
        /** [VERIFIED NOTE3] Lecture état complet ANC (type, value, auto_nc) */
        const val ANC_STATE_GET        = "/api/audio/noise_control/get"
        /** [VERIFIED NOTE3] Lecture mode téléphonique ANC */
        const val ANC_PHONE_MODE_GET   = "/api/audio/noise_control/phone_mode/get"
        /** [VERIFIED NOTE3] Écriture mode auto-ANC (true/false) — NOUVEAU */
        const val AUTO_NC_SET          = "/api/audio/noise_control/auto_nc/set"

        // ── Égaliseur ──────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Lecture valeur joystick EQ thumb (r, theta) */
        const val EQ_THUMB_GET         = "/api/audio/thumb_equalizer/value/get"
        /** [VERIFIED NOTE3] Écriture valeur joystick EQ thumb (v1..v5, r, theta) */
        const val EQ_THUMB_SET         = "/api/audio/thumb_equalizer/value/set"
        /** [VERIFIED NOTE3] Lecture état activé/désactivé EQ */
        const val EQ_ENABLED_GET       = "/api/audio/equalizer/enabled/get"
        /** [VERIFIED NOTE3] Écriture état activé/désactivé EQ */
        const val EQ_ENABLED_SET       = "/api/audio/equalizer/enabled/set"

        // ── Batterie ───────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Lecture niveau et état de charge batterie */
        const val BATTERY_GET          = "/api/system/battery/get"

        // ── Système ────────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Lecture type de produit (value="5" pour Zik3) */
        const val SYSTEM_DEVICE_GET    = "/api/system/device_type/get"
        /** [VERIFIED NOTE3] Lecture nom Bluetooth convivial */
        const val SYSTEM_NAME_GET      = "/api/bluetooth/friendlyname/get"
        /** [VERIFIED NOTE3] Lecture version firmware (sip6, pic, tts) */
        const val SYSTEM_VERSION_GET   = "/api/software/version/get"
        /** [VERIFIED NOTE3] Lecture timeout auto-extinction */
        const val SYSTEM_AUTO_OFF      = "/api/system/auto_power_off/get"
        /** [VERIFIED NOTE3] Écriture timeout auto-extinction (arg=minutes, 0=désactivé) */
        const val SYSTEM_AUTO_OFF_SET  = "/api/system/auto_power_off/set"
        /** [VERIFIED NOTE3] Lecture numéro de série */
        const val SYSTEM_PI_GET        = "/api/system/pi/get"
        /** [VERIFIED NOTE3] Lecture adresse MAC Bluetooth */
        const val SYSTEM_BT_ADDRESS    = "/api/system/bt_address/get"
        /** [VERIFIED NOTE3] Lecture couleur du casque */
        const val SYSTEM_COLOR_GET     = "/api/system/color/get"
        /** [VERIFIED NOTE3] Lecture texture du casque */
        const val SYSTEM_TEXTURE_GET   = "/api/system/texture/get"
        /** [VERIFIED NOTE3] Lecture auto-connexion activée */
        const val AUTO_CONNECTION      = "/api/system/auto_connection/enabled/get"
        /** [VERIFIED NOTE3] Écriture auto-connexion */
        const val AUTO_CONNECTION_SET  = "/api/system/auto_connection/enabled/set"
        /** [VERIFIED NOTE3] Lecture head detection (pause auto) */
        const val HEAD_DETECTION       = "/api/system/head_detection/enabled/get"

        // ── Audio ──────────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Lecture source audio (a2dp / hfp) */
        const val AUDIO_SOURCE         = "/api/audio/source/get"
        /** [VERIFIED NOTE3] Lecture métadonnées piste en cours */
        const val TRACK_METADATA       = "/api/audio/track/metadata/get"
        /** [VERIFIED NOTE3] Lecture délai audio */
        const val AUDIO_DELAY          = "/api/audio/delay/get"
        /** [VERIFIED NOTE3] Lecture mode concert/sound effect complet */
        const val SOUND_EFFECT_GET     = "/api/audio/sound_effect/get"
        /** [VERIFIED NOTE3] Lecture état activé/désactivé sound effect */
        const val SOUND_EFFECT_EN      = "/api/audio/sound_effect/enabled/get"
        /** [VERIFIED NOTE3] Écriture état activé/désactivé sound effect */
        const val SOUND_EFFECT_EN_SET  = "/api/audio/sound_effect/enabled/set"
        /** [VERIFIED NOTE3] Lecture mode stéréo/son spatialisé */
        const val SOUND_EFFECT_MODE    = "/api/audio/sound_effect/mode/get"
        /** [VERIFIED NOTE3] Écriture taille de salle concert (concert/jazz/living/silent) — NOUVEAU */
        const val SOUND_EFFECT_ROOM    = "/api/audio/sound_effect/room_size/set"
        /** [VERIFIED NOTE3] Écriture angle de spatialisation (30/60/90/120/150/180) — NOUVEAU */
        const val SOUND_EFFECT_ANGLE   = "/api/audio/sound_effect/angle/set"
        /** [VERIFIED NOTE3] Lecture Smart Audio Tune */
        const val SMART_AUDIO_TUNE     = "/api/audio/smart_audio_tune/get"
        /** [VERIFIED NOTE3] Lecture preset actif */
        const val PRESET_CURRENT       = "/api/audio/preset/current/get"
        /** [VERIFIED NOTE3] Lecture bypass preset */
        const val PRESET_BYPASS        = "/api/audio/preset/bypass/get"
        /** [VERIFIED NOTE3] Annulation producteur de preset */
        const val PRESET_CANCEL        = "/api/audio/preset/cancel_producer"
        /** [VERIFIED NOTE3] Effacement de tous les presets */
        const val PRESET_CLEAR_ALL     = "/api/audio/preset/clear_all"

        // ── Misc ───────────────────────────────────────────────────────────────
        /** [VERIFIED NOTE3] Lecture état TTS */
        const val TTS_GET              = "/api/software/tts/get"
        /** [VERIFIED NOTE3] Écriture état TTS (annonce vocale) */
        const val TTS_SET              = "/api/software/tts/set"
        /** [VERIFIED NOTE3] Déclaration version app au firmware */
        const val APPLI_VERSION_SET    = "/api/appli_version/set"
        /** [VERIFIED NOTE3] Capacités déclarées du firmware */
        const val FEATURES_GET         = "/api/features/get"
        /** [VERIFIED NOTE3] Lecture nom de compte Parrot (email) */
        const val ACCOUNT_USERNAME     = "/api/account/username/get"
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  ZikTruthTable — issu de zik_truth_table.json
//  Sources : BTSnoop session3 (app officielle v1.71) + Frida ANC log
//            + PLF v3.07 GenericXO XML @ 0x00590248
//
//  FIRMWARE GenericXO CONFIG mapping (10 BIQUADs, config identique) :
//    CONFIG #0 → ANC OFF Music   (type=off, value=0)
//    CONFIG #1 → ANC ON Music    (type=anc, value=2)
//    CONFIG #2 → ANC OFF Phone   (interne – appel téléphonique)
//    CONFIG #3 → ANC ON Phone    (interne – appel téléphonique)
//    CONFIG #4 → BYPASS          (type=aoc, bypass path)
//    CONFIG #5 → ANC OFF LouReed (interne – preset LouReed EQ)
//    CONFIG #6 → ANC ON LouReed  (type=anc, value=3 — non exposé)
// ═══════════════════════════════════════════════════════════════════════
object ZikTruthTable {

    // ── Entrées ANC valides (type + value tels que renvoyés par le firmware) ──
    data class AncEntry(
        val type: String,
        val value: Int,
        val label: String,
        val firmwareRef: String,
        val hexConfirmed: Boolean
    )

    val VALID_ANC: List<AncEntry> = listOf(
        AncEntry("anc", 2, "ANC_MAX",    "GenericXO_CONFIG1_ANC_ON_Music",        hexConfirmed = true),
        AncEntry("anc", 1, "ANC_LOW",    "GenericXO_CONFIG1_ANC_ON_Music",        hexConfirmed = false),
        AncEntry("aoc", 2, "STREET_MAX", "GenericXO_CONFIG4_BYPASS_AOC",          hexConfirmed = true),
        AncEntry("aoc", 1, "STREET_LOW", "GenericXO_CONFIG4_BYPASS_AOC",          hexConfirmed = false),
        AncEntry("off", 0, "OFF",        "GenericXO_CONFIG0_ANC_OFF_Music",       hexConfirmed = true)
    )

    // ── Ranges EQ validés en BTSnoop (session3, 88 SET observés) ──
    const val EQ_V_MIN  = -6.0   // clamp applicatif (firmware observe -3.5..+7.5)
    const val EQ_V_MAX  =  6.0
    const val EQ_R_MIN  =  0
    const val EQ_R_MAX  = 99
    const val EQ_THETA_MIN = 0
    const val EQ_THETA_MAX = 359

    // ─────────────────────────────────────────────────────────────────
    //  API publique
    // ─────────────────────────────────────────────────────────────────

    /**
     * Renvoie true si la réponse firmware (type + value) est dans la table.
     * Utilisé dans readLoop() avant d'émettre un nouvel état ANC.
     */
    fun isValidAncResponse(type: String, value: String): Boolean {
        val v = value.toIntOrNull() ?: return false
        return VALID_ANC.any { it.type == type && it.value == v }
    }

    /**
     * Renvoie le label lisible ("ANC_MAX", "OFF"…) ou null si hors table.
     */
    fun ancLabelFor(type: String, value: String): String? {
        val v = value.toIntOrNull() ?: return null
        return VALID_ANC.firstOrNull { it.type == type && it.value == v }?.label
    }

    /**
     * Renvoie true si la réponse est une entrée confirmée par trace hex
     * (BTSnoop / Frida). Permet de logguer FIRMWARE_UNCONFIRMED sans bloquer.
     */
    fun isHexConfirmed(type: String, value: String): Boolean {
        val v = value.toIntOrNull() ?: return false
        return VALID_ANC.firstOrNull { it.type == type && it.value == v }?.hexConfirmed == true
    }

    /**
     * Renvoie true si les coordonnées EQ (r, theta) sont dans les plages
     * observées en BTSnoop.
     */
    fun isValidEqCoord(r: Double, theta: Double): Boolean =
        r.toInt() in EQ_R_MIN..EQ_R_MAX && theta.toInt() in EQ_THETA_MIN..EQ_THETA_MAX
}

