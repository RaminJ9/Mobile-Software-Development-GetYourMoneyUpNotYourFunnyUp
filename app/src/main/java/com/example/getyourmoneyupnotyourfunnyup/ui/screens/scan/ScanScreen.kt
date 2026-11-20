package com.example.getyourmoneyupnotyourfunnyup.ui.screens.scan

import android.Manifest
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreviewUseCase
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.receipt.ReceiptPhotoStore
import com.example.getyourmoneyupnotyourfunnyup.data.repository.ScanGroup
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItem
import com.example.getyourmoneyupnotyourfunnyup.model.ReceiptItemKind
import com.example.getyourmoneyupnotyourfunnyup.model.ScanPerson
import com.example.getyourmoneyupnotyourfunnyup.ui.components.DashedAddButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ReceiptPhoto
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ReceiptThumbnail
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ScanLine
import com.example.getyourmoneyupnotyourfunnyup.ui.components.dashedBorder
import com.example.getyourmoneyupnotyourfunnyup.ui.components.isReceiptPhoto
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.formatDanishAmountFromCents
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.AvatarUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ItemTag
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.OwerTone
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ReviewItemUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanStep
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ScanViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.SplitItemUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildScanUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Add expense flow: the camera (CameraX) or a photo from the gallery, read on the phone (ML Kit, no
 * internet needed), then check items, who had what, and save.
 */
@Composable
fun ScanRoute(
    viewModel: ScanViewModel,
    photoStore: ReceiptPhotoStore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var capturing by remember { mutableStateOf(false) }
    LaunchedEffect(state.flashOn) {
        imageCapture.flashMode = if (state.flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onAction(if (granted) ScanAction.OpenCamera else ScanAction.CameraDenied)
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onAction(ScanAction.PhotoPicked(uri.toString()))
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.onAction(ScanAction.PermissionChecked(granted))
    }
    BackHandler(enabled = state.step == ScanStep.Review && !state.manual) { viewModel.onAction(ScanAction.OpenCamera) }
    BackHandler(enabled = state.step == ScanStep.Split) { viewModel.onAction(ScanAction.BackToReview) }

    ScanScreen(
        state = state,
        onAction = viewModel::onAction,
        onClose = onClose,
        onRequestCamera = { cameraPermission.launch(Manifest.permission.CAMERA) },
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onShoot = {
            if (!capturing) {
                capturing = true
                val file = photoStore.newCaptureFile()
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(file).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            capturing = false
                            viewModel.onAction(ScanAction.PhotoTaken(Uri.fromFile(file).toString()))
                        }

                        override fun onError(exception: ImageCaptureException) {
                            capturing = false
                            viewModel.onAction(ScanAction.CaptureFailed)
                        }
                    },
                )
            }
        },
        viewfinder = { CameraViewfinder(imageCapture, it) },
        modifier = modifier,
    )
}

/** Live CameraX preview bound to the screen's lifecycle, sharing [imageCapture] with the shutter. */
@Composable
private fun CameraViewfinder(imageCapture: ImageCapture, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    val preview = remember { CameraPreviewUseCase.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } } }
    LaunchedEffect(lifecycleOwner) {
        // CameraX only binds and unbinds on the main thread, whichever thread awaitInstance resumes on.
        withContext(Dispatchers.Main.immediate) {
            val provider = ProcessCameraProvider.awaitInstance(context)
            try {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (_: IllegalArgumentException) {
                unavailable = true // no back camera on this device
                return@withContext
            }
            try {
                awaitCancellation()
            } finally {
                provider.unbind(preview, imageCapture)
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
        if (unavailable) {
            Text(
                "No camera available. Choose a photo or type it in.",
                Modifier.padding(32.dp),
                style = jakarta(15, FontWeight.SemiBold),
                color = AppColors.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ScanScreen(
    state: ScanUiState,
    onAction: (ScanAction) -> Unit,
    onClose: () -> Unit,
    onRequestCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onShoot: () -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(AppColors.White)) {
        when (state.step) {
            ScanStep.Starting -> Unit
            ScanStep.Permission -> PermissionStep(state.permissionDenied, onRequestCamera, onAction, onClose)
            ScanStep.Camera, ScanStep.Reading -> CameraStep(state, onAction, onClose, onPickPhoto, onShoot, viewfinder)
            ScanStep.Review -> ReviewStep(state, onAction, onClose)
            ScanStep.Split -> SplitStep(state, onAction)
            ScanStep.Saved -> SavedStep(state, onAction, onClose)
        }
    }
    if (state.groupPickerOpen) GroupPickerSheet(state, onAction)
    val photo = state.photo
    if (state.receiptViewerOpen && photo != null) {
        Dialog(
            onDismissRequest = { onAction(ScanAction.ShowReceipt(false)) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ReceiptPhoto(
                photo,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAction(ScanAction.ShowReceipt(false)) },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 0 · Camera permission
// ---------------------------------------------------------------------------------------------

@Composable
private fun PermissionStep(denied: Boolean, onRequestCamera: () -> Unit, onAction: (ScanAction) -> Unit, onClose: () -> Unit) {
    FillingScrollColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.padding(top = 8.dp).semantics { contentDescription = "Close" },
            colors = IconButtonDefaults.iconButtonColors(containerColor = AppColors.Surface),
        ) { AppIcon(AppIcons.Close, size = 20.dp, strokeWidth = 2f, tint = AppColors.Ink900) }
        Spacer(Modifier.weight(1f))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(168.dp).background(AppColors.Green100, CircleShape), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .size(84.dp, 108.dp)
                        .rotate(-6f)
                        .shadow(12.dp, RoundedCornerShape(10.dp), ambientColor = AppColors.Green700, spotColor = AppColors.Green700)
                        .background(AppColors.White, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Bar(0.6f, 6, AppColors.Green200)
                    Bar(1f, 5, AppColors.Line)
                    Bar(0.85f, 5, AppColors.Line)
                    Bar(0.92f, 5, AppColors.Line)
                    Bar(0.7f, 5, AppColors.Line)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { Bar(0.5f, 7, AppColors.Green500) }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset((-18).dp, (-18).dp)
                        .size(52.dp)
                        .background(AppColors.Green100, CircleShape)
                        .padding(4.dp)
                        .background(AppColors.Green700, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Camera, size = 24.dp, strokeWidth = 2f, tint = AppColors.White) }
            }
            Text(
                "Snap a receipt,\nskip the typing",
                Modifier.padding(top = 8.dp),
                style = jakarta(26, FontWeight.ExtraBold, -0.02f, 31.sp),
                color = AppColors.Ink900,
                textAlign = TextAlign.Center,
            )
            Text(
                "We use your camera to read the items and prices for you. Everything is read on your phone, so it works offline too.",
                style = jakarta(15, FontWeight.Medium, lineHeight = 23.sp),
                color = AppColors.Ink600,
                textAlign = TextAlign.Center,
            )
            if (denied) {
                Notice(tone = NoticeTone.Warning, icon = AppIcons.AlertCircle) {
                    Text(
                        "Camera access is off. Turn it on in Settings, or type the receipt in yourself.",
                        style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                        color = AppColors.Amber800,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Allow camera", onRequestCamera, Modifier.fillMaxWidth())
            AppButton(
                "Type it in manually instead", { onAction(ScanAction.TypeManually) }, Modifier.fillMaxWidth(),
                tone = ButtonTone.Text, size = ButtonSize.Medium,
            )
        }
    }
}

@Composable
private fun Bar(fraction: Float, height: Int, color: Color) {
    Box(Modifier.fillMaxWidth(fraction).height(height.dp).background(color, RoundedCornerShape(3.dp)))
}

// ---------------------------------------------------------------------------------------------
// 1 · Camera and 2 · Reading
// ---------------------------------------------------------------------------------------------

@Composable
private fun CameraStep(
    state: ScanUiState,
    onAction: (ScanAction) -> Unit,
    onClose: () -> Unit,
    onPickPhoto: () -> Unit,
    onShoot: () -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
) {
    val reading = state.step == ScanStep.Reading
    Column(Modifier.fillMaxSize().background(AppColors.Camera)) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassButton(AppIcons.Close, "Close", onClose)
            Text(
                "Scan receipt", Modifier.weight(1f), style = jakarta(16, FontWeight.Bold),
                color = AppColors.White, textAlign = TextAlign.Center,
            )
            GlassButton(
                AppIcons.Flash, if (state.flashOn) "Flash on" else "Flash off", { onAction(ScanAction.ToggleFlash) },
                active = state.flashOn,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val frame = Modifier.fillMaxSize().padding(start = 44.dp, end = 44.dp, top = 36.dp, bottom = 40.dp)
            val photo = state.photo
            if (reading) {
                // The photo being read, under the sweeping scan line.
                if (photo != null) {
                    Box(frame) {
                        ReceiptPhoto(photo, Modifier.fillMaxSize())
                        ScanLine(Modifier.matchParentSize())
                    }
                }
            } else {
                viewfinder(Modifier.fillMaxSize())
                ViewfinderCorners(frame)
            }
        }
        if (reading) {
            ReadingSheet(state.foundLines)
        } else {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    state.cameraMessage ?: "Fit the whole receipt inside the frame",
                    Modifier.background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    style = jakarta(14, FontWeight.SemiBold),
                    color = AppColors.White,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SquareGlassButton(AppIcons.Image, "Choose from photos", onPickPhoto)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .size(80.dp)
                            .border(4.dp, AppColors.White, CircleShape)
                            .padding(9.dp)
                            .clip(CircleShape)
                            .background(AppColors.White)
                            .clickable(role = Role.Button, onClick = onShoot)
                            .semantics { contentDescription = "Take photo" },
                    )
                    Spacer(Modifier.weight(1f))
                    SquareGlassButton(AppIcons.Pencil, "Type in manually") { onAction(ScanAction.TypeManually) }
                }
            }
        }
    }
}

@Composable
private fun GlassButton(icon: AppIconSpec, label: String, onClick: () -> Unit, active: Boolean = false) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) AppColors.Green300 else Color.White.copy(alpha = 0.12f),
        ),
    ) { AppIcon(icon, size = 20.dp, strokeWidth = 2f, tint = if (active) AppColors.Ink900 else AppColors.White) }
}

@Composable
private fun SquareGlassButton(icon: AppIconSpec, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { AppIcon(icon, size = 22.dp, tint = AppColors.White) }
}

@Composable
private fun ViewfinderCorners(modifier: Modifier) {
    Canvas(modifier) {
        val arm = 36.dp.toPx()
        val radius = 18.dp.toPx()
        val stroke = Stroke(width = 4.dp.toPx())
        val inset = 2.dp.toPx()
        fun corner(origin: Offset, sx: Float, sy: Float) {
            val path = Path().apply {
                moveTo(origin.x + sx * inset, origin.y + sy * arm)
                lineTo(origin.x + sx * inset, origin.y + sy * radius)
                val left = if (sx > 0) origin.x + inset else origin.x - inset - 2 * (radius - inset)
                val top = if (sy > 0) origin.y + inset else origin.y - inset - 2 * (radius - inset)
                val start = when {
                    sx > 0 && sy > 0 -> 180f
                    sx < 0 && sy > 0 -> 0f
                    sx > 0 -> 180f
                    else -> 0f
                }
                val sweep = if (sx * sy > 0) 90f else -90f
                arcTo(Rect(Offset(left, top), Size(2 * (radius - inset), 2 * (radius - inset))), start, sweep, false)
                lineTo(origin.x + sx * arm, origin.y + sy * inset)
            }
            drawPath(path, AppColors.Green300, style = stroke)
        }
        corner(Offset(0f, 0f), 1f, 1f)
        corner(Offset(size.width, 0f), -1f, 1f)
        corner(Offset(0f, size.height), 1f, -1f)
        corner(Offset(size.width, size.height), -1f, -1f)
    }
}

@Composable
private fun ReadingSheet(foundLines: Int?) {
    // Finding the lines takes as long as the phone needs; the last two steps then tick off in turn.
    var progress by remember { mutableIntStateOf(0) }
    LaunchedEffect(foundLines) {
        if (foundLines == null) return@LaunchedEffect
        for (step in 1..3) {
            progress = step
            delay(ScanViewModel.RESULT_PAUSE_MS / 3)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(AppColors.White, AppShapes.BottomSheet)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spinner(size = 24.dp, strokeWidth = 3.dp)
            Text("Reading your receipt…", style = jakarta(18, FontWeight.ExtraBold), color = AppColors.Ink900)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val found = when (foundLines) {
                null -> "Finding the lines"
                1 -> "Found 1 line"
                else -> "Found $foundLines lines"
            }
            listOf(found, "Reading prices", "Checking the total").forEachIndexed { index, label ->
                ReadingLine(label, done = index < progress, active = index == progress)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(AppIcons.Phone, size = 14.dp, strokeWidth = 2f, tint = AppColors.Ink600)
            Text("Read on your phone, so it works without internet", style = AppTypography.Caption, color = AppColors.Ink600)
        }
    }
}

@Composable
private fun ReadingLine(label: String, done: Boolean, active: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when {
                done -> AppIcon(AppIcons.Check, size = 18.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
                active -> Box(Modifier.size(8.dp).background(AppColors.Green500, CircleShape))
                else -> Box(Modifier.size(8.dp).border(2.dp, AppColors.Ink400, CircleShape))
            }
        }
        Text(
            label,
            style = jakarta(14, if (done || active) FontWeight.SemiBold else FontWeight.Medium),
            color = when {
                done -> AppColors.Green700
                active -> AppColors.Ink900
                else -> AppColors.Ink600
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 3 · Check items
// ---------------------------------------------------------------------------------------------

@Composable
private fun StepHeader(title: String, step: Int, onBack: () -> Unit, backLabel: String) {
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) {
                AppIcon(AppIcons.ArrowLeft, size = 22.dp, strokeWidth = 2f, tint = AppColors.Ink900)
            }
            Text(title, Modifier.weight(1f), style = jakarta(20, FontWeight.ExtraBold), color = AppColors.Ink900)
            Text("Step $step of 2", Modifier.padding(end = 8.dp), style = jakarta(13, FontWeight.Bold), color = AppColors.Ink600)
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { index ->
                Box(
                    Modifier.weight(1f).height(4.dp)
                        .background(if (index < step) AppColors.Green700 else AppColors.Line, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun ReviewStep(state: ScanUiState, onAction: (ScanAction) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().imePadding()) {
        StepHeader(
            title = "Check items", step = 1,
            onBack = { if (state.manual) onClose() else onAction(ScanAction.OpenCamera) },
            backLabel = if (state.manual) "Close" else "Back to camera",
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "group") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    GroupRow(state, onAction)
                    PayerRow(state, onAction)
                    if (state.manual) {
                        LabeledTextField(
                            label = "What was it for?",
                            value = state.title,
                            onValueChange = { onAction(ScanAction.TitleChanged(it)) },
                            placeholder = "e.g. Groceries",
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ReceiptThumbnail(state.photo, width = 44.dp, height = 56.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(ScanViewModel.receiptHeading(state.title, System.currentTimeMillis()), style = AppTypography.ItemTitle, color = AppColors.Ink900)
                                Text("Receipt photo saved with the expense", style = AppTypography.Caption, color = AppColors.Ink600)
                            }
                            if (isReceiptPhoto(state.photo)) {
                                Text(
                                    "View",
                                    Modifier.clickable(role = Role.Button) { onAction(ScanAction.ShowReceipt(true)) }.padding(12.dp),
                                    style = jakarta(14, FontWeight.Bold),
                                    color = AppColors.Green700,
                                )
                            }
                        }
                        if (state.readFailed) {
                            Notice(tone = NoticeTone.Warning, icon = AppIcons.Warning) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "We couldn't read this receipt. Type the items in below, or retake the photo.",
                                        style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                                        color = AppColors.Amber800,
                                    )
                                    Text(
                                        "Retake photo",
                                        Modifier.clickable(role = Role.Button) { onAction(ScanAction.OpenCamera) }.padding(vertical = 8.dp),
                                        style = jakarta(14, FontWeight.Bold),
                                        color = AppColors.Amber800,
                                    )
                                }
                            }
                        }
                        ReceiptTotalCheck(state, onAction)
                    }
                }
            }
            items(state.items, key = { it.id }) { item -> ReviewItemRow(item, onAction) }
            item(key = "add") {
                DashedAddButton("Add item", onClick = { onAction(ScanAction.AddItem) }, modifier = Modifier.height(48.dp))
            }
        }
        Column(Modifier.navigationBarsPadding()) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.LineSoft))
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("Total", style = jakarta(12, FontWeight.SemiBold), color = AppColors.Ink600)
                    Text("${formatDanishAmountFromCents(state.totalCents)}", style = jakarta(20, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
                }
                AppButton(
                    text = "Next: who had what",
                    onClick = { onAction(ScanAction.GoToSplit) },
                    modifier = Modifier.weight(1f),
                    enabled = state.canContinue,
                    trailingIcon = AppIcons.ArrowRight,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(state: ScanUiState, onAction: (ScanAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardOutline(corner = 18.dp, color = AppColors.Line)
            .clickable(role = Role.Button) { onAction(ScanAction.ToggleGroupPicker) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val group = state.group
        if (group != null) GroupAvatar(group.avatar.initial, group.avatar.paletteIndex)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Add to group", style = jakarta(12, FontWeight.SemiBold), color = AppColors.Ink600)
            OneLine(group?.name ?: "Choose a group", AppTypography.ItemTitle, AppColors.Ink900)
        }
        Text("Change", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
    }
}

@Composable
private fun PayerRow(state: ScanUiState, onAction: (ScanAction) -> Unit) {
    val payer = state.payer ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("Paid by", style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600)
            Row(
                Modifier
                    .height(44.dp)
                    .clickable(role = Role.Button) { onAction(ScanAction.TogglePayerPicker) }
                    .semantics(mergeDescendants = true) { contentDescription = "Paid by ${payer.name}, change" }
                    .padding(start = 8.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.height(34.dp).border(1.dp, AppColors.Line, RoundedCornerShape(17.dp)).padding(start = 3.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PaletteAvatar(payer.avatar.initial, payer.avatar.paletteIndex, size = 26.dp)
                    Text(payer.name, style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900)
                    AppIcon(AppIcons.ChevronDown, size = 14.dp, strokeWidth = 2.4f, tint = AppColors.Ink900)
                }
            }
        }
        if (state.payerPickerOpen) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.payers.forEach { option ->
                    Row(
                        Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (option.selected) AppColors.Green50 else AppColors.White)
                            .border(1.5.dp, if (option.selected) AppColors.Green700 else AppColors.Line, RoundedCornerShape(22.dp))
                            .clickable(role = Role.RadioButton) { onAction(ScanAction.PickPayer(option.id)) }
                            .semantics { selected = option.selected }
                            .padding(start = 6.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PaletteAvatar(option.avatar.initial, option.avatar.paletteIndex, size = 26.dp)
                        Text(option.name, style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewItemRow(item: ReviewItemUi, onAction: (ScanAction) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (item.lowConfidence) AppColors.Amber100 else AppColors.SurfaceSoft, RoundedCornerShape(16.dp))
            .border(1.5.dp, if (item.lowConfidence) AppColors.Amber400 else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicTextField(
                value = item.name,
                onValueChange = { onAction(ScanAction.ItemNameChanged(item.id, it)) },
                singleLine = true,
                textStyle = jakarta(15, FontWeight.SemiBold).copy(color = AppColors.Ink900),
                cursorBrush = SolidColor(AppColors.Green700),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).semantics { contentDescription = "Item name" },
                decorationBox = { inner ->
                    if (item.name.isEmpty()) Text("Item name", style = jakarta(15, FontWeight.SemiBold), color = AppColors.Ink400)
                    inner()
                },
            )
            item.tag?.let { tag ->
                val (background, color) = when (tag) {
                    ItemTag.CheckPrice -> AppColors.Amber200 to AppColors.Amber800
                    ItemTag.Alcohol, ItemTag.Meat -> AppColors.Lavender50 to AppColors.Lavender800
                    else -> AppColors.Surface to AppColors.Ink600
                }
                Text(
                    tag.label,
                    Modifier.padding(start = 6.dp).background(background, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    style = jakarta(11, FontWeight.Bold),
                    color = color,
                )
            }
        }
        BasicTextField(
            value = item.priceText,
            onValueChange = { onAction(ScanAction.ItemPriceChanged(item.id, it)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = jakarta(15, FontWeight.Bold).copy(
                color = if (item.isNegative) AppColors.Green700 else AppColors.Ink900,
                textAlign = TextAlign.End,
            ),
            cursorBrush = SolidColor(AppColors.Green700),
            modifier = Modifier
                .width(84.dp)
                .height(40.dp)
                .background(AppColors.White, RoundedCornerShape(10.dp))
                .border(1.dp, if (item.lowConfidence) AppColors.Amber500 else AppColors.Line, RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Price in kroner" },
            decorationBox = { inner ->
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                    if (item.priceText.isEmpty()) Text("0,00", style = jakarta(15, FontWeight.Bold), color = AppColors.Ink400)
                    inner()
                }
            },
        )
        Box(
            Modifier
                .size(40.dp, 44.dp)
                .clickable(role = Role.Button) { onAction(ScanAction.RemoveItem(item.id)) }
                .semantics { contentDescription = "Remove item" },
            contentAlignment = Alignment.Center,
        ) { AppIcon(AppIcons.Trash, size = 18.dp, tint = AppColors.Ink400) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPickerSheet(state: ScanUiState, onAction: (ScanAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onAction(ScanAction.ToggleGroupPicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.BottomSheet,
        containerColor = AppColors.White,
        scrimColor = AppColors.Ink900.copy(alpha = 0.42f),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Add to group", Modifier.padding(bottom = 4.dp), style = jakarta(20, FontWeight.ExtraBold), color = AppColors.Ink900)
            state.groups.forEach { group ->
                val selected = group.id == state.group?.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .cardOutline(color = if (selected) AppColors.Green700 else AppColors.LineSoft)
                        .background(if (selected) AppColors.Green50 else AppColors.White)
                        .clickable(role = Role.RadioButton) { onAction(ScanAction.PickGroup(group.id)) }
                        .semantics { this.selected = selected }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GroupAvatar(group.avatar.initial, group.avatar.paletteIndex)
                    OneLine(group.name, AppTypography.ItemTitle, AppColors.Ink900, Modifier.weight(1f))
                    if (selected) AppIcon(AppIcons.Check, size = 20.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4 · Who had what
// ---------------------------------------------------------------------------------------------

@Composable
private fun SplitStep(state: ScanUiState, onAction: (ScanAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StepHeader("Who had what", 2, onBack = { onAction(ScanAction.BackToReview) }, backLabel = "Back to items")
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "people") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("Splitting between · tap to leave someone out", style = jakarta(13, FontWeight.Bold), color = AppColors.Ink600)
                    state.people.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { person ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable(enabled = person.canToggle, role = Role.Checkbox) { onAction(ScanAction.TogglePerson(person.id)) }
                                        .semantics { selected = person.included },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    ToggleAvatar(person.avatar, person.included, size = 52)
                                    Text(
                                        person.name,
                                        style = jakarta(13, FontWeight.Bold).copy(
                                            textDecoration = if (person.included) TextDecoration.None else TextDecoration.LineThrough,
                                        ),
                                        color = if (person.included) AppColors.Ink900 else AppColors.Ink400,
                                        maxLines = 1,
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    state.ruleNotice?.let { notice ->
                        Notice(
                            tone = NoticeTone.Rule,
                            leading = {
                                state.ruleAvatar?.let { PaletteAvatar(it.initial, it.paletteIndex, size = 28.dp) }
                            },
                        ) {
                            Text(notice, style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp), color = AppColors.Lavender800)
                        }
                    }
                }
            }
            items(state.splitItems, key = { it.id }) { item -> SplitItemCard(item, onAction) }
            state.extrasNote?.let { note ->
                item(key = "extras") {
                    Text(note, Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp), style = jakarta(13, FontWeight.Medium, lineHeight = 19.sp), color = AppColors.Ink600)
                }
            }
        }
        Column(Modifier.navigationBarsPadding()) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.LineSoft))
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.shares.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { share ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .background(if (share.isMe) AppColors.Surface else AppColors.Green50, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(share.name, style = jakarta(12, FontWeight.SemiBold), color = AppColors.Ink600, maxLines = 1)
                                Text(formatDanishAmountFromCents(share.amountCents, includeSuffix = false), style = jakarta(14, FontWeight.ExtraBold), color = AppColors.Ink900, maxLines = 1)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                state.saveError?.let { error ->
                    Notice(tone = NoticeTone.Warning, icon = AppIcons.AlertCircle) {
                        Text(error, style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp), color = AppColors.Amber800)
                    }
                }
                AppButton(
                    text = if (state.saving) "Saving…" else "Save expense · ${formatDanishAmountFromCents(state.totalCents)}",
                    onClick = { onAction(ScanAction.Save) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canSave,
                )
            }
        }
    }
}

/**
 * The receipt's printed total (read from the photo, or typed in when it wasn't found) and whether the
 * items add up to it. A mismatch means a price was misread or an item was missed.
 */
@Composable
private fun ReceiptTotalCheck(state: ScanUiState, onAction: (ScanAction) -> Unit) {
    LabeledTextField(
        label = "Total on receipt",
        value = state.receiptTotalText,
        onValueChange = { onAction(ScanAction.ReceiptTotalChanged(it)) },
        placeholder = "e.g. 240,35",
        keyboardType = KeyboardType.Decimal,
        isWarning = state.receiptTotalCents != null && state.receiptTotalCents != state.totalCents,
    )
    val receiptTotal = state.receiptTotalCents
    when {
        // Nothing was read at all; the notice above already says so.
        receiptTotal == null && state.readFailed -> Unit
        receiptTotal == null -> Notice(tone = NoticeTone.Warning, icon = AppIcons.AlertCircle) {
            Text(
                "We couldn't find the total on the receipt. Type it in above to check that the items add up.",
                style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                color = AppColors.Amber800,
            )
        }
        receiptTotal != state.totalCents -> Notice(tone = NoticeTone.Warning, icon = AppIcons.Warning) {
            val difference = formatDanishAmountFromCents(kotlin.math.abs(state.totalCents - receiptTotal))
            val where = if (state.items.any { it.lowConfidence }) "Check the highlighted prices." else "Check the prices against the receipt."
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("Items add up to ${formatDanishAmountFromCents(state.totalCents)}")
                    }
                    append(", but the receipt says ${formatDanishAmountFromCents(receiptTotal)}")
                    append(if (state.totalCents > receiptTotal) " ($difference too much). " else " ($difference missing). ")
                    append(where)
                },
                style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                color = AppColors.Amber800,
            )
        }
        else -> Notice(tone = NoticeTone.Success, icon = AppIcons.Check) {
            Text("Items match the receipt total", style = jakarta(14, FontWeight.Bold), color = AppColors.Green700)
        }
    }
}

@Composable
private fun ToggleAvatar(avatar: AvatarUi, on: Boolean, size: Int) {
    if (on) {
        PaletteAvatar(avatar.initial, avatar.paletteIndex, size = size.dp)
    } else {
        Box(
            Modifier.size(size.dp).background(AppColors.White, CircleShape).dashedBorder(AppColors.SwitchOff, (size / 2).dp, width = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(avatar.initial, style = jakarta((size * 0.35f).toInt(), FontWeight.ExtraBold), color = AppColors.Ink400)
        }
    }
}

@Composable
private fun SplitItemCard(item: SplitItemUi, onAction: (ScanAction) -> Unit) {
    Column(
        Modifier.fillMaxWidth().cardOutline(corner = 18.dp).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OneLine(item.name, AppTypography.ItemTitle, AppColors.Ink900, Modifier.weight(1f))
            Text("${formatDanishAmountFromCents(item.priceCents)}", style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.who.take(5).forEach { who ->
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Checkbox) { onAction(ScanAction.ToggleItemPerson(item.id, who.personId)) }
                        .semantics {
                            contentDescription = who.name + if (who.on) " shares this" else " does not share this"
                            selected = who.on
                        },
                ) { ToggleAvatar(who.avatar, who.on, size = 44) }
            }
            Text(
                item.eachLabel,
                Modifier.weight(1f),
                style = jakarta(13, FontWeight.Medium),
                color = AppColors.Ink600,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        if (item.who.size > 5) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.who.drop(5).forEach { who ->
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Checkbox) { onAction(ScanAction.ToggleItemPerson(item.id, who.personId)) }
                            .semantics {
                                contentDescription = who.name + if (who.on) " shares this" else " does not share this"
                                selected = who.on
                            },
                    ) { ToggleAvatar(who.avatar, who.on, size = 44) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5 · Saved (Confirmation)
// ---------------------------------------------------------------------------------------------

@Composable
private fun SavedStep(state: ScanUiState, onAction: (ScanAction) -> Unit, onDone: () -> Unit) {
    val saved = state.saved ?: return
    FillingScrollColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(104.dp).background(AppColors.Green100, CircleShape).padding(16.dp).background(AppColors.Green700, CircleShape), contentAlignment = Alignment.Center) {
                AppIcon(AppIcons.Check, size = 36.dp, strokeWidth = 2.6f, tint = AppColors.White)
            }
            Text("Expense saved", Modifier.padding(top = 8.dp), style = jakarta(26, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
            Text(
                "${saved.title} · ${formatDanishAmountFromCents(saved.totalCents)} · paid by ${saved.payerLabel}",
                style = jakarta(15, FontWeight.Medium),
                color = AppColors.Ink600,
                textAlign = TextAlign.Center,
            )
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).cardOutline(color = AppColors.Line).padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                saved.owers.forEach { ower ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PaletteAvatar(ower.avatar.initial, ower.avatar.paletteIndex, size = 36.dp)
                        Text(ower.text, Modifier.weight(1f), style = jakarta(15, FontWeight.SemiBold), color = AppColors.Ink900)
                        Text(
                            formatDanishAmountFromCents(
                                if (ower.tone == OwerTone.Outgoing) -ower.amountCents else ower.amountCents,
                                includePlusSign = ower.tone == OwerTone.Incoming,
                            ),
                            style = jakarta(15, FontWeight.ExtraBold),
                            color = when (ower.tone) {
                                OwerTone.Incoming -> AppColors.Green700
                                OwerTone.Outgoing -> AppColors.Red700
                                OwerTone.Neutral -> AppColors.Ink900
                            },
                        )
                    }
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Surface))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your share", Modifier.weight(1f), style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600)
                    Text("${formatDanishAmountFromCents(saved.myShareCents)}", style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
                }
            }
            Row(
                Modifier
                    .background(if (saved.synced) AppColors.Green100 else AppColors.Surface, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (saved.synced) {
                    AppIcon(AppIcons.Check, size = 16.dp, strokeWidth = 2.4f, tint = AppColors.Green700)
                } else {
                    Spinner(size = 14.dp, color = AppColors.Ink600, trackColor = AppColors.SwitchOff)
                }
                Text(
                    saved.statusText,
                    style = jakarta(14, FontWeight.SemiBold),
                    color = if (saved.synced) AppColors.Green700 else AppColors.Ink600,
                )
            }
        }
        Spacer(Modifier.weight(1f).height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Done", onDone, Modifier.fillMaxWidth())
            AppButton(
                "Scan another receipt", { onAction(ScanAction.ScanAnother) }, Modifier.fillMaxWidth(),
                tone = ButtonTone.Text, size = ButtonSize.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

private val previewGroups = listOf(
    ScanGroup(
        id = "kol", name = "Kollegiet 4. sal", colourIndex = 0, currency = "DKK", lastActivityAt = 0L,
        members = listOf(
            ScanPerson("me", "Victor", -1),
            ScanPerson("ida", "Ida", 2),
            ScanPerson("mads", "Mads", 1, excludeAlcohol = true),
            ScanPerson("jonas", "Jonas", 4),
        ),
    ),
)

private fun preview(local: ScanLocalState): ScanUiState = buildScanUiState(previewGroups, "me", true, local, null)

/** A sample Netto receipt as if it had just been read. */
private fun scanned(step: ScanStep) = ScanLocalState(
    step = step, groupId = "kol", title = "Netto",
    items = listOf(
        ReceiptItem(1, "Tuborg grøn 6-pak", "59,95", isAlcohol = true),
        ReceiptItem(2, "Pant A", "6,00", ReceiptItemKind.DEPOSIT),
        ReceiptItem(3, "Hakket oksekød", "45,00", isMeat = true),
        ReceiptItem(4, "Rugbrød", "22,00"),
        ReceiptItem(5, "Merlot rødvin", "79,00", isAlcohol = true, lowConfidence = true),
    ),
    receiptTotalText = "211,95",
)

@Composable
private fun PreviewScan(state: ScanUiState) {
    GetYourMoneyUpNotYourFunnyUpTheme {
        ScanScreen(state, {}, {}, {}, {}, {}, viewfinder = { Box(it.background(AppColors.Camera)) })
    }
}

@Preview(name = "Scan – camera permission", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Scan – camera permission (dark)", showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PermissionPreview() {
    PreviewScan(preview(ScanLocalState(step = ScanStep.Permission)))
}

@Preview(name = "Scan – camera", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Scan – camera (dark)", showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CameraPreview() {
    PreviewScan(preview(ScanLocalState(step = ScanStep.Camera)))
}

@Preview(name = "Scan – check items", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Scan – check items (dark)", showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReviewPreview() {
    PreviewScan(preview(scanned(ScanStep.Review)))
}

@Preview(name = "Scan – who had what", showBackground = true, widthDp = 412, heightDp = 915)
@Preview(name = "Scan – who had what (dark)", showBackground = true, widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SplitPreview() {
    PreviewScan(preview(scanned(ScanStep.Split)))
}
