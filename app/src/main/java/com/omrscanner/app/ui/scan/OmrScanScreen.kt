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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.omrscanner.app.ui.components.plural
import com.omrscanner.app.ui.exams.rememberBundle
import com.omrscanner.app.ui.theme.Ink
import com.omrscanner.app.ui.theme.Muted
import com.omrscanner.app.ui.theme.Warn
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private val Searching = Color(0xE6FFFFFF)
private val Aligning = Color(0xFFF2B84B)
private val Locked = Color(0xFF4ADE80)
private val TooSteep = Color(0xFFF08A3C)

/**
 * Runs on the camera's analysis thread: find the sheet → wait until it's steady → read it. After a read
 * it waits for the sheet to leave the frame before reading again, so a stack can be flipped through.
 */
private class ScanEngine {
    private val main = Handler(Looper.getMainLooper())

    // set from the UI thread
    @Volatile var layout: Layout? = null
    @Volatile var auto = true
    @Volatile var maxTilt = 25
    @Volatile var eraseRatio = 0.55
    @Volatile var captureRequested = false
    var onRead: (OmrReader.Result) -> Unit = {}

    // what the screen shows; written on the main thread only
    var corners by mutableStateOf<List<Offset>?>(null)
    var frame by mutableStateOf(Size(1, 1))
    var color by mutableStateOf(Searching)
    var status by mutableStateOf(LOOKING)

    private var last: OmrDetector.Detection? = null
    private var steady = 0
    private var missing = 0
    private var waitForRemoval = false

    fun analyze(img: ImageProxy) {
        try {
            val lay = layout ?: return
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
                main.post { status = if (det == null) "No sheet found yet" else FLATTER }
            }
            val fw = gray.cols(); val fh = gray.rows()
            gray.release()

            val pts = det?.corners?.map { Offset(it.x.toFloat(), it.y.toFloat()) }
            main.post {
                frame = Size(fw, fh); corners = pts
                when {
                    result != null || (waitForRemoval && det != null) -> { color = Locked; status = NEXT }
                    det == null -> { color = Searching; status = if (waitForRemoval) NEXT else LOOKING }
                    steep -> { color = TooSteep; status = FLATTER }
                    auto -> { color = Aligning; status = "Hold still…" }
                    else -> { color = Locked; status = "Tap the round button" }
                }
                if (result != null) onRead(result)
            }
        } catch (e: Exception) {
            Log.e("OmrScan", "analysis failed", e)
        } finally {
            img.close()
        }
    }

    companion object {
        const val LOOKING = "Fit the whole sheet in the frame"
        const val NEXT = "Done! Put the next sheet"
        const val FLATTER = "Hold the phone flatter"
    }
}

/** What the scan screen says about the sheet it just read. */
data class ScanResultUi(val name: String, val detail: String, val score: String, val flag: String?)

@Composable
fun OmrScanScreen(vm: AppViewModel, nav: NavController, examId: Long) {
    val b = rememberBundle(vm, examId) ?: return
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askPermission.launch(Manifest.permission.CAMERA) }

    val engine = remember { ScanEngine() }
    val bundle by rememberUpdatedState(b)
    var last by remember { mutableStateOf<ScanResultUi?>(null) }

    engine.layout = b.layout
    engine.auto = vm.settings.autoCapture
    engine.maxTilt = vm.settings.maxTilt
    engine.eraseRatio = vm.settings.erasePct / 100.0
    engine.onRead = { r ->
        scope.launch {
            val saved = vm.saveScan(examId, r)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val bb = bundle
            val student = bb.student(saved.roll)
            val score = Grading.score(r.marks, bb.key(saved.setCode))
            val twoAnswers = Grading.doubles(r.marks)
            last = ScanResultUi(
                name = student?.name ?: "Unknown student",
                detail = "Roll ${saved.roll}" + if (bb.exam.setCode) " · Set ${saved.setCode}" else "",
                score = score?.let { "$it/${bb.n}" } ?: "—",
                flag = when {
                    bb.exam.rollDigits > 0 && student == null -> "Check the roll later"
                    bb.exam.setCode && bb.key(saved.setCode) == null -> "Check the set later"
                    bb.sheets.any { it.id != saved.id && it.roll == saved.roll } -> "Another sheet has this roll"
                    bb.exam.doubleRule == "review" && twoAnswers > 0 -> "${plural(twoAnswers, "question")} with two answers"
                    else -> null
                },
            )
        }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            Text("The camera is needed to scan OMR sheets.", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 24.dp))
            PrimaryButton("Allow the camera", { askPermission.launch(Manifest.permission.CAMERA) })
            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.padding(top = 8.dp)) { Text("Back", color = Color.White, fontSize = 16.sp) }
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

    ScanOverlay(
        title = b.exam.title,
        count = "${b.sheets.size} of ${b.total} scanned",
        status = engine.status, color = engine.color, corners = engine.corners, frame = engine.frame,
        result = last, sheetFound = engine.corners != null,
        onClose = { nav.popBackStack() },
        onShutter = { engine.captureRequested = true },
    ) { AndroidView({ previewView }, Modifier.fillMaxSize()) }
}

/** Everything drawn over the camera: the guide or the sheet's outline, what's happening, the last result, and the buttons. */
@Composable
fun ScanOverlay(
    title: String, count: String, status: String, color: Color, corners: List<Offset>?, frame: Size,
    result: ScanResultUi?, sheetFound: Boolean, onClose: () -> Unit, onShutter: () -> Unit, camera: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        camera()

        Canvas(Modifier.fillMaxSize()) {
            if (corners == null) {
                // where to hold the sheet: four corners of an A4 page
                var gw = size.width * 0.8f
                var gh = gw * 1.414f
                if (gh > size.height * 0.6f) { gh = size.height * 0.6f; gw = gh / 1.414f }
                val l = (size.width - gw) / 2; val t = (size.height - gh) / 2 - 20.dp.toPx(); val r = l + gw; val btm = t + gh
                val len = 36.dp.toPx(); val w = 5.dp.toPx(); val c = Color.White.copy(alpha = 0.9f)
                listOf(
                    Triple(Offset(l, t), Offset(l + len, t), Offset(l, t + len)),
                    Triple(Offset(r, t), Offset(r - len, t), Offset(r, t + len)),
                    Triple(Offset(r, btm), Offset(r - len, btm), Offset(r, btm - len)),
                    Triple(Offset(l, btm), Offset(l + len, btm), Offset(l, btm - len)),
                ).forEach { (corner, a, z) ->
                    drawLine(c, corner, a, w, StrokeCap.Round)
                    drawLine(c, corner, z, w, StrokeCap.Round)
                }
            } else {
                // the sheet as found, mapped from the camera frame to the preview (which fills the screen)
                val fw = frame.width.toFloat(); val fh = frame.height.toFloat()
                val s = maxOf(size.width / fw, size.height / fh)
                val dx = (size.width - fw * s) / 2; val dy = (size.height - fh * s) / 2
                val v = corners.map { Offset(it.x * s + dx, it.y * s + dy) }
                val path = Path().apply { moveTo(v[0].x, v[0].y); v.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                drawPath(path, color.copy(alpha = 0.18f))
                drawPath(path, color, style = Stroke(width = 4.dp.toPx()))
            }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(count, color = Color(0xCCFFFFFF), fontSize = 14.sp)
                }
            }
            Row(
                Modifier.padding(top = 12.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xB3000000)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (result != null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(result.detail, color = Muted, fontSize = 14.sp)
                        if (result.flag != null) Text(result.flag, color = Warn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text(result.score, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onShutter).semantics { contentDescription = "Scan now" }
                        .border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                        .background(if (sheetFound) Color.White else Color(0x59FFFFFF)),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(999.dp)).background(Color(0x66000000)).clickable(onClick = onClose).padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Done", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
                }
            }
        }
    }
}
