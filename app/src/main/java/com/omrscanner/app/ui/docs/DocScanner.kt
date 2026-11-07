package com.omrscanner.app.ui.docs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.omrscanner.app.data.db.DocPageEntity
import com.omrscanner.app.ui.AppViewModel

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Google's ML Kit document scanner: live edge detection, auto-capture, crop handles, perspective correction
 * and clean-up filters, all on-device. Returns a launcher: call it with true for ID-card mode (2 pages).
 */
@Composable
fun rememberDocumentScanner(vm: AppViewModel, onPages: (uris: List<Uri>, idCard: Boolean) -> Unit): (Boolean) -> Unit {
    val ctx = LocalContext.current
    var idMode by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            onPages(result?.pages?.map { it.imageUri }.orEmpty(), idMode)
        }
    }
    return { idCard ->
        idMode = idCard
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(if (idCard) 2 else 50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val activity = ctx.findActivity()
        if (activity == null) vm.toast("Scanner unavailable")
        else GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { vm.toast("Document scanner unavailable: ${it.message}") }
    }
}

/** A page rendered with its filter, rotation and (for ID pages) front + back layout. */
@Composable
fun PageThumb(vm: AppViewModel, page: DocPageEntity, maxDim: Int, modifier: Modifier = Modifier) {
    val bmp by produceState<android.graphics.Bitmap?>(null, page) { value = vm.thumbnail(page, maxDim) }
    Box(modifier.background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

