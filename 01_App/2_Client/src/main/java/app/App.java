package app;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Supplier;

/**
 * Окно входа и главное окно с меню разделов.
 * Раздел «Пользователи» виден только роли «Администратор».
 */
public class App extends Application {

    static final String ADMIN = "Администратор";

    static Stage stage;

    /** Ответ /api/auth/login: id, fio, role, mustChangePassword, token. */
    static JsonObject user;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage s) {
        stage = s;
        stage.setTitle("Гостиница — система управления");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.setScene(loginScene());
        stage.show();
    }

    static Scene scene(Parent root) {
        Scene sc = new Scene(root, 1180, 720);
        sc.getStylesheets().add(Ui.CSS);
        return sc;
    }

    static Scene loginScene() {
        TextField login = new TextField();
        login.setPromptText("Введите логин");
        PasswordField password = new PasswordField();
        password.setPromptText("Введите пароль");
        Label error = Ui.label("", "error");
        error.setWrapText(true);

        Button enter = new Button("ВОЙТИ");
        enter.getStyleClass().add("primary");
        enter.setDefaultButton(true);
        enter.setMaxWidth(Double.MAX_VALUE);
        enter.setOnAction(e -> {
            error.setText("");
            if (login.getText().isBlank() || password.getText().isBlank()) {
                error.setText("Введите логин и пароль");
                return;
            }
            try {
                JsonObject u = Api.post("auth/login",
                        Api.body("login", login.getText().trim(), "password", password.getText())).getAsJsonObject();
                Api.token = u.get("token").getAsString();
                user = u;
                if (u.get("mustChangePassword").getAsBoolean() && !Ui.done(passwordDialog(true))) {
                    logout();
                    return;
                }
                stage.setScene(mainScene());
            } catch (Exception ex) {
                error.setText(ex.getMessage());
            }
        });

        VBox panel = new VBox(14,
                Ui.label("ВХОД В СИСТЕМУ", "caption"),
                Ui.label("ГОСТИНИЦА", "title"),
                Ui.line(),
                Ui.field("Логин", login),
                Ui.field("Пароль", password),
                enter, error);
        panel.getStyleClass().add("panel");
        panel.setMaxSize(400, Region.USE_PREF_SIZE);

        Label footer = Ui.label("Система управления гостиницей · доступ только для сотрудников", "muted");
        StackPane.setAlignment(footer, Pos.BOTTOM_CENTER);
        StackPane.setMargin(footer, new Insets(0, 0, 18, 0));
        StackPane root = new StackPane(panel, footer);
        root.getStyleClass().add("backdrop");
        return scene(root);
    }

    /** first = true: первый вход по выданному паролю, без смены пароля работать нельзя. */
    static Dialog<ButtonType> passwordDialog(boolean first) {
        PasswordField old = new PasswordField();
        PasswordField pas1 = new PasswordField();
        PasswordField pas2 = new PasswordField();
        Node form = Ui.form("Текущий пароль", old, "Новый пароль", pas1, "Повторите пароль", pas2);
        Node content = first
                ? new VBox(10, Ui.label("Это первый вход в систему. Задайте свой пароль.", "muted"), form)
                : form;
        return Ui.dialog("Смена пароля", content, "Сохранить", () -> {
            if (old.getText().isEmpty() || pas1.getText().isEmpty() || pas2.getText().isEmpty()) {
                throw new Exception("Заполните все поля");
            }
            if (!pas1.getText().equals(pas2.getText())) {
                throw new Exception("Новый пароль и повтор не совпадают");
            }
            Api.post("auth/password", Api.body("oldPassword", old.getText(), "newPassword", pas1.getText()));
        });
    }

    static Scene mainScene() {
        boolean admin = ADMIN.equals(Ui.text(user, "role"));
        StackPane content = new StackPane();
        content.getStyleClass().add("content");

        VBox nav = new VBox();
        nav.getStyleClass().add("nav");
        Button rooms = navButton("НОМЕРНОЙ ФОНД", nav, content, () -> new RoomsView().root);
        Button stays = navButton("БРОНИРОВАНИЯ", nav, content, () -> new StaysView().root);
        nav.getChildren().addAll(Ui.label("РАЗДЕЛЫ", "nav-caption"), rooms, stays);
        if (admin) {
            nav.getChildren().add(navButton("ПОЛЬЗОВАТЕЛИ", nav, content, () -> new UsersView().root));
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button password = new Button("СМЕНИТЬ ПАРОЛЬ");
        password.getStyleClass().add("nav-button");
        password.setOnAction(e -> {
            if (Ui.done(passwordDialog(false))) {
                Ui.info("Пароль изменён");
            }
        });
        Button exit = new Button("ВЫХОД");
        exit.getStyleClass().add("nav-button");
        exit.setOnAction(e -> logout());
        nav.getChildren().addAll(spacer, password, exit);

        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        VBox who = new VBox(2, Ui.label(Ui.text(user, "fio"), "user-name"), Ui.label(Ui.text(user, "role").toUpperCase(), "caption"));
        who.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(14, Ui.label("ГОСТИНИЦА", "title-small"), Ui.label("СИСТЕМА УПРАВЛЕНИЯ", "caption"), grow, who);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");

        BorderPane root = new BorderPane(content, header, null, null, nav);
        root.getStyleClass().add("backdrop");
        rooms.fire();
        return scene(root);
    }

    static Button navButton(String text, VBox nav, StackPane content, Supplier<Node> view) {
        Button b = new Button(text);
        b.getStyleClass().add("nav-button");
        b.setOnAction(e -> {
            List.copyOf(nav.getChildren()).forEach(n -> n.getStyleClass().remove("active"));
            b.getStyleClass().add("active");
            content.getChildren().setAll(view.get());
        });
        return b;
    }

    static void logout() {
        try {
            Api.post("auth/logout", null);
        } catch (Exception ignored) {
            // токен всё равно забываем, даже если сервер не ответил
        }
        Api.token = null;
        user = null;
        stage.setScene(loginScene());
    }
}
