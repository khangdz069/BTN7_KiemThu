package org.example.editvideoytbtool.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.editvideoytbtool.config.AppSettings;
import org.example.editvideoytbtool.llm.ScriptGenerationRequest;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class StudioDialogs {
    record AiInput(ScriptGenerationRequest request) { }
    record CharacterMetadata(String name, List<String> labels) { }

    private StudioDialogs() { }

    static Optional<String> promptScript(Window owner) {
        Dialog<String> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Dán kịch bản");
        dialog.setHeaderText("Dùng dòng ---, [Cảnh] hoặc Cảnh 1 để phân cảnh. Nếu không có, ứng dụng chia theo quy tắc 2–3 câu/cảnh.");
        TextArea area = new TextArea();
        area.setPromptText("Dán kịch bản tại đây…");
        area.setWrapText(true);
        area.setPrefSize(720, 440);
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(area.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == ButtonType.OK ? area.getText() : null);
        return dialog.showAndWait();
    }

    static Optional<AiInput> promptAi(Window owner, AppSettings settings) {
        Dialog<AiInput> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("AI viết kịch bản");
        dialog.setHeaderText("Dịch vụ mô hình ngôn ngữ là cấu hình riêng, không phải VieNeu‑TTS.");

        TextField topic = new TextField();
        TextField audience = new TextField("Người xem phổ thông");
        TextField tone = new TextField("Rõ ràng, thân thiện");
        Spinner<Integer> minutes = new Spinner<>(1, 120, 3);
        TextField baseUrl = new TextField(settings.getLlmBaseUrl());
        TextField model = new TextField(settings.getLlmModel());
        PasswordField key = new PasswordField();
        key.setText(settings.getLlmApiKey());
        key.setPromptText("Chỉ giữ trong phiên chạy / EXPLAINER_LLM_API_KEY");

        GridPane grid = formGrid();
        addRow(grid, 0, "Chủ đề", topic);
        addRow(grid, 1, "Đối tượng", audience);
        addRow(grid, 2, "Giọng văn", tone);
        addRow(grid, 3, "Phút", minutes);
        addRow(grid, 4, "Base URL", baseUrl);
        addRow(grid, 5, "Model", model);
        addRow(grid, 6, "API key", key);
        Label note = new Label("Ứng dụng yêu cầu JSON scenes hợp lệ; phản hồi sai định dạng sẽ bị từ chối, không làm thay đổi dự án.");
        note.setWrapText(true);
        grid.add(note, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Tạo kịch bản", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        ButtonType generate = dialog.getDialogPane().getButtonTypes().getFirst();
        Node ok = dialog.getDialogPane().lookupButton(generate);
        ok.disableProperty().bind(topic.textProperty().isEmpty().or(baseUrl.textProperty().isEmpty()).or(model.textProperty().isEmpty()));
        dialog.setResultConverter(button -> {
            if (button != generate) return null;
            settings.setLlmBaseUrl(baseUrl.getText());
            settings.setLlmModel(model.getText());
            settings.setLlmApiKey(key.getText());
            return new AiInput(new ScriptGenerationRequest(topic.getText(), audience.getText(), tone.getText(), minutes.getValue()));
        });
        return dialog.showAndWait();
    }

    static boolean editSettings(Window owner, AppSettings settings) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Cấu hình dịch vụ");
        dialog.setHeaderText("VieNeu‑TTS v3 Turbo và mô hình ngôn ngữ là hai dịch vụ độc lập.");
        TextField ttsUrl = new TextField(settings.getTtsBaseUrl());
        TextField voice = new TextField(settings.getTtsVoice());
        PasswordField ttsKey = new PasswordField();
        ttsKey.setText(settings.getTtsApiKey());
        TextField ffmpeg = new TextField(settings.getFfmpegPath());
        Button browse = new Button("Chọn…");
        browse.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn ffmpeg.exe");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("FFmpeg", "ffmpeg.exe", "*.exe"));
            File file = chooser.showOpenDialog(owner);
            if (file != null) ffmpeg.setText(file.getAbsolutePath());
        });
        ToolBar ffmpegBox = new ToolBar(ffmpeg, browse);
        TextField llmUrl = new TextField(settings.getLlmBaseUrl());
        TextField model = new TextField(settings.getLlmModel());
        PasswordField llmKey = new PasswordField();
        llmKey.setText(settings.getLlmApiKey());

        GridPane grid = formGrid();
        addRow(grid, 0, "VieNeu URL", ttsUrl);
        addRow(grid, 1, "VieNeu voice", voice);
        addRow(grid, 2, "VieNeu key", ttsKey);
        addRow(grid, 3, "FFmpeg", ffmpegBox);
        Separator separator = new Separator();
        grid.add(separator, 0, 4, 2, 1);
        addRow(grid, 5, "LLM base URL", llmUrl);
        addRow(grid, 6, "LLM model", model);
        addRow(grid, 7, "LLM API key", llmKey);
        Label security = new Label("API key không được lưu xuống settings.json; chỉ giữ trong bộ nhớ phiên chạy. Có thể dùng VIENEU_API_KEY và EXPLAINER_LLM_API_KEY.");
        security.setWrapText(true);
        grid.add(security, 0, 8, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return false;
            settings.setTtsBaseUrl(ttsUrl.getText());
            settings.setTtsVoice(voice.getText());
            settings.setTtsApiKey(ttsKey.getText());
            settings.setFfmpegPath(ffmpeg.getText());
            settings.setLlmBaseUrl(llmUrl.getText());
            settings.setLlmModel(model.getText());
            settings.setLlmApiKey(llmKey.getText());
            return true;
        });
        return dialog.showAndWait().orElse(false);
    }

    static Optional<CharacterMetadata> characterMetadata(Window owner, String initialName, List<String> initialLabels) {
        Dialog<CharacterMetadata> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Tên và nhãn nhân vật");
        TextField name = new TextField(initialName == null ? "" : initialName);
        TextField labels = new TextField(initialLabels == null ? "" : String.join(", ", initialLabels));
        labels.setPromptText("bình thường, đang nói, ngạc nhiên");
        GridPane grid = formGrid();
        addRow(grid, 0, "Tên", name);
        addRow(grid, 1, "Nhãn", labels);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new CharacterMetadata(name.getText(), Arrays.stream(labels.getText().split(","))
                    .map(String::trim).filter(s -> !s.isBlank()).toList()) : null);
        return dialog.showAndWait();
    }

    static boolean confirm(Window owner, String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.YES, ButtonType.NO);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    static void error(Window owner, String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    static void info(Window owner, String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, content, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private static void addRow(GridPane grid, int row, String label, Node node) {
        grid.add(new Label(label), 0, row);
        grid.add(node, 1, row);
        GridPane.setHgrow(node, Priority.ALWAYS);
        if (node instanceof TextField text) text.setPrefColumnCount(34);
    }
}
