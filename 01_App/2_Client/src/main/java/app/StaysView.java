package app;

import com.google.gson.JsonObject;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Раздел «Бронирования»: поиск брони по фамилии, новая бронь, заселение, выезд, оплата. */
public class StaysView {

    final VBox root;
    final TableView<JsonObject> table;
    final TextField search = new TextField();

    StaysView() {
        TableColumn<JsonObject, String> paid = Ui.col("Оплачено", "paid", 120);
        paid.setCellValueFactory(d -> new SimpleStringProperty(Ui.MONEY.format(d.getValue().get("paid").getAsBigDecimal())));
        table = Ui.table(List.of(
                Ui.col("№", "id", 45),
                Ui.col("Гость", "guest", 250),
                Ui.col("Номер", "room", 65),
                Ui.dateCol("Заезд", "checkIn", 95),
                Ui.dateCol("Выезд", "checkOut", 95),
                Ui.colorCol("Состояние", "state", 105),
                Ui.col("Карта доступа", "card", 115),
                paid), "Брони не найдены");

        search.setPromptText("Фамилия гостя");
        search.setPrefWidth(260);
        search.setOnAction(e -> load());
        Button find = new Button("НАЙТИ");
        find.setOnAction(e -> load());

        Button book = new Button("НОВАЯ БРОНЬ");
        book.getStyleClass().add("primary");
        book.setOnAction(e -> {
            if (Ui.done(bookingDialog())) {
                load();
            }
        });
        Button checkIn = new Button("ЗАСЕЛИТЬ");
        checkIn.setOnAction(e -> checkIn());
        Button checkOut = new Button("ОФОРМИТЬ ВЫЕЗД");
        checkOut.setOnAction(e -> checkOut());
        Button pay = new Button("ПРИНЯТЬ ОПЛАТУ");
        pay.setOnAction(e -> {
            JsonObject st = selected();
            if (st != null && Ui.done(paymentDialog(st))) {
                load();
            }
        });

        HBox top = new HBox(10, Ui.label("ПОИСК", "caption"), search, find);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox actions = new HBox(10, book, checkIn, checkOut, pay);
        root = new VBox(14, Ui.heading("БРОНИРОВАНИЯ", "Бронирование, заселение и выезд гостей"), top, table, actions);
        VBox.setVgrow(table, Priority.ALWAYS);
        load();
    }

    void load() {
        try {
            table.setItems(FXCollections.observableArrayList(
                    Ui.list(Api.get("stays?search=" + Api.encode(search.getText().trim())))));
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }

    JsonObject selected() {
        JsonObject st = table.getSelectionModel().getSelectedItem();
        if (st == null) {
            Ui.error("Выберите бронь в таблице");
        }
        return st;
    }

    /** Список номеров обновляется при смене дат: показываются только свободные на весь период. */
    static Dialog<ButtonType> bookingDialog() {
        TextField last = new TextField();
        TextField first = new TextField();
        TextField patronymic = new TextField();
        patronymic.setPromptText("при наличии");
        DatePicker in = new DatePicker(LocalDate.now());
        DatePicker out = new DatePicker(LocalDate.now().plusDays(1));
        ComboBox<JsonObject> room = new ComboBox<>();
        room.setPromptText("Свободные номера на выбранные даты");
        room.setCellFactory(x -> new RoomCell());
        room.setButtonCell(new RoomCell());

        Runnable reload = () -> {
            room.getItems().clear();
            if (in.getValue() != null && out.getValue() != null && out.getValue().isAfter(in.getValue())) {
                try {
                    room.getItems().setAll(Ui.list(Api.get("rooms/free?from=" + in.getValue() + "&to=" + out.getValue())));
                } catch (Exception e) {
                    room.setPromptText(e.getMessage());
                }
            }
        };
        in.valueProperty().addListener((o, a, b) -> reload.run());
        out.valueProperty().addListener((o, a, b) -> reload.run());
        reload.run();

        return Ui.dialog("Новое бронирование",
                Ui.form("Фамилия", last, "Имя", first, "Отчество", patronymic,
                        "Дата заезда", in, "Дата выезда", out, "Номер", room),
                "Забронировать", () -> {
                    if (last.getText().isBlank() || first.getText().isBlank()) {
                        throw new Exception("Укажите фамилию и имя гостя");
                    }
                    for (TextField f : List.of(last, first, patronymic)) {
                        if (!f.getText().isBlank() && !f.getText().trim().matches(Ui.NAME)) {
                            throw new Exception("ФИО может содержать только буквы, пробел и дефис");
                        }
                    }
                    if (in.getValue() == null || out.getValue() == null || !out.getValue().isAfter(in.getValue())) {
                        throw new Exception("Дата выезда должна быть позже даты заезда");
                    }
                    if (room.getValue() == null) {
                        throw new Exception("Выберите свободный номер");
                    }
                    Api.post("stays", Api.body("lastName", last.getText().trim(), "firstName", first.getText().trim(),
                            "patronymic", patronymic.getText().trim(), "roomId", room.getValue().get("id").getAsInt(),
                            "checkIn", in.getValue(), "checkOut", out.getValue()));
                });
    }

    /** Строка номера в выпадающем списке: «201 · Двухместный стандарт · Чистый». */
    static class RoomCell extends ListCell<JsonObject> {
        @Override
        protected void updateItem(JsonObject r, boolean empty) {
            super.updateItem(r, empty);
            setText(empty || r == null ? null : Ui.text(r, "number") + " · " + Ui.text(r, "category")
                    + " · " + Ui.text(r, "status"));
        }
    }

    void checkIn() {
        JsonObject st = selected();
        if (st == null || !Ui.confirm("Заселить гостя " + Ui.text(st, "guest") + " в номер " + Ui.text(st, "room") + "?")) {
            return;
        }
        try {
            String card = Api.post("stays/" + Ui.text(st, "id") + "/check-in", null).getAsJsonObject().get("card").getAsString();
            load();
            Ui.info("Гость заселён. Номер получил статус «Занят».\nКарта доступа № " + card + " активирована.");
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }

    void checkOut() {
        JsonObject st = selected();
        if (st == null || !Ui.confirm("Оформить выезд гостя " + Ui.text(st, "guest") + " из номера " + Ui.text(st, "room") + "?")) {
            return;
        }
        try {
            Api.post("stays/" + Ui.text(st, "id") + "/check-out", null);
            load();
            Ui.info("Выезд оформлен. Карта доступа заблокирована, номер получил статус «Грязный».");
        } catch (Exception e) {
            Ui.error(e.getMessage());
        }
    }

    static Dialog<ButtonType> paymentDialog(JsonObject st) {
        TextField amount = new TextField();
        amount.setPromptText("например, 4500,00");
        return Ui.dialog("Оплата проживания",
                Ui.form("Гость", new Label(Ui.text(st, "guest")), "Номер", new Label(Ui.text(st, "room")),
                        "Сумма, ₽", amount),
                "Принять оплату", () -> {
                    BigDecimal sum;
                    try {
                        sum = new BigDecimal(amount.getText().trim().replace(" ", "").replace(',', '.'));
                    } catch (NumberFormatException e) {
                        throw new Exception("Сумма должна быть числом");
                    }
                    if (sum.signum() <= 0) {
                        throw new Exception("Сумма оплаты должна быть больше нуля");
                    }
                    Api.post("stays/" + Ui.text(st, "id") + "/payments", Api.body("amount", sum));
                });
    }
}
