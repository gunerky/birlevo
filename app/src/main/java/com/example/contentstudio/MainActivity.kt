package com.example.contentstudio

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.data.*
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.ui.AudioToolScreen
import com.example.contentstudio.media.MediaOutputs
import com.example.contentstudio.media.PhotoPdfViewModel
import com.example.contentstudio.media.PdfReaderViewModel
import com.example.contentstudio.media.PdfPagesViewModel
import com.example.contentstudio.ui.PdfPagesScreen
import com.example.contentstudio.ui.PdfReaderScreen
import com.example.contentstudio.ui.HomeScreen
import com.example.contentstudio.ui.ProjectDraft
import com.example.contentstudio.ui.LanguageDialog
import com.example.contentstudio.ui.importIssueText
import com.example.contentstudio.ui.ImageCompressionScreen
import com.example.contentstudio.ui.VideoCompressionScreen
import com.example.contentstudio.ui.PresentationPdfScreen
import com.example.contentstudio.ui.PhotoPdfScreen
import com.example.contentstudio.ui.TextDocumentScreen
import com.example.contentstudio.documents.TextDocumentViewModel
import com.example.contentstudio.ui.StudioTheme
import com.example.contentstudio.ui.StudioBrand
import com.example.contentstudio.ui.installStudioSplash
import com.example.contentstudio.ui.configureStudioWindow
import java.util.UUID
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installStudioSplash(animate = savedInstanceState == null)
        super.onCreate(savedInstanceState)
        configureStudioWindow(window)
        setContent { StudioTheme { StudioApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioApp() {
    val context = LocalContext.current
    val repository = remember { ProjectRepository(context.applicationContext) }
    val editor: ProjectEditorViewModel = viewModel(factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ProjectEditorViewModel(repository, extras.createSavedStateHandle(), MediaOutputs(context.applicationContext)) as T
        }
    })
    val editorState by editor.state.collectAsStateWithLifecycle()
    val photoPdfModel: PhotoPdfViewModel = viewModel()
    val pdfReaderModel: PdfReaderViewModel = viewModel()
    val pdfPagesModel: PdfPagesViewModel = viewModel()
    val documentModel: TextDocumentViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val projects = editorState.projects
    val loading = editorState.loading
    val loadFailed = editorState.loadFailed
    val saving = editorState.closing || editorState.importingImage
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var showImageTool by rememberSaveable { mutableStateOf(false) }
    var showVideoTool by rememberSaveable { mutableStateOf(false) }
    var videoToolIsEditor by rememberSaveable { mutableStateOf(false) }
    var showPdfPreview by rememberSaveable { mutableStateOf(false) }
    var showPhotoPdf by rememberSaveable { mutableStateOf(false) }
    var showPdfReader by rememberSaveable { mutableStateOf(false) }
    var showPdfPages by rememberSaveable { mutableStateOf(false) }
    var showAudio by rememberSaveable { mutableStateOf(false) }
    var showDocuments by rememberSaveable { mutableStateOf(false) }
    var legacyVideoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val draft = editorState.draft
    val loadError = stringResource(R.string.load_error)
    val saveError = stringResource(R.string.save_error)
    LaunchedEffect(editorState.saveStatus, saveError) {
        if (editorState.saveStatus == DraftSaveStatus.ERROR) snackbar.showSnackbar(saveError)
    }
    fun edit(project: StudioProject) { editor.edit(project) }

    fun saveAndClose() {
        val current = draft ?: return
        editor.saveAndClose(context.getString(if (current.kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video))
    }

    DisposableEffect(editor, context) {
        val lifecycle = (context as LifecycleOwner).lifecycle
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) editor.flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = draft != null) { saveAndClose() }

    if (showPdfPreview && draft != null) {
        PresentationPdfScreen(draft, onClose = { showPdfPreview = false })
        return
    }

    if (showAudio) {
        val audioModel: AudioToolViewModel = viewModel()
        AudioToolScreen(audioModel, onClose = { showAudio = false }); return
    }
    if (showDocuments) {
        TextDocumentScreen(documentModel, onClose = { showDocuments = false }, presentations = projects,
            onReadPdf = { uri -> pdfReaderModel.load(uri); showDocuments = false; showPdfReader = true },
            onAddText = { slides, target ->
                editor.addPresentationText(slides, target, context.getString(R.string.presentation)).also { if (it) showDocuments = false }
            })
        return
    }
    if (showPdfReader) {
        PdfReaderScreen(onClose = { showPdfReader = false }, presentations = projects, model = pdfReaderModel,
            onAddToPresentation = { uri, target ->
                val attached = editor.addPresentationImage(uri, target, context.getString(R.string.presentation))
                if (attached) { pdfReaderModel.reset(); showPdfReader = false }
                attached
            }, onAddToPhotoPdf = { uri ->
                photoPdfModel.addOutput(uri).also { if (it) { pdfReaderModel.reset(); showPdfReader = false; showPhotoPdf = true } }
            })
        return
    }
    if (showPhotoPdf) { PhotoPdfScreen(onClose = { showPhotoPdf = false }, model = photoPdfModel); return }
    if (showPdfPages) {
        PdfPagesScreen(pdfPagesModel, onClose = { showPdfPages = false }, onRead = { uri ->
            pdfReaderModel.load(uri); showPdfReader = true
        })
        return
    }
    if (showImageTool) {
        ImageCompressionScreen(onClose = { showImageTool = false }, presentations = projects,
            onAddToPresentation = { uri, targetId ->
                val attached = editor.addPresentationImage(uri, targetId, context.getString(R.string.presentation))
                if (attached) showImageTool = false
                attached
            }, onAddToPhotoPdf = { uri ->
                photoPdfModel.addOutput(uri).also { if (it) { showImageTool = false; showPhotoPdf = true } }
            })
        return
    }
    if (showVideoTool) {
        VideoCompressionScreen(onClose = { showVideoTool = false; legacyVideoUri = null }, editMode = videoToolIsEditor,
            initialUri = legacyVideoUri?.let(android.net.Uri::parse), onAddToAudio = { uri ->
                val model = ViewModelProvider(context as MainActivity)[AudioToolViewModel::class.java]
                model.addOutput(uri).also { if (it) { showVideoTool = false; legacyVideoUri = null; showAudio = true } }
            })
        return
    }

    Scaffold(contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (draft == null) stringResource(R.string.app_name) else stringResource(R.string.draft),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (draft == null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (draft != null) TextButton(onClick = ::saveAndClose, enabled = !saving && !loading && !loadFailed) {
                        Text(stringResource(R.string.back))
                    }
                },
                actions = {
                    if (draft == null) TextButton(onClick = { showLanguages = true }, modifier = Modifier.padding(end = 12.dp).testTag("choose-language")) {
                        Text(stringResource(R.string.language))
                    } else FilledTonalButton(onClick = ::saveAndClose, enabled = !saving && !loading && !loadFailed,
                        modifier = Modifier.padding(end = 8.dp).testTag("save-project")) {
                        Text(stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            when {
                loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                loadFailed -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError)
                    Button(onClick = editor::load, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                draft == null -> HomeScreen(projects, onOpen = ::edit, onAudio = { showAudio = true }, onReadPdf = { showPdfReader = true }, onPdfPages = { showPdfPages = true }, onReadDocuments = { showDocuments = true }, onPhotosPdf = { showPhotoPdf = true }, onCompressImage = { showImageTool = true },
                    onCompressVideo = { videoToolIsEditor = false; showVideoTool = true },
                    onEditVideo = { videoToolIsEditor = true; showVideoTool = true }, onNew = { kind ->
                    edit(StudioProject(
                        id = UUID.randomUUID().toString(), kind = kind,
                        title = context.getString(if (kind == ProjectKind.PRESENTATION) R.string.presentation else R.string.video),
                        updatedAt = System.currentTimeMillis(),
                        slides = if (kind == ProjectKind.PRESENTATION) listOf(Slide()) else emptyList(),
                    ))
                })
                else -> ProjectDraft(draft, enabled = !saving, saveStatus = editorState.saveStatus,
                    onEditVideo = { uri -> legacyVideoUri = uri; videoToolIsEditor = true; showVideoTool = true },
                    onRetrySave = editor::flush, onChange = ::edit,
                    onPdf = { editor.flush(); showPdfPreview = true }, onMedia = editor::setMedia,
                    onSlideImage = { id, uri -> editor.importSlideImage(draft.id, id, android.net.Uri.parse(uri)) { issue ->
                        scope.launch { snackbar.showSnackbar(context.getString(issue?.let(::importIssueText) ?: R.string.slide_image_error)) }
                    } }, onError = { message ->
                    scope.launch { snackbar.showSnackbar(context.getString(message)) }
                })
            }
            if (editorState.importingImage) Surface(Modifier.align(Alignment.BottomCenter).padding(20.dp),
                shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.processing))
                }
            }
        }
    }

    if (showLanguages) LanguageDialog(onDismiss = { showLanguages = false })
}


