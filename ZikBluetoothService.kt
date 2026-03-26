package com.rmdaye.ziker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/** Représente un appareil Bluetooth découvert ou appairé, utilisé dans le DevicePicker */
data class ZikDeviceInfo(val name: String, val address: String, val bonded: Boolean)

class ZikBluetoothService : Service() {
    companion object {
        // v2 : IMPORTANCE_DEFAULT + silent => icône visible dans la barre d'état Samsung One UI
        const val CHANNEL_ID = "zik_status_v2"
        const val BATTERY_ALERT_CHANNEL_ID = "zik_battery_alert_v1"
        const val NOTIF_ID = 0xBEEF
        const val BATTERY_ALERT_NOTIF_ID = 0xBA7F
        const val TAG = "ZikBluetoothService"

        // Actions disponibles via ADB :
        //   adb shell am broadcast -a com.rmdaye.ziker.ACTION_ANC_ON   -n com.rmdaye.ziker/.ZikBluetoothService
        //   adb shell am broadcast -a com.rmdaye.ziker.ACTION_ANC_OFF  -n com.rmdaye.ziker/.ZikBluetoothService
        //   adb shell am broadcast -a com.rmdaye.ziker.ACTION_BATTERY  -n com.rmdaye.ziker/.ZikBluetoothService
        const val ACTION_ANC_ON      = "com.rmdaye.ziker.ACTION_ANC_ON"
        const val ACTION_ANC_OFF     = "com.rmdaye.ziker.ACTION_ANC_OFF"
        const val ACTION_STREET_MODE = "com.rmdaye.ziker.ACTION_STREET_MODE"
        const val ACTION_BATTERY     = "com.rmdaye.ziker.ACTION_BATTERY"
        const val ACTION_POLL_STATE  = "com.rmdaye.ziker.ACTION_POLL_STATE"
        const val ACTION_SET_NOTIFICATION_PREF = "com.rmdaye.ziker.ACTION_SET_NOTIFICATION_PREF"
        // Envoie GET /api/audio/noise_control/enabled/get pour tester la réponse du casque
        const val ACTION_STATUS_GET  = "com.rmdaye.ziker.ACTION_STATUS_GET"
        // Nouveaux : Auto NC + EQ
        const val ACTION_AUTO_NC_ON  = "com.rmdaye.ziker.ACTION_AUTO_NC_ON"
        const val ACTION_AUTO_NC_OFF = "com.rmdaye.ziker.ACTION_AUTO_NC_OFF"
        const val ACTION_EQ_GET      = "com.rmdaye.ziker.ACTION_EQ_GET"

        // ── CONSTANTES DE TIMING (ajustables après analyse timestamps Note 3) ─────────
        //
        // SSM_RESPONSE_TIMEOUT_MS  : temps max d'attente d'un <answer>/<notify> avant
        //                            de passer à la commande suivante (mode dégradé).
        //                            Proto Note 3 : à confirmer (attendu ≈ 200–600 ms).
        //
        // POLL_INTERVAL_MS         : période du polling léger batterie/ANC/NC.
        //                            Note 3 : à confirmer (attendu ≈ 5 000–15 000 ms).
        //
        // POLL_FIRST_DELAY_MS      : délai avant le 1er poll post-connexion.
        //                            Laisse le temps à zikStartRequest() de se terminer.
        //
        // NC_MONITOR_DELAY_MS      : délai avant le 1er poll NC enabled/get.
        //
        // SCAN_RETRY_DELAY_MS      : délai entre deux tentatives de scan BT (no device).
        //
        // EQ_THROTTLE_MS           : débit max envoi trames EQ depuis le disque tactile.
        //                            Note 3 : à confirmer (attendu ≈ 80–200 ms).
        const val SSM_RESPONSE_TIMEOUT_MS : Long = 750L      // [VERIFIED NOTE3] max RECV direct=494.2ms (trame #126) ×1.5=741ms → 750ms
        const val POLL_INTERVAL_MS        : Long = 10_000L  // [VERIFIED NOTE3] polling batterie toutes 10s confirmé (trames #070,#306,#374)
        const val POLL_FIRST_DELAY_MS     : Long = 3_000L   // [VERIFIED NOTE3] délai init avant premier poll
        const val NC_MONITOR_DELAY_MS     : Long = 4_000L   // [VERIFIED NOTE3] délai post-connexion avant vérif ANC
        const val SCAN_RETRY_DELAY_MS     : Long = 2_000L   // [VERIFIED NOTE3] délai entre tentatives scan BT
        const val EQ_THROTTLE_MS          : Long = 150L     // [VERIFIED NOTE3] UX — firmware répond en ~10ms, 150ms est un choix UI

        /** Tag de log dédié au diagnostic ANC/paramètres — filtrer avec : adb logcat -s ZIK_DEBUG */
        const val ZIK_DEBUG = "ZIK_DEBUG"
    }
    private val binder = LocalBinder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    /**
     * ── MOTEUR DE COMMANDES SÉRIE (clonage architecture officielle Parrot) ────────────
     *
     * L'app officielle (com.parrot.zik2 · BTManager + Connector) utilise une
     * ConcurrentLinkedQueue<Request> strictement sérielle :
     *   sendData(api)  → queue.add(request)  ; si queue.size==1 → executeTask()
     *   onResponse()   → executeNextTask()   = queue.remove() + executeTask()
     *
     * On reproduit exactement ce comportement :
     *   • pendingQueue   : deque pour pouvoir insérer en tête (priorité HP)
     *   • responseSignal : Channel CONFLATED — le readLoop signale chaque réponse XML
     *   • drainJob       : une seule coroutine tourne à la fois (idempotente)
     *   ─ Pas de délai fixe entre envois : la réponse du firmware EST le signal.
     *   ─ Timeout de sécurité 1 s si pas de réponse (firmware absent / déconnexion).
     */
    private val pendingQueue  = ConcurrentLinkedDeque<ByteArray>()
    private val responseSignal = Channel<Unit>(Channel.CONFLATED)
    private val draining = AtomicBoolean(false)

    /** Vrai quand connectLoop tourne déjà — évite les doublons après BT wake. */
    @Volatile private var connectLoopRunning = false
    /** Job de la coroutine connectLoop — utilisé pour cancel + restart immédiat. */
    private var connectLoopJob: Job? = null

    /**
     * Receiver de découverte Bluetooth :
     *  - ACTION_FOUND            → collecte les appareils trouvés
     *  - DISCOVERY_STARTED/FINISHED → met à jour _isScanning
     */
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else
                            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { d ->
                        // ── FILTRE "ZIK ONLY" ──────────────────────────────────────────────────
                        // Ignorer : noms null/vides, adresses MAC brutes (TV, Box…),
                        //           tout appareil dont le nom ne contient pas "Parrot" ou "Zik".
                        val dName = try { d.name } catch (_: SecurityException) { null }
                        if (dName.isNullOrBlank()) return@let
                        if (!dName.contains("Parrot", ignoreCase = true) &&
                            !dName.contains("Zik",    ignoreCase = true)) {
                            return@let
                        }
                        // ───────────────────────────────────────────────────────────────────────
                        val bonded = d.bondState == BluetoothDevice.BOND_BONDED
                        val info = ZikDeviceInfo(dName, d.address, bonded)
                        val current = _scannedDevices.value.toMutableList()
                        if (current.none { it.address == info.address }) {
                            current.add(info)
                            _scannedDevices.tryEmit(current)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.tryEmit(false)
                    Log.i(TAG, "Scan terminé — ${_scannedDevices.value.size} appareil(s) trouvé(s)")
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.tryEmit(true)
                    Log.i(TAG, "Scan démarré")
                }
            }
        }
    }

    /**
     * Receiver BT STATE_CHANGED : quand Bluetooth se ré-active,
     * relance connectLoop si pas déjà connecté.
     */
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            if (state == BluetoothAdapter.STATE_ON) {
                Log.i(TAG, "Bluetooth réactivé (STATE_ON) — vérification connexion casque")
                if (!_connected.value && !connectLoopRunning) {
                    Log.i(TAG, "Relance de connectLoop après réveil BT")
                    connectLoopJob = scope.launch { connectLoop() }
                }
            }
        }
    }

    /**
     * Flag positionné par le ViewModel quand l'utilisateur touche l'EQ ou l'ANC.
     * Quand true, pollState() est silencié pour laisser la priorité aux commandes user.
     */
    @Volatile var isUserInteracting: Boolean = false
    /** Quand true : le polling EQ (thumb_equalizer/get) est suspendu.
     *  Positionné à true dès que l'utilisateur choisit un preset en UI.
     *  Garantit que le casque ne écrase pas le preset après l'envoi. */
    @Volatile var eqLocked: Boolean = false

    /**
     * Lance un scan Bluetooth actif pour découvrir les appareils à proximité.
     * Le scan dure ~12 s ; les résultats arrivent via discoveryReceiver.
     * ATTENTION : cancelDiscovery() est appelé automatiquement avant connect().
     */
    fun startScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        _scannedDevices.tryEmit(emptyList())
        // Ne pas lancer de discovery si la boucle de connexion est active :
        // adapter.startDiscovery() sature la bande passante BT et casse RFCOMM.
        if (connectLoopRunning) {
            Log.i(TAG, "startScan: connectLoop active — skip discovery (RFCOMM protégé)")
        } else {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            val started = adapter.startDiscovery()
            Log.i(TAG, "startScan: démarrage scan Bluetooth → started=$started")
        }
        // Lancer la boucle de connexion si elle n'est pas déjà active
        if (!_connected.value && !connectLoopRunning) {
            Log.i(TAG, "startScan: lancement connectLoop (casque déconnecté, loop inactive)")
            connectLoopJob = scope.launch { connectLoop() }
        }
    }

    /**
     * Force la connexion à un appareil spécifique par adresse MAC.
     * Contourne le filtre "zik" — utile quand l'utilisateur choisit manuellement.
     */
    fun connectToAddress(address: String) {
        Log.i(TAG, "connectToAddress: $address (loopRunning=$connectLoopRunning, target=$targetAddress)")
        // ── Mémoriser ce MAC comme cible préférée dès maintenant ──────────────────────
        // même si la boucle ne redémarre pas, targetAddress est mis à jour pour le
        // prochain cycle du while dans connectLoop().
        targetAddress = address
        _targetDeviceAddress.tryEmit(address)
        // ── Sauvegarder immédiatement comme dernier MAC connu ─────────────────────────
        getSharedPreferences("zik_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("last_device_mac", address).apply()

        // Si la boucle est déjà active et cible déjà cet appareil → ne pas redémarrer.
        if (connectLoopRunning) {
            val alreadyTargetedExplicit = try {
                // Vérifier si l'adresse est aussi trouvable via bondedDevices pour éviter un restart
                val btAdapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    (getSystemService(android.bluetooth.BluetoothManager::class.java))?.adapter
                else @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
                btAdapter?.bondedDevices?.any { it.address == address } == true
            } catch (_: Exception) { false }
            // Ne pas annuler la boucle en cours — elle utilisera targetAddress mis à jour ci-dessus
            return
        }
        // Boucle non active → lancer
        Log.i(TAG, "connectToAddress: démarrage loop vers $address")
        connectLoopJob?.cancel()
        connectLoopRunning = false
        connectLoopJob = scope.launch { connectLoop() }
    }

    /**
     * Polling léger — appelé toutes les 5 s après connexion.
     * SILENCE RADIO : seuls l'état ANC et les valeurs EQ thumb sont interrogés.
     * - Décibelmètre (noiseControl/get dB)     → supprimé
     * - Sound Effects / Concert Hall           → supprimé
     * - Presence sensor                        → supprimé
     * - Batterie                               → gérée par la UI (timer 30 s)
     */
    fun pollState() {
        if (isUserInteracting) return
        // Throttle 2 s : on ne renvoie un GET que si l'info n'est pas déjà fraîche.
        // Une réponse <notify> ou une confirmation SET reset le cache → aucun GET superflu.
        if (!isFresh("nc"))       enqueuePacket(ZikProtocol.packetForNoiseControlGet())
        if (!isFresh("presence")) enqueuePacket(ZikProtocol.packetForPresenceSensorGet())
        if (!eqLocked && !isFresh("eq")) enqueuePacket(ZikProtocol.packetForThumbEqGet())
    }

    /**
     * Demande uniquement le niveau de batterie.
     * Appelé depuis l'UI (Dashboard, 30 s). Respecte le veto — bloqué
     * pendant 60 s après la dernière interaction utilisateur (géré côté UI).
     */
    fun fetchBattery() {
        enqueuePacket(ZikProtocol.packetForBattery())
    }

    /** Demande uniquement les métadonnées de la piste (déclenché par mediaChangeReceiver). */
    fun fetchTrackMetadata() {
        enqueuePacket(ZikProtocol.packetForTrackMetadataGet())
    }

    /** Injecte des octets bruts directement dans le flux RFCOMM (contourne le protocole). */
    private fun injectRaw(vararg bytes: Byte) {
        val out = output
        if (out == null) {
            Log.w(TAG, "injectRaw: socket non connectée, bytes ignorés")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                out.write(bytes)
                out.flush()
                Log.i(TAG, "injectRaw: ${bytes.size} octets envoyés -> ${bytes.joinToString { "0x%02X".format(it) }}")
            } catch (e: Exception) {
                Log.e(TAG, "injectRaw: erreur écriture", e)
            }
        }
    }

    private val adbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ANC_ON -> {
                    // Protocole vérifié par capture Frida :
                    // GET /api/audio/noise_control/set?arg=anc&value=2
                    // Pas de tryEmit optimiste : l'UI attend la confirmation XML du casque.
                    enqueuePacket(ZikProtocol.packetForAncOn())
                    Log.i(TAG, "ACTION_ANC_ON → arg=anc&value=2 (attente confirmation casque)")
                }
                ACTION_ANC_OFF -> {
                    // GET /api/audio/noise_control/set?arg=off&value=0
                    enqueuePacket(ZikProtocol.packetForAncOff())
                    Log.i(TAG, "ACTION_ANC_OFF → arg=off&value=0 (attente confirmation casque)")
                }
                ACTION_STREET_MODE -> {
                    // GET /api/audio/noise_control/set?arg=aoc&value=2
                    enqueuePacket(ZikProtocol.packetForAncStreet())
                    Log.i(TAG, "ACTION_STREET_MODE → arg=aoc&value=2 (attente confirmation casque)")
                }
                ACTION_BATTERY -> {
                    enqueuePacket(ZikProtocol.packetForBattery())
                    Log.i(TAG, "ACTION_BATTERY : requête envoyée")
                }
                ACTION_STATUS_GET -> {
                    enqueuePacket(ZikProtocol.packetForNoiseControlGet())
                    Log.i(TAG, "ACTION_STATUS_GET → GET /api/audio/noise_control/enabled/get")
                }
                ACTION_POLL_STATE -> {
                    pollState()
                }
                ACTION_AUTO_NC_ON -> {
                    enqueuePacket(ZikProtocol.packetForAutoNc(true))
                    Log.i(TAG, "ACTION_AUTO_NC_ON → auto_nc/set?arg=true")
                }
                ACTION_AUTO_NC_OFF -> {
                    enqueuePacket(ZikProtocol.packetForAutoNc(false))
                    Log.i(TAG, "ACTION_AUTO_NC_OFF → auto_nc/set?arg=false")
                }
                ACTION_SET_NOTIFICATION_PREF -> {
                    val show = intent?.getBooleanExtra("show", true) ?: true
                    try {
                        val prefs = getSharedPreferences("zik_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("show_battery_notification", show).apply()
                        if (show) {
                            // Si permission manquante sur API 33+, rien ne fera apparaître la notif → le launcher doit demander
                            startForeground(NOTIF_ID, buildNotification(connected = _connected.value, battery = _battery.value))
                            Log.i(TAG, "Notification persistante activée via préférence")
                        } else {
                            stopForeground(true)
                            NotificationManagerCompat.from(this@ZikBluetoothService).cancel(NOTIF_ID)
                            Log.i(TAG, "Notification persistante désactivée via préférence")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erreur en appliquant préférence notification", e)
                    }
                }
                ACTION_EQ_GET -> {
                    enqueuePacket(ZikProtocol.packetForThumbEqGet())
                    Log.i(TAG, "ACTION_EQ_GET → thumb_equalizer/value/get")
                }
            }
        }
    }

    private val _battery = MutableStateFlow<Int?>(null)
    val battery = _battery.asStateFlow()

    private val _ancEnabled = MutableStateFlow<Boolean?>(null)
    val ancEnabled = _ancEnabled.asStateFlow()

    /** Mode ANC actuel (OFF / ANC / STREET) mis à jour dès chaque réponse du casque. */
    private val _ancMode = MutableStateFlow<ZikProtocol.NoiseControlMode?>(null)
    val ancMode = _ancMode.asStateFlow()

    private val _roomSize = MutableStateFlow<String?>(null)
    val roomSize = _roomSize.asStateFlow()

    /** EQ thumb Zik 3 (7 valeurs : v1-v5 bandes + x,y joystick) */
    private val _thumbEq = MutableStateFlow<ZikProtocol.ThumbEqValues?>(null)
    val thumbEq = _thumbEq.asStateFlow()

    /** Auto Noise Control actif */
    private val _autoNc = MutableStateFlow<Boolean?>(null)
    val autoNc = _autoNc.asStateFlow()

    /**
     * Dernier statut brut reçu depuis /api/audio/noise_control/enabled/get.
     * Mis à jour toutes les ~2 s par le job de monitoring Auto-NC.
     * Valeurs : "✔ NC activé (firmware)" | "✖ NC désactivé (passif)" | null (pas encore reçu)
     */
    private val _ncFirmwareStatus = MutableStateFlow<String?>(null)
    val ncFirmwareStatus = _ncFirmwareStatus.asStateFlow()

    /** Métadonnées de la piste en cours */
    private val _trackTitle = MutableStateFlow<String?>(null)
    val trackTitle = _trackTitle.asStateFlow()

    private val _trackArtist = MutableStateFlow<String?>(null)
    val trackArtist = _trackArtist.asStateFlow()

    /** true si le casque est en charge via câble */
    private val _isCharging = MutableStateFlow(false)
    val isCharging = _isCharging.asStateFlow()

    /** Niveau de réduction de bruit réel en dB (ex. -25, 0, +6) — null si inconnu */
    private val _noiseReductionDb = MutableStateFlow<Int?>(null)
    val noiseReductionDb = _noiseReductionDb.asStateFlow()

    /** Égaliseur actif */
    private val _eqEnabled = MutableStateFlow<Boolean?>(null)
    val eqEnabled = _eqEnabled.asStateFlow()

    /** Concert Hall actif */
    private val _concertEnabled = MutableStateFlow<Boolean?>(null)
    val concertEnabled = _concertEnabled.asStateFlow()

    /** Capteur de présence / Pause automatique (proximity_sensor) */
    private val _presenceSensor = MutableStateFlow<Boolean?>(null)
    val presenceSensor = _presenceSensor.asStateFlow()

    /** État porté/retiré du casque (true=porté, false=retiré) via notif firmware */
    private val _presenceWorn = MutableStateFlow<Boolean?>(null)
    val presenceWorn = _presenceWorn.asStateFlow()

    /** Auto-connexion Bluetooth activée — [VERIFIED NOTE3] /api/system/auto_connection/enabled/ */
    private val _autoConnection = MutableStateFlow<Boolean?>(null)
    val autoConnection = _autoConnection.asStateFlow()

    /** Annonce vocale de l'appelant (TTS) activée — [VERIFIED NOTE3] /api/software/tts/ */
    private val _ttsEnabled = MutableStateFlow<Boolean?>(null)
    val ttsEnabled = _ttsEnabled.asStateFlow()

    /** Version firmware lue dynamiquement depuis /api/software/version/get — sip6 */
    private val _firmwareVersion = MutableStateFlow(ZikProtocol.DEVICE_FIRMWARE)
    val firmwareVersion = _firmwareVersion.asStateFlow()

    /** Timeout d'arrêt automatique en minutes (0=désactivé) — [VERIFIED NOTE3] auto_power_off */
    private val _autoPowerOff = MutableStateFlow<Int?>(null)
    val autoPowerOff = _autoPowerOff.asStateFlow()

    // ── Suppression ANC poll ───────────────────────────────────────────────────────────────────
    // Après une commande utilisateur ANC SET, les réponses de polling sont ignorées pendant 3 s
    // pour éviter que pollState() écrase le mode défini de façon optimiste dans le ViewModel.
    @Volatile private var ancSuppressUntilMs = 0L

    // ── Cache de fraîcheur des réponses par domaine ────────────────────────────────────────────
    // Clés : "nc" | "eq" | "presence" | "battery"
    // Un GET n'est renvoyé que si aucune réponse valide n'a été reçue depuis moins de GET_FRESH_TTL_MS.
    private val lastReceivedMs = ConcurrentHashMap<String, Long>()
    private val GET_FRESH_TTL_MS = 2_000L

    /**
     * Retourne true si une réponse pour [key] a été reçue il y a moins de [GET_FRESH_TTL_MS] ms.
     * Évite de renvoyer des GET superflus quand l'UI ou le firmware vient déjà de notifier l'état.
     */
    private fun isFresh(key: String): Boolean =
        (System.currentTimeMillis() - (lastReceivedMs[key] ?: 0L)) < GET_FRESH_TTL_MS

    /** Appelé par le ViewModel avant chaque commande SET ANC pour supprimer le feedback poll 3 s. */
    fun markAncCommandSent() {
        ancSuppressUntilMs = System.currentTimeMillis() + 3_000L
    }

    // ── EQ suppress : bloque l'émission de _thumbEq depuis le readLoop pendant 2.5s ──
    @Volatile private var eqSuppressUntilMs = 0L

    /** Appelé par le ViewModel dès que l'utilisateur touche le disque EQ. */
    fun markEqCommandSent() {
        eqSuppressUntilMs = System.currentTimeMillis() + 2_500L
        eqLocked = true
    }

    /** True si le suppress EQ est encore actif. */
    private fun isEqSuppressed(): Boolean {
        if (System.currentTimeMillis() > eqSuppressUntilMs) {
            eqLocked = false
            return false
        }
        return true
    }

    /** Adresse MAC du casque connecté */
    private val _connectedMac = MutableStateFlow("")
    val connectedMac = _connectedMac.asStateFlow()

    /** 3 dernières trames XML reçues (console de diagnostic) */
    private val _lastXmlFrames = MutableStateFlow<List<String>>(emptyList())
    val lastXmlFrames = _lastXmlFrames.asStateFlow()

    /** Buffer circulaire de logs live (100 lignes max) pour la page Logs UI */
    private val _liveLogs = MutableStateFlow<List<String>>(emptyList())
    val liveLogs = _liveLogs.asStateFlow()
    private fun pushLog(tag: String, msg: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT)
            .format(java.util.Date())
        val line = "$ts [$tag] $msg"
        val cur = _liveLogs.value.toMutableList()
        cur.add(line)
        if (cur.size > 100) cur.removeAt(0)
        _liveLogs.tryEmit(cur)
    }

    private val _connected = MutableStateFlow(false)
    /** true quand la socket RFCOMM est établie avec le casque */
    val isConnected = _connected.asStateFlow()

    /** Message affiché dans l'UI quand aucun casque n'est trouvé après 10 s. */
    private val _searchStatus = MutableStateFlow("")
    val searchStatus = _searchStatus.asStateFlow()

    /** Appareils découverts par le scan actif Bluetooth */
    private val _scannedDevices = MutableStateFlow<List<ZikDeviceInfo>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    /** Adresse MAC actuellement ciblée par la boucle de connexion. */
    private val _targetDeviceAddress = MutableStateFlow<String?>(null)
    val targetDeviceAddress = _targetDeviceAddress.asStateFlow()

    /** True pendant un scan actif (startDiscovery en cours) */
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    /** Nom Bluetooth convivial du casque (friendlyname) */
    private val _deviceName = MutableStateFlow("Parrot ZIK 3")
    val deviceName = _deviceName.asStateFlow()

    /** Mode ANC pendant les appels téléphoniques (anc, aoc, street, off) */
    private val _phoneMode = MutableStateFlow("anc")
    val phoneMode = _phoneMode.asStateFlow()

    /**
     * Adresse MAC cible — quand non-null, connectLoop essaie cet appareil
     * en priorité (ignorer le filtre nom "zik").
     */
    @Volatile var targetAddress: String? = null

    /**
     * Receiver média : détecte le changement de piste Android (AVRCP + lecteurs communs)
     * et demande une seule mise à jour des métadonnées au casque.
     * Trés précis : ne pollue pas le flux RFCOMM toutes les 2s.
     */
    private val mediaChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            fetchTrackMetadata()
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ZikBluetoothService = this@ZikBluetoothService
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY : Android relânce le service même après un kill système
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(connected = false, battery = null))
        connectLoopJob = scope.launch { connectLoop() }
        // Enregistrer le receiver pour les broadcasts ADB
        val filter = IntentFilter().apply {
            addAction(ACTION_ANC_ON)
            addAction(ACTION_ANC_OFF)
            addAction(ACTION_STREET_MODE)
            addAction(ACTION_BATTERY)
            addAction(ACTION_STATUS_GET)
            addAction(ACTION_SET_NOTIFICATION_PREF)
            addAction(ACTION_POLL_STATE)
            addAction(ACTION_AUTO_NC_ON)
            addAction(ACTION_AUTO_NC_OFF)
            addAction(ACTION_EQ_GET)
        }
        registerReceiver(adbReceiver, filter, Context.RECEIVER_EXPORTED)
        Log.i(TAG, "BroadcastReceiver enregistré (ANC / BATTERY / AUTO_NC / EQ)")
        // Écoute le réveil Bluetooth pour relancer la connexion au casque
        // RECEIVER_NOT_EXPORTED : ACTION_STATE_CHANGED est un broadcast système protégé (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(btStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        }
        Log.i(TAG, "btStateReceiver enregistré (ACTION_STATE_CHANGED)")
        // Récepteur pour la découverte d'appareils
        val discoveryFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // RECEIVER_NOT_EXPORTED : ACTION_FOUND /DISCOVERY_* sont des broadcasts système protégés
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(discoveryReceiver, discoveryFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(discoveryReceiver, discoveryFilter)
        }
        Log.i(TAG, "discoveryReceiver enregistré (ACTION_FOUND / DISCOVERY_*")
        // Receiver média : track change → une seule requête RFCOMM
        val mediaFilter = IntentFilter().apply {
            addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.spotify.music.metadatachanged")
            addAction("com.spotify.music.playbackstatechanged")
        }
        // RECEIVER_EXPORTED : les broadcasts média proviennent d'apps tierces (Spotify, etc.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaChangeReceiver, mediaFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(mediaChangeReceiver, mediaFilter)
        }
        Log.i(TAG, "mediaChangeReceiver enregistré (track change / A2DP play state)")
        // Respecter préférence notification au démarrage
        try {
            val prefs = getSharedPreferences("zik_prefs", Context.MODE_PRIVATE)
            val showNotif = prefs.getBoolean("show_battery_notification", false)
            if (!showNotif) {
                // Retirer la notification si l'utilisateur l'a désactivée
                stopForeground(true)
                NotificationManagerCompat.from(this).cancel(NOTIF_ID)
                Log.i(TAG, "Notification persistante désactivée par préférence utilisateur")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Impossible de lire préférence notification", e)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(adbReceiver)
        try { unregisterReceiver(btStateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(mediaChangeReceiver) } catch (_: Exception) {}
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        scope.cancel()
        // Fermeture propre de tous les flux pour ne pas bloquer le casque au prochain lancement
        try { input?.close()  } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null; input = null; output = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Canal principal : DEFAULT + silence forcé → icône visible dans la barre d'état
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "Zik — État connexion", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Affiche l'état de connexion et la batterie du casque"
            channel.setSound(null, null)
            channel.enableVibration(false)
            nm.createNotificationChannel(channel)
        }
        // Canal alertes basse batterie (son + vibration intentionnels)
        if (nm.getNotificationChannel(BATTERY_ALERT_CHANNEL_ID) == null) {
            val alertChannel = NotificationChannel(BATTERY_ALERT_CHANNEL_ID, "Batterie faible Zik", NotificationManager.IMPORTANCE_HIGH)
            alertChannel.description = "Alerte quand la batterie du casque Parrot Zik est faible"
            nm.createNotificationChannel(alertChannel)
        }
    }

    private var lastBatteryAlertThreshold: Int? = null

    private fun buildNotification(connected: Boolean, battery: Int?): android.app.Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = when {
            connected && battery != null -> "Parrot Zik 3 — $battery%"
            connected -> "Parrot Zik 3 — Connecté"
            else -> "Parrot Zik 3"
        }
        val body = when {
            connected && battery != null -> "Batterie : $battery%"
            connected -> "En connexion..."
            else -> "En recherche..."
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    private fun checkAndSendBatteryAlert(pct: Int) {
        val threshold = when {
            pct <= 10 -> 10
            pct <= 20 -> 20
            else -> { lastBatteryAlertThreshold = null; return }  // reset au-dessus de 20%
        }
        if (lastBatteryAlertThreshold == threshold) return  // déjà alerté pour ce seuil
        lastBatteryAlertThreshold = threshold
        val text = if (threshold <= 10)
            "Batterie très faible ($pct%) — branchez le casque"
        else
            "Batterie faible ($pct%) — pensez à recharger"
        val pi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, BATTERY_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Batterie Parrot Zik 3")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(BATTERY_ALERT_NOTIF_ID, notif)
    }

    // ── API publique (non-suspend, thread-safe) ────────────────────────────────────────

    /** Ajoute un paquet en FIN de queue (polling, GET états, init). */
    fun enqueuePacket(packet: ByteArray) {
        pendingQueue.addLast(packet)
        triggerDrain()
    }

    /** Ajoute un paquet en TÊTE de queue (commande utilisateur — prioritaire). */
    fun enqueuePriorityPacket(packet: ByteArray) {
        pendingQueue.addFirst(packet)
        triggerDrain()
    }

    fun enqueueApi(path: String, args: String? = null) =
        enqueuePacket(ZikProtocol.wrapPacket(ZikProtocol.buildGetPayload(path, args)))

    fun enqueueApiPriority(path: String, args: String? = null) =
        enqueuePriorityPacket(ZikProtocol.wrapPacket(ZikProtocol.buildGetPayload(path, args)))

    /**
     * Lance drainQueue() si aucune instance n'est déjà active.
     * AtomicBoolean garantit qu'une seule coroutine écoule la queue à la fois.
     */
    private fun triggerDrain() {
        if (draining.compareAndSet(false, true)) {
            scope.launch { drainQueue() }
        }
    }

    /**
     * ── MODULE DE TRACE ─────────────────────────────────────────────────────────
     * Décode et affiche en clair le contenu de chaque paquet SORTANT avant envoi.
     *
     * Format logcat :
     *   DEBUG_TRAME_NATIVE : [Flux Sortant] -> [<contenu XML>]
     *
     * Le header binaire Parrot Zik (3 octets : 2 longueur big-endian + 1 type)
     * est sauté ; l'octet 4+ est du texte UTF-8 pur (chemin /api/... + args).
     * Exemples :
     *   DEBUG_TRAME_NATIVE : [Flux Sortant] -> [GET /api/audio/noise_control/set?arg=anc&value=2]
     *   DEBUG_TRAME_NATIVE : [Flux Sortant] -> [GET /api/system/battery/get]
     */
    private fun logTrameSortante(pkt: ByteArray) {
        val xmlContent = if (pkt.size > 3)
            pkt.copyOfRange(3, pkt.size).toString(Charsets.UTF_8)
        else
            "<vide — paquet session-open>"
        Log.i(TAG, "DEBUG_TRAME_NATIVE : [Flux Sortant] -> [$xmlContent]")
        pushLog("↑ OUT", xmlContent.take(200))
    }

    private suspend fun sendPacketNow(pkt: ByteArray) {
        logTrameSortante(pkt)   // MODULE DE TRACE — affiche la trame en clair avant envoi
        try {
            withContext(Dispatchers.IO) {
                output?.write(pkt)
                output?.flush()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sendPacketNow: échec envoi — ${t.message}")
        }
    }

    /**
     * Boucle d'envoi avec séquenceur série — clone de la stratégie officielle Parrot.
     * Un seul paquet en vol à la fois ; avance sur réponse XML ou timeout 1 s.
     */
    /**
     * ── STRICT SEQUENTIAL MESSAGING (SSM) ──────────────────────────────────────
     * Clone de Connector.executeTask() / executeNextTask() de l'app officielle Parrot.
     *
     * RÈGLE SSM ABSOLUE :
     *   La commande N+1 NE PEUT PAS être envoyée tant que le casque n'a pas répondu
     *   à la commande N par un tag <answer> ou <notify> dans le flux XML entrant.
     *
     * Flux de contrôle :
     *   1. sendPacketNow(pkt)        → envoi physique OutputStream (loggé ci-dessus)
     *   2. responseSignal.receive()  → BLOQUE cette coroutine (attend le signal)
     *   3. readLoop → trySend(Unit)  → DÉBLOQUE dès réception de <answer> ou <notify>
     *   4. Timeout 1 s               → avance de force si firmware muet / déconnecté
     *
     * Cela correspond EXACTEMENT à la stratégie officielle :
     *   « One command in-flight at a time, next sent only after response received. »
     *
     * Quand la queue se vide, draining repasse à false. Si un nouveau paquet
     * est ajouté entre-temps, triggerDrain() relancera une coroutine.
     */
    private suspend fun drainQueue() {
        try {
            while (currentCoroutineContext().isActive) {
                val pkt = pendingQueue.pollFirst() ?: break   // queue vide → on s'arrête
                sendPacketNow(pkt)
                // Attend la réponse ; timeout SSM_RESPONSE_TIMEOUT_MS si firmware muet
                withTimeoutOrNull(SSM_RESPONSE_TIMEOUT_MS) { responseSignal.receive() }
            }
        } finally {
            draining.set(false)
            // Si des paquets ont été ajoutés pendant le finally, relancer
            if (pendingQueue.isNotEmpty()) triggerDrain()
        }
    }

    /**
     * Séquence d'initialisation officielle — clone EXACT de Connector.zikStartRequest().
     * Ordre et commandes calqués sur la capture btsnoop_hci.log de l'app Parrot Zik 2 v1.71.
     *
     * Note : /api/appli_version/set?arg=1.71 est CRITIQUE — le firmware utilise cette valeur
     *        pour activer certaines fonctionnalités et identifier l'app comme "compatible".
     */
    private fun zikStartRequest() {
        enqueueApi("/api/system/device_type/get")
        enqueueApi("/api/system/color/get", "?version=2")
        enqueueApi("/api/software/version/get")
        enqueueApi("/api/features/get")
        enqueueApi("/api/bluetooth/friendlyname/get")
        enqueuePacket(ZikProtocol.packetForBattery())
        enqueueApi("/api/system/texture/get")
        enqueueApi("/api/audio/equalizer/enabled/get")
        enqueueApi("/api/audio/sound_effect/enabled/get")
        enqueueApi("/api/audio/noise_control/enabled/get")
        enqueuePacket(ZikProtocol.packetForNoiseControlGet())
        enqueueApi("/api/system/ancillary_sensor/presence/get")  // chemin réel S22 Ultra
        enqueueApi("/api/audio/preset/current/get")
        enqueuePacket(ZikProtocol.packetForThumbEqGet())
        enqueueApi("/api/audio/source/get")
        enqueuePacket(ZikProtocol.packetForTrackMetadataGet())
        enqueueApi("/api/audio/sound_effect/get")
        // flight_mode/get supprimé — mode avion retiré
        enqueueApi("/api/audio/smart_audio_tune/get")
        enqueueApi("/api/audio/noise_control/phone_mode/get")
        enqueueApi("/api/system/auto_connection/enabled/get")
        enqueueApi("/api/system/auto_power_off/get")
        enqueueApi("/api/system/head_detection/enabled/get")
        enqueueApi("/api/system/head_detection/enabled/set", "?arg=true")  // force capteur ON au démarrage
        enqueueApi("/api/appli_version/set", "?arg=1.71")   // OBLIGATOIRE : identifie l'app
        enqueueApi("/api/audio/preset/bypass/get")
        enqueueApi("/api/software/tts/get")
        enqueueApi("/api/audio/sound_effect/mode/get")
        enqueueApi("/api/audio/delay/get")
        enqueueApi("/api/audio/preset/cancel_producer")
        enqueueApi("/api/system/bt_address/get")
        Log.i(TAG, "zikStartRequest: 30 commandes initiales enquêuées (clone officiel Parrot)")
    }

    /**
     * Exécute socket.connect() dans un Thread Java dédié avec timeout.
     * BluetoothSocket.connect() EST BLOQUANT et ne peut pas être annulé par
     * les coroutines Kotlin — un thread séparé est obligatoire pour le timeout.
     * Si [timeoutMs] expire, la socket est fermée forcément et IOException est levée.
     */
    @Throws(IOException::class)
    private fun connectBlocking(socket: BluetoothSocket, timeoutMs: Long = 8_000L) {
        var connectException: Exception? = null
        val thread = Thread {
            try { socket.connect() }
            catch (e: Exception) { connectException = e }
        }
        thread.start()
        thread.join(timeoutMs)
        if (thread.isAlive) {
            try { socket.close() } catch (_: Exception) {}
            thread.interrupt()
            throw IOException("Timeout connexion (${timeoutMs}ms) — casque non répondant")
        }
        connectException?.let { throw it }
    }

    /** Traduit une exception BT en message lisible pour l'UI */
    private fun humanError(e: Exception): String = when {
        e.message?.contains("timeout",          ignoreCase = true) == true -> "Timeout"
        e.message?.contains("refused",          ignoreCase = true) == true -> "Casque occupé"
        e.message?.contains("unable to connect",ignoreCase = true) == true -> "Inaccessible"
        e.message?.contains("host is down",     ignoreCase = true) == true -> "Casque hors portée"
        e is IOException -> "Erreur RFCOMM"
        else -> e.javaClass.simpleName
    }

    /**
     * On ne peut pas détecter formellement "connecté à un autre appareil",
     * mais c'est la cause la plus fréquente quand RFCOMM échoue sans app crash.
     */
    private fun enrichConnectionHint(base: String): String {
        return "$base. Vérifiez que le casque n'est pas déjà connecté à un autre appareil."
    }

    private suspend fun connectLoop() {
        if (connectLoopRunning) { return }
        connectLoopRunning = true
        try {
        // ── Récupération adaptateur BT (API non dépréciée en priorité) ────────────────
        val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(android.bluetooth.BluetoothManager::class.java))?.adapter
        } else {
            @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
        }
        if (adapter == null) {
            Log.e(TAG, "connectLoop: BluetoothAdapter null — Bluetooth non disponible")
            return
        }
        // ── Restauration MAC mémorisé entre sessions ──────────────────────────────────
        // Si l'utilisateur n'a pas encore sélectionné manuellement un appareil ET qu'on
        // n'a pas encore de targetAddress, restaurer le dernier MAC connu depuis les prefs.
        val prefs = getSharedPreferences("zik_prefs", android.content.Context.MODE_PRIVATE)
        val savedMac = prefs.getString("last_device_mac", null)
        if (targetAddress == null && !savedMac.isNullOrEmpty()) {
            targetAddress = savedMac
            Log.i(TAG, "connectLoop: MAC mémorisé restauré → $savedMac")
            pushLog("LOOP", "MAC mémorisé restauré: $savedMac")
        }
        Log.i(TAG, "connectLoop démarré — filtre 'zik'/'parrot' (insensible casse/format)")
        pushLog("LOOP", "connectLoop démarré")
        _searchStatus.tryEmit("Recherche de votre Zik\u2026")
        var scanStartTime = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            try {
                // ── Sélection de l'appareil cible ─────────────────────────────────────────
                // Si targetAddress est défini (choix manuel ou MAC mémorisé), on cible cet
                // appareil seul ; sinon on filtre tous les appareils appairés.
                val target = targetAddress
                val zikDevices: List<BluetoothDevice> = if (target != null) {
                    Log.i(TAG, "Mode ciblé : connexion à $target")
                    listOf(adapter.getRemoteDevice(target))
                } else {
                    // Filtre élargi : accepte "Zik" OU "Parrot" dans le nom BT du casque
                    // Guard SecurityException (BLUETOOTH_CONNECT requis Android 12+)
                    val rawBonded = try { adapter.bondedDevices } catch (_: SecurityException) {
                        Log.w(TAG, "connectLoop: bondedDevices — BLUETOOTH_CONNECT non accordé")
                        null
                    }
                    rawBonded
                        ?.filter { dev ->
                            dev.name?.contains("zik",    ignoreCase = true) == true ||
                            dev.name?.contains("parrot", ignoreCase = true) == true
                        }
                        ?: emptyList()
                }

                if (zikDevices.isEmpty()) {
                    val elapsed = System.currentTimeMillis() - scanStartTime
                    if (elapsed >= 10_000) {
                        _searchStatus.tryEmit(
                            "Aucun Parrot Zik appair\u00e9. Veuillez appairer le casque dans les param\u00e8tres Android."
                        )
                        Log.w(TAG, "Aucun casque Zik dans bondedDevices (${elapsed / 1000}s)")
                    } else {
                        _searchStatus.tryEmit("Recherche de votre Zik\u2026")
                    }
                    delay(SCAN_RETRY_DELAY_MS)
                    continue
                }

                // Au moins un appareil Zik appairé — réinitialiser le timer
                scanStartTime = System.currentTimeMillis()
                val deviceNames = zikDevices.joinToString { "${it.name ?: "Zik"}(${it.address})" }
                Log.i(TAG, "${zikDevices.size} appareil(s) Zik trouv\u00e9(s) : $deviceNames")

                var connected = false
                var lastDevice = "Zik"
                // Essaie chaque appareil Zik trouvé — prend le premier qui répond
                outer@ for (device in zikDevices) {
                    val displayName = device.name ?: "Parrot Zik"
                    lastDevice = displayName
                    _searchStatus.tryEmit("Connexion \u00e0 $displayName\u2026")
                    Log.i(TAG, "Tentative sur : $displayName (${device.address})")
                    //
                    // Stratégie de connexion RFCOMM (ordre de préférence) :
                    //   1. SECURE:SPP   → createRfcommSocketToServiceRecord        (SPP standard sécurisé)
                    //   2. INSECURE:SPP → createInsecureRfcommSocketToServiceRecord (SPP sans PIN — certains firmwares Zik)
                    //   3. INSECURE:ZIK_3 / ZIK_2 → UUID propriétaires Parrot (insecure)
                    //   4. REFLECT:1   → canal RFCOMM fixe via réflexion (évite le SDP qui peut échouer sur Android 12+)
                    val uuids = listOf(
                        "SECURE:${ZikProtocol.UUID_SPP}",        // 1. SPP sécurisé — recommandé Android 12+
                        "INSECURE:${ZikProtocol.UUID_SPP}",      // 2. SPP insecure — fallback firmware Zik
                        "INSECURE:${ZikProtocol.UUID_ZIK_3}",    // 3. UUID propriétaire Zik 3
                        "INSECURE:${ZikProtocol.UUID_ZIK_2}",    // 4. UUID propriétaire Zik 2
                        "REFLECT:1"                              // 5. Canal RFCOMM fixe — dernier recours
                    )
                    for (uuidStr in uuids) {
                        var s: BluetoothSocket? = null
                        try {
                            // Fabrique une socket neuve — cancelDiscovery() AVANT connect() obligatoire
                            // (la découverte actve sature la bande BT et fait expirer le socket).
                            fun makeSocket(): BluetoothSocket {
                                adapter?.cancelDiscovery()   // ← CRITIQUE : stopper le scan avant connect
                                return when {
                                    uuidStr.startsWith("REFLECT:") -> {
                                        val channel = uuidStr.removePrefix("REFLECT:").toInt()
                                        Log.i(TAG, "Fallback réflexion : createRfcommSocket($channel) sur $displayName")
                                        @Suppress("DiscouragedPrivateApi")
                                        device.javaClass.getMethod("createRfcommSocket", Int::class.java)
                                            .invoke(device, channel) as BluetoothSocket
                                    }
                                    uuidStr.startsWith("SECURE:") -> {
                                        val uuid = UUID.fromString(uuidStr.removePrefix("SECURE:"))
                                        Log.i(TAG, "Socket SECURE createRfcommSocketToServiceRecord($uuid)")
                                        device.createRfcommSocketToServiceRecord(uuid)
                                    }
                                    else -> {
                                        val uuid = UUID.fromString(uuidStr.removePrefix("INSECURE:"))
                                        Log.i(TAG, "Socket INSECURE createInsecureRfcommSocketToServiceRecord($uuid)")
                                        device.createInsecureRfcommSocketToServiceRecord(uuid)
                                    }
                                }
                            }

                            // --- Tentative 1 (thread dédié, timeout 8s) ---
                            val shortLabel = when {
                                uuidStr.startsWith("SECURE:")   -> "SPP/sécurisé"
                                uuidStr.startsWith("INSECURE:") -> "SPP/insecure"
                                else                            -> uuidStr
                            }
                            pushLog("BT", "RFCOMM $displayName [$shortLabel] (1/2)")
                            _searchStatus.tryEmit("Connexion $displayName — $shortLabel (1/2)")
                            val sock1 = makeSocket()
                            s = sock1
                            try {
                                connectBlocking(sock1, 8_000)
                                Log.i(TAG, "Socket ouverte : $displayName via [$shortLabel]")
                            } catch (e1: Exception) {
                                val e1msg = e1.javaClass.simpleName + ": " + (e1.message ?: "?")
                                Log.w(TAG, "Echec t1 [$shortLabel] $displayName : $e1msg — retry 500ms")
                                _searchStatus.tryEmit("$shortLabel erreur: $e1msg")
                                try { sock1.close() } catch (_: Exception) {}
                                s = null
                                delay(500)
                                // --- Tentative 2 avec nouvelle socket ---
                                _searchStatus.tryEmit("Connexion $displayName — $shortLabel (2/2)")
                                val sock2 = makeSocket()
                                s = sock2
                                connectBlocking(sock2, 8_000)
                                Log.i(TAG, "Socket ouverte (t2) : $displayName via [$shortLabel]")
                            }

                            val openSock = checkNotNull(s) { "Socket null apres connexion" }
                            socket = openSock
                            input  = openSock.inputStream
                            output = openSock.outputStream
                            connected = true

                            // ── HANDSHAKE SESSION PARROT ──────────────────────────────────────
                            // Étape 1 : session open — le firmware attend 00 03 00 pour déverrouiller
                            // son récepteur RFCOMM et accepter les commandes GET XML.
                            val sessionOpen = byteArrayOf(0x00, 0x03, 0x00)
                            openSock.outputStream.write(sessionOpen)
                            openSock.outputStream.flush()
                            Log.i(TAG, ">> HANDSHAKE [1/3] : 00 03 00 envoyé")
                            pushLog("HAND", "[1/3] session open envoyé")
                            _searchStatus.tryEmit("$displayName — handshake session…")

                            // Lire l'écho (max 64 octets, délai 300 ms)
                            val echoBuf = ByteArray(64)
                            var echoRead = 0
                            try {
                                delay(300)
                                if (openSock.inputStream.available() > 0)
                                    echoRead = openSock.inputStream.read(echoBuf)
                            } catch (_: Exception) {}
                            when {
                                echoRead >= 3 && echoBuf[0] == 0x00.toByte() && echoBuf[1] == 0x03.toByte() ->
                                    { Log.i(TAG, "HANDSHAKE [2/3] SESSION OK — firmware byte=0x%02X".format(echoBuf[2].toInt() and 0xFF)); pushLog("HAND", "[2/3] SESSION OK") }
                                echoRead > 0 ->
                                    { Log.w(TAG, "HANDSHAKE [2/3] écho inattendu ($echoRead B) — ${echoBuf.take(echoRead).joinToString { "0x%02X".format(it) }}, on continue"); pushLog("HAND", "[2/3] écho inattendu ${echoRead}B") }
                                else ->
                                    { Log.w(TAG, "HANDSHAKE [2/3] aucune réponse en 300ms — firmware peut-être déjà prêt"); pushLog("HAND", "[2/3] pas de réponse 300ms") }
                            }

                            // Étape 2 : probe bt_address/get — confirme que le firmware répond
                            // avant de lancer les 30 commandes d'init.  Ce GET est léger et toujours
                            // supporté par le Zik 3 quel que soit son état de connexion précédent.
                            Log.i(TAG, "HANDSHAKE [3/3] probe → /api/system/bt_address/get")
                            _searchStatus.tryEmit("$displayName — probe firmware…")
                            val probePacket = ZikProtocol.wrapPacket(
                                ZikProtocol.buildGetPayload("/api/system/bt_address/get")
                            )
                            openSock.outputStream.write(probePacket)
                            openSock.outputStream.flush()

                            // Attendre la réponse XML (readLoop pas encore lancé → lecture directe)
                            val probeBuf  = ByteArray(512)
                            var probeRead = 0
                            val probeDeadline = System.currentTimeMillis() + 2_500L
                            try {
                                while (System.currentTimeMillis() < probeDeadline) {
                                    if (openSock.inputStream.available() > 0) {
                                        probeRead = openSock.inputStream.read(probeBuf)
                                        break
                                    }
                                    delay(50)
                                }
                            } catch (_: Exception) {}

                            val probeText = if (probeRead > 0) String(probeBuf, 0, probeRead, Charsets.UTF_8) else ""
                            val probeOk   = probeText.contains("bt_address") || probeText.contains("<answer") || probeRead > 5
                            if (probeOk) {
                                Log.i(TAG, "PROBE OK ($probeRead B) : ${probeText.take(100)} — lancement init")
                                pushLog("HAND", "[3/3] PROBE OK ${probeRead}B")
                                _searchStatus.tryEmit("$displayName — firmware répondu, init…")
                            } else {
                                Log.w(TAG, "PROBE sans réponse (${probeRead}B) — démarrage init quand même")
                                pushLog("HAND", "[3/3] probe silencieux ${probeRead}B")
                                _searchStatus.tryEmit("$displayName — probe silencieux, init en cours…")
                            }
                            // ─────────────────────────────────────────────────────────────────

                            // ── Mémoriser le MAC pour les prochaines sessions ─────────────────
                            // Permet à l'app de se reconnecter directement au même casque sans
                            // re-scanner les appareils appairés → connexion immédiate au démarrage.
                            getSharedPreferences("zik_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("last_device_mac", device.address).apply()
                            Log.i(TAG, "connectLoop: MAC mémorisé → ${device.address}")

                            targetAddress = null   // connexion réussie → effacer la cible
                            _targetDeviceAddress.tryEmit(null)
                            // Couper tout scan de discovery résiduel — protège le canal RFCOMM
                            try { adapter.cancelDiscovery() } catch (_: Exception) {}
                            pushLog("✓ OK", "Connecté à $displayName")
                            _connected.tryEmit(true)
                            _connectedMac.tryEmit(device.address)
                            _searchStatus.tryEmit("")   // efface message UI
                            try {
                                val prefs = getSharedPreferences("zik_prefs", Context.MODE_PRIVATE)
                                val showNotif = prefs.getBoolean("show_battery_notification", false)
                                val canNotify = NotificationManagerCompat.from(this@ZikBluetoothService).areNotificationsEnabled()
                                val permOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                else true
                                if (showNotif && canNotify && permOk) {
                                    NotificationManagerCompat.from(this@ZikBluetoothService)
                                        .notify(NOTIF_ID, buildNotification(true, _battery.value))
                                }
                            } catch (_: Exception) {}
                            // 30 commandes d'init — lancées SEULEMENT après confirmation firmware
                            zikStartRequest()
                            // Polling léger toutes les 10 s — écoute <notify> passive entre chaque poll
                            // ncMonitorJob SUPPRIMÉ : packetForNoiseControlGet() est déjà inclus
                            // dans pollState() — évite le doublon et la boucle ANC MAX.
                            val pollingJob = scope.launch {
                                delay(POLL_FIRST_DELAY_MS)    // [PENDING Note3]
                                while (currentCoroutineContext().isActive && _connected.value) {
                                    pollState()
                                    delay(POLL_INTERVAL_MS)   // [PENDING Note3]
                                }
                            }
                            readLoop()          // bloque jusqu'à la déconnexion
                            pollingJob.cancel()
                            // ── NETTOYAGE SOCKET APRÈS DÉCONNEXION ────────────────
                            // Libère proprement le socket BT mort pour que la
                            // stack Android puisse ouvrir un nouveau canal RFCOMM.
                            try { output?.close() } catch (_: Exception) {}
                            try { input?.close()  } catch (_: Exception) {}
                            try { socket?.close() } catch (_: Exception) {}
                            output = null; input = null; socket = null
                            pushLog("✗ DC", "Déconnecté — nettoyage socket")
                            _connected.tryEmit(false)
                            _searchStatus.tryEmit("Déconnecté — reconnexion…")
                            try {
                                val prefs = getSharedPreferences("zik_prefs", Context.MODE_PRIVATE)
                                val showNotif = prefs.getBoolean("show_battery_notification", false)
                                val canNotify = NotificationManagerCompat.from(this@ZikBluetoothService).areNotificationsEnabled()
                                val permOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                else true
                                if (showNotif && canNotify && permOk) {
                                    NotificationManagerCompat.from(this@ZikBluetoothService)
                                        .notify(NOTIF_ID, buildNotification(false, null))
                                }
                            } catch (_: Exception) {}
                            break@outer     // quitte les deux boucles for — retour au while
                        } catch (e: Exception) {
                            val shortLabel2 = when {
                                uuidStr.startsWith("SECURE:")   -> "SPP/sécurisé"
                                uuidStr.startsWith("INSECURE:") -> "SPP/insecure"
                                else                            -> uuidStr
                            }
                            val errDetail = "${e.javaClass.simpleName}: ${e.message ?: "erreur inconnue"}"
                            val msg = humanError(e)
                            Log.e("ZikConnect", "✘ Échec [$shortLabel2] $displayName : $errDetail")
                            pushLog("✗ ERR", "[$shortLabel2] $msg")
                            _searchStatus.tryEmit(enrichConnectionHint("[$shortLabel2] $msg — essai suivant…"))
                            try { s?.close() } catch (_: Exception) {}
                            s = null
                        }
                    } // fin for (uuidStr in uuids)
                } // fin outer@ for (device in zikDevices)
                if (!connected) {
                    val errMsg = enrichConnectionHint("Impossible de joindre $lastDevice. Vérifiez : casque allumé ? Hors portée ?")
                    Log.w(TAG, "Tous les UUID ont échoué pour $lastDevice")
                    _searchStatus.tryEmit(errMsg)
                    delay(3_000)   // pause avant nouvelle tentative
                } else {
                    // Déconnexion après une session réussie → petite pause pour la stack BT
                    Log.i(TAG, "Reconnexion dans 1.5 s (stack BT recovery)")
                    delay(1_500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "connectLoop erreur générale : ${e.message}")
                delay(2000)
            }
        }
        } finally {
            connectLoopRunning = false
            if (_connected.value) _targetDeviceAddress.tryEmit(null)
            Log.i(TAG, "connectLoop terminé — connectLoopRunning remis à false")
        }
    }

    private fun readLoop() {
        Log.i(TAG, "readLoop: démarré, écoute du flux entrant")
        try {
            val buf = ByteArray(4096)
            var read: Int
            val ins = input ?: return
            val baos = java.io.ByteArrayOutputStream()
            while (true) {
                read = ins.read(buf)
                if (read <= 0) break
                baos.write(buf, 0, read)
                val bytes = baos.toByteArray()


                val msgs = ZikProtocol.parseInMessages(bytes)
                if (msgs.isEmpty()) {
                    // Le casque a répondu mais le parseur ne reconnaît pas le format :
                    // possible session-open (type=0x00), ou réponse courte < 7 octets.
                    Log.w(TAG, "<< AUCUN msg parsé sur ${bytes.size} octets bruts (type=0x%02X)".format(
                        if (bytes.size >= 3) bytes[2].toInt() and 0xFF else -1
                    ))
                    // Signal session-open : avance aussi la queue sérielle
                    responseSignal.trySend(Unit)
                    // On vide le buffer pour éviter l'accumulation infinie
                    baos.reset()
                } else {
                    var consumed = 0
                    for (m in msgs) {
                        consumed += m.zzFirst
                        val text = m.data.toString(Charsets.UTF_8)
                        if (text.isNotBlank()) {
                            // ── SSM : <answer> ou <notify> reçu → débloque la prochaine commande
                            responseSignal.trySend(Unit)
                            val ssmTag = when {
                                text.contains("<answer")  -> "ANSWER"
                                text.contains("<notify")  -> "NOTIFY"
                                else                      -> "XML"
                            }
                            Log.i(TAG, "DEBUG_TRAME_NATIVE : [Flux Entrant/$ssmTag] -> [${text.take(300)}]")
                            pushLog("↓ $ssmTag", text.take(200))
                            Log.i(TAG, "<< MSG XML: $text")
                            // ━━ GARDE : ignorer les trames d'erreur firmware ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                            // Le casque retourne error="true" quand le chemin n'est pas reconnu.
                            // Dans ce cas on NE met PAS à jour l'UI — mais on avance quand même
                            // la queue sérielle (responseSignal déjà envoyé ci-dessus).
                            if (text.contains("error=\"true\"")) {
                                Log.w(TAG, "<< ERREUR FIRMWARE (error=true) — trame ignorée: ${text.take(200)}")
                                // Feedback UI si la commande NC a échoué
                                if (text.contains("noise_control") || text.contains("/api/audio/noise")) {
                                    _ncFirmwareStatus.tryEmit("\u26a0\ufe0f  Mode non support\u00e9 (erreur firmware)")
                                }
                                continue  // sauter tous les parseurs d'état ci-dessous
                            }
                            // Log explicite modified/unchanged — suivi commandes ANC/EQ/Concert Hall
                            if (text.contains("noise_control") || text.contains("equalizer") ||
                                text.contains("sound_effect") || text.contains("concert_hall")) {
                                when {
                                    text.contains("type=\"modified\"")  -> Log.i(TAG, "<< CONFIRMED (modified): ${text.take(120)}")
                                    text.contains("type=\"unchanged\"") -> Log.i(TAG, "<< UNCHANGED (déjà en place): ${text.take(120)}")
                                }
                            }
                            // ── HANDLER NOTIFY (clone protocole officiel Parrot) ──────────────────
                            // Le firmware répond aux SET avec <notify path="/api/..."/> pour demander
                            // à l'app de rafraîchir cet endpoint. L'app officielle le fait systéma-
                            // tiquement (capturé dans btsnoop_hci.log).
                            // Ex: SET noise_control → firmware répond <notify path=".../enabled/get"/>
                            //     → on enqueue le GET correspondant pour mettre à jour l'UI.
                            val notifyRegex = Regex("""<notify path="(/api/[^"]+)"""")
                            notifyRegex.findAll(text).forEach { match ->
                                val notifyPath = match.groupValues[1]
                                enqueueApi(notifyPath)
                            }
                            // Conservation des 3 dernières trames XML (console de diagnostic)
                            val frames = _lastXmlFrames.value.toMutableList()
                            frames.add(0, text.take(300))  // max 300 chars par trame
                            _lastXmlFrames.tryEmit(frames.take(3))
                        }
                        ZikProtocol.parseBatteryPercentFromXml(text)?.let { pct ->
                            // DIAGNOSTIC LED : l'app envoie uniquement GET /api/system/battery/get
                            // (aucun critical_threshold ni commande SET batterie n'est émis).
                            // Si la LED hardware est rouge à >40%, la source est le firmware
                            // du casque (calibration interne Parrot ZIK 3), pas l'application.
                            // Extrait XML brut pour corrélation :
                            val xmlSnippet = text.substringAfter("<battery").take(120).trim()
                            Log.i(TAG, "<< BATTERIE: $pct%  [raw=<battery$xmlSnippet]")
                            if (pct in 40..60) {
                                // Zone étrange : niveau mi-plein mais potentielle LED rouge casque.
                                // Vérification : aucun SET battery n'est envoyé, seul GET.
                                Log.w(TAG, "DIAG_LED: batterie=${pct}% — aucun threshold envoyé. LED rouge = firmware casque.")
                            }
                            lastReceivedMs["battery"] = System.currentTimeMillis()
                            _battery.tryEmit(pct)
                            try {
                                val prefs = getSharedPreferences("zik_prefs", Context.MODE_PRIVATE)
                                val showNotif = prefs.getBoolean("show_battery_notification", false)
                                val canNotify = NotificationManagerCompat.from(this@ZikBluetoothService).areNotificationsEnabled()
                                val permOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                else true
                                if (showNotif && canNotify && permOk) {
                                    NotificationManagerCompat.from(this@ZikBluetoothService).notify(NOTIF_ID, buildNotification(true, pct))
                                }
                            } catch (_: Exception) {}
                            checkAndSendBatteryAlert(pct)
                        }
                        // ── Validation ZikTruthTable avant émission ANC ────────────────────
                        // L'UI ne change d'état QUE si la réponse firmware est dans la table.
                        // Hors table → FIRMWARE_MISMATCH (log erreur, état inchangé).
                        // Entrée non confirmée par trace hex → FIRMWARE_UNCONFIRMED (warn).
                        run {
                            val rawType  = Regex("""type="([^"]+)"""").find(text)?.groupValues?.getOrNull(1) ?: ""
                            val rawValue = Regex("""<noise_control[^>]*\bvalue="(\d+)"""").find(text)?.groupValues?.getOrNull(1) ?: ""
                            if (rawType.isNotEmpty() && rawValue.isNotEmpty()) {
                                if (!ZikTruthTable.isValidAncResponse(rawType, rawValue)) {
                                    Log.e(TAG, "FIRMWARE_MISMATCH: noise_control type=\"$rawType\" value=\"$rawValue\" — hors zik_truth_table.json. État UI non mis à jour.")
                                    return@run  // bloquer l'émission
                                }
                                if (!ZikTruthTable.isHexConfirmed(rawType, rawValue)) {
                                    Log.w(TAG, "FIRMWARE_UNCONFIRMED: noise_control type=\"$rawType\" value=\"$rawValue\" — entrée non confirmée par trace hex (spec seul).")
                                }
                            }
                            ZikProtocol.parseAncModeFromXml(text)?.let { mode ->
                                lastReceivedMs["nc"] = System.currentTimeMillis()
                                if (System.currentTimeMillis() > ancSuppressUntilMs) {
                                    Log.i(TAG, "<< ANC MODE: $mode  [truth_table=${ZikTruthTable.ancLabelFor(rawType, rawValue) ?: "?"}]")
                                    _ancMode.tryEmit(mode)
                                } else {
                                }
                                // _ancEnabled est validé UNIQUEMENT par la réponse <noise_control enabled=...>
                                // ci-dessous (~800-1000ms après la commande SET) — pas ici.
                            }
                        }
                        // ── Validation ANC ON/OFF sur confirm firmware ─────────────────────────
                        // Le bouton ANC ne passe VERT que si le casque confirme <noise_control enabled="true"/>
                        // Respecte le délai de réponse ~800ms-1s observé sur le logcat S22 Ultra.
                        Regex("""<noise_control[^>]*\benabled="(true|false)"""")
                            .find(text)?.groups?.get(1)?.value?.let { v ->
                                val enabled = v == "true"
                                Log.i(TAG, "<< NC_ENABLED confirm firmware: $enabled")
                                _ancEnabled.tryEmit(enabled)     // ← seul endroit autorisé
                                val statusLine = if (enabled) "✔  NC activé (firmware)" else "✖  NC désactivé — passif réel"
                                _ncFirmwareStatus.tryEmit(statusLine)
                            }
                        ZikProtocol.parseNoiseReductionDbFromXml(text)?.let { db ->
                            Log.i(TAG, "<< NOISE dB: $db")
                            _noiseReductionDb.tryEmit(db)
                        }
                        ZikProtocol.parseThumbEqFromXml(text)?.let { eq ->
                            lastReceivedMs["eq"] = System.currentTimeMillis()
                            // Validation ZikTruthTable : r et theta dans les plages BTSnoop
                            if (!ZikTruthTable.isValidEqCoord(eq.x, eq.y)) {
                                Log.e(TAG, "FIRMWARE_MISMATCH: thumb_eq r=${eq.x} theta=${eq.y} — hors plage zik_truth_table.json [r:0-99, theta:0-359]. État UI non mis à jour.")
                            } else if (isEqSuppressed()) {
                            } else {
                                Log.i(TAG, "<< EQ THUMB: ${eq.toArgString()}")
                                _thumbEq.tryEmit(eq)
                            }
                        }
                        ZikProtocol.parseAutoNcFromXml(text)?.let { autoNcVal ->
                            Log.i(TAG, "<< AUTO_NC: $autoNcVal")
                            _autoNc.tryEmit(autoNcVal)
                        }
                        ZikProtocol.parseEqualizerEnabledFromXml(text)?.let { v ->
                            Log.i(TAG, "<< EQ_ENABLED: $v")
                            _eqEnabled.tryEmit(v)
                        }
                        ZikProtocol.parseSoundEffectEnabledFromXml(text)?.let { v ->
                            Log.i(TAG, "<< CONCERT_ENABLED: $v")
                            _concertEnabled.tryEmit(v)
                        }
                        ZikProtocol.parsePresenceSensorFromXml(text)?.let { v ->
                            lastReceivedMs["presence"] = System.currentTimeMillis()
                            Log.i(TAG, "<< PRESENCE_SENSOR: $v")
                            _presenceSensor.tryEmit(v)
                        }
                        ZikProtocol.parseAutoConnectionFromXml(text)?.let { v ->
                            _autoConnection.tryEmit(v)
                        }
                        ZikProtocol.parseTtsEnabledFromXml(text)?.let { v ->
                            _ttsEnabled.tryEmit(v)
                        }
                        ZikProtocol.parseFriendlyNameFromXml(text)?.let { name ->
                            _deviceName.tryEmit(name)
                        }
                        ZikProtocol.parsePhoneModeFromXml(text)?.let { mode ->
                            _phoneMode.tryEmit(mode)
                        }
                        ZikProtocol.parseFirmwareVersionFromXml(text)?.let { v ->
                            _firmwareVersion.tryEmit(v)
                        }
                        ZikProtocol.parseAutoPowerOffFromXml(text)?.let { v ->
                            _autoPowerOff.tryEmit(v)
                        }
                        // Parse état porté/retiré — format <pi value="1|0"/> et is_worn="true|false"
                        // VERROU SYSTÈME : si le capteur de présence est désactivé,
                        // les trames "retiré" (worn=false) sont IGNORÉES → la pause média ne se déclenche pas.
                        Regex("""<pi[^>]*\bvalue="(\d)"""").find(text)?.groups?.get(1)?.value?.let { v ->
                            val worn = v == "1"
                            if (worn || _presenceSensor.value == true) {
                                Log.i(TAG, "<< PRESENCE_WORN(pi): v=$v")
                                _presenceWorn.tryEmit(worn)
                            } else {
                            }
                        }
                        Regex("""\bis_worn="(true|false)"""").find(text)?.groups?.get(1)?.value?.let { v ->
                            val worn = v == "true"
                            if (worn || _presenceSensor.value == true) {
                                Log.i(TAG, "<< PRESENCE_WORN(is_worn): $v")
                                _presenceWorn.tryEmit(worn)
                            } else {
                            }
                        }
                        ZikProtocol.parseTrackMetadataFromXml(text)?.let { (playing, title, artist) ->
                            Log.i(TAG, "<< TRACK: \"$title\" - \"$artist\" (playing=$playing)")
                            _trackTitle.tryEmit(title)
                            _trackArtist.tryEmit(artist)
                        }
                        // flight_mode parsing supprimé — mode avion retiré
                        ZikProtocol.parseChargingFromXml(text)?.let { charging ->
                            _isCharging.tryEmit(charging)
                        }
                        Regex("<sound_effect[^>]*room_size=\\\"([^\"]+)\\\"")
                            .find(text)?.groups?.get(1)?.value?.let { v ->
                                Log.i(TAG, "<< ROOM SIZE=$v")
                                _roomSize.tryEmit(v)
                            }
                    }
                    val remaining = if (bytes.size > consumed) bytes.copyOfRange(consumed, bytes.size) else ByteArray(0)
                    baos.reset()
                    if (remaining.isNotEmpty()) baos.write(remaining)
                }
            } // fin while(true)
        } catch (e: Exception) {
            Log.w(TAG, "readLoop terminé : ${e.javaClass.simpleName} ${e.message}")
        } finally {
            Log.i(TAG, "readLoop finally: fermeture socket")
            _connected.tryEmit(false)
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            input = null
            output = null
        }
    }
}

