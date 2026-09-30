package org.example.editvideoytbtool;

import javafx.application.Application;
import javafx.animation.PauseTransition;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.editvideoytbtool.ui.MainView;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        try {
            MainView root = new MainView(stage);
            Scene scene = new Scene(root, 1540, 940);
            String css = HelloApplication.class.getResource("app.css").toExternalForm();
            scene.getStylesheets().add(css);
            stage.setMinWidth(1180);
            stage.setMinHeight(720);
            stage.setScene(scene);
            stage.setOnCloseRequest(event -> {
                if (!root.requestClose()) event.consume();
            });
            stage.show();

            if (Boolean.getBoolean("explainer.smoke")) {
                PauseTransition close = new PauseTransition(Duration.seconds(2));
                close.setOnFinished(event -> {
                    root.close();
                    stage.close();
                });
                close.play();
            }
        } catch (Exception error) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    error.getMessage() == null ? error.toString() : error.getMessage(), ButtonType.OK);
            alert.setTitle("Không khởi động được Explainer Video Studio");
            alert.setHeaderText("Ứng dụng không thể khởi tạo dữ liệu cục bộ.");
            alert.showAndWait();
            throw new IllegalStateException(error);
        }
    }
}
