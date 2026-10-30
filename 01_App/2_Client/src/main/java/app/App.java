package app;

import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Окно входа и главное окно с вкладками разделов.
 * Вкладка «Пользователи» видна только роли «Администратор».
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
        stage.setTitle("Гостиница");
        stage.setScene(loginScene());
        stage.show();
    }

    static Scene loginScene() {
        TextField login = new TextField();
        PasswordField password = new PasswordField();
        Label error = new Label();
        error.setStyle("-fx-text-fill: red;");

        Button enter = new Button("Войти");
        enter.setDefaultButton(true);
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

        GridPane form = Ui.form("Логин", login, "Пароль", password);
        form.setAlignment(Pos.CENTER);
        VBox root = new VBox(10, new Label("Вход в систему"), form, enter, error);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(20));
        return new Scene(root, 420, 260);
    }

    /** first = true: первый вход по выданному паролю, без смены пароля работать нельзя. */
    static Dialog<ButtonType> passwordDialog(boolean first) {
        PasswordField old = new PasswordField();
        PasswordField pas1 = new PasswordField();
        PasswordField pas2 = new PasswordField();
        Node form = Ui.form("Текущий пароль", old, "Новый пароль", pas1, "Повторите пароль", pas2);
        Node content = first ? new VBox(10, new Label("Это первый вход в систему. Задайте свой пароль."), form) : form;
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
        RoomsView rooms = new RoomsView();
        StaysView stays = new StaysView();
        TabPane tabs = new TabPane(tab("Номерной фонд", rooms.root, rooms::load), tab("Бронирования", stays.root, stays::load));
        if (ADMIN.equals(Ui.text(user, "role"))) {
            UsersView users = new UsersView();
            tabs.getTabs().add(tab("Пользователи", users.root, users::load));
        }
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Button password = new Button("Сменить пароль");
        password.setOnAction(e -> {
            if (Ui.done(passwordDialog(false))) {
                Ui.info("Пароль изменён");
            }
        });
        Button exit = new Button("Выход");
        exit.setOnAction(e -> logout());
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(10, new Label(Ui.text(user, "fio") + " (" + Ui.text(user, "role") + ")"), grow, password, exit);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(8));

        return new Scene(new BorderPane(tabs, top, null, null, null), 1000, 620);
    }

    /** Вкладка, которая заново загружает данные с сервера каждый раз, когда её открывают. */
    static Tab tab(String title, Node content, Runnable load) {
        Tab t = new Tab(title, content);
        t.setOnSelectionChanged(e -> {
            if (t.isSelected()) {
                load.run();
            }
        });
        return t;
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
