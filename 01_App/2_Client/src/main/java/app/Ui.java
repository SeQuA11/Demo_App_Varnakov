package app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Общие элементы интерфейса: заголовки, таблицы по JSON, диалоги и сообщения. */
public class Ui {

    static final String CSS = Ui.class.getResource("/style.css").toExternalForm();

    /** Цвет значения в таблице и на плашках: статус номера, состояние брони, учётной записи. */
    static final Map<String, String> COLORS = Map.of(
            "Чистый", "st-clean", "Занят", "st-busy", "Грязный", "st-dirty", "Назначен к уборке", "st-cleaning",
            "Бронь", "st-clean", "Проживает", "st-busy", "Выехал", "st-muted",
            "Активен", "st-clean", "Заблокирован", "st-dirty");

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DecimalFormat MONEY = new DecimalFormat("#,##0.00 ₽", DecimalFormatSymbols.getInstance(new Locale("ru")));

    /** ФИО: только буквы, пробел и дефис. */
    static final String NAME = "[А-Яа-яЁёA-Za-z -]+";

    interface Action {
        void run() throws Exception;
    }

    static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    /** Заголовок раздела с подписью и золотой линией. */
    static VBox heading(String title, String subtitle) {
        return new VBox(4, label(title, "title-small"), label(subtitle, "muted"), line());
    }

    static Region line() {
        Region r = new Region();
        r.getStyleClass().add("line");
        return r;
    }

    static VBox field(String caption, Node input) {
        return new VBox(4, label(caption.toUpperCase(), "caption"), input);
    }

    /** form("Фамилия", поле, "Имя", поле, ...) — подписи слева, поля справа. */
    static GridPane form(Object... pairs) {
        GridPane g = new GridPane();
        g.setHgap(14);
        g.setVgap(10);
        for (int i = 0; i < pairs.length; i += 2) {
            Node input = (Node) pairs[i + 1];
            GridPane.setHgrow(input, Priority.ALWAYS);
            if (input instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                r.setPrefWidth(280);
            }
            g.addRow(i / 2, label((String) pairs[i], "caption"), input);
        }
        return g;
    }

    static VBox stat(String title, long value, String style) {
        VBox v = new VBox(2, label(String.valueOf(value), "stat-value"), label(title, "stat-title"));
        v.getStyleClass().addAll("stat", style);
        return v;
    }

    /** Колонки делят ширину таблицы пропорционально заданным ширинам, без горизонтальной прокрутки. */
    static TableView<JsonObject> table(List<TableColumn<JsonObject, String>> columns, String empty) {
        TableView<JsonObject> t = new TableView<>();
        t.getColumns().addAll(columns);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        t.setPlaceholder(new Label(empty));
        return t;
    }

    static TableColumn<JsonObject, String> col(String title, String key, double width) {
        TableColumn<JsonObject, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(text(d.getValue(), key)));
        c.setPrefWidth(width);
        return c;
    }

    static TableColumn<JsonObject, String> dateCol(String title, String key, double width) {
        TableColumn<JsonObject, String> c = col(title, key, width);
        c.setCellValueFactory(d -> new SimpleStringProperty(date(text(d.getValue(), key))));
        return c;
    }

    /** Колонка, где цвет текста зависит от значения (см. COLORS). */
    static TableColumn<JsonObject, String> colorCol(String title, String key, double width) {
        TableColumn<JsonObject, String> c = col(title, key, width);
        c.setCellFactory(x -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll(COLORS.values());
                setText(empty ? null : item);
                if (!empty && COLORS.containsKey(item)) {
                    getStyleClass().add(COLORS.get(item));
                }
            }
        });
        return c;
    }

    static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** 2025-03-01 → 01.03.2025 */
    static String date(String iso) {
        return iso == null || iso.isBlank() ? "" : LocalDate.parse(iso).format(DATE);
    }

    static List<JsonObject> list(JsonElement array) {
        List<JsonObject> rows = new ArrayList<>();
        array.getAsJsonArray().forEach(e -> rows.add(e.getAsJsonObject()));
        return rows;
    }

    /**
     * Диалог с кнопками «okText» и «Отмена». onOk выполняется по нажатию OK;
     * если он бросил исключение, текст ошибки показывается внизу, а окно не закрывается.
     */
    static Dialog<ButtonType> dialog(String title, Node content, String okText, Action onOk) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle(title);
        if (App.stage != null && App.stage.isShowing()) {
            d.initOwner(App.stage);
        }
        Label error = label("", "error");
        error.setWrapText(true);
        error.setMaxWidth(420);
        DialogPane p = d.getDialogPane();
        p.getStylesheets().add(CSS);
        p.setContent(new VBox(12, label(title.toUpperCase(), "title-small"), line(), content, error));
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        p.getButtonTypes().addAll(ok, new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button okButton = (Button) p.lookupButton(ok);
        okButton.getStyleClass().add("primary");
        okButton.addEventFilter(ActionEvent.ACTION, e -> {
            try {
                onOk.run();
            } catch (Exception ex) {
                error.setText(ex.getMessage());
                e.consume();
            }
        });
        return d;
    }

    static boolean done(Dialog<ButtonType> d) {
        return d.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent();
    }

    /** Сообщение в том же оформлении, что и диалоги: заголовок, линия, текст. */
    static Alert alert(Alert.AlertType type, String title, String text) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setGraphic(null);
        Label message = label(text, "message");
        message.setWrapText(true);
        message.setMaxWidth(460);
        a.getDialogPane().setContent(new VBox(12,
                label(title.toUpperCase(), type == Alert.AlertType.ERROR ? "title-error" : "title-small"), line(), message));
        a.getDialogPane().getStylesheets().add(CSS);
        a.getDialogPane().setMinWidth(440);
        if (App.stage != null && App.stage.isShowing()) {
            a.initOwner(App.stage);
        }
        return a;
    }

    static void info(String text) {
        alert(Alert.AlertType.INFORMATION, "Готово", text).showAndWait();
    }

    static void error(String text) {
        alert(Alert.AlertType.ERROR, "Ошибка", text).showAndWait();
    }

    static boolean confirm(String text) {
        Alert a = alert(Alert.AlertType.CONFIRMATION, "Подтверждение", text);
        a.getButtonTypes().setAll(new ButtonType("Да", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Нет", ButtonBar.ButtonData.CANCEL_CLOSE));
        return a.showAndWait().filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent();
    }
}
