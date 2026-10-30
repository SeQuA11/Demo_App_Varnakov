package app;

import com.google.gson.JsonObject;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Вкладка «Пользователи» (только администратор): список сотрудников, добавление, разблокировка. */
public class UsersView {

    final VBox root;
    final TableView<JsonObject> table;

    UsersView() {
        TableColumn<JsonObject, String> fio = Ui.col("ФИО", "lastName", 280);
        fio.setCellValueFactory(d -> new SimpleStringProperty(String.join(" ", Ui.text(d.getValue(), "lastName"),
                Ui.text(d.getValue(), "firstName"), Ui.text(d.getValue(), "patronymic")).trim()));
        TableColumn<JsonObject, String> state = Ui.col("Состояние", "blocked", 130);
        state.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().get("blocked").getAsBoolean() ? "Заблокирован" : "Активен"));
        table = Ui.table(List.of(fio, Ui.col("Логин", "login", 140), Ui.col("Роль", "role", 150), state,
                Ui.col("Последний вход", "lastLogin", 160)), "Нет пользователей");

        Button add = new Button("Добавить пользователя");
        add.setOnAction(e -> {
            if (Ui.done(userDialog())) {
                load();
            }
        });
        Button unblock = new Button("Разблокировать");
        unblock.setOnAction(e -> unblock());

        root = new VBox(10, table, new HBox(10, add, unblock));
        root.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    void load() {
        try {
            table.setItems(FXCollections.observableArrayList(Ui.list(Api.get("users"))));
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }

    static Dialog<ButtonType> userDialog() {
        TextField last = new TextField();
        TextField first = new TextField();
        TextField patronymic = new TextField();
        patronymic.setPromptText("при наличии");
        TextField login = new TextField();
        PasswordField password = new PasswordField();
        ComboBox<String> role = new ComboBox<>(FXCollections.observableArrayList("Пользователь", App.ADMIN));
        role.setValue("Пользователь");
        return Ui.dialog("Новый пользователь",
                new VBox(10, Ui.form("Фамилия", last, "Имя", first, "Отчество", patronymic, "Логин", login,
                        "Пароль", password, "Роль", role),
                        new Label("При первом входе сотрудник должен будет сменить пароль.")),
                "Добавить", () -> {
                    if (last.getText().isBlank() || first.getText().isBlank() || login.getText().isBlank()
                            || password.getText().isEmpty()) {
                        throw new Exception("Заполните фамилию, имя, логин и пароль");
                    }
                    for (TextField f : List.of(last, first, patronymic)) {
                        if (!f.getText().isBlank() && !f.getText().trim().matches(Ui.NAME)) {
                            throw new Exception("ФИО может содержать только буквы, пробел и дефис");
                        }
                    }
                    if (!login.getText().trim().matches("[A-Za-z0-9_.]+")) {
                        throw new Exception("Логин: латинские буквы, цифры, точка и подчёркивание");
                    }
                    Api.post("users", Api.body("lastName", last.getText().trim(), "firstName", first.getText().trim(),
                            "patronymic", patronymic.getText().trim(), "login", login.getText().trim(),
                            "password", password.getText(), "role", role.getValue()));
                });
    }

    void unblock() {
        JsonObject u = table.getSelectionModel().getSelectedItem();
        if (u == null) {
            Ui.error("Выберите пользователя в таблице");
            return;
        }
        if (!u.get("blocked").getAsBoolean()) {
            Ui.error("Пользователь не заблокирован");
            return;
        }
        try {
            Api.put("users/" + Ui.text(u, "id") + "/unblock", null);
            load();
            Ui.info("Пользователь " + Ui.text(u, "login") + " разблокирован");
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }
}
