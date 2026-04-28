package com.rmdaye.ziker

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Build
import android.util.Log
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ZikViewModel(private val context: Context) : ViewModel() {
    data class UserEqPreset(
        val id: Long,
        val name: String,
        val eq: ZikProtocol.ThumbEqValues,
        val isParametric: Boolean = false,
        val gains: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
        val concertHallEnabled: Boolean = false,
        val concertHallRoom: String = "concert",
        val concertHallAngle: Int = 90
    )

    private var service: ZikBluetoothService? = null

    // ── Audio Visualizer (FFT temps réel pour couronne EQ dynamique) ──────────
    private val audioViz = AudioVisualizerManager()
    val fftMagnitudes: StateFlow<FloatArray> = audioViz.fftMagnitudes
    val vizActive: StateFlow<Boolean> = audioViz.isActive

    /** Démarre la capture FFT pour alimenter la couronne EQ vivante */
    fun startVisualizer() = audioViz.start()
    /** Stoppe la capture FFT (quand la page EQ n'est plus visible) */
    fun stopVisualizer()  = audioViz.stop()

    // ── StateFlows synchronisés depuis le service ─────────────────────────────
    private val _battery      = MutableStateFlow<Int?>(null)
    val battery: StateFlow<Int?> = _battery

    private val _isCharging   = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging

    private val _ancEnabled   = MutableStateFlow<Boolean?>(null)
    val ancEnabled: StateFlow<Boolean?> = _ancEnabled

    private val _ancMode      = MutableStateFlow<ZikProtocol.NoiseControlMode?>(null)
    val ancMode: StateFlow<ZikProtocol.NoiseControlMode?> = _ancMode

    private val _roomSize     = MutableStateFlow<String?>(null)
    val roomSize: StateFlow<String?> = _roomSize

    private val _thumbEq      = MutableStateFlow<ZikProtocol.ThumbEqValues?>(null)
    val thumbEq: StateFlow<ZikProtocol.ThumbEqValues?> = _thumbEq

    /** Bandes EQ persistées localement — survivent aux changements de page et au polling */
    private val _eqBands = MutableStateFlow<List<Double>>(List(5) { 0.0 })
    val eqBands: StateFlow<List<Double>> = _eqBands

    /** Nom du preset EQ actif (ex: "deep", null = OFF/aucun). Persisté dans le ViewModel. */
    private val _activePreset = MutableStateFlow<String?>(null)
    val activePreset: StateFlow<String?> = _activePreset

    /** Intensité EQ active (1=Léger, 2=Modéré, 3=Fort, 4=Maximum). */
    private val _eqLevel = MutableStateFlow(4)
    val eqLevel: StateFlow<Int> = _eqLevel

    /** Coeff thumb_equalizer à 100% pour chaque preset — multiplié par level/4.0. */
    private val EQ_LEVEL_BASE: Map<String, List<Double>> = mapOf(
        "pops"    to listOf( 0.0,  6.0,  4.5,  3.5,  1.0),
        "vocal"   to listOf(-3.0,  3.0,  5.0,  4.0,  2.0),
        "crystal" to listOf( 0.0,  0.0,  2.0,  3.5,  7.5),
        "club"    to listOf( 6.0, -4.0,  3.5, -1.0,  6.5),
        "punchy"  to listOf( 6.0,  1.5,  0.5,  1.0,  4.0),
        "deep"    to listOf( 7.5,  3.0,  2.0,  0.0,  0.0),
    )

    // ── Limites EQ (calibré sur capture BTSnoop Note 3, 2 mars 2026) ──────────
    // Le firmware natif envoie des gains jusqu'à ±7.5 dB (ex: CRISTAL v5=7.5, DEEP v1=7.5)
    // Plage élargie à ±8.0 dB pour couvrir toutes les valeurs capturées.
    private val EQ_BAND_MAX_DB = 8.0
    private val EQ_BAND_MIN_DB = -8.0
    /** Borne le gain d'une bande EQ dans la plage sûr [EQ_BAND_MIN_DB, EQ_BAND_MAX_DB]. */
    private fun clampEqGain(v: Double): Double = v.coerceIn(EQ_BAND_MIN_DB, EQ_BAND_MAX_DB)

    // ── Cohérence batterie (AnalogMonitor firmware) ─────────────────────────────
    // Le firmware possède un thread AnalogMonitor indépendant du fuel-gauge.
    // Quand les deux divergent (tension basse / percent élevé), la LED rouge s'allume
    // alors que l'API renvoie >40%. Diagnostic applicatif : chute percent > 15 points
    // en un seul cycle de polling (10 s) = fuel-gauge dérivé.
    /** Alerte calibration batterie afférable depuis les Réglages. null = aucune alerte. */
    private val _batteryCalibWarning = MutableStateFlow<String?>(null)
    val batteryCalibWarning: StateFlow<String?> = _batteryCalibWarning
    private var lastBatteryPct: Int? = null
    private var lastBatteryTime: Long = 0L

    /**
     * Vérifie la cohérence entre deux lectures consécutives du pourcentage batterie.
     * Source firmware : Soft/Sources/Supervisor/AnalogMonitor.c — SetBattVoltageThresholds().
     * Une chute ≥ 15 points en ≤ 15 s révèle une dérive du fuel-gauge embarqué.
     */
    fun checkBatteryCoherence(newPct: Int) {
        val prev    = lastBatteryPct
        val now     = System.currentTimeMillis()
        if (prev != null) {
            val drop    = prev - newPct
            val elapsed = now - lastBatteryTime
            when {
                drop >= 15 && elapsed < 15_000L -> {
                    // Chute brutale : dérive fuel-gauge confirmée
                    _batteryCalibWarning.value =
                        "Battery not calibrated — Charge cycle recommended"
                    Log.w("ZikBT",
                        "BATT_COHERENCE: $prev% → $newPct% en ${elapsed}ms —" +
                        " fuel-gauge drift (AnalogMonitor firmware)")
                }
                newPct >= 95 && (_batteryCalibWarning.value != null) -> {
                    // Charge complète réelle : le fuel-gauge s'est recalibré
                    _batteryCalibWarning.value = null
                    Log.i("ZikBT", "BATT_COHERENCE: full charge — alert reset")
                }
            }
        }
        lastBatteryPct  = newPct
        lastBatteryTime = now
    }

    private val _autoNc       = MutableStateFlow<Boolean?>(null)
    val autoNc: StateFlow<Boolean?> = _autoNc

    /** Dernier statut reçu du firmware pour noise_control/enabled/get (polling 2s) */
    private val _ncFirmwareStatus = MutableStateFlow<String?>(null)
    val ncFirmwareStatus: StateFlow<String?> = _ncFirmwareStatus

    private val _trackTitle   = MutableStateFlow<String?>(null)
    val trackTitle: StateFlow<String?> = _trackTitle

    private val _trackArtist  = MutableStateFlow<String?>(null)
    val trackArtist: StateFlow<String?> = _trackArtist

    private val _connected    = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** Message de recherche BT (vide = silencieux, non-vide = avertissement UI) */
    private val _searchStatus = MutableStateFlow("")
    val searchStatus: StateFlow<String> = _searchStatus

    // ── StateFlows locaux (pas de GET endpoint) ───────────────────────────────
    private val _concertAngle    = MutableStateFlow(90)
    val concertAngle: StateFlow<Int> = _concertAngle

    /** Niveau de réduction de bruit réel lu depuis le casque (dB, ex. -25, 0, +6) */
    private val _noiseReductionDb = MutableStateFlow<Int?>(null)
    val noiseReductionDb: StateFlow<Int?> = _noiseReductionDb

    /** Appareils découverts par le scan actif Bluetooth */
    private val _scannedDevices = MutableStateFlow<List<ZikDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<ZikDeviceInfo>> = _scannedDevices

    /** Adresse MAC actuellement ciblée par la tentative de connexion. */
    private val _targetDeviceAddress = MutableStateFlow<String?>(null)
    val targetDeviceAddress: StateFlow<String?> = _targetDeviceAddress

    /** True pendant un scan actif */
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /** Égaliseur actif */
    private val _eqEnabled = MutableStateFlow<Boolean?>(null)
    val eqEnabled: StateFlow<Boolean?> = _eqEnabled

    /** Concert Hall actif */
    private val _concertEnabled = MutableStateFlow<Boolean?>(null)
    val concertEnabled: StateFlow<Boolean?> = _concertEnabled

    /** Capteur de présence / Pause automatique */
    private val _presenceSensor = MutableStateFlow<Boolean?>(null)
    val presenceSensor: StateFlow<Boolean?> = _presenceSensor

    /** État porté/retiré du casque via notify firmware (true=porté, false=retiré, null=inconnu) */
    private val _presenceWorn = MutableStateFlow<Boolean?>(null)
    val presenceWorn: StateFlow<Boolean?> = _presenceWorn

    /** Auto-connexion Bluetooth — [VERIFIED NOTE3] /api/system/auto_connection/enabled/ */
    private val _autoConnection = MutableStateFlow<Boolean?>(null)
    val autoConnection: StateFlow<Boolean?> = _autoConnection

    /** Annonce vocale de l'appelant (TTS) — [VERIFIED NOTE3] /api/software/tts/ */
    private val _ttsEnabled = MutableStateFlow<Boolean?>(null)
    val ttsEnabled: StateFlow<Boolean?> = _ttsEnabled


    // ── États transitoires (Zero-Phantoms Policy) ───────────────────────────────────────────────────
    // true → commande envoyée, UI figée jusqu'à la réponse firmware
    private val _ancPending      = MutableStateFlow(false)
    val ancPending: StateFlow<Boolean> = _ancPending

    // ── Mutex : sérialise les commandes NC pour empêcher l'interleaving de paquets ────────────────
    private val ncMutex = Mutex()
    private val _presencePending = MutableStateFlow(false)
    val presencePending: StateFlow<Boolean> = _presencePending

    /** Adresse MAC du casque connecté */
    private val _macAddress = MutableStateFlow(ZikProtocol.DEVICE_MAC)
    val macAddress: StateFlow<String> = _macAddress

    /** Version firmware lue dynamiquement depuis /api/software/version/get (sip6) */
    private val _firmwareVersion = MutableStateFlow(ZikProtocol.DEVICE_FIRMWARE)
    val firmwareVersion: StateFlow<String> = _firmwareVersion

    /** Timeout d'arrêt automatique en minutes (0=désactivé, null=non lu) */
    private val _autoPowerOff = MutableStateFlow<Int?>(null)
    val autoPowerOff: StateFlow<Int?> = _autoPowerOff

    /** 3 dernières trames XML reçues (console de diagnostic) */
    private val _lastXmlFrames = MutableStateFlow<List<String>>(emptyList())
    val lastXmlFrames: StateFlow<List<String>> = _lastXmlFrames

    /** Buffer circulaire de logs live pour la page debug */
    private val _liveLogs = MutableStateFlow<List<String>>(emptyList())
    val liveLogs: StateFlow<List<String>> = _liveLogs

    // Demo mode flag (UI only) — used to simulate a connected headset for screenshots/tests
    private val _demoMode = MutableStateFlow(false)
    val demoMode: StateFlow<Boolean> = _demoMode
    private val _realConnected = MutableStateFlow(false)
    private val _forceStartupPicker = MutableStateFlow(false)
    val forceStartupPicker: StateFlow<Boolean> = _forceStartupPicker
    private val _startupFlowToken = MutableStateFlow(0)
    val startupFlowToken: StateFlow<Int> = _startupFlowToken

    private data class DemoSnapshot(
        val battery: Int?,
        val noiseReductionDb: Int?,
        val deviceName: String,
        val ancEnabled: Boolean?,
        val ancMode: ZikProtocol.NoiseControlMode?,
        val autoNc: Boolean?,
        val eqEnabled: Boolean?,
        val concertEnabled: Boolean?,
        val roomSize: String?,
        val concertAngle: Int,
        val thumbEq: ZikProtocol.ThumbEqValues?,
        val eqBands: List<Double>,
        val trackTitle: String?,
        val trackArtist: String?,
        val presenceSensor: Boolean?,
        val presenceWorn: Boolean?,
        val autoConnection: Boolean?,
        val ttsEnabled: Boolean?,
        val autoPowerOff: Int?,
        val phoneMode: String,
        val searchStatus: String,
        val activePreset: String?,
        val activeParamPresetId: Long?,
        val rememberedProfileType: String?,
        val rememberedProfileBuiltin: String?,
        val rememberedProfileParamId: Long?,
        val rememberedProfileManualArg: String?
    )

    private var demoSnapshot: DemoSnapshot? = null
    private var demoPrefsSnapshot: Map<String, Any?>? = null

    /** Nom Bluetooth convivial du casque (friendlyname) */
    private val _deviceName = MutableStateFlow("Parrot ZIK 3")
    val deviceName: StateFlow<String> = _deviceName

    /** Mode ANC pendant les appels (anc|aoc|street|off) */
    private val _phoneMode = MutableStateFlow("anc")
    val phoneMode: StateFlow<String> = _phoneMode

    // ── Thème persistant (SharedPreferences) ─────────────────────────────────
    private val prefs = context.getSharedPreferences("zik_prefs", android.content.Context.MODE_PRIVATE)
    private val _skinKey = MutableStateFlow(prefs.getString("skin_key", "BLACK") ?: "BLACK")
    val skinKey: StateFlow<String> = _skinKey
    private val USER_EQ_PRESETS_KEY = "user_eq_presets_v1"
    private val ACTIVE_AUDIO_PROFILE_TYPE_KEY = "active_audio_profile_type_v1"
    private val ACTIVE_AUDIO_PROFILE_BUILTIN_KEY = "active_audio_profile_builtin_v1"
    private val ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY = "active_audio_profile_param_id_v1"
    private val ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY = "active_audio_profile_manual_arg_v1"
    private val EQ_LEVEL_KEY = "eq_level_v1"
    private val _userEqPresets = MutableStateFlow(loadUserEqPresets())
    val userEqPresets: StateFlow<List<UserEqPreset>> = _userEqPresets
    private var activeParamPresetId: Long? = prefs
        .getLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, -1L)
        .takeIf { it >= 0L }

    init {
        _eqLevel.value = prefs.getInt(EQ_LEVEL_KEY, 4).coerceIn(1, 4)
        restoreRememberedPresetLabel()
    }

    private fun restoreRememberedPresetLabel() {
        when (prefs.getString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, null)) {
            "builtin" -> {
                _activePreset.value = prefs.getString(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY, null)
                activeParamPresetId = null
            }
            "parametric" -> {
                val id = prefs.getLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, -1L).takeIf { it >= 0L }
                activeParamPresetId = id
                _activePreset.value = id?.let { wantedId ->
                    _userEqPresets.value.firstOrNull { it.id == wantedId }?.name
                }
            }
            "manual" -> {
                activeParamPresetId = null
                _activePreset.value = "manual"
            }
            else -> {
                activeParamPresetId = null
                _activePreset.value = null
            }
        }
    }

    private fun rememberBuiltInPreset(presetName: String) {
        activeParamPresetId = null
        prefs.edit()
            .putString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, "builtin")
            .putString(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY, presetName)
            .remove(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY)
            .remove(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY)
            .apply()
    }

    private fun rememberParamPreset(presetId: Long, presetName: String) {
        activeParamPresetId = presetId
        _activePreset.value = presetName
        prefs.edit()
            .putString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, "parametric")
            .remove(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY)
            .putLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, presetId)
            .remove(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY)
            .apply()
    }

    private fun rememberManualThumbEq(eq: ZikProtocol.ThumbEqValues) {
        activeParamPresetId = null
        _activePreset.value = "manual"
        prefs.edit()
            .putString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, "manual")
            .remove(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY)
            .remove(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY)
            .putString(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY, eq.toArgString())
            .apply()
    }

    private fun parseManualThumbEqArg(raw: String?): ZikProtocol.ThumbEqValues? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(",")
        if (parts.size != 7) return null
        val v1 = parts[0].toDoubleOrNull() ?: return null
        val v2 = parts[1].toDoubleOrNull() ?: return null
        val v3 = parts[2].toDoubleOrNull() ?: return null
        val v4 = parts[3].toDoubleOrNull() ?: return null
        val v5 = parts[4].toDoubleOrNull() ?: return null
        val x = parts[5].toDoubleOrNull() ?: return null
        val y = parts[6].toDoubleOrNull() ?: return null
        return ZikProtocol.ThumbEqValues(v1, v2, v3, v4, v5, x, y)
    }

    private fun clearRememberedAudioProfile(reason: String) {
        if (_activePreset.value == null && activeParamPresetId == null &&
            prefs.getString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, null) == null) return
        activeParamPresetId = null
        _activePreset.value = null
        prefs.edit()
            .remove(ACTIVE_AUDIO_PROFILE_TYPE_KEY)
            .remove(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY)
            .remove(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY)
            .remove(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY)
            .apply()
    }

    private fun restoreRememberedAudioProfile() {
        when (prefs.getString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, null)) {
            "builtin" -> {
                val presetName = prefs.getString(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY, null) ?: return
                Log.i("ZikControl", "[EQ] restoring built-in preset: $presetName")
                setEqPreset(presetName, rememberSelection = false)
            }
            "parametric" -> {
                val presetId = prefs.getLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, -1L)
                    .takeIf { it >= 0L } ?: return
                Log.i("ZikControl", "[EQ] restoring parametric preset id=$presetId")
                if (!applyParamPreset(presetId, rememberSelection = false)) {
                    clearRememberedAudioProfile("parametric preset not found during restore")
                }
            }
            "manual" -> {
                val eq = parseManualThumbEqArg(prefs.getString(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY, null))
                    ?: run {
                        clearRememberedAudioProfile("profil manuel invalide au restore")
                        return
                    }
                Log.i("ZikControl", "[EQ] restauration Thumb EQ manuel")
                setThumbEq(eq.v1, eq.v2, eq.v3, eq.v4, eq.v5, eq.x, eq.y)
            }
        }
    }

    fun setSkin(key: String) {
        _skinKey.value = key
        prefs.edit().putString("skin_key", key).apply()
    }

    private fun loadUserEqPresets(): List<UserEqPreset> {
        val raw = prefs.getString(USER_EQ_PRESETS_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    val name = o.optString("name", "").trim()
                    if (id <= 0L || name.isBlank()) continue
                    val isParam = o.optBoolean("isParametric", false)
                    val gainArr = o.optJSONArray("gains")
                    val gains = if (gainArr != null && gainArr.length() == 5)
                        buildList { for (gi in 0 until 5) add(gainArr.getDouble(gi).toFloat()) }
                    else listOf(0f, 0f, 0f, 0f, 0f)
                    add(
                        UserEqPreset(
                            id = id,
                            name = name,
                            eq = ZikProtocol.ThumbEqValues(
                                v1 = o.optDouble("v1", 0.0),
                                v2 = o.optDouble("v2", 0.0),
                                v3 = o.optDouble("v3", 0.0),
                                v4 = o.optDouble("v4", 0.0),
                                v5 = o.optDouble("v5", 0.0),
                                x  = o.optDouble("x", 0.0),
                                y  = o.optDouble("y", 0.0)
                            ),
                            isParametric = isParam,
                            gains = gains,
                            concertHallEnabled = o.optBoolean("chEnabled", false),
                            concertHallRoom = o.optString("chRoom", "concert"),
                            concertHallAngle = o.optInt("chAngle", 90)
                        )
                    )
                }
            }.sortedByDescending { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistUserEqPresets(list: List<UserEqPreset>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("isParametric", p.isParametric)
                    if (p.isParametric) {
                        val gainArr = JSONArray()
                        p.gains.forEach { gainArr.put(it.toDouble()) }
                        put("gains", gainArr)
                        put("chEnabled", p.concertHallEnabled)
                        put("chRoom", p.concertHallRoom)
                        put("chAngle", p.concertHallAngle)
                    } else {
                        put("v1", p.eq.v1)
                        put("v2", p.eq.v2)
                        put("v3", p.eq.v3)
                        put("v4", p.eq.v4)
                        put("v5", p.eq.v5)
                        put("x", p.eq.x)
                        put("y", p.eq.y)
                    }
                }
            )
        }
        prefs.edit().putString(USER_EQ_PRESETS_KEY, arr.toString()).apply()
    }


    /** Prévisualise l'EQ paramétrique en temps réel pendant la création d'un preset. */
    fun previewParamEq(gains: List<Float>) {
        clearRememberedAudioProfile("parametric preview")
        _eqEnabled.value = true
        _eqBands.value = gains.map { it.toDouble().coerceIn(-12.0, 12.0) }
        service?.enqueuePriorityPacket(ZikProtocol.packetForParamEq(gains))
    }

    /** Sauvegarde un preset issu de l'EQ paramétrique + Concert Hall. */
    fun saveParamPreset(
        name: String,
        gains: List<Float>,
        concertHallEnabled: Boolean,
        concertHallRoom: String,
        concertHallAngle: Int
    ): String? {
        val nm = name.trim().take(35)
        if (nm.isBlank()) return "Name required"
        val dummy = ZikProtocol.ThumbEqValues(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val current = _userEqPresets.value.toMutableList()
        if (current.any { it.name.equals(nm, ignoreCase = true) }) {
            return "A preset with this name already exists"
        }
        val preset = UserEqPreset(
            id = System.currentTimeMillis(),
            name = nm,
            eq = dummy,
            isParametric = true,
            gains = gains.map { it.coerceIn(-12f, 12f) },
            concertHallEnabled = concertHallEnabled,
            concertHallRoom = concertHallRoom,
            concertHallAngle = concertHallAngle
        )
        current.add(0, preset)
        val bounded = current.sortedByDescending { it.id }.take(24)
        _userEqPresets.value = bounded
        persistUserEqPresets(bounded)
        return null
    }

    /** Applique un preset utilisateur (paramétrique) au casque. */
    fun applyParamPreset(presetId: Long, rememberSelection: Boolean = true): Boolean {
        val p = _userEqPresets.value.firstOrNull { it.id == presetId } ?: return false
        _activePreset.value = p.name
        activeParamPresetId = p.id
        _eqEnabled.value = true
        _eqBands.value = p.gains.map { it.toDouble().coerceIn(-12.0, 12.0) }
        _thumbEq.value = ZikProtocol.ThumbEqValues(
            _eqBands.value[0], _eqBands.value[1], _eqBands.value[2], _eqBands.value[3], _eqBands.value[4], 0.0, 0.0
        )
        if (rememberSelection) rememberParamPreset(p.id, p.name)
        service?.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(true))
        service?.enqueuePriorityPacket(ZikProtocol.packetForParamEq(p.gains))
        service?.enqueuePriorityPacket(ZikProtocol.packetForConcertHallEnabled(p.concertHallEnabled))
        if (p.concertHallEnabled) {
            service?.enqueuePriorityPacket(ZikProtocol.packetForRoomSize(p.concertHallRoom))
            service?.enqueuePriorityPacket(ZikProtocol.packetForAngle(p.concertHallAngle))
        }
        return true
    }

    /** Met à jour un preset existant par id. */
    fun updateParamPreset(
        presetId: Long,
        name: String,
        gains: List<Float>,
        concertHallEnabled: Boolean,
        concertHallRoom: String,
        concertHallAngle: Int
    ): String? {
        val nm = name.trim().take(35)
        if (nm.isBlank()) return "Name required"
        val current = _userEqPresets.value.toMutableList()
        val idx = current.indexOfFirst { it.id == presetId }
        if (idx < 0) return "Preset not found"
        if (current.any { it.id != presetId && it.name.equals(nm, ignoreCase = true) }) {
            return "A preset with this name already exists"
        }

        val prev = current[idx]
        current[idx] = prev.copy(
            name = nm,
            isParametric = true,
            gains = gains.map { it.coerceIn(-12f, 12f) },
            concertHallEnabled = concertHallEnabled,
            concertHallRoom = concertHallRoom,
            concertHallAngle = concertHallAngle
        )

        val bounded = current.sortedByDescending { it.id }.take(24)
        _userEqPresets.value = bounded
        persistUserEqPresets(bounded)
        if (activeParamPresetId == presetId) {
            _activePreset.value = nm
            rememberParamPreset(presetId, nm)
        }
        return null
    }

    fun renameParamPreset(presetId: Long, name: String): String? {
        val nm = name.trim().take(35)
        if (nm.isBlank()) return "Name required"
        val current = _userEqPresets.value.toMutableList()
        val idx = current.indexOfFirst { it.id == presetId }
        if (idx < 0) return "Preset not found"
        if (current.any { it.id != presetId && it.name.equals(nm, ignoreCase = true) }) {
            return "A preset with this name already exists"
        }

        current[idx] = current[idx].copy(name = nm)
        val bounded = current.sortedByDescending { it.id }.take(24)
        _userEqPresets.value = bounded
        persistUserEqPresets(bounded)
        if (activeParamPresetId == presetId) {
            _activePreset.value = nm
            rememberParamPreset(presetId, nm)
        }
        return null
    }

    /** Supprime un preset utilisateur. */
    fun deleteParamPreset(presetId: Long) {
        val next = _userEqPresets.value.filterNot { it.id == presetId }
        _userEqPresets.value = next
        persistUserEqPresets(next)
        if (activeParamPresetId == presetId) {
            clearRememberedAudioProfile("deleting active parametric preset")
        } else if (_activePreset.value != null && next.none { it.name == _activePreset.value }) {
            _activePreset.value = null
        }
    }

    /**
     * Appareils Zik déjà appairés sur ce téléphone (filtre nom "zik").
     * Calculé au moment de l'appel (lecture synchrone de bondedDevices).
     */
    fun bondedZikDevices(): List<ZikDeviceInfo> = try {
        val btAdapter = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
            (context.getSystemService(android.bluetooth.BluetoothManager::class.java))?.adapter
        else
            @Suppress("DEPRECATION") android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        btAdapter
            ?.bondedDevices
            ?.filter { d ->
                d.name?.contains("zik",    ignoreCase = true) == true ||
                d.name?.contains("parrot", ignoreCase = true) == true
            }
            ?.map { d ->
                ZikDeviceInfo(
                    name    = try { d.name } catch (_: SecurityException) { d.address },
                    address = d.address,
                    bonded  = true
                )
            } ?: emptyList()
    } catch (_: SecurityException) { emptyList() }

    /** True quand l'utilisateur manipule l'EQ pad ou le slider Angle — bloque la mise à jour par le polling. */
    private val _isUserInteracting = MutableStateFlow(false)
    val isUserInteracting: StateFlow<Boolean> = _isUserInteracting

    /**
     * Retourne le dernier MAC connu (sauvegardé lors de la dernière connexion réussie).
     * null si aucune connexion précédente n'a été mémorisée.
     */
    fun getSavedDeviceMac(): String? =
        prefs.getString("last_device_mac", null)?.takeIf { it.isNotEmpty() }

    fun setUserInteracting(active: Boolean) {
        _isUserInteracting.value = active
        service?.isUserInteracting = active   // bloque le polling dans le service aussi
    }

    // ── EQ PENDING GATE — Anti-rebound (identique pattern ancPending) ─────────
    // Après que l'utilisateur touche le disque EQ, TOUTES les données EQ provenant
    // du polling Bluetooth sont ignorées pendant EQ_SUPPRESS_MS pour éviter que la
    // position locale (doigt) ne soit écrasée par l'ancienne valeur firmware.
    private val _eqPending = MutableStateFlow(false)
    val eqPending: StateFlow<Boolean> = _eqPending
    @Volatile private var eqSuppressUntilMs = 0L
    private val EQ_SUPPRESS_MS = 2_500L

    /** Appelé dès que l'utilisateur TOUCHE le disque EQ (down event). */
    fun markEqTouched() {
        _eqPending.value = true
        eqSuppressUntilMs = System.currentTimeMillis() + EQ_SUPPRESS_MS
        service?.markEqCommandSent()
    }

    /** Appelé manuellement si nécessaire, ou auto-expire via le temps. */
    fun clearEqPending() {
        _eqPending.value = false
    }

    /** Vérifie si le gate EQ est encore actif (expire automatiquement après EQ_SUPPRESS_MS). */
    private fun isEqSuppressed(): Boolean {
        if (!_eqPending.value) return false
        if (System.currentTimeMillis() > eqSuppressUntilMs) {
            _eqPending.value = false
            return false
        }
        return true
    }
    // ── ServiceConnection ─────────────────────────────────────────────────────
    /** Adresse en attente si connectToAddress() est appelé avant que le service soit lié */
    private var pendingTargetAddress: String? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? ZikBluetoothService.LocalBinder)?.getService()
            service = s
            attachToService()
            // Consomme l'adresse cible en attente (appelée avant que le service soit prêt)
            pendingTargetAddress?.let { addr ->
                pendingTargetAddress = null
                s?.connectToAddress(addr)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) { service = null }
    }

    fun bind() {
        val i = Intent(context, ZikBluetoothService::class.java)
        context.bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        try { context.unbindService(conn) } catch (_: Exception) {}
    }

    /**
     * Démarre et lie le service inconditionnellement.
     * DOIT être appelé dès que l'écran DevicePicker est visible,
     * même si aucun appareil bondé n'est encore connu.
     */
    fun ensureRunning() {
        try {
            val i = Intent(context, ZikBluetoothService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        } catch (_: Exception) {}
        if (service == null) bind()
    }

    fun searchForHeadset(): Boolean {
        try {
            val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                (context.getSystemService(android.bluetooth.BluetoothManager::class.java))?.adapter
            else @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) return false
            // 1. Vérifier d'abord si on a un MAC mémorisé depuis la dernière session
            val prefs = context.getSharedPreferences("zik_prefs", android.content.Context.MODE_PRIVATE)
            val savedMac = prefs.getString("last_device_mac", null)
            // 2. Recherche parmi les appareils appairés : MAC mémorisé OU "zik"/"parrot" dans le nom
            val found = try {
                adapter.bondedDevices?.firstOrNull { d ->
                    (!savedMac.isNullOrEmpty() && d.address == savedMac) ||
                    d.name?.contains("zik",    ignoreCase = true) == true ||
                    d.name?.contains("parrot", ignoreCase = true) == true
                }
            } catch (_: SecurityException) { null }
            if (found != null) {
                val i = Intent(context, ZikBluetoothService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
                else context.startService(i)
                bind()
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun attachToService() {
        val s = service ?: return
        viewModelScope.launch {
            var wasConnected = false
            launch { s.battery.collect {
                _battery.emit(it)
                it?.let { pct -> checkBatteryCoherence(pct) }   // diagnostic fuel-gauge
            } }
            launch { s.isCharging.collect   { _isCharging.emit(it) } }
            launch { s.ancEnabled.collect { v ->
                _ancEnabled.emit(v)
                _ancPending.value = false   // firmware confirmé → fin transitoire
            } }
            launch { s.ancMode.collect { mode ->
                // Gate : ne pas écraser le mode local si une commande est en cours
                if (!_ancPending.value) {
                    _ancMode.emit(mode)
                } else {
                }
            } }
            launch { s.roomSize.collect     { _roomSize.emit(it) } }
            launch { s.thumbEq.collect { eq ->
                // Gate EQ : ignorer les mises à jour polling pendant 2.5s après un touch
                if (!_isUserInteracting.value && !isEqSuppressed()) {
                    _thumbEq.emit(eq)
                    if (eq != null) _eqBands.value = listOf(eq.v1, eq.v2, eq.v3, eq.v4, eq.v5)
                } else {
                }
            } }
            launch { s.autoNc.collect       { _autoNc.emit(it) } }
            launch { s.ncFirmwareStatus.collect { v -> _ncFirmwareStatus.emit(v) } }
            launch { s.trackTitle.collect   { _trackTitle.emit(it) } }
            launch { s.trackArtist.collect  { _trackArtist.emit(it) } }
            launch { s.isConnected.collect  { realConnected ->
                _realConnected.value = realConnected

                if (_demoMode.value) {
                    // Tant qu'on est en mode démo, ne pas écraser l'UI connectée
                    // avec l'état réel du service (souvent false si casque éteint).
                    if (realConnected) {
                        // Un vrai casque s'est connecté : sortir du mode démo
                        // en restaurant les réglages utilisateur, sans retour startup.
                        disableDemoMode(returnToStartup = false)
                    } else {
                        _connected.emit(true)
                    }
                    return@collect
                }

                if (realConnected) {
                    _forceStartupPicker.value = false
                }

                _connected.emit(realConnected)
                if (realConnected && !wasConnected) {
                    viewModelScope.launch {
                        delay(1_500L)
                        if (_connected.value) restoreRememberedAudioProfile()
                    }
                }
                wasConnected = realConnected
            } }
            launch { s.searchStatus.collect  { _searchStatus.emit(it) } }
            launch { s.noiseReductionDb.collect { _noiseReductionDb.emit(it) } }
            launch { s.scannedDevices.collect  { _scannedDevices.emit(it) } }
            launch { s.targetDeviceAddress.collect { _targetDeviceAddress.emit(it) } }
            launch { s.isScanning.collect       { _isScanning.emit(it) } }
            launch { s.eqEnabled.collect        { v -> v?.let { _eqEnabled.emit(it) } } }
            launch { s.concertEnabled.collect   { v -> v?.let { _concertEnabled.emit(it) } } }
            launch { s.presenceSensor.collect { v ->
                _presenceSensor.emit(v)
                _presencePending.value = false   // firmware confirmé head_detection
            } }
            launch { s.presenceWorn.collect       { v -> _presenceWorn.emit(v) } }
            launch { s.autoConnection.collect      { v -> _autoConnection.emit(v) } }
            launch { s.ttsEnabled.collect          { v -> _ttsEnabled.emit(v) } }
            launch { s.firmwareVersion.collect     { v -> _firmwareVersion.emit(v) } }
            launch { s.autoPowerOff.collect        { v -> _autoPowerOff.emit(v) } }
            launch { s.connectedMac.collect     { v -> if (v.isNotEmpty()) _macAddress.emit(v) } }
            launch { s.lastXmlFrames.collect     { _lastXmlFrames.emit(it) } }
            launch { s.liveLogs.collect          { _liveLogs.emit(it) } }
            launch { s.deviceName.collect        { _deviceName.emit(it) } }
            launch { s.phoneMode.collect         { _phoneMode.emit(it) } }
        }
    }

    /** Active le mode démo (UI seulement) — force des états simulés localement. */
    fun enableDemoMode() {
        if (_demoMode.value) return

        // Snapshot intégral des préférences: toute écriture en démo sera annulée à la sortie.
        demoPrefsSnapshot = HashMap(prefs.all)

        demoSnapshot = DemoSnapshot(
            battery = _battery.value,
            noiseReductionDb = _noiseReductionDb.value,
            deviceName = _deviceName.value,
            ancEnabled = _ancEnabled.value,
            ancMode = _ancMode.value,
            autoNc = _autoNc.value,
            eqEnabled = _eqEnabled.value,
            concertEnabled = _concertEnabled.value,
            roomSize = _roomSize.value,
            concertAngle = _concertAngle.value,
            thumbEq = _thumbEq.value,
            eqBands = _eqBands.value.toList(),
            trackTitle = _trackTitle.value,
            trackArtist = _trackArtist.value,
            presenceSensor = _presenceSensor.value,
            presenceWorn = _presenceWorn.value,
            autoConnection = _autoConnection.value,
            ttsEnabled = _ttsEnabled.value,
            autoPowerOff = _autoPowerOff.value,
            phoneMode = _phoneMode.value,
            searchStatus = _searchStatus.value,
            activePreset = _activePreset.value,
            activeParamPresetId = activeParamPresetId,
            rememberedProfileType = prefs.getString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, null),
            rememberedProfileBuiltin = prefs.getString(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY, null),
            rememberedProfileParamId = prefs.getLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, -1L).takeIf { it >= 0L },
            rememberedProfileManualArg = prefs.getString(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY, null)
        )

        _demoMode.value = true
        _connected.value = true
        _battery.value = 85
        _eqBands.value = listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        _noiseReductionDb.value = 18
        _deviceName.value = "Parrot ZIK (Demo Mode)"
    }

    /** Désactive le mode démo et relance le flux normal de connexion. */
    fun disableDemoMode(returnToStartup: Boolean = true) {
        _demoMode.value = false

        demoPrefsSnapshot?.let { snap ->
            restorePrefsSnapshot(snap)
            _skinKey.value = prefs.getString("skin_key", "BLACK") ?: "BLACK"
            _eqLevel.value = prefs.getInt(EQ_LEVEL_KEY, 4).coerceIn(1, 4)
            _userEqPresets.value = loadUserEqPresets()
        }

        // Restaurer les états utilisateur pré-démo.
        demoSnapshot?.let { snap ->
            _battery.value = snap.battery
            _noiseReductionDb.value = snap.noiseReductionDb
            _deviceName.value = snap.deviceName
            _ancEnabled.value = snap.ancEnabled
            _ancMode.value = snap.ancMode
            _autoNc.value = snap.autoNc
            _eqEnabled.value = snap.eqEnabled
            _concertEnabled.value = snap.concertEnabled
            _roomSize.value = snap.roomSize
            _concertAngle.value = snap.concertAngle
            _thumbEq.value = snap.thumbEq
            _eqBands.value = snap.eqBands
            _trackTitle.value = snap.trackTitle
            _trackArtist.value = snap.trackArtist
            _presenceSensor.value = snap.presenceSensor
            _presenceWorn.value = snap.presenceWorn
            _autoConnection.value = snap.autoConnection
            _ttsEnabled.value = snap.ttsEnabled
            _autoPowerOff.value = snap.autoPowerOff
            _phoneMode.value = snap.phoneMode
            _searchStatus.value = snap.searchStatus
            _activePreset.value = snap.activePreset
            activeParamPresetId = snap.activeParamPresetId

            prefs.edit().apply {
                if (snap.rememberedProfileType != null) putString(ACTIVE_AUDIO_PROFILE_TYPE_KEY, snap.rememberedProfileType)
                else remove(ACTIVE_AUDIO_PROFILE_TYPE_KEY)

                if (snap.rememberedProfileBuiltin != null) putString(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY, snap.rememberedProfileBuiltin)
                else remove(ACTIVE_AUDIO_PROFILE_BUILTIN_KEY)

                if (snap.rememberedProfileParamId != null) putLong(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY, snap.rememberedProfileParamId)
                else remove(ACTIVE_AUDIO_PROFILE_PARAM_ID_KEY)

                if (snap.rememberedProfileManualArg != null) putString(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY, snap.rememberedProfileManualArg)
                else remove(ACTIVE_AUDIO_PROFILE_MANUAL_ARG_KEY)
            }.apply()
        } ?: run {
            _noiseReductionDb.value = null
            _battery.value = null
            _deviceName.value = ""
            _searchStatus.value = ""
            _thumbEq.value?.let { eq ->
                _eqBands.value = listOf(eq.v1, eq.v2, eq.v3, eq.v4, eq.v5)
            }
        }
        demoSnapshot = null
        demoPrefsSnapshot = null

        if (returnToStartup) {
            // Retour explicite au menu de démarrage + relance de la logique initiale.
            _connected.value = false
            _targetDeviceAddress.value = null
            _forceStartupPicker.value = true
            _startupFlowToken.value = _startupFlowToken.value + 1
        } else {
            // Sortie démo suite à vraie connexion détectée : rester en flux normal connecté.
            _connected.value = _realConnected.value
            _forceStartupPicker.value = false
            service?.pollState()
            requestBattery()
            fetchTrackMetadata()
            service?.enqueueApi(ZikProtocol.Api.SYSTEM_FRIENDLY_NAME_GET)
        }
    }

    private fun restorePrefsSnapshot(snapshot: Map<String, Any?>) {
        val editor = prefs.edit().clear()
        snapshot.forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, (value as Set<String>).toSet())
                }
            }
        }
        editor.apply()
    }

    /**
     * Action utilisateur depuis le menu latéral pour relancer une reconnexion casque.
     * En mode démo, on sort de la démo puis on relance le flux startup normal.
     */
    fun requestHeadsetReconnectFromDrawer() {
        if (_demoMode.value) {
            disableDemoMode(returnToStartup = true)
            return
        }

        ensureRunning()
        _connected.value = false
        _targetDeviceAddress.value = null
        _forceStartupPicker.value = true
        _startupFlowToken.value = _startupFlowToken.value + 1
    }

    /** Quitte explicitement la démo depuis le drawer et revient au flux startup. */
    fun exitDemoModeFromDrawer() {
        if (_demoMode.value) {
            disableDemoMode(returnToStartup = true)
        }
    }

    // ── Commandes ─────────────────────────────────────────────────────────────
    fun requestBattery() {
        service?.enqueueApi(ZikProtocol.Api.BATTERY_GET)
    }

    /**
     * Lance un scan Bluetooth actif pour découvrir les appareils à proximité.
     * Nécessite BLUETOOTH_SCAN (Android 12+).
     */
    fun startScan() {
        ensureRunning()
        service?.startScan() ?: run {
            // Si service pas encore lié, on relancera le scan quand il sera prêt
            Log.i("ZikControl", "startScan: service not bound — scan deferred")
        }
    }

    /** Demande une seule mise à jour de la batterie (utilisé par le kill-switch Dashboard). */
    fun fetchBatteryOnce() {
        service?.fetchBattery()
    }

    /** Demande une mise à jour des métadonnées piste (déclenché sur événement média). */
    fun fetchTrackMetadata() {
        service?.fetchTrackMetadata()
    }

    /**
     * Force la connexion à un appareil spécifique par adresse MAC.
     * Utilisé quand l'utilisateur sélectionne manuellement un appareil dans le DevicePicker.
     */
    fun connectToAddress(address: String) {
        ensureRunning()                           // garantit que le service existe
        _targetDeviceAddress.value = address
        service?.connectToAddress(address) ?: run {
            // service pas encore lié : sauvegarde l'adresse, sera consommée dans onServiceConnected
            pendingTargetAddress = address
        }
    }

    fun setAncEnabled(enabled: Boolean) {
        setNoiseControlMode(if (enabled) ZikProtocol.NoiseControlMode.ANC else ZikProtocol.NoiseControlMode.OFF)
    }

    fun setNoiseControlMode(mode: ZikProtocol.NoiseControlMode) {
        viewModelScope.launch {
            ncMutex.withLock {
                service?.markAncCommandSent()   // supprime le feedback poll pendant 3 s
                // Boucle de Vérité : UI en attente jusqu'à confirm <answer> firmware
                _ancPending.value = true
                _ancMode.value  = mode
                // Auto NC ne se désactive que lorsqu'on va sur OFF
                if (mode == ZikProtocol.NoiseControlMode.OFF) {
                    _autoNc.value = false
                }
                when (mode) {
                    ZikProtocol.NoiseControlMode.ANC -> {
                        // ANC max — arg=anc&value=2 | hex: 61 72 67 3D 61 6E 63 26 76 61 6C 75 65 3D 32
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(true))
                        delay(120L)  // Firmware 3.07 : DSP biquad switch GenericXO CONFIG_1
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlMode(ZikProtocol.NoiseControlMode.ANC))
                    }
                    ZikProtocol.NoiseControlMode.ANC_LOW -> {
                        // ANC low — arg=anc&value=1 | hex: 61 72 67 3D 61 6E 63 26 76 61 6C 75 65 3D 31
                        // BUG FIX : ajout du delay() manquant — le firmware n'a pas le temps de
                        //           traiter enabled=true avant de recevoir value=1 → mode sautait.
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(true))
                        delay(120L)
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlMode(ZikProtocol.NoiseControlMode.ANC_LOW))
                    }
                    ZikProtocol.NoiseControlMode.OFF -> {
                        // OFF — arg=off&value=0 | hex: 61 72 67 3D 6F 66 66 26 76 61 6C 75 65 3D 30
                        service?.enqueuePriorityPacket(ZikProtocol.packetForAutoNc(false))
                        delay(120L)
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(false))
                    }
                    ZikProtocol.NoiseControlMode.STREET_LOW -> {
                        // STREET low — arg=aoc&value=1 | hex: 61 72 67 3D 61 6F 63 26 76 61 6C 75 65 3D 31
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(true))
                        delay(120L)
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlMode(mode))
                    }
                    ZikProtocol.NoiseControlMode.STREET -> {
                        // STREET max — arg=aoc&value=2 | hex: 61 72 67 3D 61 6F 63 26 76 61 6C 75 65 3D 32
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(true))
                        delay(120L)
                        service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlMode(mode))
                    }
                }
                // Relâcher le verrou UI après un délai suffisant pour la confirmation firmware
                delay(1_200L)
                _ancPending.value = false
            }
        }
    }

    fun setRoomSize(mode: String) {
        _roomSize.value = mode   // optimiste — mis à jour sans attendre la réponse
        service?.enqueueApiPriority(ZikProtocol.Api.AUDIO_SOUND_EFFECT_ROOM_SIZE_SET, "?arg=$mode")
    }

    fun setAngle(angle: Int) {
        _concertAngle.value = angle   // optimiste
        service?.enqueueApiPriority(ZikProtocol.Api.AUDIO_SOUND_EFFECT_ANGLE_SET, "?arg=$angle")
    }

    /**
     * Active un preset égaliseur par nom ("pops", "vocal", "crystal", "club", "punchy", "deep").
     * Séquence : equalizer/enabled/set?arg=true → preset/set?arg=<name>
     * Bloque le polling EQ (eqLocked) pour empêcher le casque d'écraser le preset.
     */
    fun setEqPreset(presetName: String, rememberSelection: Boolean = true) {
        val svc = service ?: return
        svc.eqLocked = true
        _activePreset.value = presetName
        activeParamPresetId = null
        val scale = _eqLevel.value / 4.0
        val base  = EQ_LEVEL_BASE[presetName] ?: List(5) { 0.0 }
        // clampEqGain : prévient l'écrêtage DSP (seuil DRC -48.5dBFS, TalaSettings firmware 3.07)
        val scaled = base.map { clampEqGain(it * scale) }
        _eqEnabled.value = true
        _eqBands.value = scaled
        _thumbEq.value = ZikProtocol.ThumbEqValues(
            scaled[0], scaled[1], scaled[2], scaled[3], scaled[4], 0.0, 0.0
        )
        if (rememberSelection) rememberBuiltInPreset(presetName)
        viewModelScope.launch {
            svc.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(false)) // 1. OFF  — reset DSP
            delay(80L)
            svc.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(true))  // 2. ON   — réactive
            delay(80L)
            svc.enqueuePriorityPacket(ZikProtocol.packetForPreset(presetName))     // 3. PRESET — applique
            delay(120L)
            // 4. Thumb-EQ scalé selon le niveau d'intensité demandé
            svc.enqueuePriorityPacket(
                ZikProtocol.packetForThumbEqManual(
                    scaled[0], scaled[1], scaled[2], scaled[3], scaled[4], 0.0, 0.0
                )
            )
        }
    }

    /**
     * Désactive l'égaliseur (mode plat / OFF).
     * Séquence : equalizer/enabled/set?arg=false
     * Réactive le polling EQ pour suivre l'état réel du casque.
     */
    fun setEqOff() {
        service?.eqLocked = false         // reprend le polling EQ
        clearRememberedAudioProfile("EQ OFF manuel")
        service?.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(false))
    }

    /**
     * Change le niveau d'intensité EQ (1–4). Si un preset est actif, le réapplique immédiatement.
     */
    fun setEqLevel(level: Int) {
        val lvl = level.coerceIn(1, 4)
        _eqLevel.value = lvl
        prefs.edit().putInt(EQ_LEVEL_KEY, lvl).apply()
        val preset = _activePreset.value ?: return  // pas de preset actif — rien à faire
        if (activeParamPresetId == null) {
            setEqPreset(preset)  // ré-applique le preset avec la nouvelle échelle
        }
    }

    fun setThumbEq(v1: Double, v2: Double, v3: Double, v4: Double, v5: Double, x: Double, y: Double) {
        // Borne chaque bande dans ±6.0 dB (seuil DRC DSP -48.5 dBFS, TalaSettings firmware 3.07)
        val s1 = clampEqGain(v1); val s2 = clampEqGain(v2); val s3 = clampEqGain(v3)
        val s4 = clampEqGain(v4); val s5 = clampEqGain(v5)
        // Persistance locale immédiate (UI suit le doigt sans attendre la réponse)
        _eqBands.value = listOf(s1, s2, s3, s4, s5)
        // Sauvegarder position dans _thumbEq pour survie entre navigations de pages
        val manualEq = ZikProtocol.ThumbEqValues(s1, s2, s3, s4, s5, x, y)
        _thumbEq.value = manualEq
        _eqEnabled.value = true
        rememberManualThumbEq(manualEq)
        // Refresh gate : empêcher le polling d'écraser pendant 2.5s
        markEqTouched()
        // ─ Format Parrot natif : gains %.1f (pas 0.5 dB), r/theta %.0f (entiers) ──────
        // Throttle 500ms pendant drag via Flow.sample() dans MainScreen
        service?.enqueuePriorityPacket(
            ZikProtocol.packetForThumbEqManual(s1, s2, s3, s4, s5, x, y)
        )
    }

    fun setAutoNc(enabled: Boolean) {
        viewModelScope.launch {
            ncMutex.withLock {
                service?.markAncCommandSent()   // supprime le feedback poll pendant 3 s
                _ancPending.value = true         // verrouille le slider UI
                _autoNc.value = enabled
                if (enabled) {
                    // Auto-NC nécessite que l'étage NC soit activé en premier
                    service?.enqueuePriorityPacket(ZikProtocol.packetForNoiseControlEnable(true))
                    delay(120L)
                }
                service?.enqueuePriorityPacket(ZikProtocol.packetForAutoNc(enabled))
                delay(1_200L)
                _ancPending.value = false   // déverrouille après confirmation firmware
            }
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        if (enabled) {
            // Active l'EQ et renvoie immédiatement la position actuelle du disque
            // pour éviter tout saut de volume incohérent.
            val svc = service ?: run {
                service?.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(true))
                return
            }
            val bands = _eqBands.value
            viewModelScope.launch {
                svc.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(true))
                delay(80L)
                if (bands.size == 5) {
                    svc.enqueuePriorityPacket(
                        ZikProtocol.packetForThumbEqManual(
                            bands[0], bands[1], bands[2], bands[3], bands[4], 0.0, 0.0
                        )
                    )
                }
            }
        } else {
            clearRememberedAudioProfile("EQ manually disabled")
            service?.enqueuePriorityPacket(ZikProtocol.packetForEqualizerEnable(false))
        }
        // GET de confirmation en file normale (basse priorité, après la commande)
        service?.enqueuePacket(ZikProtocol.packetForEqualizerEnabledGet())
    }

    fun setConcertHallEnabled(enabled: Boolean) {
        _concertEnabled.value = enabled
        service?.enqueuePriorityPacket(ZikProtocol.packetForConcertHallEnabled(enabled))
    }

    /** Active/désactive le capteur de présence (pause automatique quand le casque est retiré). */
    fun setPresenceSensor(enabled: Boolean) {
        _presencePending.value = true
        viewModelScope.launch { delay(1_500L); _presencePending.value = false }  // timeout 1.5s
        _presenceSensor.value = enabled
        if (!enabled) _presenceWorn.value = null
        service?.enqueuePriorityPacket(ZikProtocol.packetForPresenceSensor(enabled))
    }

    /** Active/désactive l'auto-connexion Bluetooth. [VERIFIED NOTE3] auto_connection/enabled/set */
    fun setAutoConnection(enabled: Boolean) {
        _autoConnection.value = enabled
        service?.enqueuePriorityPacket(ZikProtocol.packetForAutoConnection(enabled))
    }

    /** Définit le timeout d'arrêt automatique. [VERIFIED NOTE3] auto_power_off/set?arg=<minutes> */
    fun setAutoPowerOff(minutes: Int) {
        _autoPowerOff.value = minutes
        service?.enqueuePriorityPacket(ZikProtocol.packetForAutoPowerOff(minutes))
    }

    /** Active/désactive l'annonce vocale de l'appelant (TTS). [VERIFIED NOTE3] tts/set */
    fun setTtsEnabled(enabled: Boolean) {
        _ttsEnabled.value = enabled
        service?.enqueuePriorityPacket(ZikProtocol.packetForTts(enabled))
    }

    /** Définit le mode ANC pendant les appels téléphoniques. */
    fun setPhoneMode(mode: String) {
        _phoneMode.value = mode
        service?.enqueuePriorityPacket(ZikProtocol.packetForPhoneMode(mode))
    }

    /** Renomme le casque via /api/bluetooth/friendlyname/set?arg=<name>. */
    fun setFriendlyName(name: String) {
        val trimmed = name.trim().take(35)
        if (trimmed.isBlank()) return

        _deviceName.value = trimmed

        val encoded = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")
        service?.enqueueApiPriority(ZikProtocol.Api.SYSTEM_FRIENDLY_NAME_SET, "?arg=$encoded")
        // Relecture pour confirmer la valeur réellement acceptée par le firmware.
        service?.enqueueApi(ZikProtocol.Api.SYSTEM_FRIENDLY_NAME_GET)
    }

    override fun onCleared() {
        super.onCleared()
        audioViz.stop()
        unbind()
    }
}

