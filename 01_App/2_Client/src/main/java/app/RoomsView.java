package app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/** Вкладка «Номерной фонд»: номера с текущим статусом, фильтры, смена статуса при уборке. */
public class RoomsView {

    static final String ALL = "Все";

    final VBox root;
    final TableView<JsonObject> table;
    final ComboBox<String> floor = new ComboBox<>();
    final ComboBox<String> status = new ComboBox<>();
    final ComboBox<String> newStatus = new ComboBox<>();
    final Label counts = new Label();
    List<JsonObject> rooms = List.of();

    RoomsView() {
        table = Ui.table(List.of(
                Ui.col("Номер", "number", 70),
                Ui.col("Этаж", "floor", 60),
                Ui.col("Категория", "category", 360),
                Ui.col("Класс", "comfortClass", 110),
                Ui.col("Статус", "status", 160),
                Ui.dateCol("Дата статуса", "statusDate", 120)), "Нет номеров");
        floor.setOnAction(e -> filter());
        status.setOnAction(e -> filter());

        Button refresh = new Button("Обновить");
        refresh.setOnAction(e -> load());
        Button change = new Button("Изменить статус");
        change.setOnAction(e -> change());

        HBox filters = new HBox(10, new Label("Этаж"), floor, new Label("Статус"), status, refresh);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, new Label("Новый статус выбранного номера"), newStatus, change);
        actions.setAlignment(Pos.CENTER_LEFT);
        root = new VBox(10, counts, filters, table, actions);
        root.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    void load() {
        try {
            rooms = Ui.list(Api.get("rooms"));
            List<String> statuses = Api.get("rooms/statuses").getAsJsonArray().asList().stream()
                    .map(JsonElement::getAsString).toList();
            fill(floor, rooms.stream().map(r -> Ui.text(r, "floor")).distinct().sorted().toList());
            fill(status, statuses);
            newStatus.getItems().setAll(statuses.stream().filter(s -> !s.equals("Занят")).toList());

            StringBuilder sb = new StringBuilder("Всего номеров: " + rooms.size());
            for (String s : statuses) {
                sb.append("    ").append(s).append(": ")
                        .append(rooms.stream().filter(r -> s.equals(Ui.text(r, "status"))).count());
            }
            counts.setText(sb.toString());
            filter();
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }

    /** Первый пункт — «Все», выбранное значение сохраняется после обновления. */
    static void fill(ComboBox<String> box, List<String> values) {
        String selected = box.getValue();
        box.getItems().setAll(ALL);
        box.getItems().addAll(values);
        box.setValue(selected != null && box.getItems().contains(selected) ? selected : ALL);
    }

    void filter() {
        table.setItems(FXCollections.observableArrayList(rooms.stream()
                .filter(r -> ALL.equals(floor.getValue()) || Objects.equals(floor.getValue(), Ui.text(r, "floor")))
                .filter(r -> ALL.equals(status.getValue()) || Objects.equals(status.getValue(), Ui.text(r, "status")))
                .toList()));
    }

    void change() {
        JsonObject room = table.getSelectionModel().getSelectedItem();
        if (room == null) {
            Ui.error("Выберите номер в таблице");
            return;
        }
        if (newStatus.getValue() == null) {
            Ui.error("Выберите новый статус");
            return;
        }
        try {
            Api.put("rooms/" + Ui.text(room, "id") + "/status", Api.body("status", newStatus.getValue()));
            load();
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }
}
