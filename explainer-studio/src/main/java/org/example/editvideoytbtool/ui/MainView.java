package org.example.editvideoytbtool.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.editvideoytbtool.audio.AudioPlaybackService;
import org.example.editvideoytbtool.audio.VieNeuTtsClient;
import org.example.editvideoytbtool.audio.WavFileUtil;
import org.example.editvideoytbtool.config.AppSettings;
import org.example.editvideoytbtool.config.SettingsService;
import org.example.editvideoytbtool.export.FfmpegConfig;
import org.example.editvideoytbtool.export.FfmpegProbeResult;
import org.example.editvideoytbtool.export.VideoExporter;
import org.example.editvideoytbtool.library.CharacterAsset;
import org.example.editvideoytbtool.library.CharacterLibraryService;
import org.example.editvideoytbtool.llm.GeneratedScene;
import org.example.editvideoytbtool.llm.OpenAiScriptClient;
import org.example.editvideoytbtool.llm.ScriptGenerationResult;
import org.example.editvideoytbtool.model.LayoutPreset;
import org.example.editvideoytbtool.model.ProjectDocument;
import org.example.editvideoytbtool.model.SceneData;
import org.example.editvideoytbtool.model.VisualLayer;
import org.example.editvideoytbtool.model.VisualLayerType;
import org.example.editvideoytbtool.persistence.ProjectRepository;
import org.example.editvideoytbtool.service.LayoutPresetService;
import org.example.editvideoytbtool.service.ScriptSceneService;
import org.example.editvideoytbtool.service.TimelineResult;
import org.example.editvideoytbtool.service.TimelineService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Main desktop editor. The UI mutates the scene/layer model directly and persists it as JSON. */
public final class MainView extends BorderPane implements AutoCloseable {
    private final Stage stage;
    private final ProjectRepository projectRepository = new ProjectRepository();
    private final TimelineService timelineService = new TimelineService();
    private final ScriptSceneService scriptSceneService = new ScriptSceneService(timelineService);
    private final LayoutPresetService layoutService = new LayoutPresetService();
    private final SettingsService settingsService = new SettingsService();
    private final AppSettings settings;
    private final CharacterLibraryService characterLibrary;
    private final AudioPlaybackService audioPlayer = new AudioPlaybackService();
    private final OpenAiScriptClient llmClient = new OpenAiScriptClient();
    private final Path sessionDirectory;

    private ProjectDocument project;
    private TimelineResult timeline;
    private SceneData selectedScene;
    private VisualLayer selectedLayer;
    private boolean dirty;
    private boolean updatingUi;
    private int busyTasks;

    private final ListView<SceneData> sceneList = new ListView<>();
    private final ListView<CharacterAsset> characterList = new ListView<>();
    private final ListView<VisualLayer> layerList = new ListView<>();
    private final PreviewPane preview = new PreviewPane();
    private final TimelineView timelineView = new TimelineView();
    private final TextArea narrationArea = new TextArea();
    private final ComboBox<LayoutPreset> layoutCombo = new ComboBox<>();
    private final TextField manualStartField = new TextField();
    private final TextField pauseField = new TextField();
    private final Label audioStatus = new Label("Chưa có WAV");
    private final Slider playhead = new Slider();
    private final Label playheadLabel = new Label("0.000 s");
    private final TextArea layerContent = new TextArea();
    private final TextField xField = new TextField();
    private final TextField yField = new TextField();
    private final TextField widthField = new TextField();
    private final TextField heightField = new TextField();
    private final TextField rotationField = new TextField();
    private final TextField opacityField = new TextField();
    private final TextField layerStartField = new TextField();
    private final TextField layerEndField = new TextField();
    private final Label statusLabel = new Label("Sẵn sàng");
    private final ProgressBar progress = new ProgressBar();
    private final ToolBar toolbar = new ToolBar();
    private AnimationTimer playbackTimer;
    private long playbackStartedNanos;

    public MainView(Stage stage) throws IOException {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.settings = settingsService.load();
        this.characterLibrary = new CharacterLibraryService(SettingsService.resolveAppData());
        this.sessionDirectory = Files.createTempDirectory("explainer-video-session-");
        buildUi();
        installHandlers();
        newProject(false);
        refreshCharacterLibrary();
    }

    private void buildUi() {
        getStyleClass().add("app-root");
        setTop(buildToolbar());
        setLeft(buildLeftPanel());
        setCenter(buildCenter());
        setRight(buildInspector());
        setBottom(buildStatusBar());
        preview.setAssetResolver(this::resolveStoredPathQuietly);
        preview.setOnSelected(layer -> {
            selectedLayer = layer;
            layerList.getSelectionModel().select(layer);
            refreshLayerInspector();
        });
        preview.setOnChanged(() -> {
            markDirty();
            refreshLayerInspector();
        });
    }

    private Node buildToolbar() {
        Button create = toolbarButton("Mới", event -> newProject(true));
        Button open = toolbarButton("Mở", event -> openProject());
        Button save = toolbarButton("Lưu", event -> saveProject(false));
        Button paste = toolbarButton("Dán kịch bản", event -> pasteScript());
        Button ai = toolbarButton("AI viết kịch bản", event -> generateScriptWithAi());
        Button tts = toolbarButton("Tạo giọng cảnh", event -> generateSelectedAudio());
        Button ttsAll = toolbarButton("Tạo WAV cần thiết", event -> generateAllAudio());
        Button play = toolbarButton("Phát thử", event -> playSelectedAudio());
        Button export = toolbarButton("Xuất MP4", event -> exportVideo());
        export.getStyleClass().add("accent");
        Button config = toolbarButton("Cấu hình", event -> editSettings());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getItems().addAll(create, open, save, new Separator(Orientation.VERTICAL), paste, ai,
                new Separator(Orientation.VERTICAL), tts, ttsAll, play,
                new Separator(Orientation.VERTICAL), export, spacer, config);
        return toolbar;
    }

    private Node buildLeftPanel() {
        VBox root = new VBox(8);
        root.getStyleClass().add("panel");
        root.setPadding(new Insets(10));
        root.setPrefWidth(320);
        Label sceneTitle = title("Cảnh");
        sceneList.getStyleClass().add("scene-list");
        sceneList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(SceneData scene, boolean empty) {
                super.updateItem(scene, empty);
                if (empty || scene == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String audio = scene.getAudioPath() == null ? "chưa WAV" : scene.isAudioStale() ? "WAV cũ" : format(scene.getAudioDurationSeconds()) + "s";
                    setText("Cảnh " + (getIndex() + 1) + "  •  " + audio + "\n" + abbreviate(scene.getNarration(), 56));
                }
            }
        });
        HBox sceneButtons = new HBox(5,
                smallButton("+", event -> addScene()),
                smallButton("Tách", event -> splitScene()),
                smallButton("Gộp", event -> mergeScene()),
                smallButton("↑", event -> moveScene(-1)),
                smallButton("↓", event -> moveScene(1)),
                smallButton("Xóa", event -> removeScene()));
        VBox.setVgrow(sceneList, Priority.ALWAYS);

        Separator separator = new Separator();
        Label libraryTitle = title("Thư viện PNG nhân vật");
        characterList.setPrefHeight(230);
        characterList.setCellFactory(ignored -> new CharacterCell());
        HBox libraryButtons = new HBox(5,
                smallButton("Nhập PNG", event -> importCharacters()),
                smallButton("Dùng cho cảnh", event -> assignCharacter()),
                smallButton("Tên/nhãn", event -> editCharacter()));
        root.getChildren().addAll(sceneTitle, sceneList, sceneButtons, separator, libraryTitle, characterList, libraryButtons);
        return root;
    }

    private Node buildCenter() {
        BorderPane center = new BorderPane();
        center.setCenter(preview);
        HBox playheadBar = new HBox(8, new Label("Vị trí xem"), playhead, playheadLabel,
                smallButton("▶", event -> playSelectedAudio()), smallButton("■", event -> stopPlayback()));
        playheadBar.setAlignment(Pos.CENTER_LEFT);
        playheadBar.setPadding(new Insets(6, 10, 6, 10));
        HBox.setHgrow(playhead, Priority.ALWAYS);
        VBox bottom = new VBox(playheadBar, timelineView);
        VBox.setVgrow(timelineView, Priority.ALWAYS);
        bottom.setPrefHeight(270);
        center.setBottom(bottom);
        return center;
    }

    private Node buildInspector() {
        TabPane tabs = new TabPane();
        tabs.setPrefWidth(350);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(new Tab("Cảnh", buildSceneInspector()), new Tab("Lớp", buildLayerInspector()));
        return tabs;
    }

    private Node buildSceneInspector() {
        VBox box = inspectorBox();
        narrationArea.setWrapText(true);
        narrationArea.setPrefRowCount(9);
        layoutCombo.setItems(FXCollections.observableArrayList(
                LayoutPreset.CHARACTER_RIGHT_OVERLAP_IMAGE,
                LayoutPreset.CHARACTER_RIGHT_IMAGE_LEFT,
                LayoutPreset.CHARACTER_CENTER_TEXT_AROUND,
                LayoutPreset.CUSTOM));
        layoutCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane timing = grid();
        addGridRow(timing, 0, "Bắt đầu (s)", manualStartField);
        manualStartField.setPromptText("Tự động");
        addGridRow(timing, 1, "Nghỉ sau cảnh (s)", pauseField);
        HBox audioButtons = new HBox(6,
                smallButton("Tạo WAV", event -> generateSelectedAudio()),
                smallButton("Phát", event -> playSelectedAudio()),
                smallButton("Kiểm tra server", event -> checkTtsServer()));
        audioStatus.setWrapText(true);
        box.getChildren().addAll(title("Lời thoại"), narrationArea, title("Bố cục ban đầu"), layoutCombo,
                title("Mốc cảnh"), timing, title("Âm thanh VieNeu‑TTS"), audioStatus, audioButtons);
        return new ScrollPane(box) {{ setFitToWidth(true); }};
    }

    private Node buildLayerInspector() {
        VBox box = inspectorBox();
        layerList.setPrefHeight(170);
        layerList.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(VisualLayer layer, boolean empty) {
                super.updateItem(layer, empty);
                setText(empty || layer == null ? null : layer.getType() + "  •  " + (layer.getName().isBlank() ? layer.getId() : layer.getName()));
            }
        });
        HBox addButtons = new HBox(5,
                smallButton("+ Chữ", event -> addTextLayer()),
                smallButton("+ Ảnh", event -> addImageLayer()),
                smallButton("Thay ảnh", event -> replaceLayerImage()),
                smallButton("Xóa", event -> deleteLayer()));
        layerContent.setWrapText(true);
        layerContent.setPrefRowCount(3);
        GridPane transform = grid();
        addGridRow(transform, 0, "X", xField); addGridRow(transform, 1, "Y", yField);
        addGridRow(transform, 2, "Rộng", widthField); addGridRow(transform, 3, "Cao", heightField);
        addGridRow(transform, 4, "Xoay °", rotationField); addGridRow(transform, 5, "Opacity", opacityField);
        addGridRow(transform, 6, "Hiện từ (s)", layerStartField); addGridRow(transform, 7, "Ẩn tại (s)", layerEndField);
        layerEndField.setPromptText("Hết cảnh");
        HBox order = new HBox(6,
                smallButton("Đưa lên", event -> changeLayerOrder(1)),
                smallButton("Đưa xuống", event -> changeLayerOrder(-1)),
                smallButton("Áp bố cục", event -> applyCurrentLayout()));
        box.getChildren().addAll(title("Danh sách lớp"), layerList, addButtons, title("Nội dung / mô tả"), layerContent,
                title("Vị trí và thời gian tương đối"), transform, order);
        return new ScrollPane(box) {{ setFitToWidth(true); }};
    }

    private Node buildStatusBar() {
        progress.setVisible(false);
        progress.setPrefWidth(160);
        HBox bar = new HBox(10, progress, statusLabel);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void installHandlers() {
        sceneList.getSelectionModel().selectedItemProperty().addListener((obs, old, scene) -> selectScene(scene));
        characterList.setOnMouseClicked(event -> { if (event.getClickCount() == 2) assignCharacter(); });
        layerList.getSelectionModel().selectedItemProperty().addListener((obs, old, layer) -> {
            if (updatingUi) return;
            selectedLayer = layer;
            refreshLayerInspector();
            preview.selectLayer(layer);
        });
        narrationArea.textProperty().addListener((obs, old, text) -> {
            if (!updatingUi && selectedScene != null) {
                scriptSceneService.updateNarration(project, selectedScene.getId(), text);
                markDirty();
                refreshTimelineAndPreview(false);
            }
        });
        layoutCombo.setOnAction(event -> {
            if (!updatingUi && selectedScene != null && layoutCombo.getValue() != null) {
                layoutService.apply(selectedScene, layoutCombo.getValue());
                markDirty();
                refreshLayersAndPreview();
            }
        });
        commitOnActionAndFocus(manualStartField, this::commitSceneTiming);
        commitOnActionAndFocus(pauseField, this::commitSceneTiming);
        layerContent.textProperty().addListener((obs, old, value) -> {
            if (updatingUi || selectedLayer == null) return;
            if (selectedLayer.getType() == VisualLayerType.TEXT) selectedLayer.setText(value);
            else if (selectedLayer.getType() == VisualLayerType.PLACEHOLDER) selectedLayer.setDescription(value);
            selectedLayer.setName(value.isBlank() ? selectedLayer.getType().name() : abbreviate(value, 28));
            markDirty();
            refreshLayersAndPreview();
        });
        bindLayerNumber(xField, () -> selectedLayer == null ? 0d : selectedLayer.getX(), value -> selectedLayer.setX(value));
        bindLayerNumber(yField, () -> selectedLayer == null ? 0d : selectedLayer.getY(), value -> selectedLayer.setY(value));
        bindLayerNumber(widthField, () -> selectedLayer == null ? 0d : selectedLayer.getWidth(), value -> selectedLayer.setWidth(Math.max(1, value)));
        bindLayerNumber(heightField, () -> selectedLayer == null ? 0d : selectedLayer.getHeight(), value -> selectedLayer.setHeight(Math.max(1, value)));
        bindLayerNumber(rotationField, () -> selectedLayer == null ? 0d : selectedLayer.getRotationDegrees(), value -> selectedLayer.setRotationDegrees(value));
        bindLayerNumber(opacityField, () -> selectedLayer == null ? 1d : selectedLayer.getOpacity(), value -> selectedLayer.setOpacity(Math.max(0, Math.min(1, value))));
        bindLayerNumber(layerStartField, () -> selectedLayer == null ? 0d : selectedLayer.getStartOffsetSeconds(),
                value -> selectedLayer.setVisibilityWindow(Math.max(0, value), selectedLayer.getEndOffsetSeconds()));
        commitOnActionAndFocus(layerEndField, this::commitLayerEnd);
        playhead.valueProperty().addListener((obs, old, value) -> {
            playheadLabel.setText(String.format(Locale.ROOT, "%.3f s", value.doubleValue()));
            refreshPreviewOnly();
        });
        timelineView.setOnLayerChanged(layer -> {
            selectedLayer = layer;
            markDirty();
            refreshLayerInspector();
            refreshPreviewOnly();
        });
        timelineView.setOnSceneStartChanged((id, seconds) -> {
            timelineService.setManualStart(project, id, seconds);
            markDirty();
            refreshAllKeepingSelection(id);
        });
    }

    private void newProject(boolean ask) {
        if (ask && !confirmDiscardIfDirty()) return;
        stopPlayback();
        project = new ProjectDocument();
        SceneData first = new SceneData("");
        first.setLayoutPreset(LayoutPreset.CHARACTER_RIGHT_OVERLAP_IMAGE);
        layoutService.addDefaultIllustrationPlaceholder(first, "Chọn ảnh minh họa từ máy");
        project.getScenes().add(first);
        timeline = timelineService.recalculate(project);
        dirty = false;
        refreshAllKeepingSelection(first.getId());
        updateTitle();
        status("Dự án mới. Dán kịch bản hoặc nhập lời thoại cho cảnh đầu tiên.");
    }

    private void pasteScript() {
        StudioDialogs.promptScript(stage).ifPresent(script -> {
            if (!project.getScenes().isEmpty() && dirty
                    && !StudioDialogs.confirm(stage, "Thay kịch bản", "Thay toàn bộ danh sách cảnh hiện tại?")) return;
            scriptSceneService.replaceProjectScenes(project, script);
            for (SceneData scene : project.getScenes()) layoutService.addDefaultIllustrationPlaceholder(scene, "Chọn ảnh minh họa từ máy");
            markDirty();
            refreshAllKeepingSelection(project.getScenes().isEmpty() ? null : project.getScenes().getFirst().getId());
            status("Đã chia kịch bản theo dấu phân cảnh/quy tắc câu. Bạn có thể tách, gộp và sửa tiếp.");
        });
    }

    private void generateScriptWithAi() {
        StudioDialogs.promptAi(stage, settings).ifPresent(input -> {
            saveSettingsQuietly();
            background("Đang yêu cầu mô hình viết kịch bản…",
                    () -> llmClient.generate(settings.getLlmBaseUrl(), settings.getLlmModel(), settings.getLlmApiKey(), input.request()),
                    result -> applyGeneratedScript(result), "Tạo kịch bản thất bại");
        });
    }

    private void applyGeneratedScript(ScriptGenerationResult result) {
        List<SceneData> scenes = new ArrayList<>();
        for (GeneratedScene generated : result.scenes()) {
            SceneData scene = new SceneData(generated.narration());
            LayoutPreset preset = switch (generated.layout()) {
                case "RIGHT_OVERLAY" -> LayoutPreset.CHARACTER_RIGHT_OVERLAP_IMAGE;
                case "CENTER" -> LayoutPreset.CHARACTER_CENTER_TEXT_AROUND;
                default -> LayoutPreset.CHARACTER_RIGHT_IMAGE_LEFT;
            };
            scene.setLayoutPreset(preset);
            for (String text : generated.onScreenTexts()) scene.getLayers().add(VisualLayer.text(text));
            layoutService.addDefaultIllustrationPlaceholder(scene, generated.illustrationPrompt());
            layoutService.apply(scene, preset);
            scenes.add(scene);
        }
        project.setScenes(scenes);
        timelineService.recalculate(project);
        markDirty();
        refreshAllKeepingSelection(scenes.getFirst().getId());
        status("Đã tạo và kiểm tra JSON gồm " + scenes.size() + " cảnh. Ảnh AI gợi ý đang là lớp chỗ trống.");
    }

    private void addScene() {
        int index = selectedScene == null ? project.getScenes().size() : project.indexOfScene(selectedScene.getId()) + 1;
        SceneData added = scriptSceneService.addScene(project, index, "");
        layoutService.addDefaultIllustrationPlaceholder(added, "Chọn ảnh minh họa từ máy");
        markDirty();
        refreshAllKeepingSelection(added.getId());
        Platform.runLater(narrationArea::requestFocus);
    }

    private void splitScene() {
        if (selectedScene == null) return;
        try {
            SceneData second = scriptSceneService.splitScene(project, selectedScene.getId(), narrationArea.getCaretPosition());
            markDirty();
            refreshAllKeepingSelection(second.getId());
        } catch (IllegalArgumentException error) {
            StudioDialogs.error(stage, "Không thể tách cảnh", "Đặt con trỏ giữa hai phần lời thoại rồi thử lại.");
        }
    }

    private void mergeScene() {
        if (selectedScene == null) return;
        try {
            String id = selectedScene.getId();
            scriptSceneService.mergeWithNext(project, id);
            markDirty();
            refreshAllKeepingSelection(id);
        } catch (IllegalArgumentException error) {
            StudioDialogs.error(stage, "Không thể gộp cảnh", "Cảnh cuối không có cảnh kế tiếp để gộp.");
        }
    }

    private void moveScene(int delta) {
        if (selectedScene == null) return;
        int from = project.indexOfScene(selectedScene.getId());
        int to = from + delta;
        if (to < 0 || to >= project.getScenes().size()) return;
        String id = selectedScene.getId();
        scriptSceneService.moveScene(project, from, to);
        markDirty();
        refreshAllKeepingSelection(id);
    }

    private void removeScene() {
        if (selectedScene == null) return;
        if (!StudioDialogs.confirm(stage, "Xóa cảnh", "Xóa cảnh đang chọn?")) return;
        int oldIndex = project.indexOfScene(selectedScene.getId());
        scriptSceneService.removeScene(project, selectedScene.getId());
        if (project.getScenes().isEmpty()) scriptSceneService.addScene(project, 0, "");
        int next = Math.min(oldIndex, project.getScenes().size() - 1);
        markDirty();
        refreshAllKeepingSelection(project.getScenes().get(next).getId());
    }

    private void importCharacters() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Nhập PNG nhân vật nền trong suốt");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;
        int imported = 0;
        for (File file : files) {
            String name = stripExtension(file.getName());
            Optional<StudioDialogs.CharacterMetadata> metadata = StudioDialogs.characterMetadata(stage, name, List.of());
            if (metadata.isEmpty()) continue;
            try {
                characterLibrary.importPng(file.toPath(), metadata.get().name(), metadata.get().labels());
                imported++;
            } catch (IOException error) {
                StudioDialogs.error(stage, "Không nhập được PNG", error.getMessage());
            }
        }
        refreshCharacterLibrary();
        status("Đã lưu " + imported + " PNG vào thư viện dùng chung cho các dự án.");
    }

    private void assignCharacter() {
        CharacterAsset asset = characterList.getSelectionModel().getSelectedItem();
        if (asset == null || selectedScene == null) return;
        try {
            Path image = characterLibrary.resolveImage(asset);
            VisualLayer layer = selectedScene.getCharacterLayer().orElse(null);
            if (layer == null) {
                layer = VisualLayer.character(image.toString());
                layer.setName(asset.getDisplayName());
                selectedScene.getLayers().add(layer);
                layoutService.apply(selectedScene, selectedScene.getLayoutPreset());
            } else {
                layer.setAssetPath(image.toString());
                layer.setName(asset.getDisplayName());
            }
            selectedLayer = layer;
            markDirty();
            refreshLayersAndPreview();
            layerList.getSelectionModel().select(layer);
        } catch (IOException error) {
            StudioDialogs.error(stage, "Không dùng được nhân vật", error.getMessage());
        }
    }

    private void editCharacter() {
        CharacterAsset asset = characterList.getSelectionModel().getSelectedItem();
        if (asset == null) return;
        StudioDialogs.characterMetadata(stage, asset.getDisplayName(), asset.getLabels()).ifPresent(metadata -> {
            try {
                characterLibrary.update(asset.getId(), metadata.name(), metadata.labels());
                refreshCharacterLibrary();
            } catch (IOException error) {
                StudioDialogs.error(stage, "Không cập nhật được thư viện", error.getMessage());
            }
        });
    }

    private void addTextLayer() {
        if (selectedScene == null) return;
        TextInputDialog dialog = new TextInputDialog("Ý chính");
        dialog.initOwner(stage);
        dialog.setTitle("Thêm chữ");
        dialog.setHeaderText("Nhập chữ hiển thị trên khung hình");
        dialog.showAndWait().filter(text -> !text.isBlank()).ifPresent(text -> {
            VisualLayer layer = VisualLayer.text(text);
            layer.setX(100); layer.setY(100); layer.setZIndex(nextZ());
            selectedScene.getLayers().add(layer);
            selectedLayer = layer;
            markDirty();
            refreshLayersAndPreview();
            layerList.getSelectionModel().select(layer);
        });
    }

    private void addImageLayer() {
        if (selectedScene == null) return;
        File file = chooseImage("Chọn ảnh minh họa");
        if (file == null) return;
        VisualLayer layer = VisualLayer.image(file.getAbsolutePath());
        layer.setX(100); layer.setY(180); layer.setWidth(960); layer.setHeight(720); layer.setZIndex(nextZ());
        selectedScene.getLayers().add(layer);
        selectedLayer = layer;
        markDirty();
        refreshLayersAndPreview();
        layerList.getSelectionModel().select(layer);
    }

    private void replaceLayerImage() {
        if (selectedLayer == null || (selectedLayer.getType() != VisualLayerType.IMAGE
                && selectedLayer.getType() != VisualLayerType.PLACEHOLDER
                && selectedLayer.getType() != VisualLayerType.CHARACTER)) return;
        File file = chooseImage("Chọn file ảnh thay thế");
        if (file == null) return;
        if (selectedLayer.getType() == VisualLayerType.PLACEHOLDER) {
            selectedLayer.setType(VisualLayerType.IMAGE);
            selectedLayer.setName("Ảnh minh họa");
        }
        selectedLayer.setAssetPath(file.getAbsolutePath());
        markDirty();
        refreshLayersAndPreview();
    }

    private void deleteLayer() {
        if (selectedScene == null || selectedLayer == null) return;
        selectedScene.getLayers().remove(selectedLayer);
        selectedLayer = null;
        markDirty();
        refreshLayersAndPreview();
    }

    private void changeLayerOrder(int delta) {
        if (selectedScene == null || selectedLayer == null) return;
        selectedLayer.setZIndex(selectedLayer.getZIndex() + delta * 15);
        layoutService.normalizeZ(selectedScene);
        markDirty();
        refreshLayersAndPreview();
        layerList.getSelectionModel().select(selectedLayer);
    }

    private void applyCurrentLayout() {
        if (selectedScene == null) return;
        layoutService.apply(selectedScene, layoutCombo.getValue() == null ? selectedScene.getLayoutPreset() : layoutCombo.getValue());
        markDirty();
        refreshLayersAndPreview();
    }

    private void checkTtsServer() {
        background("Đang kiểm tra VieNeu‑TTS v3 Turbo…",
                () -> new VieNeuTtsClient(settings.getTtsBaseUrl(), settings.getTtsApiKey()).checkHealth(),
                health -> status("VieNeu‑TTS sẵn sàng: " + abbreviate(health, 180)), "VieNeu‑TTS chưa sẵn sàng");
    }

    private void generateSelectedAudio() {
        if (selectedScene == null || selectedScene.getNarration().isBlank()) {
            StudioDialogs.error(stage, "Chưa có lời thoại", "Hãy nhập lời thoại cho cảnh trước khi tạo giọng đọc.");
            return;
        }
        String id = selectedScene.getId();
        background("Đang tạo WAV cho cảnh " + (project.indexOfScene(id) + 1) + "…",
                () -> synthesizeScenes(List.of(id)), updates -> applyAudioUpdates(updates, id), "Tạo giọng đọc thất bại");
    }

    private void generateAllAudio() {
        List<String> ids = project.getScenes().stream()
                .filter(scene -> !scene.getNarration().isBlank())
                .filter(scene -> scene.getAudioPath() == null || scene.isAudioStale())
                .map(SceneData::getId).toList();
        if (ids.isEmpty()) {
            status("Tất cả cảnh có lời thoại đã có WAV mới nhất.");
            return;
        }
        background("Đang tạo " + ids.size() + " file WAV…",
                () -> synthesizeScenes(ids), updates -> applyAudioUpdates(updates, selectedScene == null ? null : selectedScene.getId()),
                "Tạo giọng đọc thất bại");
    }

    private List<AudioUpdate> synthesizeScenes(List<String> ids) throws Exception {
        VieNeuTtsClient client = new VieNeuTtsClient(settings.getTtsBaseUrl(), settings.getTtsApiKey());
        client.checkHealth();
        List<AudioUpdate> updates = new ArrayList<>();
        for (String id : ids) {
            SceneData scene = project.findScene(id).orElseThrow();
            AudioTarget target = audioTarget(scene);
            client.synthesizeToWav(scene.getNarration(), settings.getTtsVoice(), target.file());
            updates.add(new AudioUpdate(id, target.storedPath(), WavFileUtil.readDurationSeconds(target.file())));
        }
        return updates;
    }

    private void applyAudioUpdates(List<AudioUpdate> updates, String selectId) {
        for (AudioUpdate update : updates) {
            project.findScene(update.sceneId()).ifPresent(scene -> scene.markAudioGenerated(update.storedPath(), update.durationSeconds()));
        }
        timelineService.recalculate(project);
        markDirty();
        refreshAllKeepingSelection(selectId);
        status("Đã tạo " + updates.size() + " file WAV PCM chuẩn 48 kHz và tính lại timeline.");
    }

    private AudioTarget audioTarget(SceneData scene) throws IOException {
        if (project.getProjectDirectory() != null) {
            Path directory = project.getProjectDirectory().resolve("assets").resolve("audio");
            Files.createDirectories(directory);
            Path target = directory.resolve("scene-" + scene.getId() + ".wav");
            String stored = project.getProjectDirectory().relativize(target).toString().replace('\\', '/');
            return new AudioTarget(target, stored);
        }
        Path directory = sessionDirectory.resolve("audio");
        Files.createDirectories(directory);
        Path target = directory.resolve("scene-" + scene.getId() + ".wav");
        return new AudioTarget(target, target.toString());
    }

    private void playSelectedAudio() {
        if (selectedScene == null || selectedScene.getAudioPath() == null) {
            StudioDialogs.error(stage, "Chưa có WAV", "Hãy tạo giọng đọc cho cảnh này trước.");
            return;
        }
        try {
            Path wav = resolveStoredPath(selectedScene.getAudioPath());
            audioPlayer.play(wav);
            playbackStartedNanos = System.nanoTime();
            playhead.setValue(0);
            if (playbackTimer != null) playbackTimer.stop();
            playbackTimer = new AnimationTimer() {
                @Override public void handle(long now) {
                    double elapsed = (now - playbackStartedNanos) / 1_000_000_000d;
                    if (elapsed >= playhead.getMax() || !audioPlayer.isPlaying()) stopPlayback();
                    else playhead.setValue(elapsed);
                }
            };
            playbackTimer.start();
            status("Đang phát WAV cảnh " + (project.indexOfScene(selectedScene.getId()) + 1) + ".");
        } catch (Exception error) {
            StudioDialogs.error(stage, "Không phát được WAV", error.getMessage());
        }
    }

    private void stopPlayback() {
        audioPlayer.stop();
        if (playbackTimer != null) playbackTimer.stop();
        playbackTimer = null;
    }

    private void saveProject(boolean saveAs) {
        Path target = project.getProjectFile();
        if (saveAs || target == null) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Lưu dự án Explainer Video Studio");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Explainer project JSON", "*.evs.json", "*.json"));
            chooser.setInitialFileName(safeProjectName(project.getName()) + ".evs.json");
            File file = chooser.showSaveDialog(stage);
            if (file == null) return;
            target = file.toPath();
        }
        try {
            timelineService.recalculate(project);
            projectRepository.save(project, target);
            dirty = false;
            updateTitle();
            refreshAllKeepingSelection(selectedScene == null ? null : selectedScene.getId());
            status("Đã lưu dự án và sao chép tài nguyên vào " + target.getParent() + ".");
        } catch (Exception error) {
            StudioDialogs.error(stage, "Không lưu được dự án", error.getMessage());
        }
    }

    private void openProject() {
        if (!confirmDiscardIfDirty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Mở dự án Explainer Video Studio");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Explainer project JSON", "*.evs.json", "*.json"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        try {
            stopPlayback();
            project = projectRepository.open(file.toPath());
            timeline = timelineService.recalculate(project);
            dirty = false;
            refreshAllKeepingSelection(project.getScenes().isEmpty() ? null : project.getScenes().getFirst().getId());
            updateTitle();
            status("Đã mở " + file.getName() + ".");
        } catch (Exception error) {
            StudioDialogs.error(stage, "Không mở được dự án", error.getMessage());
        }
    }

    private void exportVideo() {
        if (project.getProjectFile() == null || dirty) {
            saveProject(false);
            if (project.getProjectFile() == null || dirty) return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Xuất video MP4");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP4 video", "*.mp4"));
        chooser.setInitialFileName(safeProjectName(project.getName()) + ".mp4");
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        VideoExporter exporter = new VideoExporter(new FfmpegConfig(settings.getFfmpegPath(), Duration.ofSeconds(8), Duration.ofHours(2)));
        background("Đang kiểm tra FFmpeg…", () -> {
            FfmpegProbeResult probe = exporter.probeFfmpeg();
            if (!probe.available()) throw new IOException(probe.message());
            return exporter.export(project, file.toPath(), (value, message) -> Platform.runLater(() -> {
                progress.setProgress(value);
                statusLabel.setText(message);
            }));
        }, result -> {
            progress.setProgress(1);
            status("Đã xuất MP4 có âm thanh: " + result.outputFile() + " (" + format(result.videoDurationSeconds()) + "s)");
            StudioDialogs.info(stage, "Xuất MP4 hoàn tất", "File: " + result.outputFile() + "\nThời lượng: " + format(result.videoDurationSeconds()) + " giây");
        }, "Xuất MP4 thất bại");
    }

    private void editSettings() {
        if (StudioDialogs.editSettings(stage, settings)) {
            saveSettingsQuietly();
            VideoExporter exporter = new VideoExporter(new FfmpegConfig(settings.getFfmpegPath(), Duration.ofSeconds(8), Duration.ofHours(2)));
            background("Đang kiểm tra FFmpeg…", exporter::probeFfmpeg,
                    result -> status(result.message()), "Không kiểm tra được FFmpeg");
        }
    }

    private void selectScene(SceneData scene) {
        if (updatingUi) return;
        stopPlayback();
        selectedScene = scene;
        selectedLayer = null;
        updatingUi = true;
        try {
            if (scene == null) {
                narrationArea.clear();
                layerList.getItems().clear();
                return;
            }
            narrationArea.setText(scene.getNarration());
            layoutCombo.setValue(scene.getLayoutPreset());
            manualStartField.setText(scene.getManualStartSeconds() == null ? "" : format(scene.getManualStartSeconds()));
            pauseField.setText(format(scene.getPauseAfterSeconds()));
            double duration = Math.max(0.1, scene.getTimelineEndSeconds() - scene.getTimelineStartSeconds());
            playhead.setMin(0); playhead.setMax(duration); playhead.setValue(0);
            layerList.setItems(FXCollections.observableArrayList(sortedLayers(scene)));
            audioStatus.setText(audioDescription(scene));
        } finally {
            updatingUi = false;
        }
        refreshLayerInspector();
        refreshTimelineAndPreview(false);
    }

    private void refreshAllKeepingSelection(String sceneId) {
        timeline = timelineService.recalculate(project);
        updatingUi = true;
        try {
            sceneList.setItems(FXCollections.observableArrayList(project.getScenes()));
            SceneData scene = sceneId == null ? null : project.findScene(sceneId).orElse(null);
            if (scene == null && !project.getScenes().isEmpty()) scene = project.getScenes().getFirst();
            sceneList.getSelectionModel().select(scene);
            selectedScene = scene;
        } finally {
            updatingUi = false;
        }
        selectSceneDirect(selectedScene);
        updateTitle();
    }

    private void selectSceneDirect(SceneData scene) {
        selectedScene = null;
        updatingUi = false;
        selectScene(scene);
    }

    private void refreshLayersAndPreview() {
        if (selectedScene == null) return;
        VisualLayer keep = selectedLayer;
        updatingUi = true;
        layerList.setItems(FXCollections.observableArrayList(sortedLayers(selectedScene)));
        layerList.getSelectionModel().select(keep);
        updatingUi = false;
        selectedLayer = keep;
        refreshLayerInspector();
        refreshTimelineAndPreview(false);
        sceneList.refresh();
    }

    private void refreshTimelineAndPreview(boolean resetPlayhead) {
        timeline = timelineService.recalculate(project);
        if (selectedScene != null && resetPlayhead) playhead.setValue(0);
        List<TimelineView.SceneItem> items = new ArrayList<>();
        for (SceneData scene : project.getScenes()) {
            Path wav = scene.getAudioPath() == null ? null : resolveStoredPathQuietly(scene.getAudioPath());
            items.add(new TimelineView.SceneItem(scene.getId(), abbreviate(scene.getNarration(), 24),
                    scene.getTimelineStartSeconds(), scene.getTimelineEndSeconds() - scene.getTimelineStartSeconds(), wav, scene.getLayers()));
        }
        timelineView.setData(items, selectedScene == null ? null : selectedScene.getId());
        if (selectedScene != null) {
            playhead.setMax(Math.max(0.1, selectedScene.getTimelineEndSeconds() - selectedScene.getTimelineStartSeconds()));
            audioStatus.setText(audioDescription(selectedScene));
        }
        refreshPreviewOnly();
        sceneList.refresh();
    }

    private void refreshPreviewOnly() {
        if (selectedScene == null) preview.showLayers(List.of(), 1, 0);
        else preview.showLayers(selectedScene.getLayers(), Math.max(0.1,
                selectedScene.getTimelineEndSeconds() - selectedScene.getTimelineStartSeconds()), playhead.getValue());
    }

    private void refreshLayerInspector() {
        updatingUi = true;
        try {
            boolean enabled = selectedLayer != null;
            for (Control control : List.of(layerContent, xField, yField, widthField, heightField,
                    rotationField, opacityField, layerStartField, layerEndField)) control.setDisable(!enabled);
            if (!enabled) {
                layerContent.clear();
                for (TextField field : List.of(xField, yField, widthField, heightField, rotationField,
                        opacityField, layerStartField, layerEndField)) field.clear();
                return;
            }
            layerContent.setText(selectedLayer.getType() == VisualLayerType.TEXT ? selectedLayer.getText()
                    : selectedLayer.getType() == VisualLayerType.PLACEHOLDER ? selectedLayer.getDescription() : selectedLayer.getName());
            xField.setText(format(selectedLayer.getX())); yField.setText(format(selectedLayer.getY()));
            widthField.setText(format(selectedLayer.getWidth())); heightField.setText(format(selectedLayer.getHeight()));
            rotationField.setText(format(selectedLayer.getRotationDegrees())); opacityField.setText(format(selectedLayer.getOpacity()));
            layerStartField.setText(format(selectedLayer.getStartOffsetSeconds()));
            layerEndField.setText(selectedLayer.getEndOffsetSeconds() == null ? "" : format(selectedLayer.getEndOffsetSeconds()));
        } finally {
            updatingUi = false;
        }
    }

    private void refreshCharacterLibrary() {
        characterList.setItems(FXCollections.observableArrayList(characterLibrary.list()));
    }

    private void commitSceneTiming() {
        if (updatingUi || selectedScene == null) return;
        try {
            String manual = manualStartField.getText().trim();
            selectedScene.setManualStartSeconds(manual.isEmpty() ? null : nonNegative(manual));
            selectedScene.setPauseAfterSeconds(nonNegative(pauseField.getText()));
            markDirty();
            refreshAllKeepingSelection(selectedScene.getId());
        } catch (RuntimeException error) {
            StudioDialogs.error(stage, "Mốc thời gian không hợp lệ", "Hãy nhập số giây không âm.");
            selectSceneDirect(selectedScene);
        }
    }

    private void commitLayerEnd() {
        if (updatingUi || selectedLayer == null) return;
        try {
            String value = layerEndField.getText().trim();
            Double end = value.isEmpty() ? null : nonNegative(value);
            selectedLayer.setVisibilityWindow(selectedLayer.getStartOffsetSeconds(), end);
            markDirty();
            refreshLayersAndPreview();
        } catch (RuntimeException error) {
            StudioDialogs.error(stage, "Thời điểm lớp không hợp lệ", "Mốc ẩn phải để trống hoặc không nhỏ hơn mốc hiện.");
            refreshLayerInspector();
        }
    }

    private void bindLayerNumber(TextField field, Supplier<Double> fallback, DoubleConsumer setter) {
        commitOnActionAndFocus(field, () -> {
            if (updatingUi || selectedLayer == null) return;
            try {
                setter.accept(Double.parseDouble(field.getText().trim().replace(',', '.')));
                markDirty();
                refreshLayersAndPreview();
            } catch (RuntimeException error) {
                field.setText(format(fallback.get()));
            }
        });
    }

    private void backgroundRaw(String startMessage, Callable<?> operation, Consumer<Object> success, String errorTitle) {
        status(startMessage);
        setBusy(true);
        Task<Object> task = new Task<>() {
            @Override protected Object call() throws Exception { return operation.call(); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            setBusy(false);
            Throwable error = task.getException();
            String message = error == null || error.getMessage() == null ? String.valueOf(error) : error.getMessage();
            status(errorTitle + ": " + message);
            StudioDialogs.error(stage, errorTitle, message);
        });
        Thread thread = new Thread(task, "explainer-background-task");
        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("unchecked")
    private <T> void background(String startMessage, Callable<T> operation, Consumer<T> success, String errorTitle) {
        backgroundRaw(startMessage, (Callable<?>) operation, value -> success.accept((T) value), errorTitle);
    }

    private void setBusy(boolean busy) {
        busyTasks += busy ? 1 : -1;
        busyTasks = Math.max(0, busyTasks);
        progress.setVisible(busyTasks > 0);
        progress.setProgress(busyTasks > 0 ? ProgressIndicator.INDETERMINATE_PROGRESS : 0);
    }

    public boolean requestClose() {
        if (!confirmDiscardIfDirty()) return false;
        close();
        return true;
    }

    @Override public void close() {
        stopPlayback();
        audioPlayer.close();
        try (var paths = Files.walk(sessionDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private boolean confirmDiscardIfDirty() {
        return !dirty || StudioDialogs.confirm(stage, "Bỏ thay đổi", "Dự án có thay đổi chưa lưu. Tiếp tục và bỏ các thay đổi này?");
    }

    private void markDirty() {
        dirty = true;
        project.touch();
        updateTitle();
    }

    private void updateTitle() {
        if (project == null) return;
        String file = project.getProjectFile() == null ? project.getName() : project.getProjectFile().getFileName().toString();
        stage.setTitle((dirty ? "● " : "") + file + " — Explainer Video Studio");
    }

    private void status(String text) { statusLabel.setText(text == null ? "" : text); }

    private Path resolveStoredPathQuietly(String stored) {
        try { return resolveStoredPath(stored); }
        catch (Exception ignored) { return stored == null ? sessionDirectory.resolve("missing") : Path.of(stored).toAbsolutePath().normalize(); }
    }

    private Path resolveStoredPath(String stored) throws IOException {
        Path path = Path.of(stored);
        if (path.isAbsolute()) return path.normalize();
        if (project.getProjectFile() == null) throw new IOException("Dự án chưa có thư mục để đọc tài nguyên tương đối.");
        return projectRepository.resolveAsset(project, stored);
    }

    private String audioDescription(SceneData scene) {
        if (scene.getAudioPath() == null) return "Chưa có WAV — cảnh dùng thời lượng xem trước " + format(scene.getTimelineEndSeconds() - scene.getTimelineStartSeconds()) + "s.";
        return (scene.isAudioStale() ? "WAV cần tạo lại" : "WAV sẵn sàng") + " • " + format(scene.getAudioDurationSeconds())
                + "s • bắt đầu " + format(scene.getTimelineStartSeconds()) + "s";
    }

    private int nextZ() {
        return selectedScene == null ? 0 : selectedScene.getLayers().stream().mapToInt(VisualLayer::getZIndex).max().orElse(0) + 10;
    }

    private List<VisualLayer> sortedLayers(SceneData scene) {
        return scene.getLayers().stream().sorted(Comparator.comparingInt(VisualLayer::getZIndex).reversed()).toList();
    }

    private File chooseImage(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh", "*.png", "*.jpg", "*.jpeg"));
        return chooser.showOpenDialog(stage);
    }

    private void saveSettingsQuietly() {
        try { settingsService.save(settings); }
        catch (IOException error) { status("Không lưu được cấu hình: " + error.getMessage()); }
    }

    private final class CharacterCell extends ListCell<CharacterAsset> {
        @Override protected void updateItem(CharacterAsset asset, boolean empty) {
            super.updateItem(asset, empty);
            if (empty || asset == null) {
                setText(null); setGraphic(null); return;
            }
            ImageView thumbnail = new ImageView();
            thumbnail.setFitWidth(56); thumbnail.setFitHeight(56); thumbnail.setPreserveRatio(true);
            try { thumbnail.setImage(new Image(characterLibrary.resolveImage(asset).toUri().toString(), 56, 56, true, true)); }
            catch (Exception ignored) { }
            Label name = new Label(asset.getDisplayName());
            name.setStyle("-fx-font-weight: bold;");
            Label labels = new Label(String.join(", ", asset.getLabels()));
            labels.setWrapText(true);
            VBox text = new VBox(3, name, labels);
            setGraphic(new HBox(8, thumbnail, text));
        }
    }

    private Button toolbarButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text); button.setOnAction(action); return button;
    }
    private Button smallButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text); button.setOnAction(action); return button;
    }
    private Label title(String text) { Label label = new Label(text); label.getStyleClass().add("panel-title"); return label; }
    private VBox inspectorBox() { VBox box = new VBox(9); box.setPadding(new Insets(12)); return box; }
    private GridPane grid() { GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(7); return grid; }
    private void addGridRow(GridPane grid, int row, String label, Node field) {
        grid.add(new Label(label), 0, row); grid.add(field, 1, row); GridPane.setHgrow(field, Priority.ALWAYS);
    }
    private void commitOnActionAndFocus(TextField field, Runnable commit) {
        field.setOnAction(event -> commit.run());
        field.focusedProperty().addListener((obs, old, focused) -> { if (!focused) commit.run(); });
    }
    private static double nonNegative(String value) {
        double parsed = Double.parseDouble(value.trim().replace(',', '.'));
        if (!Double.isFinite(parsed) || parsed < 0) throw new NumberFormatException();
        return parsed;
    }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static String abbreviate(String value, int max) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }
    private static String stripExtension(String file) { int dot = file.lastIndexOf('.'); return dot > 0 ? file.substring(0, dot) : file; }
    private static String safeProjectName(String value) {
        String result = value == null ? "video" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return result.isBlank() ? "video" : result;
    }

    private record AudioTarget(Path file, String storedPath) { }
    private record AudioUpdate(String sceneId, String storedPath, double durationSeconds) { }
}
