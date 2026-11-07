package com.omrscanner.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.omrscanner.app.omr.Grading
import com.omrscanner.app.omr.Layout
import com.omrscanner.app.omr.OmrDetector
import com.omrscanner.app.omr.OmrReader
import com.omrscanner.app.omr.toUprightGray
import com.omrscanner.app.ui.AppViewModel
import com.omrscanner.app.ui.components.PrimaryButton
import com.omrscanner.app.ui.exams.rememberBundle
import com.omrscanner.app.ui.theme.Accent
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Mono
import com.omrscanner.app.ui.theme.Paper
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val Searching = Color(0x99FFFFFF)
private val Aligning = Color(0xFFF2B84B)
private val Locked = Color(0xFF4ADE80)
private val TooSteep = Color(0xFFF08A3C)

/**
 * Runs on the camera's analysis thread: detect → wait until steady → read. After a read it waits for the
 * sheet to leave the frame before reading again, so a stack can be flipped through one sheet at a time.
 */
private class ScanEngine {
    private val main = Handler(Looper.getMainLooper())

    // configuration, set from the UI thread
    @Volatile var layout: Layout? = null
    @Volatile var auto = true
    @Volatile var maxTilt = 25
    @Volatile var eraseRatio = 0.55
    @Volatile var enabled = true
    @Volatile var captureRequested = false
    var onRead: (OmrReader.Result) -> Unit = {}

    // UI state, written on the main thread only
    var corners by mutableStateOf<List<Offset>?>(null)
    var frame by mutableStateOf(Size(1, 1))
    var color by mutableStateOf(Searching)
    var status by mutableStateOf("Looking for an answer sheet…")
    var level by mutableStateOf("")

    private var last: OmrDetector.Detection? = null
    private var steady = 0
    private var missing = 0
    private var waitForRemoval = false

    fun analyze(img: ImageProxy) {
        try {
            val lay = layout
            if (!enabled || lay == null) return
            val gray = img.toUprightGray()
            val det = OmrDetector.detect(gray)
            val steep = det != null && det.tilt > maxTilt
            if (det == null) { missing++; steady = 0; if (missing > 4) waitForRemoval = false }
            else { missing = 0; steady = if (OmrDetector.isSteady(det, last)) steady + 1 else 0 }
            last = det

            val wantRead = det != null && !steep && !waitForRemoval && ((auto && steady >= 6) || captureRequested)
            var result: OmrReader.Result? = null
            if (wantRead) {
                captureRequested = false
                result = OmrReader.read(gray, det!!, lay, eraseRatio)
                waitForRemoval = true; steady = 0
            } else if (captureRequested && (det == null || steep)) {
                captureRequested = false
                main.post { status = if (det == null) "No sheet in frame yet" else "Too steep to read — hold the phone flatter" }
            }
            val fw = gray.cols(); val fh = gray.rows()
            gray.release()

            val pts = det?.corners?.map { Offset(it.x.toFloat(), it.y.toFloat()) }
            val tiltTxt = det?.let { "Tilt ${it.tilt.toInt()}° · " + (if (steep) "too steep" else if (it.tilt > 6) "auto-correcting" else "flat") + (if (it.rotation != 0) " · turned ${it.rotation}°" else "") } ?: ""
            main.post {
                frame = Size(fw, fh); corners = pts; level = tiltTxt
                when {
                    result != null -> { color = Locked; status = "Captured — move to the next sheet" }
                    det == null -> { color = Searching; status = if (waitForRemoval) "Captured — move to the next sheet" else "Looking for an answer sheet…" }
                    waitForRemoval -> { color = Locked; status = "Captured — move to the next sheet" }
                    steep -> { color = TooSteep; status = "Too steep — hold the phone flatter over the sheet" }
                    auto -> { color = Aligning; status = "Hold steady — 4 corners found" }
                    else -> { color = Locked; status = "Aligned — tap the shutter" }
                }
                if (result != null) onRead(result)
            }
        } catch (e: Exception) {
            Log.e("OmrScan", "analysis failed", e)
        } finally {
            img.close()
        }
    }
}

@Composable
fun OmrScanScreen(vm: AppViewModel, nav: NavController, examId: Long) {
    val b = rememberBundle(vm, examId) ?: return
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    val engine = remember { ScanEngine() }
    val bundle by rememberUpdatedState(b)
    var session by remember { mutableIntStateOf(0) }
    var lastResult by remember { mutableStateOf<String?>(null) }

    engine.layout = b.layout
    engine.auto = vm.settings.autoCapture
    engine.maxTilt = vm.settings.maxTilt
    engine.eraseRatio = vm.settings.erasePct / 100.0
    engine.enabled = b.left > 0
    engine.onRead = { r ->
        scope.launch {
            val saved = vm.saveScan(examId, r)
            session++
            val bb = bundle
            val name = bb.student(saved.roll)?.name ?: "Roll ${saved.roll}"
            val sc = Grading.score(r.marks, bb.key(saved.setCode))
            val dbl = Grading.doubles(r.marks)
            val flag = when {
                bb.exam.rollDigits > 0 && bb.student(saved.roll) == null -> if (r.rollAlt.isNotEmpty()) " · roll double-filled" else " · check roll"
                bb.exam.setCode && bb.key(saved.setCode) == null -> if (r.setAlt.isNotEmpty()) " · set double-filled" else " · check set"
                dbl > 0 -> " · $dbl double"
                else -> ""
            }
            lastResult = "$name · ${sc ?: "—"}/${bb.n}$flag"
        }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().background(Ink).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera access is needed to scan answer sheets.", color = Paper, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Allow camera", { askPermission.launch(Manifest.permission.CAMERA) })
            Spacer(Modifier.height(8.dp))
            Text("Back", color = Paper, modifier = Modifier.clickable { nav.popBackStack() }.padding(12.dp))
        }
        return
    }

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val executor = Executors.newSingleThreadExecutor()
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
            .build()
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().setResolutionSelector(selector).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder().setResolutionSelector(selector).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { engine.analyze(it) }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // detected sheet outline, mapped from frame pixels to the FILL_CENTER preview
        Canvas(Modifier.fillMaxSize()) {
            val pts = engine.corners ?: return@Canvas
            val fw = engine.frame.width.toFloat(); val fh = engine.frame.height.toFloat()
            val s = maxOf(size.width / fw, size.height / fh)
            val dx = (size.width - fw * s) / 2; val dy = (size.height - fh * s) / 2
            val v = pts.map { Offset(it.x * s + dx, it.y * s + dy) }
            val path = Path().apply { moveTo(v[0].x, v[0].y); v.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            drawPath(path, engine.color.copy(alpha = 0.16f))
            drawPath(path, engine.color, style = Stroke(width = 3.dp.toPx()))
            v.forEach { drawCircle(engine.color, 7.dp.toPx(), it) }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x33FFFFFF)).clickable { nav.popBackStack() }, contentAlignment = Alignment.Center) { Text("✕", color = Paper, fontSize = 18.sp) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(b.exam.title, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("${b.sheets.size} / ${b.total} scanned", color = Color(0xFFB9B4AA), fontFamily = Mono, fontSize = 12.sp)
                }
                Spacer(Modifier.width(44.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x99000000)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(engine.color))
                Text(if (b.left == 0) "All sheets scanned" else engine.status, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp))
            }
            if (engine.level.isNotEmpty()) Text(engine.level, color = Color(0xFFD8D3C9), fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            lastResult?.let {
                Box(Modifier.padding(bottom = 14.dp).clip(RoundedCornerShape(999.dp)).background(Paper).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(it, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
            Row(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x33FFFFFF)).padding(3.dp)) {
                listOf(true to "Auto", false to "Manual").forEach { (v, label) ->
                    val on = vm.settings.autoCapture == v
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(if (on) Paper else Color.Transparent).clickable { vm.settings.autoCapture = v }.padding(horizontal = 16.dp, vertical = 7.dp)) {
                        Text(label, color = if (on) Ink else Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$session", color = Paper, fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                    Text("this session", color = Color(0xFFB9B4AA), fontSize = 12.sp)
                }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).border(4.dp, Paper, CircleShape).padding(6.dp).clip(CircleShape)
                        .background(if (engine.corners != null) Accent else Color(0x59F6F4EF)).clickable { engine.captureRequested = true },
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0x33FFFFFF)).clickable { nav.popBackStack() }.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text("Done", color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
