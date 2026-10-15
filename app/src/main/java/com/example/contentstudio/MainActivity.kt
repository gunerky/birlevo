package com.example.contentstudio

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.contentstudio.audio.AudioToolViewModel
import com.example.contentstudio.data.*
import com.example.contentstudio.documents.TextDocumentViewModel
import com.example.contentstudio.media.*
import com.example.contentstudio.ui.*
import java.util.UUID
import kotlinx.coroutines.flow.first
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
    val editor: ProjectEditorViewModel =
        viewModel(
            factory =
                remember(repository) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(
                            modelClass: Class<T>,
                            extras: CreationExtras,
                        ): T =
                            ProjectEditorViewModel(
                                repository,
                                extras.createSavedStateHandle(),
                                MediaOutputs(context.applicationContext),
                            )
                                as T
                    }
                }
        )
    val editorState by editor.state.collectAsStateWithLifecycle()
    // Resume cards read metadata only. Media restoration starts when its tool is visited.
    val photoPdfModel by
        remember(context) {
            lazy { ViewModelProvider(context as MainActivity)[PhotoPdfViewModel::class.java] }
        }
    val pdfReaderModel by
        remember(context) {
            lazy { ViewModelProvider(context as MainActivity)[PdfReaderViewModel::class.java] }
        }
    val pdfPagesModel by
        remember(context) {
            lazy { ViewModelProvider(context as MainActivity)[PdfPagesViewModel::class.java] }
        }
    val documentModel by
        remember(context) {
            lazy { ViewModelProvider(context as MainActivity)[TextDocumentViewModel::class.java] }
        }
    val imageModel by
        remember(context) {
            lazy {
                ViewModelProvider(context as MainActivity)[ImageCompressionViewModel::class.java]
            }
        }
    val slideshowModel by
        remember(context) {
            lazy { ViewModelProvider(context as MainActivity)[SlideshowViewModel::class.java] }
        }
    var slideshowRequest by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val draft = editorState.draft
    val projects =
        remember(editorState.projects, draft) {
            if (draft == null) editorState.projects
            else listOf(draft) + editorState.projects.filterNot { it.id == draft.id }
        }
    val loading = editorState.loading
    val loadFailed = editorState.loadFailed
    val saving = editorState.closing || editorState.importingImage || editorState.deleting
    var routes by
        rememberSaveable(
            stateSaver =
                listSaver<List<StudioRoute>, String>(
                    save = { it.map(StudioRoute::name) },
                    restore = { it.map(StudioRoute::valueOf) },
                )
        ) {
            mutableStateOf(listOf(StudioRoute.HOME))
        }
    val route = routes.last()
    val screens = rememberSaveableStateHolder()
    fun navigate(next: StudioRoute) {
        if (route != next) routes = routes + next
    }
    fun back() {
        if (routes.size > 1) routes = routes.dropLast(1)
    }
    var showLanguages by rememberSaveable { mutableStateOf(false) }
    var videoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pickedUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<PickedFile?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var chooseImageTarget by rememberSaveable { mutableStateOf(false) }
    fun importWhenReady(action: suspend () -> Unit) {
        selectionJob?.cancel()
        selectionJob =
            scope.launch {
                selecting = true
                try {
                    action()
                } finally {
                    selecting = false
                }
            }
    }
    fun busyJob(busy: Boolean): Boolean {
        if (busy) scope.launch { snackbar.showSnackbar(context.getString(R.string.processing)) }
        return busy
    }
    val openFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                pickedUri = uri.toString()
                selectedFile = null
                navigate(StudioRoute.FILE_ACTIONS)
            }
        }
    LaunchedEffect(pickedUri) {
        selectedFile =
            pickedUri?.let {
                runCatching { describeFile(context, Uri.parse(it)) }
                    .getOrElse { PickedFile("", null) }
            }
    }
    val resumeEntries by
        produceState<List<ResumeWork>?>(null, route, editorState.workRevision) {
            if (route == StudioRoute.HOME) value = resumeWork(context)
        }
    val saveError = stringResource(R.string.save_error)
    LaunchedEffect(editorState.saveStatus, saveError) {
        if (editorState.saveStatus == DraftSaveStatus.ERROR) snackbar.showSnackbar(saveError)
    }
    fun edit(project: StudioProject) {
        editor.edit(project)
        navigate(StudioRoute.PROJECT)
    }
    fun saveAndClose() {
        val current = draft ?: return
        editor.saveAndClose(
            context.getString(
                if (current.kind == ProjectKind.PRESENTATION) R.string.presentation
                else R.string.video
            )
        )
    }
    LaunchedEffect(draft?.id, loading, route) {
        if (!loading && draft == null && route == StudioRoute.PROJECT) back()
    }
    DisposableEffect(editor, context) {
        val lifecycle = (context as LifecycleOwner).lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) editor.flush()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = draft != null && route == StudioRoute.PROJECT) { saveAndClose() }
    when (route) {
        StudioRoute.PRESENTATION_PLAYER,
        StudioRoute.PRESENTATION_IMAGES,
        StudioRoute.PRESENTATION_PPTX -> {
            if (draft != null)
                screens.SaveableStateProvider(route.name) {
                    if (route == StudioRoute.PRESENTATION_PLAYER) {
                        val model: PresentationPlayerViewModel = viewModel()
                        PresentationPlayerScreen(draft, model, ::back)
                    } else if (route == StudioRoute.PRESENTATION_PPTX) {
                        val model: PresentationPptxViewModel = viewModel()
                        PresentationPptxScreen(draft, model, ::back)
                    } else {
                        val model: PresentationImageViewModel = viewModel()
                        PresentationImageScreen(
                            draft,
                            model,
                            ::back,
                            onToImage = { uri ->
                                if (imageModel.state.value.busy) false
                                else {
                                    imageModel.load(uri)
                                    val loaded = imageModel.state.first { !it.busy }
                                    (loaded.source?.originalUri == uri).also {
                                        if (it) navigate(StudioRoute.IMAGE)
                                    }
                                }
                            },
                        )
                    }
                }
            else {
                LaunchedEffect(loading) { if (!loading) back() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                BackHandler { back() }
            }
            return
        }
        StudioRoute.SLIDESHOW,
        StudioRoute.SLIDESHOW_PRESENTATION -> {
            screens.SaveableStateProvider(route.name) {
                SlideshowScreen(
                    ::back,
                    slideshowModel,
                    project = if (route == StudioRoute.SLIDESHOW_PRESENTATION) draft else null,
                    importRequest = slideshowRequest,
                    onEditOutput = { uri ->
                        val model =
                            ViewModelProvider(context as MainActivity)[
                                VideoCompressionViewModel::class.java]
                        val ready = model.state.first { !it.busy || it.workStatus?.active == true }
                        if (ready.busy) false
                        else {
                            model.load(uri)
                            val loaded = model.state.first { !it.busy }
                            (loaded.source?.originalUri == uri).also {
                                if (it) {
                                    videoUri = null
                                    navigate(StudioRoute.VIDEO)
                                }
                            }
                        }
                    },
                )
            }
            return
        }
        StudioRoute.AUDIO -> {
            screens.SaveableStateProvider(route.name) {
                val model: AudioToolViewModel = viewModel()
                AudioToolScreen(model, ::back)
            }
            return
        }
        StudioRoute.DOCUMENTS -> {
            screens.SaveableStateProvider(route.name) {
                TextDocumentScreen(
                    documentModel,
                    ::back,
                    presentations = projects,
                    onReadPdf = {
                        pdfReaderModel.load(it)
                        navigate(StudioRoute.PDF_READER)
                    },
                    onAddText = { slides, target ->
                        editor
                            .addPresentationText(
                                slides,
                                target,
                                context.getString(R.string.presentation),
                            )
                            .also { if (it) navigate(StudioRoute.PROJECT) }
                    },
                )
            }
            return
        }
        StudioRoute.PDF_READER,
        StudioRoute.PDF_IMAGE -> {
            screens.SaveableStateProvider(route.name) {
                PdfReaderScreen(
                    onClose = ::back,
                    presentations = projects,
                    model = pdfReaderModel,
                    initialPanel = if (route == StudioRoute.PDF_IMAGE) "image" else null,
                    onAddToPresentation = { uri, target ->
                        editor
                            .addPresentationImage(
                                uri,
                                target,
                                context.getString(R.string.presentation),
                            )
                            .also { if (it) navigate(StudioRoute.PROJECT) }
                    },
                    onAddToPhotoPdf = { uri ->
                        photoPdfModel.addOutput(uri).also {
                            if (it) navigate(StudioRoute.PHOTO_PDF)
                        }
                    },
                )
            }
            return
        }
        StudioRoute.PHOTO_PDF -> {
            screens.SaveableStateProvider(route.name) { PhotoPdfScreen(::back, photoPdfModel) }
            return
        }
        StudioRoute.PDF_PAGES -> {
            screens.SaveableStateProvider(route.name) {
                PdfPagesScreen(
                    pdfPagesModel,
                    ::back,
                    onRead = {
                        pdfReaderModel.load(it)
                        navigate(StudioRoute.PDF_READER)
                    },
                )
            }
            return
        }
        StudioRoute.BATCH_IMAGES -> {
            screens.SaveableStateProvider(route.name) { BatchImageScreen(::back) }
            return
        }
        StudioRoute.IMAGE -> {
            screens.SaveableStateProvider(route.name) {
                ImageCompressionScreen(
                    ::back,
                    imageModel,
                    projects,
                    onAddToPresentation = { uri, target ->
                        editor
                            .addPresentationImage(
                                uri,
                                target,
                                context.getString(R.string.presentation),
                            )
                            .also { if (it) navigate(StudioRoute.PROJECT) }
                    },
                    onAddToSlideshow = { uri ->
                        val ready =
                            slideshowModel.state.first { !it.busy || it.workStatus?.active == true }
                        if (ready.busy) false
                        else
                            slideshowModel.addOutput(uri).also {
                                if (it) navigate(StudioRoute.SLIDESHOW)
                            }
                    },
                    onAddToPhotoPdf = { uri ->
                        photoPdfModel.addOutput(uri).also {
                            if (it) navigate(StudioRoute.PHOTO_PDF)
                        }
                    },
                )
            }
            return
        }
        StudioRoute.VIDEO,
        StudioRoute.VIDEO_FRAME -> {
            screens.SaveableStateProvider(route.name) {
                VideoCompressionScreen(
                    ::back,
                    editMode = true,
                    initialUri = videoUri?.let(Uri::parse),
                    initialFrame = route == StudioRoute.VIDEO_FRAME,
                    onAddToAudio = { uri ->
                        val model =
                            ViewModelProvider(context as MainActivity)[
                                AudioToolViewModel::class.java]
                        model.addOutput(uri).also { if (it) navigate(StudioRoute.AUDIO) }
                    },
                    onFrameToImage = { file, originals ->
                        imageModel.addFrame(file, originals).also {
                            if (it) navigate(StudioRoute.IMAGE)
                        }
                    },
                )
            }
            return
        }
        StudioRoute.PRESENTATION_PDF ->
            if (draft != null) {
                screens.SaveableStateProvider(route.name) { PresentationPdfScreen(draft, ::back) }
                return
            }
        StudioRoute.FILE_ACTIONS -> {
            Box(Modifier.fillMaxSize()) {
                FileActionScreen(
                    selectedFile,
                    selecting,
                    {
                        selectionJob?.cancel()
                        back()
                    },
                ) { action ->
                    val uri = pickedUri?.let(Uri::parse) ?: return@FileActionScreen
                    when (action) {
                        FileAction.SLIDESHOW ->
                            importWhenReady {
                                val ready =
                                    slideshowModel.state.first {
                                        !it.busy || it.workStatus?.active == true
                                    }
                                if (!busyJob(ready.busy)) {
                                    slideshowModel.add(listOf(uri))
                                    navigate(StudioRoute.SLIDESHOW)
                                }
                            }
                        FileAction.IMAGE ->
                            if (!busyJob(imageModel.state.value.busy)) {
                                imageModel.load(uri)
                                navigate(StudioRoute.IMAGE)
                            }
                        FileAction.PHOTO_PDF ->
                            importWhenReady {
                                val ready =
                                    photoPdfModel.state.first {
                                        !it.busy || it.workStatus?.active == true
                                    }
                                if (!busyJob(ready.busy)) {
                                    photoPdfModel.add(listOf(uri))
                                    navigate(StudioRoute.PHOTO_PDF)
                                }
                            }
                        FileAction.PRESENTATION_IMAGE -> chooseImageTarget = true
                        FileAction.VIDEO,
                        FileAction.FRAME ->
                            importWhenReady {
                                val model =
                                    ViewModelProvider(context as MainActivity)[
                                        VideoCompressionViewModel::class.java]
                                val ready =
                                    model.state.first { !it.busy || it.workStatus?.active == true }
                                if (!busyJob(ready.busy)) {
                                    videoUri = uri.toString()
                                    navigate(
                                        if (action == FileAction.FRAME) StudioRoute.VIDEO_FRAME
                                        else StudioRoute.VIDEO
                                    )
                                }
                            }
                        FileAction.AUDIO ->
                            importWhenReady {
                                val model =
                                    ViewModelProvider(context as MainActivity)[
                                        AudioToolViewModel::class.java]
                                val ready =
                                    model.state.first { !it.busy || it.workStatus?.active == true }
                                if (!busyJob(ready.busy)) {
                                    model.load(uri)
                                    navigate(StudioRoute.AUDIO)
                                }
                            }
                        FileAction.PDF_READER,
                        FileAction.PDF_IMAGE ->
                            if (!busyJob(pdfReaderModel.state.value.busy)) {
                                pdfReaderModel.load(uri)
                                navigate(
                                    if (action == FileAction.PDF_IMAGE) StudioRoute.PDF_IMAGE
                                    else StudioRoute.PDF_READER
                                )
                            }
                        FileAction.PDF_PAGES ->
                            if (!busyJob(pdfPagesModel.state.value.busy)) {
                                pdfPagesModel.add(listOf(uri))
                                navigate(StudioRoute.PDF_PAGES)
                            }
                        FileAction.DOCUMENTS ->
                            if (!busyJob(documentModel.state.value.busy)) {
                                documentModel.load(uri)
                                navigate(StudioRoute.DOCUMENTS)
                            }
                    }
                }
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                if (chooseImageTarget)
                    PresentationTargetPicker(
                        projects,
                        onChoose = { target ->
                            chooseImageTarget = false
                            val uri = pickedUri?.let(Uri::parse) ?: return@PresentationTargetPicker
                            selectionJob =
                                scope.launch {
                                    selecting = true
                                    try {
                                        if (
                                            editor.addPresentationImage(
                                                uri,
                                                target,
                                                context.getString(R.string.presentation),
                                            )
                                        )
                                            navigate(StudioRoute.PROJECT)
                                        else
                                            snackbar.showSnackbar(
                                                context.getString(R.string.slide_image_error)
                                            )
                                    } finally {
                                        selecting = false
                                    }
                                }
                        },
                        onDismiss = { chooseImageTarget = false },
                    )
            }
            return
        }
        else -> Unit
    }
    Scaffold(
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                windowInsets =
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = {
                    Text(
                        stringResource(
                            if (route == StudioRoute.PROJECT && draft != null) R.string.draft
                            else R.string.app_name
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color =
                            if (route == StudioRoute.PROJECT) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (route == StudioRoute.PROJECT && draft != null)
                        TextButton(
                            onClick = ::saveAndClose,
                            enabled = !saving && !loading && !loadFailed,
                        ) {
                            Text(stringResource(R.string.back))
                        }
                },
                actions = {
                    if (route != StudioRoute.PROJECT || draft == null)
                        TextButton(
                            onClick = { showLanguages = true },
                            modifier = Modifier.padding(end = 12.dp).testTag("choose-language"),
                        ) {
                            Text(stringResource(R.string.language))
                        }
                    else
                        FilledTonalButton(
                            onClick = ::saveAndClose,
                            enabled = !saving && !loading && !loadFailed,
                            modifier = Modifier.padding(end = 8.dp).testTag("save-project"),
                        ) {
                            Text(stringResource(R.string.save))
                        }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.padding(32.dp))
                loadFailed ->
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.load_error))
                        Button(onClick = editor::load) { Text(stringResource(R.string.retry)) }
                    }
                route != StudioRoute.PROJECT || draft == null ->
                    HomeScreen(
                        projects,
                        ::edit,
                        onOpenFile = { openFile.launch(FileActions.inputMimes) },
                        onBatchImages = { navigate(StudioRoute.BATCH_IMAGES) },
                        sessions = resumeEntries.orEmpty(),
                        sessionsLoaded = resumeEntries != null,
                        onDeleteProject = editor::deleteProject,
                        onClearWork = { kind ->
                            editor.clearWork { when (kind) {
                                "video-compression" -> ViewModelProvider(context as MainActivity)
                                    .get(VideoCompressionViewModel::class.java).clearSavedWork()
                                "audio-conversion" -> ViewModelProvider(context as MainActivity)
                                    .get(AudioToolViewModel::class.java).clearSavedWork()
                                "photo-pdf" -> photoPdfModel.clearSavedWork()
                                "batch-images" -> ViewModelProvider(context as MainActivity)
                                    .get(com.example.contentstudio.batch.BatchImageViewModel::class.java).clearSavedWork()
                                SlideshowEngine.FOLDER -> slideshowModel.clearSavedWork()
                                else -> false
                            } }
                        },
                        onResume = { kind ->
                            videoUri = null
                            navigate(
                                when (kind) {
                                    "video-compression" -> StudioRoute.VIDEO
                                    "audio-conversion" -> StudioRoute.AUDIO
                                    "batch-images" -> StudioRoute.BATCH_IMAGES
                                    SlideshowEngine.FOLDER -> StudioRoute.SLIDESHOW
                                    else -> StudioRoute.PHOTO_PDF
                                }
                            )
                        },
                        onAudio = { navigate(StudioRoute.AUDIO) },
                        onReadPdf = { navigate(StudioRoute.PDF_READER) },
                        onPdfPages = { navigate(StudioRoute.PDF_PAGES) },
                        onReadDocuments = { navigate(StudioRoute.DOCUMENTS) },
                        onPhotosPdf = { navigate(StudioRoute.PHOTO_PDF) },
                        onSlideshow = { navigate(StudioRoute.SLIDESHOW) },
                        onCompressImage = { navigate(StudioRoute.IMAGE) },
                        onCompressVideo = {
                            videoUri = null
                            navigate(StudioRoute.VIDEO)
                        },
                        onEditVideo = {
                            videoUri = null
                            navigate(StudioRoute.VIDEO)
                        },
                        onNew = { kind ->
                            edit(
                                StudioProject(
                                    id = UUID.randomUUID().toString(),
                                    kind = kind,
                                    title =
                                        context.getString(
                                            if (kind == ProjectKind.PRESENTATION)
                                                R.string.presentation
                                            else R.string.video
                                        ),
                                    updatedAt = System.currentTimeMillis(),
                                    slides =
                                        if (kind == ProjectKind.PRESENTATION) listOf(Slide())
                                        else emptyList(),
                                )
                            )
                        },
                    )
                else ->
                    ProjectDraft(
                        draft,
                        !saving,
                        editorState.saveStatus,
                        editor::flush,
                        editor::edit,
                        onPdf = {
                            editor.flush()
                            navigate(StudioRoute.PRESENTATION_PDF)
                        },
                        onPresent = {
                            editor.flush()
                            navigate(StudioRoute.PRESENTATION_PLAYER)
                        },
                        onSlideImages = {
                            editor.flush()
                            navigate(StudioRoute.PRESENTATION_IMAGES)
                        },
                        onPptx = {
                            editor.flush()
                            navigate(StudioRoute.PRESENTATION_PPTX)
                        },
                        onVideo = {
                            editor.flush()
                            slideshowRequest++
                            navigate(StudioRoute.SLIDESHOW_PRESENTATION)
                        },
                        onMedia = editor::setMedia,
                        onEditVideo = {
                            videoUri = it
                            navigate(StudioRoute.VIDEO)
                        },
                        onSlideImage = { id, uri ->
                            editor.importSlideImage(draft.id, id, Uri.parse(uri)) { issue ->
                                scope.launch {
                                    snackbar.showSnackbar(
                                        context.getString(
                                            issue?.let(::importIssueText)
                                                ?: R.string.slide_image_error
                                        )
                                    )
                                }
                            }
                        },
                        onError = { scope.launch { snackbar.showSnackbar(context.getString(it)) } },
                    )
            }
            if (editorState.importingImage)
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(20.dp),
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 8.dp,
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text(stringResource(R.string.processing))
                    }
                }
        }
    }
    if (showLanguages) LanguageDialog { showLanguages = false }
}
