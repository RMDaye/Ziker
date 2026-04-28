package com.rmdaye.ziker

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Encapsule le [Visualizer] Android pour extraire les magnitudes FFT en temps réel.
 *
 * Le Visualizer capte la session audio globale (sessionId=0) — nécessite RECORD_AUDIO.
 * Les magnitudes brutes sont lissées par un low-pass exponentiel (smoothing factor 0.35)
 * pour reproduire la "respiration" douce du Note 3 original.
 *
 * Expose [fftMagnitudes] : FloatArray de [BAND_COUNT] valeurs normalisées [0f..1f],
 * ordonnées des basses (index 0) aux aigus (index N-1).
 *
 * Usage :
 *   manager.start()   // attache au flux audio
 *   manager.stop()    // libère le Visualizer
 */
class AudioVisualizerManager {

    companion object {
        private const val TAG = "AudioViz"

        /** Nombre de bandes d'amplitude exposées (basses → aigus) */
        const val BAND_COUNT = 48

        /** Taille de capture FFT (doit être puissance de 2 : min 128, max 1024 pour Visualizer) */
        private const val CAPTURE_SIZE = 512

        /**
         * Facteur de lissage exponentiel (low-pass) :
         * 0.0 = figé, 1.0 = aucun lissage (sauts bruts).
         * 0.35 donne un amortissement doux proche du Note 3.
         */
        private const val SMOOTHING_RISE = 0.40f   // montée (attaque)
        private const val SMOOTHING_FALL = 0.15f   // descente (release) — plus lente = respiration
    }

    private var visualizer: Visualizer? = null

    /** Magnitudes lissées [0f..1f] × BAND_COUNT — observé par l'UI */
    private val _fftMagnitudes = MutableStateFlow(FloatArray(BAND_COUNT))
    val fftMagnitudes: StateFlow<FloatArray> = _fftMagnitudes

    /** true quand le Visualizer est actif et écoute */
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    /** Buffer interne de magnitudes lissées */
    private val smoothed = FloatArray(BAND_COUNT)

    /**
     * Démarre la capture FFT sur la session audio globale (session 0).
     * Appelé quand la page EQ est visible ET que la permission RECORD_AUDIO est acquise.
     */
    fun start() {
        if (visualizer != null) return          // déjà actif
        try {
            val viz = Visualizer(0)             // session 0 = sortie audio globale
            viz.captureSize = CAPTURE_SIZE
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int
                    ) { /* non utilisé */ }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        processFFT(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),  // ~20 captures/s
                false,  // pas de waveform
                true    // oui FFT
            )
            viz.enabled = true
            visualizer = viz
            _isActive.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start Visualizer: ${e.message}")
            _isActive.value = false
        }
    }

    /**
     * Stoppe le Visualizer et libère les ressources audio.
     */
    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer release error: ${e.message}")
        }
        visualizer = null
        _isActive.value = false
        // Reset les magnitudes à zéro (extinction douce gérée côté UI)
        _fftMagnitudes.value = FloatArray(BAND_COUNT)
        smoothed.fill(0f)
    }

    /**
     * Traite les données FFT brutes du Visualizer Android.
     *
     * Le format FFT du Visualizer : pour N=captureSize/2 bins complexes,
     * fft[0] = DC component (real), fft[1] = Nyquist (real),
     * puis pour k=1..N-1 : fft[2*k] = real[k], fft[2*k+1] = imag[k]
     *
     * On regroupe ces N bins en [BAND_COUNT] bandes linéaires, puis
     * normalise + low-pass exponentiel pour le smoothing.
     */
    private fun processFFT(fft: ByteArray) {
        val binCount = fft.size / 2         // nombre de bins fréquentiels
        val binsPerBand = (binCount.toFloat() / BAND_COUNT).coerceAtLeast(1f)

        val raw = FloatArray(BAND_COUNT)

        for (band in 0 until BAND_COUNT) {
            val startBin = (band * binsPerBand).toInt().coerceIn(1, binCount - 1)
            val endBin   = ((band + 1) * binsPerBand).toInt().coerceIn(startBin + 1, binCount)
            var maxMag = 0f

            for (bin in startBin until endBin) {
                val re = fft[2 * bin].toFloat()
                val im = fft[2 * bin + 1].toFloat()
                val mag = Math.sqrt((re * re + im * im).toDouble()).toFloat()
                if (mag > maxMag) maxMag = mag
            }
            // Normalisation : valeurs brutes sont dans [-128, 127] (byte signé), donc mag max ≈ 181
            raw[band] = (maxMag / 180f).coerceIn(0f, 1f)
        }

        // ── Low-pass exponentiel asymétrique (montée rapide, descente lente) ──
        for (i in 0 until BAND_COUNT) {
            val factor = if (raw[i] > smoothed[i]) SMOOTHING_RISE else SMOOTHING_FALL
            smoothed[i] = smoothed[i] + (raw[i] - smoothed[i]) * factor
        }

        // Publie une copie (StateFlow compare par référence)
        _fftMagnitudes.value = smoothed.copyOf()
    }
}

