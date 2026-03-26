package com.rmdaye.ziker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.ui.res.stringResource

// ─── Palette ─────────────────────────────────────────────────────────────────
private val ZikBlack  = Color(0xFF000000)
private val CardBg    = Color(0xFF111111)
private val ZikOrange = Color(0xFFFF5722)  // Orange Parrot officiel
private val ZikDark   = Color(0xFF222222)
private val ZikWhite  = Color(0xFFFFFFFF)
private val ZikGray   = Color(0xFF9E9E9E)
private val ZikGreen  = Color(0xFF4CAF50)
private val ZikBlue   = Color(0xFF64B5F6)

// ─── Police Legacy Parrot ─────────────────────────────────────────────────────────────────
// Roboto Condensed — police originale de l'app Parrot Zik 2/3 (téléchargée via
// Google Fonts au premier lancement, mise en cache ensuite hors-ligne).
private val ZikFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)
private val _RcFont = GoogleFont("Roboto Condensed")
val RobotoCondensed = FontFamily(
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.Thin),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.ExtraLight),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.Light),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.Normal),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.Medium),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.Bold),
    GoogleFontItem(_RcFont, ZikFontProvider, FontWeight.ExtraBold)
)
// Police Parrot originale = Helvetica Neue UltraLight → remplacée par Roboto Thin
// Usage: fontWeight = FontWeight.Thin ou FontWeight.ExtraLight

// ─── ZIK SKINS ───────────────────────────────────────────────────────────────
private data class ZikSkin(
    val key           : String,
    val label         : String,
    val background    : Color,   // fond général
    val cardBg        : Color,   // cartes / lignes de liste
    val dark          : Color,   // contrôles inactifs / fonds secondaires
    val accent        : Color,   // orange Parrot — état actif
    val textPrimary   : Color,   // texte principal
    val textSecondary : Color,   // labels sous-titres, icônes inactives
    val onSkin        : Color,   // = textPrimary (swatches)
    val navBg         : Color,   // fond barre de navigation
)
// 11 thèmes Parrot officiels — couleurs extraites de colors.xml / styles.xml décompilés
// bg_color, bg_color_light, wheel_unselected, theme_second_color, text_main_color, textSecondary, onSkin, actionbar bg
private val ZIK_SKINS = listOf(
    //                                    background          cardBg(light)       dark(unsel)         accent(second)      textPrimary         textSecondary       onSkin              navBg
    ZikSkin("BLACK",   "Black (Parrot)",  Color(0xFF000000), Color(0xFF121212), Color(0xFF313131), Color(0xFFFF8429), Color(0xFFFFFFFF), Color(0xFF757575), Color(0xFFFFFFFF), Color(0xFF000000)),
    ZikSkin("BLUE",    "Blue (Parrot)",   Color(0xFF375FBE), Color(0xFF0D399C), Color(0xFF698BAA), Color(0xFF7BD1FF), Color(0xFFFFFFFF), Color(0xFF9EBBDA), Color(0xFFFFFFFF), Color(0xFF406DD0)),
    ZikSkin("MOCHA",   "Mocha (Parrot)",  Color(0xFF9F8061), Color(0xFFA79078), Color(0xFFC49E7D), Color(0xFFFFB434), Color(0xFFFFFFFF), Color(0xFFD4C0A8), Color(0xFFFFFFFF), Color(0xFFB7967B)),
    ZikSkin("ORANGE",  "Orange (Parrot)", Color(0xFFFD7E37), Color(0xFFF28548), Color(0xFFF5A57E), Color(0xFFB02601), Color(0xFFFFFFFF), Color(0xFFFFD0B0), Color(0xFFFFFFFF), Color(0xFFFD7C36)),
    ZikSkin("WHITE",   "White (Parrot)",  Color(0xFFFFFFFF), Color(0xFFCECECE), Color(0xFFDEDEDE), Color(0xFFFF9635), Color(0xFF000000), Color(0xFF757575), Color(0xFF000000), Color(0xFFE8E8E8)),
    ZikSkin("YELLOW",  "Yellow (Parrot)", Color(0xFFFFDD61), Color(0xFFFDD95F), Color(0xFFE6C248), Color(0xFFD43E25), Color(0xFF000000), Color(0xFF7A6530), Color(0xFF000000), Color(0xFFFFD851)),
    ZikSkin("GREEN",   "Green (Parrot)",  Color(0xFF22533B), Color(0xFF113925), Color(0xFF313131), Color(0xFFFF8429), Color(0xFFFFFFFF), Color(0xFF80A090), Color(0xFFFFFFFF), Color(0xFF22533B)),
    ZikSkin("RED",     "Red (Parrot)",    Color(0xFFA81C20), Color(0xFF5B0003), Color(0xFF313131), Color(0xFFFF8429), Color(0xFFFFFFFF), Color(0xFFE09090), Color(0xFFFFFFFF), Color(0xFFA81C20)),
    ZikSkin("IVORY",   "Ivory (Parrot)",  Color(0xFFF7E3CD), Color(0xFFDAC0A2), Color(0xFFDEDEDE), Color(0xFFFF9635), Color(0xFF000000), Color(0xFF8A7560), Color(0xFF000000), Color(0xFFF7E3CD)),
    ZikSkin("CAMEL",   "Camel (Parrot)",  Color(0xFFCF8248), Color(0xFF91501F), Color(0xFF313131), Color(0xFFFF8429), Color(0xFFFFFFFF), Color(0xFFE0B890), Color(0xFFFFFFFF), Color(0xFFCF8248)),
    ZikSkin("BROWN",   "Brown (Parrot)",  Color(0xFF4D2911), Color(0xFF6F3D1A), Color(0xFF313131), Color(0xFFFF8429), Color(0xFFFFFFFF), Color(0xFFA08060), Color(0xFFFFFFFF), Color(0xFF4D2911)),
)
/** Propagation du thème dans l'arbre Compose sans threading de paramètres */
private val LocalZikSkin = compositionLocalOf { ZIK_SKINS[0] }
private val LocalDemoMode = compositionLocalOf { false }

// ─── Tab meta ────────────────────────────────────────────────────────────────
private data class ZikTab(val icon: ImageVector, val label: String)
private val TABS = listOf(
    ZikTab(Icons.Default.Dashboard,  "Dashboard"),
    ZikTab(Icons.Default.VolumeUp,   "Bruit"),
    ZikTab(Icons.Default.Equalizer,  "EQ"),
    ZikTab(Icons.Default.MusicNote,  "Concert"),
    ZikTab(Icons.Default.Settings,   "Réglages"),
)

// ─── NestedScrollConnection qui consomme le scroll horizontal ──────────────────
private val NoHorizontalScroll = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f) // consomme tout le delta X
    override suspend fun onPreFling(available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

// ─────────────────────────────────────────────────────────────────────────────
//  MainScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MainScreen(viewModel: ZikViewModel) {
    val context          = LocalContext.current
    val battery          by viewModel.battery.collectAsState()
    val isCharging       by viewModel.isCharging.collectAsState()
    val trackTitle       by viewModel.trackTitle.collectAsState()
    val trackArtist      by viewModel.trackArtist.collectAsState()
    val connected        by viewModel.connected.collectAsState()
    val searchStatus     by viewModel.searchStatus.collectAsState()
    val ancMode          by viewModel.ancMode.collectAsState()
    val roomSize         by viewModel.roomSize.collectAsState()
    val concertAngle     by viewModel.concertAngle.collectAsState()
    val eqEnabled        by viewModel.eqEnabled.collectAsState()
    val concertEnabled   by viewModel.concertEnabled.collectAsState()
    val macAddress       by viewModel.macAddress.collectAsState()
    val firmwareVersion  by viewModel.firmwareVersion.collectAsState()
    val scannedDevices   by viewModel.scannedDevices.collectAsState()
    val isScanning       by viewModel.isScanning.collectAsState()
    val targetDeviceAddress by viewModel.targetDeviceAddress.collectAsState()
    val liveLogs         by viewModel.liveLogs.collectAsState()
    val demoMode         by viewModel.demoMode.collectAsState()
    val forceStartupPicker by viewModel.forceStartupPicker.collectAsState()
    val startupFlowToken by viewModel.startupFlowToken.collectAsState()
    val latestConnectivityLog = remember(liveLogs) {
        liveLogs.asReversed().firstOrNull { line ->
            line.contains("[BT]") ||
            line.contains("[HAND]") ||
            line.contains("[LOOP]") ||
            line.contains("[✓ OK]") ||
            line.contains("[✗ DC]") ||
            line.contains("[✗ ERR]")
        }
    }

    val btPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else emptyArray()

    val btAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var btEnabled by remember { mutableStateOf(btAdapter?.isEnabled == true) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) btEnabled = btAdapter?.isEnabled == true
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    var hasPermission by remember {
        mutableStateOf(btPerms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r -> hasPermission = r.values.all { it } }

    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { btEnabled = btAdapter?.isEnabled == true }

    val skinKey     by viewModel.skinKey.collectAsState()
    val currentSkin  = ZIK_SKINS.firstOrNull { it.key == skinKey } ?: ZIK_SKINS[0]

    LaunchedEffect(hasPermission, btEnabled) {
        if (hasPermission && !btEnabled)
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    CompositionLocalProvider(LocalZikSkin provides currentSkin) {
        Box(Modifier.fillMaxSize().background(currentSkin.background)) {
            when {
                demoMode -> DemoSlideshowScreen(
                    onExitDemo = { viewModel.exitDemoModeFromDrawer() }
                )
                !hasPermission -> PermissionScreen { permLauncher.launch(btPerms) }
                !btEnabled     -> BluetoothOffScreen { btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                !connected || forceStartupPicker -> DevicePickerScreen(
                    viewModel      = viewModel,
                    status         = searchStatus,
                    scannedDevices = scannedDevices,
                    isScanning     = isScanning,
                    targetDeviceAddress = targetDeviceAddress,
                    latestLog = latestConnectivityLog,
                    startupToken = startupFlowToken
                )
                else           -> ConnectedPager(
                    viewModel, battery, isCharging, trackTitle, trackArtist,
                    ancMode, roomSize, concertAngle,
                    eqEnabled, concertEnabled, macAddress, firmwareVersion
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DemoSlideshowScreen(onExitDemo: () -> Unit) {
    val t = LocalZikSkin.current
    val slides = listOf(
        R.drawable.demo_slide_1,
        R.drawable.demo_slide_2,
        R.drawable.demo_slide_3,
        R.drawable.demo_slide_4,
        R.drawable.demo_slide_5,
        R.drawable.demo_slide_6,
        R.drawable.demo_slide_7,
        R.drawable.demo_slide_8,
        R.drawable.demo_slide_9,
        R.drawable.demo_slide_10
    )
    val pagerState = rememberPagerState(pageCount = { slides.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.background)
    ) {
        ParrotTopBar(title = "Mode Démo")

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                Image(
                    painter = painterResource(id = slides[page]),
                    contentDescription = "Capture démonstration ${page + 1}",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            }

        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                slides.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (index == pagerState.currentPage) t.accent else t.textSecondary.copy(alpha = 0.35f))
                    )
                }
            }
        }

        Button(
            onClick = onExitDemo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = t.accent)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color(0xFF1A1A1A))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Quitter mode démo",
                color = Color(0xFF1A1A1A),
                fontFamily = RobotoCondensed,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(10.dp))
    }
}
// ─────────────────────────────────────────────────────────────────────────────
//  Composant bouton reutilisable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ZikButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalZikSkin.current
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(backgroundColor = t.accent)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color(0xFF1A1A1A),
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color(0xFF1A1A1A), fontWeight = FontWeight.Bold)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    val t = LocalZikSkin.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Bluetooth, null, tint = t.accent, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("Permissions Bluetooth requises pour détecter votre Parrot Zik.",
            color = t.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        ZikButton("Accorder les permissions", onClick = onRequest)
    }
}

@Composable
private fun BluetoothOffScreen(onEnable: () -> Unit) {
    val t = LocalZikSkin.current
    Column(Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.BluetoothDisabled, null, tint = t.accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text("Bluetooth désactivé", color = t.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("L'app se connectera automatiquement dès qu'il sera activé.",
            color = t.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp))
        Spacer(Modifier.height(28.dp))
        ZikButton("Activer le Bluetooth", icon = Icons.Default.Bluetooth, onClick = onEnable,
            modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth().height(48.dp))
    }
}

@Composable
private fun DevicePickerScreen(
    viewModel: ZikViewModel,
    status: String,
    scannedDevices: List<ZikDeviceInfo>,
    isScanning: Boolean,
    targetDeviceAddress: String?,
    latestLog: String?,
    startupToken: Int
) {
    // Appareils déjà appairés (lecture synchrone, stable)
    val bondedDevices = viewModel.bondedZikDevices()
    val latestLogMessage = remember(latestLog) {
        latestLog
            ?.substringAfter("] ", latestLog)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
    val connectionMessage = remember(status, latestLogMessage, targetDeviceAddress) {
        when {
            targetDeviceAddress != null && latestLogMessage != null -> latestLogMessage
            status.isNotBlank() -> status
            else -> "En attente d'un casque…"
        }
    }

    // Au premier affichage : démarrer le service + connexion
    LaunchedEffect(startupToken) {
        viewModel.ensureRunning()   // CRITIQUE : démarre + lie ZikBluetoothService

        // ── Stratégie de connexion automatique ────────────────────────────────
        //  1. MAC mémorisé depuis la dernière session (priorité absolue)
        //  2. Premier appareil bondé "Zik" ou "Parrot" trouvé
        //  3. Scan actif si aucun appareil bondé trouvé
        val savedMac = viewModel.getSavedDeviceMac()
        when {
            savedMac != null -> {
                // MAC connu → se reconnecter directement au même casque
                viewModel.connectToAddress(savedMac)
            }
            bondedDevices.isNotEmpty() -> {
                // Appareil bondé connu → pas de discovery (interfère avec RFCOMM)
                viewModel.connectToAddress(bondedDevices.first().address)
            }
            else -> {
                // Aucun appareil bondé → scan actif pour en découvrir
                viewModel.startScan()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val t = LocalZikSkin.current
            val isDarkText = t.textPrimary == Color(0xFF000000)
            val logPanelBg = if (isDarkText)
                colorLerp(t.cardBg, Color.White, 0.35f)
            else
                colorLerp(t.cardBg, Color.White, 0.10f)
            val logPanelBorder = if (isDarkText)
                t.accent.copy(alpha = 0.24f)
            else
                t.accent.copy(alpha = 0.36f)
            val logLabelColor = if (isDarkText)
                t.textPrimary.copy(alpha = 0.72f)
            else
                t.textSecondary.copy(alpha = 0.9f)
            val logTextColor = if (isDarkText)
                Color(0xFF111111)
            else
                t.textPrimary

            Icon(
                Icons.Default.Headset,
                contentDescription = null,
                tint = t.accent.copy(alpha = 0.9f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(18.dp))
            Text("Parrot ZIK", color = t.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(logPanelBg)
                    .border(1.dp, logPanelBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (targetDeviceAddress != null) {
                        CircularProgressIndicator(
                            color = t.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = "Journal connexion",
                        color = logLabelColor,
                        fontSize = 10.sp,
                        fontFamily = RobotoCondensed,
                        letterSpacing = 0.6.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = connectionMessage,
                    color = logTextColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = LocalZikSkin.current.cardBg,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            elevation = 12.dp
        ) {
            DevicePickerContent(
                bondedDevices  = bondedDevices,
                scannedDevices = scannedDevices,
                isScanning     = isScanning,
                connectingAddress = targetDeviceAddress,
                onDeviceTap    = { device -> viewModel.connectToAddress(device.address) },
                onDemoClicked  = { viewModel.enableDemoMode() }
            )
        }
    }
}

@Composable
private fun DevicePickerContent(
    bondedDevices: List<ZikDeviceInfo>,
    scannedDevices: List<ZikDeviceInfo>,
    isScanning: Boolean,
    connectingAddress: String?,
    onDeviceTap: (ZikDeviceInfo) -> Unit,
    onDemoClicked: () -> Unit
) {
    val t = LocalZikSkin.current
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        // En-tête
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)) {
            Icon(Icons.Default.Bluetooth, null, tint = t.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Appareils Parrot Zik", color = t.textPrimary, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (isScanning && connectingAddress == null) {
                CircularProgressIndicator(color = t.accent,
                    modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Appareils appairés (connexion automatique + tap manuel)
        if (bondedDevices.isNotEmpty()) {
            Text("DÉJÀ APPAIRÉS", color = t.textSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            bondedDevices.forEach { device ->
                DeviceRow(device = device, onClick = { onDeviceTap(device) },
                    isConnecting = connectingAddress == device.address)
                Spacer(Modifier.height(6.dp))
            }
        }

        // Appareils découverts (non encore appairés)
        val newDevices = scannedDevices.filter { s ->
            bondedDevices.none { b -> b.address == s.address }
        }
        if (newDevices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("DÉCOUVERTS", color = t.textSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            newDevices.forEach { device ->
                DeviceRow(device = device, onClick = { onDeviceTap(device) },
                    isConnecting = connectingAddress == device.address)
                Spacer(Modifier.height(6.dp))
            }
        }

        if (bondedDevices.isEmpty() && scannedDevices.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (isScanning) "Recherche en cours…" else "Aucun appareil Zik trouvé. Vérifiez que le casque est allumé.",
                color = t.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // Mode Démo — bouton discret
        val t2 = LocalZikSkin.current
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = onDemoClicked,
                modifier = Modifier
                    .height(40.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = t2.accent.copy(alpha = 0.16f)),
                elevation = ButtonDefaults.elevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                ),
                border = BorderStroke(1.dp, t2.accent.copy(alpha = 0.28f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Visibility, null,
                    tint = t2.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Mode Démo",
                    color = t2.accent,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    fontFamily = RobotoCondensed
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Footer (resource string)
        val footer = stringResource(id = R.string.footer_text)
        Text(
            text = footer,
            color = t.textSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DeviceRow(
    device: ZikDeviceInfo,
    onClick: () -> Unit,
    isConnecting: Boolean = false
) {
    val t = LocalZikSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isConnecting) t.accent.copy(alpha = 0.08f) else t.dark)
            .border(
                width = if (isConnecting) 1.dp else 0.dp,
                color = if (isConnecting) t.accent.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Headset, null,
            tint = if (isConnecting) t.accent else t.textSecondary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, color = t.textPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(device.address, color = t.textSecondary, fontSize = 10.sp)
        }
        if (isConnecting) {
            CircularProgressIndicator(
                color = t.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(Icons.Default.ArrowForwardIos, null, tint = t.textSecondary,
                modifier = Modifier.size(14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAGER CONNECTÉ
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectedPager(
    viewModel: ZikViewModel,
    battery: Int?, isCharging: Boolean,
    trackTitle: String?, trackArtist: String?,
    ancMode: ZikProtocol.NoiseControlMode?,
    roomSize: String?, concertAngle: Int,
    eqEnabled: Boolean?,
    concertEnabled: Boolean?,
    macAddress: String,
    firmwareVersion: String
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()
    val demoMode by viewModel.demoMode.collectAsState()

    // Bloque le swipe du Pager quand l'utilisateur touche le pad EQ
    val isUserInteracting by viewModel.isUserInteracting.collectAsState()

    // ── État casque pour le drawer ────────────────────────────────────────────
    val deviceNameDrawer  by viewModel.deviceName.collectAsState()
    val autoPowerOffVal   by viewModel.autoPowerOff.collectAsState()
    val autoConnectionVal by viewModel.autoConnection.collectAsState()
    val ttsEnabledVal     by viewModel.ttsEnabled.collectAsState()
    val liveLogs          by viewModel.liveLogs.collectAsState()
    val presenceSensorVal by viewModel.presenceSensor.collectAsState()
    val presenceWornVal   by viewModel.presenceWorn.collectAsState()
    val phoneModeVal      by viewModel.phoneMode.collectAsState()

    // ── Kill-switch batterie (Dashboard page 0 uniquement) ───────────────────
    // Règle :
    //  • Intervalle normal   : 30 s
    //  • Dès qu'une interaction ANC/EQ démarre  : suspend le poll
    //  • Après la fin de l'interaction : attend 60 s supplémentaires avant de
    //    reprendre, pour ne pas polluer le bus BT pendant la stabilisation DSP
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 0) {
            while (currentCoroutineContext().isActive) {
                if (viewModel.isUserInteracting.value) {
                    // Attendre la fin de l'interaction…
                    while (viewModel.isUserInteracting.value && currentCoroutineContext().isActive) {
                        delay(500L)
                    }
                    // …puis 60 s supplémentaires (DSP stabilisation)
                    delay(60_000L)
                }
                viewModel.fetchBatteryOnce()
                delay(30_000L)
            }
        }
    }

    val drawerState  = rememberDrawerState(DrawerValue.Closed)
    val onMenuOpen: () -> Unit = { scope.launch { drawerState.open() } }

    CompositionLocalProvider(LocalDemoMode provides demoMode) {
            ModalDrawer(
        drawerState          = drawerState,
        // Toujours activer les gestes quand le drawer est ouvert (pour pouvoir le fermer
        // par swipe même sur la page EQ ou après une interaction).
        gesturesEnabled      = drawerState.isOpen || (!isUserInteracting && pagerState.currentPage != 2),
        drawerBackgroundColor = LocalZikSkin.current.cardBg,
        scrimColor           = Color.Black.copy(alpha = 0.55f),
        drawerShape          = RoundedCornerShape(0.dp),
        drawerContent = {
            ZikDrawerContent(
                deviceName      = deviceNameDrawer.ifBlank { "Parrot ZIK 3" },
                firmwareVersion = firmwareVersion,
                macAddress      = macAddress,
                battery         = battery,
                isCharging      = isCharging,
                autoPowerOff    = autoPowerOffVal,
                autoConnection  = autoConnectionVal,
                ttsEnabled      = ttsEnabledVal,
                ancMode         = ancMode,
                eqEnabled       = eqEnabled,
                concertEnabled  = concertEnabled,
                presenceSensor  = presenceSensorVal,
                presenceWorn    = presenceWornVal,
                phoneMode       = phoneModeVal,
                liveLogs        = liveLogs,
                currentPage     = pagerState.currentPage,
                onNavigate      = { idx ->
                    scope.launch { drawerState.close() }
                    scope.launch { pagerState.animateScrollToPage(idx) }
                },
                onReconnectHeadset = {
                    viewModel.requestHeadsetReconnectFromDrawer()
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                onQuitDemo = {
                    viewModel.exitDemoModeFromDrawer()
                    scope.launch { pagerState.animateScrollToPage(0) }
                },
                onDismiss       = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false   // swipe désactivé — navigation par ZikNavBar uniquement
            ) { page ->
                when (page) {
                    0 -> PageDashboard(
                            battery, isCharging, trackTitle, trackArtist,
                            ancMode,
                            ancEnabled = viewModel.ancEnabled.collectAsState().value,
                            eqEnabled, concertEnabled,
                            presenceSensor = viewModel.presenceSensor.collectAsState().value,
                            presenceWorn    = viewModel.presenceWorn.collectAsState().value,
                            macAddress      = macAddress,
                            firmwareVersion = firmwareVersion,
                            onNavigate = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                            onToggleBruit   = { on -> viewModel.setNoiseControlMode(if (on) ZikProtocol.NoiseControlMode.ANC else ZikProtocol.NoiseControlMode.OFF) },
                            onToggleEq      = { on -> viewModel.setEqEnabled(on) },
                            onToggleConcert = { on -> viewModel.setConcertHallEnabled(on) },
                            onTogglePresence = { on -> viewModel.setPresenceSensor(on) },
                            onMenuOpen       = onMenuOpen
                        )
                    1 -> PageNoiseControl(
                            ancMode = ancMode,
                            viewModel = viewModel,
                            isVisible = pagerState.currentPage == 1,
                            onMenuOpen = onMenuOpen
                        )
                    2 -> PageEqualizer(viewModel, onMenuOpen = onMenuOpen)
                    3 -> PageConcertHall(roomSize, concertAngle, concertEnabled, viewModel, onMenuOpen = onMenuOpen)
                    4 -> PageSettings(
                            battery         = battery,
                            isCharging      = isCharging,
                            macAddress      = macAddress,
                            presenceSensor  = viewModel.presenceSensor.collectAsState().value,
                            onTogglePresence = { on -> viewModel.setPresenceSensor(on) },
                            skinKey         = viewModel.skinKey.collectAsState().value,
                            onSkinPick      = { key -> viewModel.setSkin(key) },
                            viewModel       = viewModel,
                            onMenuOpen       = onMenuOpen
                        )
                    // Logs page removed per UI refactor
                    else -> {}
                }
            }
            ZikNavBar(
                currentPage   = pagerState.currentPage,
                totalPages    = 5,
                onTabSelected = { idx -> scope.launch { pagerState.animateScrollToPage(idx) } }
            )
            // Espace pour la barre de navigation système (gesture bar / boutons soft)
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PARROT ACTION BAR — Commune à toutes les pages
//  Ref: action_bar_container [0,75][1080,219]  ActionBar = 144px = 48dp
//  menu_button [0,94][150,199]  → hamburger 50dp×48dp gauche
//  action_bar_title centré      → Roboto Condensed bold 18sp, couleur beige
//  indicator   [x,176][x,191]   → liseré 2dp, or #D4A843, sous titre
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ParrotTopBar(
    title: String,
    trailingContent: @Composable (() -> Unit)? = null,
    onMenuClick: () -> Unit = {}
) {
    val t = LocalZikSkin.current
    val demoMode = LocalDemoMode.current
    val appName = stringResource(id = R.string.app_name)
    val showBrandBadge = !title.equals(appName, ignoreCase = true)
    Column(Modifier.fillMaxWidth().background(t.navBg)) {
    // Espace pour la status bar du téléphone
    Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        // ── Hamburger gauche ────────────────────────────────────────────────
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(width = 50.dp, height = 48.dp)
                .clickable { onMenuClick() },
            contentAlignment = Alignment.Center
        ) {
            val lineColor = t.textPrimary
            Canvas(Modifier.size(22.dp, 14.dp)) {
                listOf(0f, size.height / 2f, size.height).forEach { y ->
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.5f)
                }
            }
        }
        // ── Titre bicolore (original: "Parrot" blanc + "Zik" accent) ──────
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showBrandBadge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.zikker_round),
                        contentDescription = appName,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        appName,
                        color = t.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = RobotoCondensed,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            Text(
                buildAnnotatedString {
                    title.split(" ").forEachIndexed { i, word ->
                        if (i > 0) append(" ")
                        if (word.equals("Zik", ignoreCase = true)) {
                            withStyle(SpanStyle(color = t.accent)) { append(word) }
                        } else {
                            withStyle(SpanStyle(color = t.textPrimary)) { append(word) }
                        }
                    }
                },
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RobotoCondensed
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.width(44.dp).height(2.dp).background(t.accent))
        }
        // ── Trailing (AUTO chip, etc.) ──────────────────────────────────────
        if (trailingContent != null) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
            ) { trailingContent() }
        } else if (demoMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                color = t.accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, t.accent.copy(alpha = 0.40f)),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    "MODE DEMO",
                    color = t.accent,
                    fontSize = 10.sp,
                    fontFamily = RobotoCondensed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
    } // Column
}

// ─────────────────────────────────────────────────────────────────────────────
//  BARRE ON AIR PRESET — Commune à NC / EQ / Concert (pas Settings)
//  Ref: bottom_toolbar_button [0,1710][1080,1920] = 210px = 70dp
//  on_air_preset [45,1763][316,1806]  presetName [45,1806][804,1867]
//  addPreset btn [915,1725][1050,1905]
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ParrotPresetBar(onAddPreset: () -> Unit = {}) {
    val t = LocalZikSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(t.background)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "On air preset",
                color = t.textSecondary, fontSize = 9.sp,
                letterSpacing = 1.6.sp, fontFamily = RobotoCondensed,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Pas de preset sélectionné",
                color = t.textPrimary, fontSize = 14.sp,
                fontFamily = RobotoCondensed, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier.size(44.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { onAddPreset() }) },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.width * 0.22f
                drawRoundRect(color = t.dark, cornerRadius = CornerRadius(r))
                drawRoundRect(color = t.textPrimary.copy(alpha = 0.12f),
                    cornerRadius = CornerRadius(r), style = Stroke(width = size.width * 0.05f))
                val hw = size.width * 0.22f; val lw = size.width * 0.065f
                drawLine(t.accent, Offset(size.width/2f - hw, size.height/2f),
                    Offset(size.width/2f + hw, size.height/2f), strokeWidth = lw)
                drawLine(t.accent, Offset(size.width/2f, size.height/2f - hw),
                    Offset(size.width/2f, size.height/2f + hw), strokeWidth = lw)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 0 — DASHBOARD  (ref: zik_ref_dashboard.xml 1080×1920)
//  progressBarTwo  = [165,491][915,1241]  → wheel diam 750px = 69.4% screen
//  buttons_container = [0,1410][1080,1710] → 3 zones, toggle icon 70dp
//  bottom_toolbar = [0,1710][1080,1920]  → ON AIR PRESET bar, 70dp
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageDashboard(
    battery: Int?, isCharging: Boolean,
    trackTitle: String?, trackArtist: String?,
    ancMode: ZikProtocol.NoiseControlMode?,
    ancEnabled: Boolean?,
    eqEnabled: Boolean?,
    concertEnabled: Boolean?,
    presenceSensor: Boolean?,
    presenceWorn: Boolean?,
    macAddress: String,
    firmwareVersion: String,
    onNavigate: (Int) -> Unit,
    onToggleBruit: (Boolean) -> Unit,
    onToggleEq: (Boolean) -> Unit,
    onToggleConcert: (Boolean) -> Unit,
    onTogglePresence: (Boolean) -> Unit,
    onMenuOpen: () -> Unit = {}
) {
    val t                = LocalZikSkin.current
    val hasActiveAnc     = ancMode != null && ancMode != ZikProtocol.NoiseControlMode.OFF
    val hasActiveEq      = eqEnabled == true
    val hasActiveConcert = concertEnabled == true

    Column(Modifier.fillMaxSize()) {

        // ── ActionBar app name
        ParrotTopBar(title = stringResource(id = R.string.app_name), onMenuClick = onMenuOpen)

        // ── Barre metadata musique (ref lnMetadataTop [0,219][1080,322] = 103px = 34dp) ─
        // imgPlay [30,250][58,290]  tvSong [133,240][1080,301]
        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(t.cardBg)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône play (ref imgPlay 28×40px ≈ 9×13dp @ 3x density)
            Box(
                Modifier
                    .size(width = 9.dp, height = 13.dp)
                    .background(t.dark, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(6.dp, 8.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close()
                    }
                    drawPath(path, color = t.textSecondary)
                }
            }
            Spacer(Modifier.width(10.dp))
            // tvArtist hidden when blank (ref [103,244][133,297] very narrow)
            if (!trackArtist.isNullOrBlank()) {
                Text(
                    trackArtist, color = t.textSecondary,
                    fontSize = 10.sp, fontFamily = RobotoCondensed,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.28f)
                )
            }
            Text(
                when {
                    isCharging && (trackTitle.isNullOrBlank()) -> "\u26A1 En charge"
                    isCharging -> "\u26A1 $trackTitle"
                    !trackTitle.isNullOrBlank() -> trackTitle
                    else -> "En attente de lecture"
                } + if (firmwareVersion.isNotBlank()) "  \u2022 FW $firmwareVersion" else "",
                color = t.textPrimary, fontSize = 13.sp, fontFamily = RobotoCondensed,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Roue batterie (ref wheel_container [0,322][1080,1410]) ─────────────────
        // progressBarTwo = [165,491][915,1241] → diam 750px / 1080px = 69.4%
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            BatteryCircle(battery = battery, isCharging = isCharging)
        }

        // ── 3 boutons Parrot (ref buttons_container [0,1410][1080,1710] = 100dp) ─────
        // Chaque zone : anc=[67-337] eq=[367-697] phc=[742-1012]
        // Icon toggle : 70dp × 70dp   Label : Roboto Condensed
        Row(
            Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ParrotTabButton(
                label = "Contrôle\ndu bruit", icon = Icons.Default.VolumeUp,
                active = hasActiveAnc, onToggle = onToggleBruit,
                onNavigate = { onNavigate(1) },
                modifier = Modifier.weight(1f)
            )
            ParrotTabButton(
                label = "Egaliseur", icon = Icons.Default.Equalizer,
                active = hasActiveEq, onToggle = onToggleEq,
                onNavigate = { onNavigate(2) },
                modifier = Modifier.weight(1f)
            )
            ParrotTabButton(
                label = "Concert\nHall", icon = Icons.Default.MusicNote,
                active = hasActiveConcert, onToggle = onToggleConcert,
                onNavigate = { onNavigate(3) },
                modifier = Modifier.weight(1f)
            )
        }

    }
}

// ─── Bouton onglet style Parrot legacy — ToggleButton ON/OFF ─────────────────
// Ref : app Parrot originale = ToggleButton (checkable=true)  — tap = ON/OFF
// Long-press = navigue vers la page correspondante
@Composable
private fun ParrotTabButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalZikSkin.current
    val iconColor = if (active) t.accent else t.textSecondary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(active) {
                detectTapGestures(
                    onTap       = { onToggle(!active) },
                    onLongPress = { onNavigate() }
                )
            }
            .padding(top = 4.dp, bottom = 2.dp)
    ) {
        // Icône — compacte
        Icon(
            icon, label,
            tint = iconColor,
            modifier = Modifier.size(30.dp)
        )

        Spacer(Modifier.height(2.dp))

        // ── Label ────────────────────────────────────────────────────
        Text(
            label,
            color      = iconColor,
            fontSize   = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign  = TextAlign.Center,
            lineHeight = 12.sp,
            fontFamily = RobotoCondensed
        )

        Spacer(Modifier.height(1.dp))

        // ── Indicateur ON/OFF compact ────────────────────────────────
        Text(
            if (active) "ON" else "OFF",
            color      = if (active) t.accent else t.textSecondary.copy(alpha = 0.50f),
            fontSize   = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RobotoCondensed
        )
    }
}

// ─── Roue batterie style Parrot legacy — Canvas tirets (199 dashes)  ─────────
// Source : ProgressWheel.java décompilé
// 199 tirets DashPathEffect, arc commence à -90° (haut), sens antihoraire
// Icône batterie bitmap-like en haut du cercle, texte % centré (Helvetica UltraLight → Roboto Condensed Light)
// Couleur rim = wheel_color_unselected (theme), bar = wheel_color_selected (theme)
@Composable
private fun BatteryCircle(battery: Int?, isCharging: Boolean) {
    val t = LocalZikSkin.current

    // ── Couleur Parrot originale : wheel_color_selected du thème actif ────────
    // Pas de couleur spéciale pour charging (original: même couleur, seul l'icône clignote)
    val battColor = when {
        (battery ?: 101) < 15 -> Color(0xFFEF5350)            // rouge critique (ajout sécu)
        (battery ?: 101) < 30 -> Color(0xFFFF8A65)            // orange faible (ajout sécu)
        else                  -> t.accent                      // wheel_color_selected = thème
    }

    // Animation clignotement charge (original : alpha 0→255, 1000ms, REVERSE INFINITE)
    val infCharge = rememberInfiniteTransition(label = "charge")
    val iconAlpha by infCharge.animateFloat(
        initialValue  = if (isCharging) 0f else 1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "iconAlpha"
    )

    // fillMaxWidth(0.694f) = 750/1080 (ref progressBarTwo)
    Box(
        Modifier
            .fillMaxWidth(0.694f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val W    = size.width
            val H    = size.height
            val cx   = W / 2f
            val cy   = H / 2f
            val rimW = H * 0.045f  // rimWidth / height ≈ 20/444

            // ── 1. 199 dashes : périmètre = π*H, dashOn=4px, gap=calculé ──
            val perimeter = (PI * H).toFloat()
            val dashOn    = 4f * (H / 444f)  // scalé proportionnellement
            val dashOff   = (perimeter - 199f * dashOn) / 199f

            val rimRadius = H / 2f - rimW / 2f

            // ── 2. Fond : cercle complet de tirets (unselected) ──────────
            // Couleur : wheel_color_unselected → textSecondary.copy(0.25)
            val unselColor = t.textSecondary.copy(alpha = 0.25f)
            val selColor   = battColor

            // Tirets individuels à la main (DashPathEffect pas dispo Compose)
            val dashAngle = (dashOn / perimeter) * 360f
            val gapAngle  = (dashOff / perimeter) * 360f
            val totalStep = dashAngle + gapAngle

            // Fond : 199 tirets gris sur tout le tour
            for (i in 0 until 199) {
                val startA = -90f + i * totalStep
                drawArc(
                    color     = unselColor,
                    startAngle = startA,
                    sweepAngle = dashAngle,
                    useCenter = false,
                    topLeft   = Offset(cx - rimRadius, cy - rimRadius),
                    size      = Size(rimRadius * 2f, rimRadius * 2f),
                    style     = Stroke(width = rimW, cap = StrokeCap.Butt)
                )
            }

            // ── 3. Arc de progression coloré (du haut, antihoraire = négatif) ──
            // Original : drawArc(circleBounds, -90, -progress_degrees, ...)
            // progress_degrees = percent * 3.6
            val norm = ((battery ?: 0) / 100f).coerceIn(0f, 1f)
            val progressDeg = norm * 360f
            // Tirets colorés qui couvrent l'arc de progression
            if (norm > 0f) {
                for (i in 0 until 199) {
                    val startA = -90f - i * totalStep  // sens antihoraire
                    val covered = i * totalStep
                    if (covered > progressDeg) break
                    drawArc(
                        color     = selColor,
                        startAngle = startA - dashAngle,
                        sweepAngle = dashAngle,
                        useCenter = false,
                        topLeft   = Offset(cx - rimRadius, cy - rimRadius),
                        size      = Size(rimRadius * 2f, rimRadius * 2f),
                        style     = Stroke(width = rimW, cap = StrokeCap.Butt)
                    )
                }
            }

            // ── 4. Contour semi-transparent (original: 0xAA000000) ──────
            drawCircle(
                color  = Color(0xAA000000),
                radius = rimRadius + rimW / 2f + 1f,
                center = Offset(cx, cy),
                style  = Stroke(width = 1.5f)
            )
            drawCircle(
                color  = Color(0xAA000000),
                radius = rimRadius - rimW / 2f - 1f,
                center = Offset(cx, cy),
                style  = Stroke(width = 1.5f)
            )

            // ── 5. Icône batterie en haut (bitmap-like : petit éclair) ──
            val spaceArea = (H - rimW * 2f - H * 0.38f) / 2f  // espace entre rim et texte
            val boltCx = cx
            val boltCy = rimW + spaceArea * 0.35f
            val boltH  = H * 0.055f
            val boltW  = boltH * 0.6f
            val boltAlpha = if (isCharging) iconAlpha else 1f

            // Dessine un petit éclair (⚡) symbolisant la batterie
            val bolt = Path().apply {
                moveTo(boltCx - boltW * 0.1f, boltCy - boltH / 2f)
                lineTo(boltCx + boltW * 0.45f, boltCy - boltH / 2f)
                lineTo(boltCx + boltW * 0.1f, boltCy - boltH * 0.05f)
                lineTo(boltCx + boltW * 0.5f, boltCy - boltH * 0.05f)
                lineTo(boltCx - boltW * 0.15f, boltCy + boltH / 2f)
                lineTo(boltCx + boltW * 0.05f, boltCy + boltH * 0.1f)
                lineTo(boltCx - boltW * 0.3f, boltCy + boltH * 0.1f)
                close()
            }
            drawPath(bolt, color = t.textPrimary.copy(alpha = 0.5f * boltAlpha))
        }

        // ── Centre : pourcentage batterie (original: Helvetica Neue UltraLight) ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                battery?.let { "$it" } ?: "--",
                color      = battColor,
                fontSize   = 64.sp,
                fontWeight = FontWeight.Normal,           // UltraLight → Normal (le + léger dispo)
                fontFamily = RobotoCondensed,
                lineHeight = 64.sp
            )
            Text(
                "%",
                color      = battColor.copy(alpha = 0.70f),
                fontSize   = 22.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = RobotoCondensed
            )
        }
    }
}

// ─── Carte de mode avec switch On/Off (Dashboard) ────────────────────────────
@Composable
private fun ModeCard(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
    onNavigate: () -> Unit
) {
    val t = LocalZikSkin.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) t.accent.copy(alpha = 0.10f) else t.cardBg)
            .border(
                width = if (active) 1.5.dp else 0.dp,
                color = if (active) t.accent.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onNavigate)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (active) t.accent.copy(alpha = 0.18f) else t.dark)
                .border(1.dp, if (active) t.accent else t.dark, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (active) t.accent else t.textSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color      = if (active) t.accent else t.textSecondary,
            fontSize   = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
        Switch(
            checked = active,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor   = t.accent,
                checkedTrackColor   = t.accent.copy(alpha = 0.4f),
                uncheckedThumbColor = t.textSecondary,
                uncheckedTrackColor = t.dark
            )
        )
    }
}

// ── Données d'un cran ANC ───────────────────────────────────────────────────
private data class AncStep(
    val mode : ZikProtocol.NoiseControlMode,
    val label: String,
    val sub  : String,
    val icon : ImageVector
)
private val ANC_STEPS = listOf(
    AncStep(ZikProtocol.NoiseControlMode.ANC,        "ANC MAX",     "Annulation active (\u221230 dB)",     Icons.Default.HearingDisabled),
    AncStep(ZikProtocol.NoiseControlMode.ANC_LOW,    "ANC",         "R\u00e9duction standard (\u221225 dB)",  Icons.Default.VolumeDown),
    AncStep(ZikProtocol.NoiseControlMode.OFF,        "PASSIF / OFF","Isolation passive (\u221210 dB)",     Icons.Default.Hearing),
    AncStep(ZikProtocol.NoiseControlMode.STREET_LOW, "STREET LOW",  "Transparence l\u00e9g\u00e8re (\u22125 dB)",   Icons.Default.VolumeUp),
    AncStep(ZikProtocol.NoiseControlMode.STREET,     "STREET MAX",  "Transparence totale (0 dB)",        Icons.Default.GraphicEq),
)

// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 1 — NOISE CONTROL  (fidèle screenshot Parrot Zik original)
//  - Cercle CENTRÉ sur la page, GRANDIT/RÉTRÉCIT selon le mode
//  - Curseur orange fixe à 6h (bas du cercle) → traverse les points fixes
//  - 5 points de sélection fixes en axe vertical
//  - ANC max = petit cercle (curseur haut), STREET max = grand cercle (bas)
//  - in_db au centre du cercle, externalNoiseLevel en haut
//  - dB calculé : ambiant(micro) − atténuation(mode) comme appli Parrot originale
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageNoiseControl(
    ancMode: ZikProtocol.NoiseControlMode?,
    viewModel: ZikViewModel,
    isVisible: Boolean = false,
    onMenuOpen: () -> Unit = {}
) {
    val t          = LocalZikSkin.current
    val ancPending by viewModel.ancPending.collectAsState()
    val autoNc     by viewModel.autoNc.collectAsState()
    val fftMags    by viewModel.fftMagnitudes.collectAsState()

    // ── Mesure micro ambiant ─────────────────────────────────────────────
    val context = LocalContext.current
    var dbAmbiant by remember { mutableStateOf<Int?>(null) }
    // Permission micro — demandée automatiquement si pas encore accordée
    var micPermGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micPermGranted = granted }

    // Demander la permission au premier affichage si pas encore accordée
    LaunchedEffect(isVisible, micPermGranted) {
        if (isVisible && !micPermGranted) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (micPermGranted && isVisible) {
        LaunchedEffect(isVisible) {
            withContext(Dispatchers.IO) {
                val sr  = 44100
                val bufMin = AudioRecord.getMinBufferSize(sr,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (bufMin <= 0) return@withContext
                @Suppress("DEPRECATION")
                val ar = AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(bufMin, 4096))
                if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return@withContext }
                ar.startRecording()
                try {
                    val pcm    = ShortArray(1024)
                    val smooth = mutableListOf<Double>()
                    var lastMs = 0L
                    while (isActive) {
                        val n = ar.read(pcm, 0, pcm.size)
                        if (n > 0) {
                            var s = 0.0; for (i in 0 until n) s += pcm[i].toDouble() * pcm[i]
                            val rms = sqrt(s / n)
                            val db  = if (rms > 1.0)
                                (20.0 * log10(rms / 32768.0) + 90.0).coerceIn(0.0, 130.0)
                            else 0.0
                            smooth.add(db); if (smooth.size > 10) smooth.removeAt(0)
                            val now = System.currentTimeMillis()
                            if (now - lastMs >= 500L) {
                                lastMs = now
                                val avg = smooth.average().roundToInt()
                                val level = if (avg < 0 || avg > 130) 0 else avg
                                withContext(Dispatchers.Main) { dbAmbiant = level }
                            }
                        }
                    }
                } finally { ar.stop(); ar.release() }
            }
        }
    }

    val pendingAlpha = if (ancPending) 0.45f else 1.0f

    // ── Modes NC — dynamique selon Auto (3) / Manuel (5) ─────────────────
    val isAutoNc = autoNc == true
    val modeOrder = if (isAutoNc) listOf(
        ZikProtocol.NoiseControlMode.ANC,        // 0 = − (haut)
        ZikProtocol.NoiseControlMode.OFF,        // 1 = ○ (milieu)
        ZikProtocol.NoiseControlMode.STREET      // 2 = + (bas)
    ) else listOf(
        ZikProtocol.NoiseControlMode.ANC,        // 0 = − (haut)
        ZikProtocol.NoiseControlMode.ANC_LOW,    // 1
        ZikProtocol.NoiseControlMode.OFF,        // 2 = ○
        ZikProtocol.NoiseControlMode.STREET_LOW, // 3
        ZikProtocol.NoiseControlMode.STREET      // 4 = + (bas)
    )
    val modeCount = modeOrder.size
    val maxIdx = modeCount - 1
    val currentIdx = modeOrder.indexOf(ancMode).takeIf { it >= 0 }
        ?: if (isAutoNc) when (ancMode) {
            ZikProtocol.NoiseControlMode.ANC_LOW    -> 0   // → ANC
            ZikProtocol.NoiseControlMode.STREET_LOW -> 2   // → STREET
            else -> 1                                       // → OFF
        } else modeCount / 2                               // → OFF (index 2)

    val modeLabel = when (ancMode) {
        ZikProtocol.NoiseControlMode.ANC        -> "Réduction de bruit (max)"
        ZikProtocol.NoiseControlMode.ANC_LOW    -> "Réduction de bruit"
        ZikProtocol.NoiseControlMode.STREET      -> "Street mode (max)"
        ZikProtocol.NoiseControlMode.STREET_LOW  -> "Street mode"
        else                                      -> "Off"
    }
    val ancAttenuation = when (ancMode) {
        ZikProtocol.NoiseControlMode.ANC        -> 30
        ZikProtocol.NoiseControlMode.ANC_LOW    -> 25
        ZikProtocol.NoiseControlMode.STREET_LOW -> 5
        ZikProtocol.NoiseControlMode.STREET     -> 0
        else                                     -> 10
    }
    val internalDb = if (dbAmbiant != null) {
        val calc = dbAmbiant!! - ancAttenuation
        if (calc > 0) calc else 0
    } else null

    // ── Position du curseur (indice flottant 0.0–maxIdx) ─────────────────
    // 0 = haut (ANC max, sur la bague à 6h), maxIdx = bas (STREET max, position +)
    var cursorPos by remember { mutableStateOf(currentIdx.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentIdx, isDragging) {
        if (!isDragging) cursorPos = currentIdx.toFloat()
    }

    val displayPos by animateFloatAsState(
        targetValue = cursorPos,
        animationSpec = if (isDragging) snap() else tween(250, easing = FastOutSlowInEasing),
        label = "cursorPos"
    )

    // ── Animations sunburst ──────────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "ncgl")
    val rayPhase by inf.animateFloat(
        initialValue = 0f, targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing)
        ), label = "rayPhase"
    )
    val circlePulse by inf.animateFloat(
        initialValue = 0.99f, targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "circlePulse"
    )
    val rayAlphaPulse by inf.animateFloat(
        initialValue = 0.10f, targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "rayAlpha"
    )

    Column(Modifier.fillMaxSize()) {
        // ── ActionBar ─────────────────────────────────────────────────────
        ParrotTopBar(
            title = "Contrôle du bruit",
            onMenuClick = onMenuOpen
        )

        // ── Zone principale ───────────────────────────────────────────────
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(t.background)
                .pointerInput(ancPending, modeCount) {
                    if (ancPending) return@pointerInput
                    // Gestionnaire combiné : tap (sélection directe) + drag (glisser entre modes)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val startPos = down.position
                        val slop = viewConfiguration.touchSlop
                        var dragging = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull() ?: break
                            ch.consume()

                            if (!ch.pressed) {
                                // ── Relâchement ───────────────────────────────────
                                if (dragging) {
                                    // Fin de drag → snap au mode le plus proche
                                    val snapIdx = (cursorPos + 0.5f).toInt().coerceIn(0, maxIdx)
                                    cursorPos = snapIdx.toFloat()
                                    isDragging = false
                                    if (snapIdx != currentIdx) {
                                        viewModel.setNoiseControlMode(modeOrder[snapIdx])
                                    }
                                } else {
                                    // TAP simple → détection intelligente des points d'ancrage prioritaires
                                    val tapY = ch.position.y
                                    val tapX = ch.position.x
                                    
                                    // Géométrie du cercle (reproduite du Canvas)
                                    val W = size.width; val H = size.height
                                    val cx = W / 2f
                                    val cy = H * 0.42f
                                    val totalSpan = W * 0.36f
                                    val anchorSpacing = totalSpan / maxIdx.toFloat()
                                    val anchorCenterY = cy + W * 0.36f
                                    val recalcAnchorYs = FloatArray(modeCount) { i -> anchorCenterY + (i - modeCount / 2) * anchorSpacing }
                                    
                                    // Rayon de détection pour les points d'ancrage (distance euclidienne)
                                    val tapRadius = kotlin.math.sqrt(((tapX - cx) * (tapX - cx) + (tapY - recalcAnchorYs[0]) * (tapY - recalcAnchorYs[0])).toDouble()).toFloat()
                                    val detectionThreshold = 60f  // pixels — assez généreux pour faciliter le tapotage
                                    
                                    // Chercher l'ancrage le plus proche
                                    var closestIdx = -1
                                    var closestDist = Float.MAX_VALUE
                                    for (i in 0 until modeCount) {
                                        val anchorDist = kotlin.math.sqrt(((tapX - cx) * (tapX - cx) + (tapY - recalcAnchorYs[i]) * (tapY - recalcAnchorYs[i])).toDouble()).toFloat()
                                        if (anchorDist < closestDist) {
                                            closestDist = anchorDist
                                            closestIdx = i
                                        }
                                    }
                                    
                                    // Si suffisamment proche d'un anchor point, le sélectionner directement
                                    val tapIdx = if (closestDist < detectionThreshold) {
                                        closestIdx
                                    } else {
                                        // Fallback: sélection par zone verticale
                                        (((tapY / size.height) * modeCount.toFloat()) + 0.5f).toInt().coerceIn(0, maxIdx)
                                    }
                                    
                                    cursorPos = tapIdx.toFloat()
                                    if (tapIdx != currentIdx) {
                                        viewModel.setNoiseControlMode(modeOrder[tapIdx])
                                    }
                                }
                                break
                            }

                            if (!dragging && (ch.position - startPos).getDistance() > slop) {
                                dragging = true
                                isDragging = true
                            }

                            if (dragging) {
                                // drag DOWN → STREET, drag UP → ANC
                                // Increased sensitivity: reduced step to make the same movement produce larger changes
                                val step = size.width * 0.12f / maxIdx.toFloat()  // Halved from 0.24f → 0.12f
                                val dy = ch.position.y - startPos.y
                                cursorPos = (currentIdx.toFloat() + dy / step).coerceIn(0f, maxIdx.toFloat())
                            }
                        }
                    }
                }
        ) {
            // ── Canvas principal ──────────────────────────────────────────
            Canvas(Modifier.fillMaxSize()) {
                val W = size.width; val H = size.height
                val cx = W / 2f
                val cy = H * 0.42f   // Centre du cercle — calibré Parrot original (~46% zone contenu)

                // ── N points de sélection FIXES sur axe vertical ──────────
                val totalSpan = W * 0.36f
                val anchorSpacing = totalSpan / maxIdx.toFloat()
                val anchorCenterY = cy + W * 0.36f   // position Y de l'ancre OFF (milieu)
                val anchorYs = FloatArray(modeCount) { i -> anchorCenterY + (i - modeCount / 2) * anchorSpacing }

                // ── Rayon du cercle = fait que 6h tombe sur l'ancre ───────
                val targetAnchorY = anchorYs[0] + displayPos * anchorSpacing
                // Rayon minimum assez gros pour que "XX dB" rentre dedans
                val circleR = (targetAnchorY - cy).coerceAtLeast(W * 0.15f)
                val ringStroke = 8f   // anneau ÉPAIS
                val noiseNorm = ((dbAmbiant ?: 40) / 100f).coerceIn(0.15f, 1.0f)
                val accentColor = t.accent

                // ── Rayons solaires (sunburst) — référence Parrot + FFT musique ──
                // 200 rayons denses, épaisseur suit position drag (ANC→Street),
                // longueur modulée par FFT temps-réel. Gap 30° en bas pour OFF.
                val rayCount = 200
                val streetFrac = (displayPos / maxIdx.toFloat()).coerceIn(0f, 1f)
                // Épaisseur : 1.8 (ANC max) → 4.5 (Street max) — toujours bien visible
                val dynStroke = 1.8f + 2.7f * streetFrac
                val gapCenterRad = PI.toFloat() / 2f
                val gapHalfRad   = Math.toRadians(15.0).toFloat()
                val fftBands = fftMags

                for (i in 0 until rayCount) {
                    val baseAngle = (i.toFloat() / rayCount) * 2f * PI.toFloat()
                    val angle = baseAngle + rayPhase * 0.018f

                    // Skip rays in the OFF gap zone
                    val distToGap = kotlin.math.abs(((angle - gapCenterRad + 3f * PI.toFloat()) % (2f * PI.toFloat())) - PI.toFloat())
                    if (distToGap < gapHalfRad) continue

                    // ── FFT modulation : mapper l'angle du rayon sur une bande FFT ──
                    val angleFrac = ((angle % (2f * PI.toFloat())) / (2f * PI.toFloat())).coerceIn(0f, 1f)
                    val fftIdx = (angleFrac * (fftBands.size - 1)).toInt().coerceIn(0, fftBands.size - 1)
                    val fftVal = fftBands.getOrElse(fftIdx) { 0f }.coerceIn(0f, 1f)

                    val wave = sin(rayPhase * 1.5f + i * 0.40f)
                    // Longueur : base généreuse + FFT pousse les rayons jusqu'à +60%
                    val rayLen = circleR * (1.30f + 0.45f * noiseNorm + 0.60f * fftVal + 0.10f * wave * noiseNorm)
                    val startDist = circleR * 0.04f
                    val x0 = cx + startDist * cos(angle)
                    val y0 = cy + startDist * sin(angle)
                    val x1 = cx + rayLen * cos(angle)
                    val y1 = cy + rayLen * sin(angle)
                    // Alpha plus élevé pour être bien visible sur thèmes clairs
                    val iAlpha = 0.22f + 0.20f * fftVal + 0.06f * sin(rayPhase * 1.3f + i * 0.7f)
                    drawLine(
                        color       = t.textSecondary.copy(alpha = iAlpha.coerceIn(0.18f, 0.55f)),
                        start       = Offset(x0, y0),
                        end         = Offset(x1, y1),
                        strokeWidth = dynStroke
                    )
                }

                // ── Tirets autour du cercle — ÉPAIS et visibles ──────────
                val tickCount = 72
                for (i in 0 until tickCount) {
                    val angle = (i.toFloat() / tickCount) * 2f * PI.toFloat()
                    val tickInner = circleR - 8f
                    val tickOuter = circleR + 8f
                    val isMajor = i % 6 == 0  // tirets majeurs tous les 6
                    drawLine(
                        color       = t.textSecondary.copy(alpha = if (isMajor) 0.30f else 0.18f),
                        start       = Offset(cx + tickInner * cos(angle), cy + tickInner * sin(angle)),
                        end         = Offset(cx + tickOuter * cos(angle), cy + tickOuter * sin(angle)),
                        strokeWidth = if (isMajor) 2.0f else 1.5f
                    )
                }

                // ── Halo externe ──────────────────────────────────────────
                drawCircle(
                    color  = accentColor.copy(alpha = 0.04f),
                    radius = circleR * 1.30f,
                    center = Offset(cx, cy),
                    style  = Stroke(width = 0.6f)
                )

                // ── Cercle principal orange (anneau) ──────────────────────
                drawCircle(
                    color  = accentColor,
                    radius = circleR,
                    center = Offset(cx, cy),
                    style  = Stroke(width = ringStroke)
                )

                // ── Fond opaque intérieur ─────────────────────────────────
                drawCircle(
                    color  = t.background,
                    radius = circleR - ringStroke / 2f,
                    center = Offset(cx, cy)
                )

                // ── Ligne verticale guide (relie les ancres) ──────────────
                drawLine(
                    color       = t.textSecondary.copy(alpha = 0.08f),
                    start       = Offset(cx, anchorYs[0] - anchorSpacing * 0.3f),
                    end         = Offset(cx, anchorYs.last() + anchorSpacing * 0.3f),
                    strokeWidth = 1f
                )

                // ── Curseur FIXE à 6h — GROS et visible ──────────────────
                val cursorX = cx
                val cursorY = cy + circleR   // 6 o'clock
                val cursorR = 28f            // Curseur TRÈS GROS pour faciliter le toucher
                // Halo externe
                drawCircle(
                    color  = accentColor.copy(alpha = 0.10f),
                    radius = cursorR * 2.5f,
                    center = Offset(cursorX, cursorY)
                )
                // Halo moyen
                drawCircle(
                    color  = accentColor.copy(alpha = 0.25f),
                    radius = cursorR * 1.5f,
                    center = Offset(cursorX, cursorY)
                )
                // Point plein orange
                drawCircle(
                    color  = accentColor,
                    radius = cursorR,
                    center = Offset(cursorX, cursorY)
                )
                // Centre blanc
                drawCircle(
                    color  = Color.White.copy(alpha = 0.25f),
                    radius = cursorR * 0.22f,
                    center = Offset(cursorX, cursorY)
                )

                // ── Points de sélection FIXES — colorés ───────────────────
                for (i in 0 until modeCount) {
                    val ay = anchorYs[i]
                    // Masquer si le curseur est dessus
                    val dist = kotlin.math.abs(ay - cursorY)
                    if (dist < cursorR * 1.2f) continue

                    val isActive = i == currentIdx
                    val anchorColor = when {
                        isActive -> accentColor
                        kotlin.math.abs(i - currentIdx) == 1 -> accentColor.copy(alpha = 0.50f)
                        else -> t.textSecondary.copy(alpha = 0.45f)
                    }

                    when (modeOrder[i]) {
                        ZikProtocol.NoiseControlMode.ANC -> {
                            // "−" ANC max
                            val cLen = 20f
                            drawLine(anchorColor, Offset(cx - cLen, ay), Offset(cx + cLen, ay), strokeWidth = 4f)
                        }
                        ZikProtocol.NoiseControlMode.STREET -> {
                            // "+" STREET max
                            val cLen = 20f
                            drawLine(anchorColor, Offset(cx - cLen, ay), Offset(cx + cLen, ay), strokeWidth = 4f)
                            drawLine(anchorColor, Offset(cx, ay - cLen), Offset(cx, ay + cLen), strokeWidth = 4f)
                        }
                        ZikProtocol.NoiseControlMode.OFF -> {
                            // Cercle ouvert OFF
                            drawCircle(anchorColor, radius = 14f, center = Offset(cx, ay),
                                style = Stroke(width = 3f))
                        }
                        else -> {
                            // Points intermédiaires (ANC_LOW, STREET_LOW)
                            drawCircle(anchorColor, radius = 10f, center = Offset(cx, ay))
                        }
                    }
                }
            }

            // ── externalNoiseLevel — Haut ("61dB" brut micro) ────────────
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (dbAmbiant != null) "${dbAmbiant}dB" else "—dB",
                    color = when {
                        dbAmbiant == null       -> t.textSecondary.copy(alpha = 0.4f)
                        dbAmbiant!! >= 85       -> Color(0xFF640000)
                        dbAmbiant!! >= 70       -> Color(0xFFFF8429)
                        else                    -> t.textPrimary
                    },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Thin,
                    fontFamily = RobotoCondensed,
                    textAlign = TextAlign.Center
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (autoNc == true) Color(0xFFFF8429).copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (autoNc == true) Color(0xFFFF8429) else t.textSecondary.copy(alpha = 0.40f),
                            RoundedCornerShape(50)
                        )
                        .clickable(enabled = !ancPending) { viewModel.setAutoNc(autoNc != true) }
                        .alpha(pendingAlpha)
                        .padding(horizontal = 18.dp, vertical = 7.dp)
                ) {
                    Text(
                        "AUTO",
                        color = if (autoNc == true) Color(0xFFFF8429) else t.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontFamily = RobotoCondensed
                    )
                }
            }

            // ── in_db — Centre du cercle ("31 dB" calculé Parrot) ────────
            // Calcul Parrot original : internalNoise = externalNoise − atténuation
            // ANC=30dB, ANC_LOW=25dB, IDLE=10dB, STREET_LOW=5dB, STREET=0dB
            Text(
                text = if (internalDb != null && internalDb > 0) "$internalDb dB" else "- dB",   // Parrot original: tiret simple
                color = t.textPrimary,
                fontSize = 42.sp,
                fontWeight = FontWeight.Thin,
                fontFamily = RobotoCondensed,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val containerH = constraints.maxHeight
                        val cCy = (containerH * 0.42f).toInt()   // même cy que le Canvas
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(
                                (constraints.maxWidth - placeable.width) / 2,
                                cCy - placeable.height / 2
                            )
                        }
                    }
            )

            // ── tvNoiseMode — Bas (centré, gros texte) ───────────────────
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 38.dp),
                contentAlignment = Alignment.Center
            ) {
                if (autoNc == true) {
                    Text(
                        buildAnnotatedString {
                            append(modeLabel)
                            append(" ")
                            withStyle(SpanStyle(
                                color = Color(0xFFFF8429),
                                fontWeight = FontWeight.Bold
                            )) {
                                append("AUTO")
                            }
                        },
                        color = t.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = RobotoCondensed,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                } else {
                    Text(
                        modeLabel,
                        color = t.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = RobotoCondensed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } // noise_view Box
    } // Column
}

// ─────────────────────────────────────────────────────────────────────────────
//  BANDEAU STATUT FIRMWARE ANC
//  Affiche en bas de PageNoiseControl le mode ANC actif tel que rapporté par le firmware.
//  Discret, lecture seule — pas d'interaction.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AncFirmwareBanner(
    ancMode  : ZikProtocol.NoiseControlMode?,
    autoNc   : Boolean?,
    ncStatus : String?,
    modifier : Modifier = Modifier
) {
    // Ne pas afficher si erreur déjà gérée par le bandeau rouge, ou si rien de reçu
    if (ncStatus?.contains("non support") == true) return
    if (ancMode == null && ncStatus == null) return

    val t = LocalZikSkin.current

    val isActive = autoNc == true ||
        (ancMode != null && ancMode != ZikProtocol.NoiseControlMode.OFF)

    val modeLabel = when {
        autoNc == true                                     -> "Mode Auto — adaptatif"
        ancMode == ZikProtocol.NoiseControlMode.ANC        -> "ANC Max"
        ancMode == ZikProtocol.NoiseControlMode.ANC_LOW    -> "ANC Low"
        ancMode == ZikProtocol.NoiseControlMode.STREET     -> "Street Max"
        ancMode == ZikProtocol.NoiseControlMode.STREET_LOW -> "Street Low"
        ancMode == ZikProtocol.NoiseControlMode.OFF        -> "Passif / OFF"
        else                                               -> ncStatus?.take(60) ?: return
    }

    // Tag de confirmation firmware (✔ = casque confirmé actif, ✖ = confirmé passif)
    val firmwareTag = when {
        ncStatus?.contains("✔") == true -> "FIRMWARE ✔"
        ncStatus?.contains("✖") == true -> "FIRMWARE ✖"
        else -> null
    }

    val bgColor = if (isActive) t.accent.copy(alpha = 0.10f) else t.cardBg.copy(alpha = 0.60f)
    val fgColor = if (isActive) t.accent else t.textSecondary
    val iconVec = if (isActive) Icons.Default.GraphicEq else Icons.Default.VolumeOff

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(iconVec, contentDescription = null, tint = fgColor, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "STATUT CASQUE",
                color = t.textSecondary, fontSize = 9.sp,
                letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium
            )
            Text(
                modeLabel,
                color = fgColor, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
        if (firmwareTag != null) {
            Text(
                firmwareTag,
                color = fgColor.copy(alpha = 0.65f),
                fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ONDE ANIMÉE AUTO-NC — pas de faux dB, uniquement feedback visuel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AutoNcWave() {
    val t     = LocalZikSkin.current
    val inf   = rememberInfiniteTransition(label = "autoNc")
    val phase by inf.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "phase"
    )
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.accent.copy(alpha = 0.09f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Autorenew, null, tint = t.accent, modifier = Modifier.size(16.dp))
            Text("Adaptation dynamique en cours",
                color = t.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(44.dp)) {
            val w   = size.width
            val h   = size.height
            val mid = h / 2f
            val amp = h * 0.36f
            val steps = 160
            for (i in 0 until steps) {
                val x0 = i / steps.toFloat() * w
                val x1 = (i + 1) / steps.toFloat() * w
                val y0 = mid - amp * sin(phase + i / steps.toFloat() * 2.0f * PI.toFloat())
                val y1 = mid - amp * sin(phase + (i + 1) / steps.toFloat() * 2.0f * PI.toFloat())
                drawLine(
                    color       = t.accent.copy(alpha = 0.80f),
                    start       = Offset(x0, y0.toFloat()),
                    end         = Offset(x1, y1.toFloat()),
                    strokeWidth = 2.5f
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  DÉCIBELMÈTRE compact (chip — valeur brute du micro uniquement)
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Affiche uniquement le niveau micro brut (dBSPL) en une ligne compacte.
 * AudioRecord démarré à l'entrée du composable, libéré à la sortie.
 * Ne pas afficher ce composable si ANC est OFF (parent contrôle la visibilité).
 */
@Composable
private fun DbChip() {
    val context = LocalContext.current
    val t       = LocalZikSkin.current

    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPerm = granted }

    if (!hasAudioPerm) {
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(t.cardBg)
                .clickable { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Mic, null, tint = t.textSecondary, modifier = Modifier.size(16.dp))
                Text("Autoriser le micro", color = t.textSecondary, fontSize = 12.sp)
            }
        }
        return
    }

    var dbRaw by remember { mutableStateOf(30.0) }
    LaunchedEffect(true) {
        withContext(Dispatchers.IO) {
            val sr  = 44100
            val buf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                .takeIf { it > 0 } ?: return@withContext
            @Suppress("DEPRECATION")
            val ar = AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(buf, 4096))
            if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return@withContext }
            ar.startRecording()
            try {
                val pcm = ShortArray(1024)
                while (isActive) {
                    val n = ar.read(pcm, 0, pcm.size)
                    if (n > 0) {
                        var s = 0.0
                        for (i in 0 until n) s += pcm[i].toDouble() * pcm[i]
                        val rms = sqrt(s / n)
                        val db  = if (rms > 1.0) (20.0 * log10(rms / 32768.0) + 90.0).coerceIn(30.0, 120.0) else 30.0
                        withContext(Dispatchers.Main) { dbRaw = db }
                    }
                }
            } finally { ar.stop(); ar.release() }
        }
    }

    val barColor = when {
        dbRaw >= 85 -> Color(0xFFFF5252)
        dbRaw >= 70 -> Color(0xFFFFB300)
        else        -> Color(0xFF4CAF50)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(barColor.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Mic, null, tint = barColor, modifier = Modifier.size(18.dp))
        Text("Niveau ambiant", color = t.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("%.0f dB".format(dbRaw), color = barColor,
            fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// Dépréciée mais laissée — plus appelée mais référencée nulle part.
private fun ancAttenuationDb(mode: ZikProtocol.NoiseControlMode?): Double = when (mode) {
    ZikProtocol.NoiseControlMode.ANC      -> 30.0
    ZikProtocol.NoiseControlMode.ANC_LOW  -> 25.0
    ZikProtocol.NoiseControlMode.STREET_LOW -> 5.0
    ZikProtocol.NoiseControlMode.STREET   -> 0.0
    else                                   -> 10.0   // passive isolation
}

// ─────────────────────────────────────────────────────────────────────────────
//  dBMÈTRE MIROIR — Bruit Ambiant (micro) vs Bruit Réduit (calcul ANC)
//  S'affiche uniquement si l'app a la permission RECORD_AUDIO.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AmbientMirrorWidget(
    ancMode: ZikProtocol.NoiseControlMode?,
    ancEnabled: Boolean?      // confirmé firmware — null = inconnu
) {
    val context = LocalContext.current
    val t       = LocalZikSkin.current
    val hasPerm = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    if (!hasPerm) return   // pas de permission — widget invisible

    // ancAttenuation = 0 si le firmware n'a pas explicitément confirmé ancEnabled==true.
    // Zéro-fantome : on n'affiche jamais une réduction imaginaire.
    val ancAttenuation = if (ancEnabled == true) when (ancMode) {
        ZikProtocol.NoiseControlMode.ANC      -> 30.0
        ZikProtocol.NoiseControlMode.ANC_LOW  -> 25.0
        ZikProtocol.NoiseControlMode.STREET_LOW -> 5.0
        ZikProtocol.NoiseControlMode.STREET   -> 0.0
        else                                   -> 10.0
    } else 10.0  // passive isolation même sans ANC
    var dbAmbiant by remember { mutableStateOf(30.0) }
    var dbDisplay  by remember { mutableStateOf(30.0) }   // gelé toutes les 500 ms
    val smoothBuf  = remember { mutableListOf<Double>() }  // moyenne glissante 8 éch.
    LaunchedEffect(true) {
        withContext(Dispatchers.IO) {
            val sr  = 44100
            val buf = AudioRecord.getMinBufferSize(sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).takeIf { it > 0 } ?: return@withContext
            @Suppress("DEPRECATION")
            val ar = AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(buf, 4096))
            if (ar.state != AudioRecord.STATE_INITIALIZED) { ar.release(); return@withContext }
            ar.startRecording()
            try {
                val pcm = ShortArray(1024)
                var lastDisplayMs = System.currentTimeMillis()
                while (isActive) {
                    val n = ar.read(pcm, 0, pcm.size)
                    if (n > 0) {
                        var s = 0.0; for (i in 0 until n) s += pcm[i].toDouble() * pcm[i]
                        val rms = sqrt(s / n)
                        val db  = if (rms > 1.0) (20.0 * log10(rms / 32768.0) + 90.0).coerceIn(30.0, 120.0) else 30.0
                        // Enqueue dans le buffer de lissage (max 8)
                        smoothBuf.add(db)
                        if (smoothBuf.size > 10) smoothBuf.removeAt(0)
                        val smoothed = smoothBuf.average()
                        // Rafraîcht affichage uniquement à 500ms max
                        val now = System.currentTimeMillis()
                        if (now - lastDisplayMs >= 500L) {
                            lastDisplayMs = now
                            withContext(Dispatchers.Main) { dbAmbiant = smoothed; dbDisplay = smoothed }
                        }
                    }
                }
            } finally { ar.stop(); ar.release() }
        }
    }
    val dbReduit = (dbDisplay - ancAttenuation).coerceAtLeast(30.0)
    val isAncActive = ancAttenuation > 0
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(t.cardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colonne gauche : Ambiant
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎙", fontSize = 16.sp)
            Text("%.0f dB".format(dbDisplay),
                color = when {
                    dbAmbiant >= 85 -> Color(0xFFFF5252)
                    dbAmbiant >= 70 -> Color(0xFFFFB300)
                    else            -> t.textPrimary
                },
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("Ambiant", color = t.textSecondary, fontSize = 10.sp)
        }
        // Séparateur
        Box(Modifier.width(1.dp).height(36.dp).background(t.dark))
        // Colonne droite : Réduit
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎧", fontSize = 16.sp)
            Text("%.0f dB".format(dbReduit),
                color = if (isAncActive) Color(0xFF4CAF50) else t.textSecondary,
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text(if (isAncActive) "−25 dB" else "ANC OFF",
                color = if (isAncActive) Color(0xFF4CAF50) else t.textSecondary,
                fontSize = 10.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PRESETS EQ — CALIBRÉS SUR CAPTURE BTSnoop Note 3 (2 mars 2026)
//  Valeurs = gains à r=100 (rayon max) mesurés sur le firmware Parrot natif
//  Angles : y-flip (0°=Est/3h, 90°=haut, sens anti-horaire)
//  CRISTAL |   0° |  0.0   0.0   2.0   3.5   7.5  (capt. r=98,θ=0)
//  VOCAL   |  60° | -3.0   3.0   5.0   4.0   2.0  (capt. r=99,θ=60)
//  POP     | 120° |  0.0   6.0   4.5   3.5   1.0  (capt. r=98,θ=120)
//  DEEP    | 180° |  7.5   3.0   2.0   0.0   0.0  (capt. r=98,θ=180)
//  PUNCHY  | 231° |  6.0   1.5   0.5   1.0   4.0  (capt. r=99,θ=231)
//  CLUB    | 300° |  6.0  -4.0   3.5  -1.0   6.5  (capt. r=99,θ=299)
// ─────────────────────────────────────────────────────────────────────────────
private data class EqPreset(
    val angleDeg  : Float,
    val label     : String,
    val presetName: String,
    val v1: Double, val v2: Double, val v3: Double,
    val v4: Double, val v5: Double
)

private val EQ_PRESETS = listOf(
    EqPreset( 60f, "VOCAL",   "vocal",   -3.0,  3.0,  5.0,  4.0,  2.0),
    EqPreset(120f, "POP",     "pops",     0.0,  6.0,  4.5,  3.5,  1.0),
    EqPreset(180f, "DEEP",    "deep",     7.5,  3.0,  2.0,  0.0,  0.0),
    EqPreset(240f, "PUNCHY",  "punchy",   6.0,  1.5,  0.5,  1.0,  4.0),
    EqPreset(300f, "CLUB",    "club",     6.0, -4.0,  3.5, -1.0,  6.5),
    EqPreset(360f, "CRISTAL", "crystal",  0.0,  0.0,  2.0,  3.5,  7.5),
)

// ─────────────────────────────────────────────────────────────────────────────
//  DISQUE TACTILE THUMB-EQUALIZER — Continuous float physics engine
//  - Coordonnées (rF, thetaF) en Float continu : zéro quantification UI
//  - 1:1 mapping touch → DSP gain, arrondi uniquement au protocole BT
//  - 5 anneaux concentriques (REFERENCE_UI fidèle)
//  - Glow radial lisse sur le thumb orange
//  - Double-tap = reset centre (EQ plat)
//  - Single-tap = placement direct continu
//  - Interpolation linéaire entre presets adjacents (algo firmware natif)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ThumbDisk(viewModel: ZikViewModel) {
    val t          = LocalZikSkin.current
    val eqEnabled  by viewModel.eqEnabled.collectAsState()
    val thumbEq    by viewModel.thumbEq.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val diskActive = eqEnabled != false
    val density    = LocalDensity.current
    val haptic     = LocalHapticFeedback.current
    val showPresetOverlay = !activePreset.isNullOrBlank() && !activePreset.equals("manual", ignoreCase = true)
    var presetOverlayDismissed by remember(activePreset) { mutableStateOf(false) }

    // ── Audio Visualizer FFT pour couronne dynamique ─────────────────────
    val ancMode    by viewModel.ancMode.collectAsState()
    val fftMags    by viewModel.fftMagnitudes.collectAsState()
    val context    = LocalContext.current

    // Démarrer/stopper le Visualizer quand le composable entre/quitte la composition
    DisposableEffect(Unit) {
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) viewModel.startVisualizer()
        onDispose { viewModel.stopVisualizer() }
    }

    // ── État drag local — FLOAT CONTINU (persistant via rememberSaveable) ──
    // rF / thetaF = cible brute du doigt (mise à jour immédiate par le touch)
    var rF                 by rememberSaveable { mutableStateOf(thumbEq?.x?.toFloat() ?: 0f) }
    var thetaF             by rememberSaveable { mutableStateOf(thumbEq?.y?.toFloat() ?: 0f) }
    // rSmooth / thetaSmooth = valeurs lissées envoyées au casque (convergent vers rF/thetaF)
    var rSmooth            by rememberSaveable { mutableStateOf(thumbEq?.x?.toFloat() ?: 0f) }
    var thetaSmooth        by rememberSaveable { mutableStateOf(thumbEq?.y?.toFloat() ?: 0f) }
    var lastSendMs         by remember { mutableStateOf(0L) }
    var initializedFromSvc by rememberSaveable { mutableStateOf(false) }
    // Flag : true pendant un drag actif (la boucle LERP tourne)
    var isDragging         by remember { mutableStateOf(false) }
    // Flag : true quand un tap a défini une cible (la boucle LERP anime vers elle)
    var isTapAnimating     by remember { mutableStateOf(false) }

    // ── LERP Smoothing Engine + REAL_TIME_EQ_STREAMING ─────────────────────
    // Paramètres calibrés pour reproduire la douceur du Note 3 :
    // - LERP_FACTOR : 0.28 = convergence rapide mais sans saut (0=immobile, 1=instantané)
    // - LERP_TICK_MS : 16ms = 60 fps de lissage interne (visuel uniquement pendant drag)
    // - EQ_SAMPLE_MS : 500ms = 2 positions/sec vers le Zik (identique Parrot natif)
    // - SNAP_EPSILON : en dessous de cette distance, on considère la convergence atteinte
    val LERP_FACTOR     = 0.28f
    val LERP_TICK_MS    = 16L
    val EQ_SAMPLE_MS    = 500L
    val SNAP_EPSILON    = 0.3f

    // ── Flow streaming : positions EQ émises en continu pendant le drag ──────
    // Le sample(40ms) garantit exactement 25 paquets/sec : assez "direct pur"
    // pour l'oreille, assez lent pour ne pas saturer le DSP Zik.
    val eqDragFlow = remember {
        MutableSharedFlow<Pair<Float, Float>>(
            extraBufferCapacity = 64,
            onBufferOverflow    = BufferOverflow.DROP_OLDEST
        )
    }

    // Collecteur eqDragFlow → placé APRÈS bandsFromPolarF() (évite forward reference)

    /** Interpole l'angle en tenant compte du wrap-around 0°/360° */
    fun lerpAngle(from: Float, to: Float, t: Float): Float {
        var delta = ((to - from + 540f) % 360f) - 180f
        return (from + delta * t + 360f) % 360f
    }

    // ── Interpolation CONTINUE entre 2 presets adjacents ──────────────────
    // Identique à l'algo firmware natif MAIS sans arrondi 0.5 dB côté UI.
    // L'arrondi est appliqué uniquement dans le paquet BT protocol (setThumbEq).

    /** Trouve les 2 presets adjacents et la fraction linéaire entre eux */
    fun findAdjacentPresets(angleDeg: Float): Triple<EqPreset, EqPreset, Double> {
        val a = ((angleDeg % 360f) + 360f) % 360f
        for (i in EQ_PRESETS.indices) {
            val p1 = EQ_PRESETS[i]
            val p2 = EQ_PRESETS[(i + 1) % EQ_PRESETS.size]
            val a1 = p1.angleDeg % 360f
            val a2Raw = p2.angleDeg % 360f
            val a2 = if (a2Raw <= a1) a2Raw + 360f else a2Raw
            val aTest = if (a < a1) a + 360f else a
            if (aTest in a1..a2) {
                val frac = if (a2 - a1 > 0.001f) ((aTest - a1) / (a2 - a1)).toDouble() else 0.0
                return Triple(p1, p2, frac.coerceIn(0.0, 1.0))
            }
        }
        return Triple(EQ_PRESETS[0], EQ_PRESETS[1], 0.0)
    }

    /** Calcule les 5 bandes pour une position FLOAT continue — pas d'arrondi */
    fun bandsFromPolarF(radius: Float, angleDeg: Float): List<Double> {
        if (radius < 0.5f) return listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        val rNorm = (radius / 100.0).coerceIn(0.0, 1.0)
        val (p1, p2, frac) = findAdjacentPresets(angleDeg)
        val g1 = listOf(p1.v1, p1.v2, p1.v3, p1.v4, p1.v5)
        val g2 = listOf(p2.v1, p2.v2, p2.v3, p2.v4, p2.v5)
        return (0..4).map { i ->
            val blended = (1.0 - frac) * g1[i] + frac * g2[i]
            kotlin.math.round(rNorm * blended * 2.0) / 2.0   // Arrondi 0.5 dB — identique firmware natif
        }
    }

    /** Arrondi 0.5 dB pour envoi protocole BT uniquement */
    fun roundHalfForProtocol(v: Double): Double = (v * 2.0).let { kotlin.math.round(it) / 2.0 }

    // ── Collecteur eqDragFlow avec sample(40ms) ── envoie les coordonnées RAW au casque
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        eqDragFlow
            .sample(EQ_SAMPLE_MS)
            .collect { (r, theta) ->
                if (r < 0.5f) {
                    viewModel.setThumbEq(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                } else {
                    val bands = bandsFromPolarF(r, theta)
                    // Format Parrot natif : gains 0.5 dB, r/theta entiers, r max 99
                    viewModel.setThumbEq(
                        bands[0], bands[1], bands[2], bands[3], bands[4],
                        kotlin.math.round(r).toDouble().coerceAtMost(99.0),
                        kotlin.math.round(theta).toDouble() % 360.0
                    )
                }
            }
    }

    // Gains calculés depuis la position courante — résolution float continue
    val currentBands by remember {
        derivedStateOf {
            bandsFromPolarF(rF, thetaF)
        }
    }

    /** Envoie la position finale au casque — format Parrot natif */
    fun sendSmoothed() {
        if (rSmooth < 0.5f) {
            viewModel.setThumbEq(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        } else {
            val bands = bandsFromPolarF(rSmooth, thetaSmooth)
            // Format Parrot natif : gains 0.5 dB, r/theta entiers, r ≤ 99
            viewModel.setThumbEq(
                bands[0], bands[1], bands[2], bands[3], bands[4],
                kotlin.math.round(rSmooth).toDouble().coerceAtMost(99.0),
                kotlin.math.round(thetaSmooth).toDouble() % 360.0
            )
        }
    }

    // Boucle LERP : converge rSmooth/thetaSmooth vers rF/thetaF à 60 fps.
    // REAL_TIME_EQ_STREAMING : pendant un drag, le LERP ne gère que le VISUEL.
    // L'envoi BT est délégué au flow eqDragFlow → sample(40ms) → setThumbEq.
    // Pendant un tap-animation, le LERP envoie les positions animées via le flow.
    LaunchedEffect(isDragging, isTapAnimating) {
        if (!isDragging && !isTapAnimating) return@LaunchedEffect
        while (isDragging || isTapAnimating) {
            // ─ Pas LERP (convergence visuelle) ─
            val drDelta  = rF - rSmooth
            val oldTheta = thetaSmooth
            rSmooth      = rSmooth + drDelta * LERP_FACTOR
            thetaSmooth  = if (rF < 0.5f) thetaF else lerpAngle(oldTheta, thetaF, LERP_FACTOR)

            // Convergence atteinte ?
            val rClose     = kotlin.math.abs(rF - rSmooth) < SNAP_EPSILON
            val thetaClose = kotlin.math.abs(((thetaF - thetaSmooth + 540f) % 360f) - 180f) < SNAP_EPSILON
            if (rClose && thetaClose) {
                rSmooth = rF; thetaSmooth = thetaF
                if (isTapAnimating) {
                    isTapAnimating = false
                    // Envoi final après animation tap (via le flow)
                    eqDragFlow.tryEmit(rSmooth to thetaSmooth)
                    sendSmoothed()   // + envoi direct de confirmation
                    break
                }
            }

            // Pendant tap-animation : streamer les positions animées via le flow
            if (isTapAnimating) {
                eqDragFlow.tryEmit(rSmooth to thetaSmooth)
            }
            // Pendant drag : PAS d'envoi ici — les positions RAW sont streamées
            // directement depuis le gesture handler → eqDragFlow → sample(40ms)

            delay(LERP_TICK_MS)
        }
    }

    // Resynchroniser depuis le ViewModel (source de vérité) au premier poll UNIQUEMENT
    // Guard : (1) seulement la première init, (2) ignorer si eqPending est actif,
    // (3) ne pas écraser avec (0,0) parasite du firmware au démarrage
    val eqPending by viewModel.eqPending.collectAsState()
    LaunchedEffect(thumbEq) {
        val eq = thumbEq ?: return@LaunchedEffect
        if (eqPending) return@LaunchedEffect               // gate anti-rebound actif
        if (!initializedFromSvc) {
            // Guard centre : ne pas forcer (0,0) si le firmware renvoie un état vide au démarrage
            // Seul un double-tap explicite peut mettre (0,0).
            if (eq.x == 0.0 && eq.y == 0.0 && eq.v1 == 0.0 && eq.v2 == 0.0 && eq.v3 == 0.0 && eq.v4 == 0.0 && eq.v5 == 0.0) {
                // État initial vide firmware — ne pas écraser la position locale
            } else {
                rF = eq.x.toFloat(); thetaF = eq.y.toFloat()
                rSmooth = rF; thetaSmooth = thetaF
            }
            initializedFromSvc = true
        }
    }

    // Preset le plus proche — derivedStateOf continu (pas de snap forcé)
    val nearestPreset by remember {
        derivedStateOf {
            if (rF < 35f) null
            else {
                val best = EQ_PRESETS.minByOrNull { p ->
                    ((thetaF - p.angleDeg + 360f) % 360f)
                        .let { if (it > 180f) 360f - it else it }
                }
                if (best != null) {
                    val d = ((thetaF - best.angleDeg + 360f) % 360f)
                        .let { if (it > 180f) 360f - it else it }
                    if (d < 25f) best else null
                } else null
            }
        }
    }

    // ── Coordonnées polaires FLOAT + dead zone logarithmique ────────────
    // Plus on est proche du centre, plus le mouvement est atténué (pow 1.4)
    // pour imiter la douceur du Note 3 original. La zone < 8px = dead zone réelle.
    fun computePolarF(px: Float, py: Float, cx: Float, cy: Float, maxR: Float): Pair<Float, Float> {
        val dx  = px - cx; val dy = py - cy
        val rawR = sqrt(dx * dx + dy * dy) / maxR * 100f
        return if (rawR < 8f) {
            0f to 0f
        } else {
            // Courbe logarithmique : les petits rayons sont compressés,
            // les grands rayons sont quasi-linéaires. pow(norm, 1.4) ∈ [0,1]
            val norm = ((rawR - 8f) / 92f).coerceIn(0f, 1f)   // 8..100 → 0..1
            val curved = norm.toDouble().let { Math.pow(it, 1.4) }.toFloat()
            val rClamped = (curved * 91f + 8f).coerceIn(8f, 99f)  // Max 99 — Parrot natif
            // Convention wire format Parrot : 0°=est(droite), sens anti-horaire — identique atan2 standard
            val tRaw     = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble()))
            val tClamped = ((tRaw + 360.0) % 360.0).toFloat()
            rClamped to tClamped
        }
    }

    // sendToDevice() SUPPRIMÉ — remplacé par eqDragFlow + sendSmoothed()

    // ── Animation rotative autour du cercle EQ (style Parrot OpenGL) ──
    val ringTransition = rememberInfiniteTransition(label = "eqRing")
    val ringAngle by ringTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing)
        ),
        label = "ringRotation"
    )
    val ringPulse by ringTransition.animateFloat(
        initialValue = 0.12f,
        targetValue  = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringPulse"
    )

    // ── Glow pulse : pulsation douce du halo autour du thumb ──
    val glowPulse by ringTransition.animateFloat(
        initialValue = 0.30f,
        targetValue  = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── DISQUE (remplit toute la hauteur hors bandeau) ───────────────
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(diskActive) {
                    // Gestionnaire unifié tap / double-tap / drag
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        if (!diskActive) return@awaitEachGesture

                        if (showPresetOverlay) {
                            presetOverlayDismissed = true
                        }

                        // ── ANTI-REBOUND : bloquer le polling EQ dès le premier contact ──
                        viewModel.markEqTouched()
                        viewModel.setUserInteracting(true)

                        val cxG = size.width / 2f
                        val cyG = size.height / 2f
                        val maxRG = minOf(cxG, cyG) * 0.72f

                        // ── Ignorer les appuis hors du cercle de l'EQ ─────────
                        val dxDown = down.position.x - cxG
                        val dyDown = down.position.y - cyG
                        val distDown = sqrt(dxDown * dxDown + dyDown * dyDown)
                        if (distDown > maxRG * 1.05f) return@awaitEachGesture

                        val startPos = down.position
                        val slop = viewConfiguration.touchSlop
                        var dragging = false
                        var tapPos: Offset? = null
                        val downMs = System.currentTimeMillis()

                        // ─ Phase 1 : suivre le doigt — cible LERP mise à jour ──
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull() ?: break
                            ch.consume()

                            if (!ch.pressed) { tapPos = ch.position; break }

                            if (!dragging && (ch.position - startPos).getDistance() > slop
                                && System.currentTimeMillis() - downMs >= 60L) {
                                dragging = true
                                isDragging = true   // démarre la boucle LERP
                            }

                            if (dragging) {
                                val (dr, dt) = computePolarF(ch.position.x, ch.position.y, cxG, cyG, maxRG)
                                rF = dr; thetaF = dt
                                // REAL_TIME_EQ_STREAMING : position RAW émise pendant le drag.
                                // Le flow sample(40ms) garantit 25 paquets/sec au DSP.
                                eqDragFlow.tryEmit(dr to dt)
                            }
                        }

                        if (dragging) {
                            // Fin de drag → snap les smooth sur la cible finale et envoyer
                            isDragging = false
                            viewModel.setUserInteracting(false)
                            rSmooth = rF; thetaSmooth = thetaF
                            sendSmoothed()
                            return@awaitEachGesture
                        }

                        // ─ Phase 2 : tap simple ou double-tap ─────────────────
                        val tp = tapPos ?: return@awaitEachGesture

                        val secondDown = withTimeoutOrNull(300L) {
                            awaitFirstDown(requireUnconsumed = false)
                        }

                        if (secondDown != null) {
                            secondDown.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                ev.changes.forEach { it.consume() }
                                if (ev.changes.none { it.pressed }) break
                            }
                            // Double-tap → reset centre (EQ plat) — instantané, pas de LERP
                            rF = 0f; thetaF = 0f
                            rSmooth = 0f; thetaSmooth = 0f
                            viewModel.setThumbEq(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                            viewModel.setUserInteracting(false)
                        } else {
                            // Single tap → placer le thumb à la position touchée.
                            val (tapR, tapTheta) = computePolarF(tp.x, tp.y, cxG, cyG, maxRG)
                            rF = tapR; thetaF = tapTheta
                            isTapAnimating = true
                            viewModel.setUserInteracting(false)
                        }
                    }
                }
        ) {
            val cxPx   = constraints.maxWidth  / 2f
            val cyPx   = constraints.maxHeight / 2f
            val maxR   = minOf(cxPx, cyPx) * 0.72f
            val lblR   = maxR + with(density) { 26.dp.toPx() }
            val thumbR = with(density) { 16.dp.toPx() }

            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width  / 2f
                val cy = size.height / 2f

                // ── Fond du disque — gradient radial teinté thème ──────────
                drawCircle(
                    brush  = Brush.radialGradient(
                        colors = if (diskActive) listOf(
                            t.accent.copy(alpha = 0.10f),
                            t.accent.copy(alpha = 0.04f),
                            t.background
                        ) else listOf(
                            Color(0xFF2A2A2A), Color(0xFF1C1C1C), Color(0xFF111111)
                        ),
                        center = Offset(cx, cy),
                        radius = maxR
                    ),
                    radius = maxR,
                    center = Offset(cx, cy)
                )

                // ── Contour statique du disque EQ ─────────────────────────
                drawCircle(
                    color  = t.textSecondary.copy(alpha = if (diskActive) 0.18f else 0.10f),
                    radius = maxR,
                    center = Offset(cx, cy),
                    style  = Stroke(width = 1f)
                )

                // (couronne sunburst retirée — design épuré)

                // ── 5 anneaux concentriques (fidèle REFERENCE_UI Parrot) ──
                if (diskActive) {
                    for (frac in listOf(0.20f, 0.40f, 0.60f, 0.80f, 1.00f)) {
                        drawCircle(
                            color  = t.textSecondary.copy(
                                alpha = if (frac == 1.00f) 0.14f else 0.07f
                            ),
                            radius = maxR * frac,
                            center = Offset(cx, cy),
                            style  = Stroke(width = if (frac == 1.00f) 1f else 0.5f)
                        )
                    }
                }

                // ── Zone preset accentuée quand le curseur est proche ─────
                if (diskActive && nearestPreset != null) {
                    val np = nearestPreset!!
                    val canvasAngle = (360f - np.angleDeg) % 360f
                    drawArc(
                        color      = t.accent.copy(alpha = 0.06f),
                        startAngle = canvasAngle - 15f,
                        sweepAngle = 30f,
                        useCenter  = true,
                        topLeft    = Offset(cx - maxR, cy - maxR),
                        size       = Size(maxR * 2f, maxR * 2f)
                    )
                }

                // ── Arc rotatif animé — style Parrot OpenGL ──────────────
                if (diskActive) {
                    val arcRect = Rect(
                        cx - maxR, cy - maxR,
                        cx + maxR, cy + maxR
                    )
                    rotate(ringAngle, Offset(cx, cy)) {
                        drawArc(
                            color      = t.accent.copy(alpha = ringPulse),
                            startAngle = 0f,
                            sweepAngle = 120f,
                            useCenter  = false,
                            topLeft    = arcRect.topLeft,
                            size       = arcRect.size,
                            style      = Stroke(
                                width    = 2f,
                                cap      = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 8f), 0f
                                )
                            )
                        )
                    }
                    rotate(ringAngle + 180f, Offset(cx, cy)) {
                        drawArc(
                            color      = t.accent.copy(alpha = ringPulse * 0.6f),
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter  = false,
                            topLeft    = arcRect.topLeft,
                            size       = arcRect.size,
                            style      = Stroke(
                                width    = 1.5f,
                                cap      = StrokeCap.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8f, 10f), 0f
                                )
                            )
                        )
                    }
                }

                // ── Point central permanent (EQ plat, repère visuel) ──────
                val centerDotR = thumbR * 0.40f
                drawCircle(
                    color  = t.accent.copy(alpha = if (diskActive) 0.35f else 0.12f),
                    radius = centerDotR,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color  = t.accent.copy(alpha = if (diskActive) 0.70f else 0.20f),
                    radius = centerDotR,
                    center = Offset(cx, cy),
                    style  = Stroke(width = 1.5f)
                )

                // ── Curseur EQ — point orange simple + halo subtil ──
                if (diskActive) {
                    val aRad = Math.toRadians(thetaSmooth.toDouble()).toFloat()
                    val dist = (rSmooth / 100f * maxR)
                    // Convention 0°=est, CCW → cos pour X, -sin pour Y (coordonnées écran)
                    val tx   = cx + dist * cos(aRad)
                    val ty   = cy - dist * sin(aRad)

                    // Halo doux radial
                    drawCircle(
                        color  = t.accent.copy(alpha = glowPulse * 0.18f),
                        radius = thumbR * 2.2f,
                        center = Offset(tx, ty)
                    )
                    drawCircle(
                        color  = t.accent.copy(alpha = glowPulse * 0.10f),
                        radius = thumbR * 1.5f,
                        center = Offset(tx, ty)
                    )
                    // Corps orange plein
                    drawCircle(
                        color  = t.accent,
                        radius = thumbR,
                        center = Offset(tx, ty)
                    )
                }
            }

            // ── Labels presets en périphérie ─────────────────────────────
            EQ_PRESETS.forEach { p ->
                val rad    = Math.toRadians(p.angleDeg.toDouble())
                // Convention 0°=est, CCW → cos/sin standard
                val lx     = (cxPx + lblR * cos(rad)).toFloat()
                val ly     = (cyPx - lblR * sin(rad)).toFloat()
                val isNear = nearestPreset == p
                Text(
                    p.label,
                    color      = if (isNear && diskActive) t.accent
                                 else t.textPrimary.copy(
                                     alpha = if (diskActive) 0.75f else 0.35f),
                    fontSize   = if (isNear) 12.sp else 10.sp,
                    fontWeight = if (isNear) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = RobotoCondensed,
                    modifier   = Modifier.layout { measurable, c ->
                        val pl = measurable.measure(c)
                        layout(pl.width, pl.height) {
                            pl.placeRelative(
                                (lx - pl.width  / 2f).roundToInt(),
                                (ly - pl.height / 2f).roundToInt()
                            )
                        }
                    }
                )
            }

            // ── Warning overlay quand l'EQ est désactivé ────────────────
            if (!diskActive) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable {
                            viewModel.setEqEnabled(true)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Appuyez pour activer\nl'égaliseur",
                        color      = t.textPrimary.copy(alpha = 0.80f),
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RobotoCondensed,
                        textAlign  = TextAlign.Center
                    )
                }
            } else if (showPresetOverlay && !presetOverlayDismissed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 34.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(t.background.copy(alpha = 0.88f))
                            .border(1.dp, t.accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "PRESET ACTIF",
                            color = t.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoCondensed,
                            letterSpacing = 1.sp
                        )
                        Text(
                            activePreset?.uppercase() ?: "",
                            color = t.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoCondensed,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Touchez le disque pour repasser en mode manuel.",
                            color = t.textSecondary,
                            fontSize = 12.sp,
                            fontFamily = RobotoCondensed,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // ── Mini display gains 5 bandes — résolution float continue ─────
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val bandNames = listOf("Bass", "M.Low", "Mid", "M.Hi", "High")
            val displayBands = if (diskActive && rF > 0.5f) currentBands
                               else listOf(0.0, 0.0, 0.0, 0.0, 0.0)
            displayBands.forEachIndexed { i, v ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${if (v >= 0) "+" else ""}${String.format("%.1f", v)}",
                        color = if (!diskActive) t.textSecondary.copy(alpha = 0.4f)
                                else if (v >= 0) t.accent else Color(0xFF64B5F6),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        fontFamily = RobotoCondensed
                    )
                    Text(
                        bandNames[i],
                        color = t.textSecondary.copy(alpha = if (diskActive) 0.65f else 0.35f),
                        fontSize = 8.sp, fontFamily = RobotoCondensed
                    )
                }
            }
        }

        // ── Bouton EQ ON / OFF — pilule premium animée ────────────────
        val eqBgAlpha by animateFloatAsState(
            targetValue = if (diskActive) 1f else 0f,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "eqBtnBg"
        )
        val eqGlowAlpha by animateFloatAsState(
            targetValue = if (diskActive) 0.25f else 0f,
            animationSpec = tween(400), label = "eqBtnGlow"
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Halo lumineux arrière — scale > 1 pour déborder autour de la pilule
                if (eqGlowAlpha > 0.01f) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = 1.12f
                                scaleY = 1.25f
                            }
                            .clip(RoundedCornerShape(50.dp))
                            .background(t.accent.copy(alpha = eqGlowAlpha))
                    )
                }
                // Pilule principale
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(
                            if (diskActive) t.accent
                            else t.cardBg
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (diskActive) t.accent
                                    else t.textSecondary.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50.dp)
                        )
                        .clickable { viewModel.setEqEnabled(!diskActive) }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (diskActive) t.background else t.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        if (diskActive) "ON" else "OFF",
                        color = if (diskActive) t.background else t.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = RobotoCondensed,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
//  PAGE 2 â€” Ã‰GALISEUR â€” PAD ORANGE (ThumbDisk uniquement, zÃ©ro bouton preset)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
private fun PageEqualizer(viewModel: ZikViewModel, onMenuOpen: () -> Unit = {}) {
    val t = LocalZikSkin.current
    var showCreatePreset by remember { mutableStateOf(false) }
    var openOnPresets by remember { mutableStateOf(false) }

    if (showCreatePreset) {
        CreatePresetScreen(
            viewModel = viewModel,
            onDismiss = { showCreatePreset = false },
            startOnPresets = openOnPresets
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        ParrotTopBar(title = "Egaliseur", onMenuClick = onMenuOpen)
        Box(Modifier.weight(1f)) {
            ThumbDisk(viewModel)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(t.cardBg)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    openOnPresets = true
                    showCreatePreset = true
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Text("Mes presets", fontFamily = RobotoCondensed, color = t.textPrimary)
            }
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .height(24.dp)
                    .width(1.dp)
                    .background(t.accent.copy(alpha = 0.55f))
            )
            TextButton(
                onClick = {
                    openOnPresets = false
                    showCreatePreset = true
                },
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Text("Creer un preset", fontFamily = RobotoCondensed, color = t.textPrimary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 3 — CONCERT HALL  (ref: zik_ref_concert_hall.xml 1080×1920)
//  degree text [0,294][1080,594] = 100dp zone at top, center 72sp
//  Arc visuel plein ecran + idem Parrot legacy tabs en bas
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageConcertHall(
    roomSize: String?,
    concertAngle: Int,
    concertEnabled: Boolean?,
    viewModel: ZikViewModel,
    onMenuOpen: () -> Unit = {}
) {
    val t     = LocalZikSkin.current
    val isOn  = concertEnabled == true

    // Mapping salle → index (original Parrot SoundRooms: concert=0, jazz=1, living=2, silent=3)
    // Index 0 = plus grand cercle (outermost), index 3 = plus petit (innermost)
    val roomIdx = when (roomSize) {
        "concert" -> 0; "jazz" -> 1; "living" -> 2; "silent" -> 3; else -> 0
    }
    // Noms décompilés (AConcertHall.java : mNamesRooms[0..3])
    val roomNames = listOf("Concert Hall", "Jazz Club", "Living Room", "Silent Room")
    val roomKeys  = listOf("concert", "jazz", "living", "silent")

    Column(Modifier.fillMaxSize()) {
        ParrotTopBar(title = "Parrot Concert Hall", onMenuClick = onMenuOpen)

        // ── Zone des cercles — drag vertical pour changer l'angle ─────
        val angleState = rememberUpdatedState(concertAngle)
        Box(
            Modifier
                .weight(1f)
                .background(t.background)
                .pointerInput(isOn) {
                    if (!isOn) return@pointerInput
                    var startAngle = 0
                    var accumY = 0f
                    var lastAngle = 0
                    detectDragGestures(
                        onDragStart = {
                            startAngle = angleState.value
                            accumY = 0f
                        },
                        onDrag = { _, dragAmount ->
                            accumY += dragAmount.y
                            lastAngle = (startAngle + (accumY / 3.7f).toInt()).coerceIn(30, 180)
                            viewModel.setAngle(lastAngle)
                        },
                        onDragEnd = {
                            val snapped = ((lastAngle + 15) / 30) * 30
                            viewModel.setAngle(snapped.coerceIn(30, 180))
                        }
                    )
                }
        ) {
            // ── 1. Canvas : 4 cercles, arc orange, halo + enceintes ──
            Canvas(
                Modifier
                    .fillMaxSize()
                    .alpha(if (isOn) 1f else 0.35f)
            ) {
                val W = size.width;  val H = size.height
                val cx = W / 2f
                val cy = H * 0.46f          // remonté pour laisser de la marge en bas

                val circleRadius = W * 0.42f
                val sw = circleRadius / 4f - 5f

                val rects = Array(4) { i ->
                    val inset = sw * i
                    Rect(cx - circleRadius + inset, cy - circleRadius + inset * 2f,
                         cx + circleRadius - inset, cy + circleRadius)
                }

                val borderColor = t.textPrimary
                for (i in 0 until 4) {
                    val a = if (i < roomIdx) 0.16f else 0.31f
                    val rect = rects[i]
                    val rx = (rect.right - rect.left) / 2f
                    val ry = (rect.bottom - rect.top) / 2f
                    val rcx = rect.left + rx; val rcy = rect.top + ry
                    drawOval(borderColor.copy(alpha = a),
                        Offset(rcx - rx, rcy - ry), Size(rx * 2f, ry * 2f), style = Stroke(3f))
                }

                // ── Arc orange sur le cercle sélectionné ──
                if (isOn && roomIdx in 0..3) {
                    val rect = rects[roomIdx]
                    val rx = (rect.right - rect.left) / 2f
                    val ry = (rect.bottom - rect.top) / 2f
                    val rcx = rect.left + rx; val rcy = rect.top + ry
                    val halfSpread = concertAngle * 0.75f

                    drawArc(t.accent, -(halfSpread + 90f), halfSpread * 2f, false,
                        Offset(rcx - rx, rcy - ry), Size(rx * 2f, ry * 2f), style = Stroke(7f))

                    // ── Halo + icône d'enceinte aux 2 extrémités de l'arc ──
                    val angleL = Math.toRadians((-90.0 - halfSpread).toDouble())
                    val angleR = Math.toRadians((-90.0 + halfSpread).toDouble())
                    for (a in listOf(angleL, angleR)) {
                        val px = rcx + rx * cos(a).toFloat()
                        val py = rcy + ry * sin(a).toFloat()
                        // Halo concentrique (original: circleFactor circles, alpha=7)
                        for (c in 5 downTo 1) {
                            drawCircle(t.textPrimary.copy(alpha = 0.027f),
                                radius = c * rx * 0.18f, center = Offset(px, py))
                        }
                        // Enceinte : petit cercle plein orange + triangle intérieur
                        drawCircle(t.accent, radius = 14f, center = Offset(px, py))
                        drawCircle(t.background, radius = 9f, center = Offset(px, py))
                        // Cône de haut-parleur (triangle simplifié)
                        val triPath = Path().apply {
                            moveTo(px - 4f, py - 5f)
                            lineTo(px + 5f, py)
                            lineTo(px - 4f, py + 5f)
                            close()
                        }
                        drawPath(triPath, t.accent)
                    }
                }
            }

            // ── 2. Noms des salles centrés dans chaque anneau ──
            // Positionnement fidèle Parrot : textY démarre en haut du cercle externe
            // et avance de strokeWidth * 2 par salle
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val W = constraints.maxWidth.toFloat()
                val H = constraints.maxHeight.toFloat()
                val cx = W / 2f;  val cy = H * 0.46f
                val circleRadius = W * 0.42f; val sw = circleRadius / 4f - 5f
                val density = LocalDensity.current
                val textSizePx = with(density) { 15.sp.toPx() }
                val nameOffsetPx = with(density) { 7.dp.toPx() }

                // Y initial = haut du cercle + demi-bande + textSize + offset
                val topCircle = cy - circleRadius
                val textYStart = topCircle + sw / 2f + textSizePx + nameOffsetPx

                roomNames.forEachIndexed { i, name ->
                    val textCenterY = textYStart + i * sw * 2f
                    val isSel = i == roomIdx
                    val lines = name.split(" ")
                    // ajustement vertical si 2 lignes
                    val lineH = with(density) { (if (isSel) 17.sp else 15.sp).toPx() }
                    val totalH = lineH * lines.size
                    val offsetY = textCenterY - totalH / 2f

                    Column(
                        Modifier
                            .offset { with(density) { IntOffset((cx - 60.dp.toPx()).toInt(), offsetY.toInt()) } }
                            .width(120.dp)
                            .alpha(if (isOn) (if (isSel) 1f else 0.60f) else 0.25f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                if (!isOn) viewModel.setConcertHallEnabled(true)
                                viewModel.setRoomSize(roomKeys[i])
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        lines.forEach { line ->
                            Text(line, color = t.textPrimary,
                                fontSize = if (isSel) 17.sp else 15.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = RobotoCondensed, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // ── 3. Angle en grand — centré haut (ref Parrot [0,294][1080,594] = 100dp zone) ──
            Text(
                if (isOn) "${concertAngle}°" else "–",
                color      = if (isOn) t.accent else t.textSecondary.copy(0.45f),
                fontSize   = 72.sp, fontWeight = FontWeight.Thin,
                fontFamily = RobotoCondensed, textAlign = TextAlign.Center,
                modifier   = Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
            )
        } // inner Box (cercles)

        // ── 4. ON/OFF + indication drag ───────────────────────────────────────
        // Glissez verticalement sur les cercles pour changer l'angle (snap 30°)
        Column(
            Modifier
                .fillMaxWidth()
                .background(t.background)
                .padding(top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(if (isOn) t.accent else t.cardBg)
                    .border(1.5.dp,
                        if (isOn) t.accent else t.textSecondary.copy(0.50f),
                        RoundedCornerShape(50.dp))
                    .clickable { viewModel.setConcertHallEnabled(!isOn) }
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isOn) "ON" else "OFF",
                    color = if (isOn) t.background else t.textPrimary,
                    fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, fontFamily = RobotoCondensed)
            }
            if (isOn) {
                Text("Glissez sur les cercles pour régler l'angle",
                    color = t.textSecondary.copy(alpha = 0.45f),
                    fontSize = 10.sp, fontFamily = RobotoCondensed)
            }
        }
    } // Column
}


// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 4 — SYSTEME (debug page, non exposee dans le pager principal)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageSystem(
    battery: Int?,
    isCharging: Boolean,
    macAddress: String,
    firmwareVersion: String,
    lastXmlFrames: List<String>,
    viewModel: ZikViewModel
) {
    val t = LocalZikSkin.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("SYSTEME DEBUG", color = t.textSecondary, fontSize = 11.sp,
            letterSpacing = 1.5.sp, fontFamily = RobotoCondensed)
        Divider(color = t.dark, thickness = 0.5.dp)
        Text("MAC : $macAddress", color = t.textPrimary, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace)
        Text("FW  : $firmwareVersion", color = t.textPrimary, fontSize = 13.sp,
            fontFamily = FontFamily.Monospace)
        Text("BAT : ${battery ?: "--"}% ${if (isCharging) "(charge)" else ""}",
            color = t.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))
        Text("XML FRAMES", color = t.textSecondary, fontSize = 10.sp, letterSpacing = 1.2.sp)
        lastXmlFrames.takeLast(10).forEach { frame ->
            Text(frame, color = t.textSecondary, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(t.cardBg)
                    .padding(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 5 — REGLAGES  (ref: zik_ref_reglages_top.xml 1080x1920)
//  Rows 213px = 71dp h. Label [30,x][693,x]. Value [693,x][1050,x].
//  Row 1 : Nom du Parrot Zik  [30,222][693/1050,432]
//  Row 2 : En appel / Street mode
//  Row 3 : Capteur de presence + switch
//  Row 4 : Auto-connexion + switch
// ─────────────────────────────────────────────────────────────────────────────
//  CREATE PRESET SCREEN — EQ Paramétrique 5 bandes + Concert Hall
//  Inspiré de ProducerModeActivity Parrot (fragment_equalizer.xml)
//  Gains : -12 dB .. +12 dB  |  Fréquences fixes : 70, 381, 960, 2419, 11000 Hz
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CreatePresetScreen(
    viewModel: ZikViewModel,
    onDismiss: () -> Unit,
    startOnPresets: Boolean = false
) {
    val t = LocalZikSkin.current
    val presets by viewModel.userEqPresets.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()

    val bandLabels  = listOf("70 Hz", "381 Hz", "960 Hz", "2.4 kHz", "11 kHz")
    var gains       = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }
    var chEnabled   by remember { mutableStateOf(false) }
    var chRoom      by remember { mutableStateOf("concert") }
    var chAngle     by remember { mutableStateOf(90) }
    var nameInput   by remember { mutableStateOf("") }
    var saveError   by remember { mutableStateOf<String?>(null) }
    var editingPresetId by remember { mutableStateOf<Long?>(null) }
    var presetsMode by rememberSaveable { mutableStateOf(startOnPresets) }
    var showSaveNameDialog by remember { mutableStateOf(false) }
    var renamePresetId by remember { mutableStateOf<Long?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    fun resetEditorState() {
        editingPresetId = null
        nameInput = ""
        gains.indices.forEach { gains[it] = 0f }
        chEnabled = false
        chRoom = "concert"
        chAngle = 90
        saveError = null
        showSaveNameDialog = false
    }

    fun loadPresetIntoEditor(p: ZikViewModel.UserEqPreset) {
        editingPresetId = p.id
        nameInput = p.name
        val src = if (p.gains.size == 5) p.gains else listOf(
            p.eq.v1.toFloat(), p.eq.v2.toFloat(), p.eq.v3.toFloat(), p.eq.v4.toFloat(), p.eq.v5.toFloat()
        )
        gains.indices.forEach { gains[it] = src.getOrElse(it) { 0f }.coerceIn(-12f, 12f) }
        chEnabled = p.concertHallEnabled
        chRoom = p.concertHallRoom
        chAngle = p.concertHallAngle
        saveError = null
    }

    fun finishPersistSuccess() {
        resetEditorState()
        presetsMode = true
    }

    fun persistCurrentPreset(targetName: String): String? {
        return if (editingPresetId == null) {
            viewModel.saveParamPreset(
                name = targetName,
                gains = gains.toList(),
                concertHallEnabled = chEnabled,
                concertHallRoom = chRoom,
                concertHallAngle = chAngle
            )
        } else {
            val presetId = editingPresetId ?: return "Preset introuvable"
            viewModel.updateParamPreset(
                presetId = presetId,
                name = targetName,
                gains = gains.toList(),
                concertHallEnabled = chEnabled,
                concertHallRoom = chRoom,
                concertHallAngle = chAngle
            )
        }
    }

    // Prévisualisation temps réel (envoi au casque quand les sliders bougent).
    // presetsMode intentionnellement ABSENT des clés : changer d'onglet ne doit
    // pas réinitialiser l'EQ ; seule une vraie modification de valeur déclenche l'envoi.
    LaunchedEffect(gains.toList(), chEnabled, chRoom, chAngle) {
        if (presetsMode) return@LaunchedEffect
        viewModel.previewParamEq(gains.toList())
        viewModel.setConcertHallEnabled(chEnabled)
        if (chEnabled) {
            viewModel.setRoomSize(chRoom)
            viewModel.setAngle(chAngle)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(t.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(t.cardBg)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = t.textPrimary)
                }
                Text(
                    when {
                        presetsMode -> "Mes presets"
                        editingPresetId != null -> "Modifier le preset"
                        else -> "Creer / editer"
                    },
                    color = t.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RobotoCondensed,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text("Retour EQ", color = t.textPrimary, fontFamily = RobotoCondensed, fontSize = 12.sp)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            presetsMode = true
                            saveError = null
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (presetsMode) t.accent else t.textSecondary.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (presetsMode) t.accent.copy(alpha = 0.10f) else Color.Transparent,
                            contentColor = if (presetsMode) t.accent else t.textSecondary
                        )
                    ) { Text("Mes presets", fontFamily = RobotoCondensed, fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            presetsMode = false
                            if (editingPresetId == null) {
                                if (activePreset == null) resetEditorState()
                                saveError = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if (!presetsMode) t.accent else t.textSecondary.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            backgroundColor = if (!presetsMode) t.accent.copy(alpha = 0.10f) else Color.Transparent,
                            contentColor = if (!presetsMode) t.accent else t.textSecondary
                        )
                    ) { Text("Creer / editer", fontFamily = RobotoCondensed, fontSize = 12.sp) }
                }

                if (presetsMode) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(t.cardBg)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "GERER MES PRESETS",
                            color = t.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoCondensed,
                            letterSpacing = 1.sp
                        )
                        if (presets.isEmpty()) {
                            Text(
                                "Aucun preset sauvegarde pour le moment.",
                                color = t.textSecondary,
                                fontSize = 13.sp,
                                fontFamily = RobotoCondensed
                            )
                            TextButton(
                                onClick = {
                                    presetsMode = false
                                    resetEditorState()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Creer un preset", fontFamily = RobotoCondensed, color = t.textPrimary)
                            }
                        } else {
                            presets.forEach { p ->
                                val isActive = activePreset.equals(p.name, ignoreCase = true)
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isActive) t.accent.copy(alpha = 0.12f) else t.dark.copy(alpha = 0.35f))
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                p.name,
                                                color = if (isActive) t.accent else t.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                                fontFamily = RobotoCondensed,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                if (isActive) "Preset actuellement applique" else "Preset personnalise",
                                                color = t.textSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = RobotoCondensed
                                            )
                                        }
                                        if (isActive) {
                                            Text(
                                                "ACTIF",
                                                color = t.accent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = RobotoCondensed
                                            )
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.applyParamPreset(p.id)
                                                val src = if (p.gains.size == 5) p.gains else listOf(
                                                    p.eq.v1.toFloat(), p.eq.v2.toFloat(), p.eq.v3.toFloat(),
                                                    p.eq.v4.toFloat(), p.eq.v5.toFloat()
                                                )
                                                gains.indices.forEach { gains[it] = src[it].coerceIn(-12f, 12f) }
                                                chEnabled = p.concertHallEnabled
                                                chRoom = p.concertHallRoom
                                                chAngle = p.concertHallAngle
                                                nameInput = p.name
                                                saveError = null
                                            },
                                            modifier = Modifier.weight(0.95f),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) {
                                            Text("Appliquer", fontSize = 10.sp, fontFamily = RobotoCondensed, color = t.textPrimary, maxLines = 1)
                                        }
                                        TextButton(
                                            onClick = {
                                                presetsMode = false
                                                loadPresetIntoEditor(p)
                                            },
                                            modifier = Modifier.weight(0.82f),
                                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)
                                        ) {
                                            Text("Editer", fontSize = 10.sp, fontFamily = RobotoCondensed, color = t.textPrimary, maxLines = 1)
                                        }
                                        TextButton(
                                            onClick = {
                                                renamePresetId = p.id
                                                renameInput = p.name
                                                renameError = null
                                            },
                                            modifier = Modifier.weight(0.98f),
                                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 0.dp)
                                        ) {
                                            Text("Renommer", fontSize = 10.sp, fontFamily = RobotoCondensed, color = t.textPrimary, maxLines = 1)
                                        }
                                        IconButton(
                                            onClick = {
                                            if (editingPresetId == p.id) resetEditorState()
                                            if (renamePresetId == p.id) {
                                                renamePresetId = null
                                                renameInput = ""
                                                renameError = null
                                            }
                                            viewModel.deleteParamPreset(p.id)
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Supprimer preset",
                                                tint = t.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (editingPresetId != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(t.cardBg)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "PRESET EN COURS D'EDITION",
                                color = t.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = RobotoCondensed,
                                letterSpacing = 1.sp
                            )
                            Text(
                                nameInput,
                                color = t.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = RobotoCondensed
                            )
                            Text(
                                "Le renommage se fait depuis l'onglet Mes presets.",
                                color = t.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = RobotoCondensed
                            )
                        }
                    }

                // ── Section EQ 5 bandes ──────────────────────────────────────
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.cardBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "ÉGALISEUR PARAMÉTRIQUE",
                        color = t.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RobotoCondensed,
                        letterSpacing = 1.sp
                    )
                    gains.indices.forEach { idx ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                bandLabels[idx],
                                color = t.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = RobotoCondensed,
                                modifier = Modifier.width(54.dp)
                            )
                            Slider(
                                value = gains[idx],
                                onValueChange = { v ->
                                    gains[idx] = (v * 2).roundToInt() / 2f  // pas 0.5 dB
                                    coroutineScope.launch { viewModel.previewParamEq(gains.toList()) }
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = t.accent,
                                    activeTrackColor = t.accent,
                                    inactiveTrackColor = t.textSecondary.copy(alpha = 0.3f)
                                )
                            )
                            val gVal = gains[idx]
                            Text(
                                text = if (gVal >= 0f) "+${"%.1f".format(gVal)}" else "${"%.1f".format(gVal)}",
                                color = if (gVal != 0f) t.accent else t.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = RobotoCondensed,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(42.dp)
                            )
                        }
                    }
                    // Bouton reset bandes
                    TextButton(
                        onClick = { gains.indices.forEach { gains[it] = 0f } },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Remettre à 0", color = t.textSecondary, fontSize = 12.sp, fontFamily = RobotoCondensed)
                    }
                }

                // ── Section Concert Hall ─────────────────────────────────────
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.cardBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "CONCERT HALL",
                            color = t.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RobotoCondensed,
                            letterSpacing = 1.sp
                        )
                        Switch(
                            checked = chEnabled,
                            onCheckedChange = { chEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = t.accent,
                                checkedTrackColor = t.accent.copy(alpha = 0.4f)
                            )
                        )
                    }
                    if (chEnabled) {
                        // Sélecteur de salle
                        val rooms = listOf("silent" to "Silent", "living" to "Living", "jazz" to "Jazz", "concert" to "Concert")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rooms.forEach { (key, label) ->
                                val sel = chRoom == key
                                OutlinedButton(
                                    onClick = { chRoom = key },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(1.dp, if (sel) t.accent else t.textSecondary.copy(alpha = 0.3f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        backgroundColor = if (sel) t.accent.copy(alpha = 0.15f) else Color.Transparent,
                                        contentColor = if (sel) t.accent else t.textSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, fontFamily = RobotoCondensed, maxLines = 1)
                                }
                            }
                        }
                        // Sélecteur d'angle
                        Text("Angle", color = t.textSecondary, fontSize = 12.sp, fontFamily = RobotoCondensed)
                        val angles = listOf(30, 60, 90, 120, 150, 180)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            angles.forEach { a ->
                                val sel = chAngle == a
                                TextButton(
                                    onClick = { chAngle = a },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sel) t.accent.copy(alpha = 0.15f) else Color.Transparent),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text(
                                        "$a°",
                                        fontSize = 12.sp,
                                        fontFamily = RobotoCondensed,
                                        color = if (sel) t.accent else t.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(t.cardBg)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (editingPresetId == null) "ENREGISTREMENT" else "MODIFICATION",
                        color = t.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RobotoCondensed,
                        letterSpacing = 1.sp
                    )
                    Text(
                        if (editingPresetId == null)
                            "Le nom sera demande au moment d'enregistrer le preset."
                        else
                            "Les reglages seront enregistres sur le preset actuel. Le renommage se fait depuis Mes presets.",
                        color = t.textSecondary,
                        fontSize = 12.sp,
                        fontFamily = RobotoCondensed
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!saveError.isNullOrBlank()) {
                            Text(
                                saveError ?: "",
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                fontFamily = RobotoCondensed,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (editingPresetId != null) {
                            TextButton(onClick = { resetEditorState() }) {
                                Text(
                                    "Annuler edition",
                                    fontSize = 11.sp,
                                    fontFamily = RobotoCondensed,
                                    color = t.textPrimary
                                )
                            }
                        }
                    }
                }

                // Espacement bas pour le bouton flottant
                if (!presetsMode) Spacer(Modifier.height(72.dp))
                }
            }
        }

        if (!presetsMode) {
            // ── Bouton Sauvegarder (flottant en bas) ─────────────────────────
            Button(
                onClick = {
                    saveError = null
                    if (editingPresetId == null) {
                        showSaveNameDialog = true
                    } else {
                        val err = persistCurrentPreset(nameInput)
                        if (err == null) finishPersistSuccess() else saveError = err
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = t.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (editingPresetId == null) "ENREGISTRER LE PRESET" else "ENREGISTRER LES MODIFICATIONS",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = RobotoCondensed,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        if (showSaveNameDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSaveNameDialog = false
                    saveError = null
                },
                title = {
                    Text("Nom du preset", fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Choisissez le nom du preset a enregistrer.",
                            color = t.textSecondary,
                            fontSize = 12.sp,
                            fontFamily = RobotoCondensed
                        )
                        TextField(
                            value = nameInput,
                            onValueChange = {
                                if (it.length <= 35) {
                                    nameInput = it
                                    saveError = null
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("Ex: Basses", fontFamily = RobotoCondensed, color = t.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.textFieldColors(
                                textColor = t.textPrimary,
                                backgroundColor = t.dark.copy(alpha = 0.5f),
                                cursorColor = t.accent,
                                focusedIndicatorColor = t.accent,
                                unfocusedIndicatorColor = t.textSecondary.copy(alpha = 0.3f)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("${nameInput.length}/35", fontSize = 11.sp, color = t.textSecondary, fontFamily = RobotoCondensed)
                        }
                        if (!saveError.isNullOrBlank()) {
                            Text(saveError ?: "", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontFamily = RobotoCondensed)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val err = persistCurrentPreset(nameInput)
                        if (err == null) {
                            showSaveNameDialog = false
                            finishPersistSuccess()
                        } else {
                            saveError = err
                        }
                    }) {
                        Text("Enregistrer", fontFamily = RobotoCondensed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSaveNameDialog = false
                        saveError = null
                    }) {
                        Text("Annuler", fontFamily = RobotoCondensed)
                    }
                }
            )
        }

        if (renamePresetId != null) {
            AlertDialog(
                onDismissRequest = {
                    renamePresetId = null
                    renameInput = ""
                    renameError = null
                },
                title = {
                    Text("Renommer le preset", fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = renameInput,
                            onValueChange = {
                                if (it.length <= 35) {
                                    renameInput = it
                                    renameError = null
                                }
                            },
                            singleLine = true,
                            placeholder = { Text("Nom du preset", fontFamily = RobotoCondensed, color = t.textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.textFieldColors(
                                textColor = t.textPrimary,
                                backgroundColor = t.dark.copy(alpha = 0.5f),
                                cursorColor = t.accent,
                                focusedIndicatorColor = t.accent,
                                unfocusedIndicatorColor = t.textSecondary.copy(alpha = 0.3f)
                            )
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("${renameInput.length}/35", fontSize = 11.sp, color = t.textSecondary, fontFamily = RobotoCondensed)
                        }
                        if (!renameError.isNullOrBlank()) {
                            Text(renameError ?: "", color = Color(0xFFFF6B6B), fontSize = 12.sp, fontFamily = RobotoCondensed)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val presetId = renamePresetId ?: return@TextButton
                        val err = viewModel.renameParamPreset(presetId, renameInput)
                        if (err == null) {
                            if (editingPresetId == presetId) nameInput = renameInput.trim().take(35)
                            renamePresetId = null
                            renameInput = ""
                            renameError = null
                        } else {
                            renameError = err
                        }
                    }) {
                        Text("Renommer", fontFamily = RobotoCondensed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        renamePresetId = null
                        renameInput = ""
                        renameError = null
                    }) {
                        Text("Annuler", fontFamily = RobotoCondensed)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PAGE 4 — PARAMÈTRES
//  Row 5 : Notifications de batterie + switch
//  Row 6 : Alerte batterie faible + switch
//  Row 7 : Arret automatique (dialogue)
//  Row 8 : Annonce vocale + switch
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageSettings(
    battery: Int?,
    isCharging: Boolean,
    macAddress: String,
    presenceSensor: Boolean?,
    onTogglePresence: (Boolean) -> Unit,
    skinKey: String,
    onSkinPick: (String) -> Unit,
    viewModel: ZikViewModel,
    onMenuOpen: () -> Unit = {}
) {
    val t = LocalZikSkin.current
    val context = LocalContext.current
    val coffeeUrl = "https://buymeacoffee.com/rmdaye"
    val prefs = remember(context) { context.getSharedPreferences("zik_prefs", Context.MODE_PRIVATE) }
    var showBatteryNotification by remember {
        mutableStateOf(prefs.getBoolean("show_battery_notification", false))
    }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showBatteryNotification = granted
        prefs.edit().putBoolean("show_battery_notification", granted).apply()
        context.sendBroadcast(
            Intent(ZikBluetoothService.ACTION_SET_NOTIFICATION_PREF).putExtra("show", granted)
        )
    }
    val autoConnection   by viewModel.autoConnection.collectAsState()
    val ttsEnabled       by viewModel.ttsEnabled.collectAsState()
    val autoPowerOff     by viewModel.autoPowerOff.collectAsState()
    val batteryCalibWarn by viewModel.batteryCalibWarning.collectAsState()
    val deviceName       by viewModel.deviceName.collectAsState()
    val phoneMode        by viewModel.phoneMode.collectAsState()
    var showAutoPowerOffDialog by remember { mutableStateOf(false) }
    var showThemeDialog        by remember { mutableStateOf(false) }
    var showPhoneModeDialog    by remember { mutableStateOf(false) }
    var showNameDialog         by remember { mutableStateOf(false) }
    var pendingDeviceName      by remember { mutableStateOf("") }
    var nameError              by remember { mutableStateOf<String?>(null) }



    // Labels mode téléphonique ANC
    val phoneModes = listOf(
        "anc"    to "Réduction de bruit",
        "aoc"    to "Réduction de bruit",
        "street" to "Street mode",
        "off"    to "Désactivé"
    )
    val phoneModeLabel = phoneModes.firstOrNull { it.first == phoneMode }?.second ?: phoneMode

    val apoPairs = listOf(0 to "Desactive", 5 to "5 minutes", 10 to "10 minutes",
                          20 to "20 minutes", 30 to "30 minutes", 60 to "1 heure")
    val apoLabel = apoPairs.firstOrNull { it.first == autoPowerOff }?.second
        ?: if (autoPowerOff == null) "Desactive" else "${autoPowerOff} min"

    if (showAutoPowerOffDialog) {
        AlertDialog(
            onDismissRequest = { showAutoPowerOffDialog = false },
            title = { Text("Arret automatique", fontWeight = FontWeight.Bold,
                fontFamily = RobotoCondensed) },
            text = {
                Column {
                    apoPairs.forEach { (minutes, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    viewModel.setAutoPowerOff(minutes)
                                    showAutoPowerOffDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = autoPowerOff == minutes ||
                                           (autoPowerOff == null && minutes == 0),
                                onClick  = {
                                    viewModel.setAutoPowerOff(minutes)
                                    showAutoPowerOffDialog = false
                                }
                            )
                            Text(label, fontSize = 15.sp, fontFamily = RobotoCondensed)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoPowerOffDialog = false }) {
                    Text("Fermer", fontFamily = RobotoCondensed)
                }
            }
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme", fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ZIK_SKINS.forEach { skin ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { onSkinPick(skin.key); showThemeDialog = false }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = skinKey == skin.key,
                                onClick  = { onSkinPick(skin.key); showThemeDialog = false }
                            )
                            // Pastille dual : moitié fond + moitié accent
                            Box(Modifier.size(28.dp)) {
                                Canvas(Modifier.fillMaxSize()) {
                                    // Moitié gauche = background du thème
                                    drawArc(skin.background, 90f, 180f, true, size = size)
                                    // Moitié droite = accent du thème
                                    drawArc(skin.accent, -90f, 180f, true, size = size)
                                    // Contour
                                    drawCircle(
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        radius = size.minDimension / 2f,
                                        style = Stroke(width = 1.5f)
                                    )
                                }
                            }
                            Text(skin.label.replace("\n", " "), fontSize = 15.sp,
                                fontFamily = RobotoCondensed)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Fermer", fontFamily = RobotoCondensed)
                }
            }
        )
    }

    if (showPhoneModeDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneModeDialog = false },
            title = { Text("En appel", fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed) },
            text = {
                Column {
                    listOf("anc" to "Réduction de bruit", "street" to "Street mode", "off" to "Désactivé").forEach { (modeKey, modeLabel) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    viewModel.setPhoneMode(modeKey)
                                    showPhoneModeDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = phoneMode == modeKey || (phoneMode == "aoc" && modeKey == "anc"),
                                onClick  = {
                                    viewModel.setPhoneMode(modeKey)
                                    showPhoneModeDialog = false
                                }
                            )
                            Text(modeLabel, fontSize = 15.sp, fontFamily = RobotoCondensed)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhoneModeDialog = false }) {
                    Text("Fermer", fontFamily = RobotoCondensed)
                }
            }
        )
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = {
                showNameDialog = false
                nameError = null
            },
            title = { Text("Nom du Parrot Zik", fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = pendingDeviceName,
                        onValueChange = {
                            if (it.length <= 35) {
                                pendingDeviceName = it
                                nameError = null
                            }
                        },
                        singleLine = true,
                        placeholder = { Text("Ex: ZIK 3 RMDaye") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "${pendingDeviceName.length}/35",
                            fontSize = 11.sp,
                            color = if (pendingDeviceName.length > 35) Color(0xFFFF6B6B) else t.textSecondary,
                            fontFamily = RobotoCondensed
                        )
                    }
                    if (nameError != null) {
                        Text(
                            nameError ?: "",
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontFamily = RobotoCondensed
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = pendingDeviceName.trim()
                    when {
                        trimmed.isBlank() -> nameError = "Le nom ne peut pas etre vide"
                        trimmed.length > 35 -> nameError = "Maximum 35 caracteres"
                        else -> {
                            viewModel.setFriendlyName(trimmed)
                            showNameDialog = false
                            nameError = null
                        }
                    }
                }) {
                    Text("Appliquer", fontFamily = RobotoCondensed)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNameDialog = false
                    nameError = null
                }) {
                    Text("Annuler", fontFamily = RobotoCondensed)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        ParrotTopBar(title = "Réglages", onMenuClick = onMenuOpen)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

        if (batteryCalibWarn != null) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF8F00).copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFFFF8F00).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.BatteryAlert, null, tint = Color(0xFFFF8F00),
                    modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("Diagnostic batterie", color = Color(0xFFFF8F00), fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed)
                    Text(batteryCalibWarn ?: "", color = t.textPrimary, fontSize = 12.sp,
                        lineHeight = 16.sp)
                }
            }
        }

        // Ref row 1 : Nom du Parrot Zik [30,222][693/1050,432] = 70dp
        SettingsRow(
            label = "Nom du Parrot Zik",
            value = deviceName,
            onClick = {
                pendingDeviceName = deviceName
                nameError = null
                showNameDialog = true
            }
        )
        SettingsRow(label = "En appel", value = phoneModeLabel,
            onClick = { showPhoneModeDialog = true })
        SettingsSwitchRow(label = "Capteur de presence",
            checked = presenceSensor == true, onChanged = onTogglePresence)
        SettingsSwitchRow(label = "Auto-connexion",
            checked = autoConnection == true) { viewModel.setAutoConnection(it) }

        SettingsRow(label = "Arret automatique", value = apoLabel,
            onClick = { showAutoPowerOffDialog = true })
        SettingsSwitchRow(label = "Annonce vocale de l'appelant",
            checked = ttsEnabled == true) { viewModel.setTtsEnabled(it) }
        SettingsSwitchRow(
            label = "Afficher la barre de notification",
            checked = showBatteryNotification
        ) { checked ->
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    showBatteryNotification = true
                    prefs.edit().putBoolean("show_battery_notification", true).apply()
                    context.sendBroadcast(
                        Intent(ZikBluetoothService.ACTION_SET_NOTIFICATION_PREF).putExtra("show", true)
                    )
                }
            } else {
                showBatteryNotification = false
                prefs.edit().putBoolean("show_battery_notification", false).apply()
                context.sendBroadcast(
                    Intent(ZikBluetoothService.ACTION_SET_NOTIFICATION_PREF).putExtra("show", false)
                )
            }
        }
        SettingsRow(
            label = "Theme",
            value = ZIK_SKINS.firstOrNull { it.key == skinKey }?.label?.replace("\n", " ") ?: skinKey,
            onClick = { showThemeDialog = true }
        )

        Spacer(Modifier.height(16.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Si vous voulez supporter le projet :",
                color = t.textSecondary,
                fontSize = 12.sp,
                fontFamily = RobotoCondensed
            )
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(coffeeUrl)))
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.LocalCafe,
                        contentDescription = "Buy me a coffee",
                        tint = t.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Buy me a coffee",
                        color = t.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = RobotoCondensed
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        } // inner Column (scrollable)
    } // outer Column
}

// ─── Ligne de reglage simple ─────────────────────────────────────────────────
// Ref: [30,x][693,x][1050,x+213] = 213px = 71dp h
// Label [30,x][693,x+213]  Value [693,x][1050,x+213]
@Composable
private fun SettingsRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val t = LocalZikSkin.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(71.dp)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Label — occupe 64.2% de la largeur (ref: 663/1020px)
            Text(label, color = t.textPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Normal, fontFamily = RobotoCondensed,
                modifier = Modifier.weight(0.642f))
            // Value — occupe 35.8% (ref: 357/1020px)
            Row(
                Modifier.weight(0.358f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (value.isNotBlank()) {
                    Text(value, color = t.textSecondary, fontSize = 13.sp,
                        fontFamily = RobotoCondensed, textAlign = TextAlign.End,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp))
                }
                if (onClick != null) {
                    Text("›", color = t.textSecondary, fontSize = 20.sp,
                        fontFamily = RobotoCondensed)
                }
            }
        }
        // Divider ref: 3px = 1dp
        Box(Modifier.fillMaxWidth().padding(start = 10.dp).height(0.5.dp)
            .background(t.dark))
    }
}

// ─── Ligne de reglage avec switch ────────────────────────────────────────────
// Ref switch [741,x+66][1050,x+149] dans chaque row 213px
@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val t = LocalZikSkin.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(71.dp)
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = t.textPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Normal, fontFamily = RobotoCondensed,
                modifier = Modifier.weight(0.642f))
            Box(
                Modifier.weight(0.358f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = Color(0xFF1A1A1A),
                        checkedTrackColor   = t.accent,
                        uncheckedThumbColor = t.textSecondary,
                        uncheckedTrackColor = t.dark
                    )
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 10.dp).height(0.5.dp)
            .background(t.dark))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PageLogs — Fenêtre pleine page de lecture des logs en temps réel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageLogs(viewModel: ZikViewModel, onMenuOpen: () -> Unit = {}) {
    val t = LocalZikSkin.current
    val logs by viewModel.liveLogs.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll vers le bas quand de nouveaux logs arrivent
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        ParrotTopBar("Logs", onMenuClick = onMenuOpen)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(logs.size) { idx ->
                val line = logs[idx]
                val lineColor = when {
                    "✓ OK"  in line || "SESSION OK" in line || "PROBE OK" in line -> Color(0xFF4CAF50)
                    "✗ ERR" in line || "✗ DC" in line                             -> Color(0xFFEF5350)
                    "HAND"  in line                                                -> Color(0xFFFFB74D)
                    "↑ OUT" in line                                                -> Color(0xFF64B5F6)
                    "↓"     in line                                                -> Color(0xFF81C784)
                    "LOOP"  in line || "BT" in line                                -> Color(0xFFCE93D8)
                    else                                                           -> Color(0xFFBBBBBB)
                }
                Text(
                    text = line,
                    color = lineColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Message vide si aucun log
            if (logs.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "En attente de logs…\nConnectez le casque pour voir les trames.",
                            color = t.textSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ZikNavBar — Barre de navigation 5 onglets style Parrot legacy
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ZikNavBar(
    currentPage  : Int,
    totalPages   : Int,
    onTabSelected: (Int) -> Unit
) {
    val t = LocalZikSkin.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(t.navBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TABS.take(totalPages).forEachIndexed { idx, tab ->
            val isActive = idx == currentPage
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(idx) }
                    .padding(vertical = 6.dp)
            ) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isActive) t.accent else Color.Transparent)
                )
                Spacer(Modifier.height(2.dp))
                Icon(
                    tab.icon, contentDescription = tab.label,
                    tint     = if (isActive) t.accent else t.textSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    color      = if (isActive) t.accent else t.textSecondary,
                    fontSize   = 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = RobotoCondensed,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1
                )
            }
        }
    }
}

// ─── Entrée de menu du Drawer ────────────────────────────────────────────────
private data class DrawerItem(val pageIdx: Int, val icon: ImageVector, val label: String)

// ─────────────────────────────────────────────────────────────────────────────
//  MENU LATÉRAL (Drawer) — ref: zik_ref_menu_lateral.xml 1080×1920
//  drawer_container width = 810/1080 = 75% (géré par ModalDrawer Material 2)
//  Header linearLayout123 [0,219][810,369] = 50dp
//  item row [0,x][810,x+144] = 48dp  —  icon [69,x+42][129,x+102] ≈ 20dp
//  Footer include_login [0,1770][810,1920] = 50dp : Inscription | Se connecter
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ZikDrawerContent(
    deviceName     : String       = "",
    firmwareVersion: String       = "",
    macAddress     : String       = "",
    battery        : Int?         = null,
    isCharging     : Boolean      = false,
    autoPowerOff   : Int?         = null,
    autoConnection : Boolean?     = null,
    ttsEnabled     : Boolean?     = null,
    ancMode        : ZikProtocol.NoiseControlMode? = null,
    eqEnabled      : Boolean?     = null,
    concertEnabled : Boolean?     = null,
    presenceSensor : Boolean?     = null,
    presenceWorn   : Boolean?     = null,
    phoneMode      : String       = "",
    liveLogs       : List<String> = emptyList(),
    currentPage    : Int          = 0,
    onNavigate     : (Int) -> Unit = {},
    onReconnectHeadset: () -> Unit = {},
    onQuitDemo     : () -> Unit   = {},
    onDismiss      : () -> Unit   = {}
) {
    val t = LocalZikSkin.current
    var showHelpDialog by remember { mutableStateOf(false) }

    // ── Dialog Aide (VerticalScroll complet, padding S22 Ultra) ─────────────
    if (showHelpDialog) {
        Dialog(
            onDismissRequest = { showHelpDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = t.cardBg,
                elevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Tout le contenu scrollable
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = 34.dp,   // S22 Ultra status bar offset
                                start = 20.dp,
                                end = 20.dp,
                                bottom = 12.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Titre
                            Text("Mode d'emploi — Ziker / Parrot Zik 3",
                            fontFamily = RobotoCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = t.accent)

                        Spacer(Modifier.height(4.dp))

                            Text(
                                "Cette aide reprend l'esprit du manuel Parrot et l'adapte a Ziker tel qu'il fonctionne aujourd'hui : commande locale du casque, reproduction des ecrans historiques, et acces direct aux fonctions reellement supportees par le firmware.",
                                fontSize = 13.sp,
                                fontFamily = RobotoCondensed,
                                color = t.textSecondary,
                                lineHeight = 18.sp
                            )

                            Text(
                                "Navigation rapide : utilisez les onglets en bas pour passer d'un ecran a l'autre. Le menu lateral sert au resume d'etat, a l'aide et aux reglages. Pour refermer le menu, glissez vers la gauche sur la zone vide ou utilisez la fleche de retour dans l'en-tete.",
                                fontSize = 13.sp,
                                fontFamily = RobotoCondensed,
                                color = t.textSecondary,
                                lineHeight = 18.sp
                            )

                        val sectionData = listOf(
                                "\uD83C\uDFA7  Tableau de bord" to
                                    "Le tableau de bord regroupe les informations utiles au quotidien : niveau de batterie, etat de charge, titre en lecture et commandes media. Appuyez sur la pochette pour lecture / pause, et utilisez les boutons precedent / suivant pour piloter la source audio connectee.\n\nLes cartes de statut donnent aussi un acces rapide aux grandes fonctions du casque : egaliseur, controle du bruit, Concert Hall et capteur de presence.",
                                "\uD83C\uDF0A  Controle du bruit" to
                                    "Cette page reprend la logique Parrot : un axe vertical pour passer d'un mode a l'autre. Selon le modele et l'etat du firmware, Ziker pilote :\n• Reduction de bruit max\n• Reduction de bruit legere\n• OFF / passif\n• Street mode leger\n• Street mode max\n\nLe mode AUTO verrouille le geste manuel pendant que le casque ajuste lui-meme le niveau. Le cercle central affiche le bruit ambiant mesure ; au-dessus d'environ 85 dB, l'affichage passe en rouge pour signaler un environnement bruyant.",
                                "\uD83C\uDFB5  Egaliseur Thumb EQ" to
                                    "L'ecran EQ reste la commande rapide historique du Zik. Le disque central permet de modeler la signature sonore du bout des doigts, dans l'esprit 'Tune your music with your fingertips' de l'application Parrot.\n\nLes presets integres Pop, Vocal, Crystal, Club, Punchy et Deep sont accessibles directement. Ils servent de points de reference et de raccourcis d'ecoute, puis le Thumb EQ affine ensuite la courbe envoyee au casque.",
                                "\uD83D\uDCC1  Mes presets / Creer un preset" to
                                    "Depuis la page EQ, deux entrees coexistent :\n• Mes presets : liste vos presets locaux, avec Appliquer, Editer et Supprimer\n• Creer un preset : ouvre l'editeur complet\n\nQuand vous appliquez un preset, il est immediatement envoye au casque et reste en place. Si vous basculez ensuite vers Creer / editer, Ziker repart des valeurs du preset applique au lieu de reinitialiser l'EQ.\n\nL'editeur enregistre un preset complet : nom, EQ parametrique 5 bandes et etat Concert Hall.",
                                "\uD83C\uDF9B  EQ parametrique + Concert Hall" to
                                    "L'editeur avance permet de regler 5 bandes : 60 Hz, 200 Hz, 1 kHz, 2,4 kHz et 11 kHz. Chaque bande agit directement sur le casque pendant l'edition, ce qui permet d'entendre le resultat en temps reel.\n\nLe preset peut aussi memoriser le Concert Hall : activation, type de salle (Silent Room, Living Room, Jazz Club, Concert Hall) et angle de position de 0° a 180°. Cela permet de rappeler en une seule action une combinaison complete EQ + spatialisation.",
                                "\u2699\uFE0F  Reglages et comportement du casque" to
                                    "La page Reglages centralise les options systeme disponibles dans Ziker : theme visuel, nom du casque, arret automatique, auto-connexion, annonce vocale de l'appelant, capteur de presence, informations firmware et adresse MAC.\n\nLe capteur de presence reprend la logique Parrot : selon le casque et son etat, retirer le Zik ou le poser autour du cou peut mettre la lecture en pause. Les options avancees permettent aussi d'ajuster le comportement du Thumb EQ et l'orientation gauche / droite.",
                                "\uD83D\uDD14  Batterie et notifications" to
                                    "Ziker maintient une notification systeme persistante tant que le service Bluetooth du casque est actif. Elle affiche l'etat de connexion et le pourcentage de batterie dans la barre systeme.\n\nDes alertes se declenchent egalement en batterie faible pour eviter une coupure sans preavis. Si aucune notification n'apparait, verifiez que les notifications Android de l'application ne sont pas bloquees au niveau systeme.",
                                "\u2139\uFE0F  Ce que fait Ziker" to
                                    "Ziker vise d'abord la reconstruction technique des fonctions audio et systeme reellement acceptees par le casque : Bluetooth, EQ, ANC, Concert Hall, presets, capteurs et reglages utiles. Certaines briques communautaires de l'application Parrot d'origine (boutique, cloud de presets, services externes) ne sont pas le coeur de cette version et peuvent ne pas etre reproduites de la meme maniere."
                        )
                        sectionData.forEach { (title, body) ->
                            Text(title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = RobotoCondensed,
                                color = t.textPrimary)
                            Text(body,
                                fontSize = 13.sp,
                                fontFamily = RobotoCondensed,
                                color = t.textSecondary,
                                lineHeight = 18.sp)
                        }

                        // Firmware version en bas du scroll
                        Spacer(Modifier.height(16.dp))
                        Box(
                            Modifier.fillMaxWidth().height(0.5.dp)
                                .background(t.dark.copy(alpha = 0.4f))
                        )
                        Spacer(Modifier.height(8.dp))
                        if (firmwareVersion.isNotBlank()) {
                            Text("Firmware v$firmwareVersion",
                                fontSize = 11.sp,
                                fontFamily = RobotoCondensed,
                                color = t.textSecondary.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth())
                        }
                    }

                    // Bouton Fermer — toujours visible hors du scroll
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(t.cardBg)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        TextButton(onClick = { showHelpDialog = false }) {
                            Text("Fermer", fontFamily = RobotoCondensed, color = t.accent)
                        }
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 34.dp)
            .background(t.cardBg)
    ) {
        // ── Carte appareil ─────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .background(t.accent.copy(alpha = 0.07f))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Nom + icône BT + bouton fermeture
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, tint = t.accent,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    deviceName.ifBlank { "—" },
                    color = t.textPrimary, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Fermer le menu",
                        tint = t.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Firmware + MAC
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (firmwareVersion.isNotBlank()) {
                    Text("fw $firmwareVersion", fontSize = 11.sp,
                        color = t.textSecondary, fontFamily = RobotoCondensed)
                }
                if (macAddress.isNotBlank()) {
                    Text(macAddress, fontSize = 10.sp,
                        color = t.textSecondary.copy(alpha = 0.7f),
                        fontFamily = RobotoCondensed, letterSpacing = 0.3.sp)
                }
            }
            // Batterie
            if (battery != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(t.accent.copy(alpha = 0.16f))
                        .border(1.dp, t.accent.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (battery <= 20) Color(0xFFFF6B6B) else t.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isCharging) "Batterie: $battery% (charge)" else "Batterie: $battery%",
                        fontSize = 12.sp,
                        color = t.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = RobotoCondensed
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.dark.copy(alpha = 0.55f)))

        // ── Informations casque (lecture seule) ───────────────────────────
        val statusOnColor = Color(0xFF63C07A)
        val statusOffColor = Color(0xFFFF6B6B)
        Text(
            "Informations casque",
            fontSize = 10.sp,
            color = t.textSecondary.copy(alpha = 0.68f),
            fontFamily = RobotoCondensed,
            modifier = Modifier.padding(start = 18.dp, top = 8.dp)
        )
        val hasQuickInfo = autoPowerOff != null || autoConnection != null || ttsEnabled != null
        if (hasQuickInfo) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (autoPowerOff != null) {
                    DrawerInfoRow(
                        label = "Auto-veille",
                        value = if (autoPowerOff == 0) "Désactivée" else "${autoPowerOff} min",
                        valueColor = if (autoPowerOff == 0) statusOffColor else t.textPrimary,
                        t = t
                    )
                }
                if (autoConnection != null) {
                    DrawerInfoRow(
                        label = "Auto-connexion",
                        value = if (autoConnection == true) "Activée" else "Désactivée",
                        valueColor = if (autoConnection == true) statusOnColor else statusOffColor,
                        t = t
                    )
                }
                if (ttsEnabled != null) {
                    DrawerInfoRow(
                        label = "Annonces vocales",
                        value = if (ttsEnabled == true) "Activées" else "Désactivées",
                        valueColor = if (ttsEnabled == true) statusOnColor else statusOffColor,
                        t = t
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.dark.copy(alpha = 0.4f)))
        }

        // ── Statut audio / capteurs ───────────────────────────────────────
        val ancLabel = when (ancMode) {
            ZikProtocol.NoiseControlMode.ANC        -> "ANC fort"
            ZikProtocol.NoiseControlMode.ANC_LOW    -> "ANC léger"
            ZikProtocol.NoiseControlMode.STREET     -> "Rue fort"
            ZikProtocol.NoiseControlMode.STREET_LOW -> "Rue léger"
            ZikProtocol.NoiseControlMode.OFF        -> "Bruit: OFF"
            null                                    -> "Bruit: —"
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Statut en direct",
                fontSize = 10.sp,
                color = t.textSecondary.copy(alpha = 0.68f),
                fontFamily = RobotoCondensed
            )
            DrawerInfoRow(label = "Réduction bruit", value = ancLabel, t = t)
            if (eqEnabled != null) {
                DrawerInfoRow(
                    label = "Égaliseur",
                    value = if (eqEnabled == true) "Actif" else "Inactif",
                    valueColor = if (eqEnabled == true) statusOnColor else statusOffColor,
                    t = t
                )
            }
            if (concertEnabled != null) {
                DrawerInfoRow(
                    label = "Concert Hall",
                    value = if (concertEnabled == true) "Actif" else "Inactif",
                    valueColor = if (concertEnabled == true) statusOnColor else statusOffColor,
                    t = t
                )
            }
            if (presenceSensor != null) {
                DrawerInfoRow(
                    label = "Détection tête",
                    value = if (presenceSensor == true) "Active" else "Inactive",
                    valueColor = if (presenceSensor == true) statusOnColor else statusOffColor,
                    t = t
                )
            }
            if (presenceWorn != null || phoneMode.isNotBlank()) {
                if (presenceWorn != null) {
                    DrawerInfoRow(
                        label = "Port du casque",
                        value = if (presenceWorn == true) "Porté" else "Retiré",
                        valueColor = if (presenceWorn == true) statusOnColor else statusOffColor,
                        t = t
                    )
                }
                if (phoneMode.isNotBlank()) {
                    DrawerInfoRow(label = "Mode téléphone", value = phoneMode.uppercase(), t = t)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.dark.copy(alpha = 0.4f)))

        // ── Navigation ────────────────────────────────────────────────────
        val navItems = listOf(
            DrawerItem(0, Icons.Default.Dashboard, "Menu principal"),
            DrawerItem(4, Icons.Default.Settings,  "Réglages"),
        )
        Column(Modifier.fillMaxWidth()) {
            navItems.forEach { item ->
                val isActive = (item.label == "Menu principal" && currentPage == 0)
                            || (item.label == "Réglages"       && currentPage == 4)
                ZikDrawerRow(
                    icon     = item.icon,
                    label    = item.label,
                    isActive = isActive,
                    onClick  = {
                        onNavigate(item.pageIdx); onDismiss()
                    }
                )
            }
            ZikDrawerRow(
                icon = Icons.Default.Refresh,
                label = "Reconnecter le casque",
                isActive = false,
                onClick = {
                    onReconnectHeadset()
                    onDismiss()
                }
            )
            if (LocalDemoMode.current) {
                ZikDrawerRow(
                    icon = Icons.Default.ExitToApp,
                    label = "Quitter mode démo",
                    isActive = false,
                    onClick = {
                        onQuitDemo()
                        onDismiss()
                    }
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.zikker_round),
                contentDescription = stringResource(id = R.string.app_name),
                tint = Color.Unspecified,
                modifier = Modifier.size(68.dp)
            )
            Text(
                stringResource(id = R.string.app_name),
                color = t.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RobotoCondensed
            )
        }

        Spacer(Modifier.weight(0.35f))

        // ── Crédits ──────────────────────────────────────────────────────
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(t.dark.copy(alpha = 0.35f)))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🇫🇷 RMDaye with VS Code 🇫🇷",
                color = t.textPrimary, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, fontFamily = RobotoCondensed)
            Spacer(Modifier.height(6.dp))
            Text(
                "Si vous voulez supporter le projet :",
                color = t.textSecondary,
                fontSize = 11.sp,
                fontFamily = RobotoCondensed
            )
            Spacer(Modifier.height(4.dp))
            val context2 = LocalContext.current
            val coffeeUrl = "https://buymeacoffee.com/rmdaye"
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val i = Intent(Intent.ACTION_VIEW, Uri.parse(coffeeUrl))
                            context2.startActivity(i)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalCafe,
                        contentDescription = "Buy me a coffee",
                        tint = t.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Buy me a coffee", color = t.textSecondary, fontSize = 11.sp,
                        textAlign = TextAlign.Center, fontFamily = RobotoCondensed)
                }
            }
        }
    }
}

// ─── Ligne info alignée (drawer) ─────────────────────────────────────────────
@Composable
private fun DrawerInfoRow(
    label: String,
    value: String,
    t: ZikSkin,
    valueColor: Color = t.textPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = t.textSecondary,
            fontFamily = RobotoCondensed,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = valueColor,
            fontFamily = RobotoCondensed,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

// ─── Ligne de menu Drawer ─────────────────────────────────────────────────────
// item_icon [69,x+42][129,x+102] : start = 23dp  icon 20dp
// item_title [129,x][786,x+144]  : 14sp RobotoCondensed
@Composable
private fun ZikDrawerRow(
    icon    : ImageVector,
    label   : String,
    isActive: Boolean = false,
    onClick : () -> Unit
) {
    val t = LocalZikSkin.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(if (isActive) t.accent.copy(alpha = 0.10f) else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(start = 22.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null,
                tint = if (isActive) t.accent else t.textSecondary,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(label,
                color = if (isActive) t.accent else t.textPrimary,
                fontSize = 14.sp, fontFamily = RobotoCondensed,
                modifier = Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(start = 22.dp).height(0.5.dp)
            .background(t.dark.copy(alpha = 0.45f)))
    }
}

