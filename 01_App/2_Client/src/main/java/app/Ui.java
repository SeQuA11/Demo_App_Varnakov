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
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Общие элементы интерфейса: таблицы по JSON, формы, диалоги и сообщения. */
public class Ui {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    static final DecimalFormat MONEY = new DecimalFormat("#,##0.00 ₽", DecimalFormatSymbols.getInstance(new Locale("ru")));

    /** ФИО: только буквы, пробел и дефис. */
    static final String NAME = "[А-Яа-яЁёA-Za-z -]+";

    interface Action {
        void run() throws Exception;
    }

    /** form("Фамилия", поле, "Имя", поле, ...) — подписи слева, поля справа. */
    static GridPane form(Object... pairs) {
        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        for (int i = 0; i < pairs.length; i += 2) {
            g.addRow(i / 2, new Label((String) pairs[i]), (Node) pairs[i + 1]);
        }
        return g;
    }

    /** Колонки делят ширину таблицы пропорционально заданным ширинам. */
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
        d.setHeaderText(title);
        if (App.stage != null && App.stage.isShowing()) {
            d.initOwner(App.stage);
        }
        Label error = new Label();
        error.setStyle("-fx-text-fill: red;");
        error.setWrapText(true);
        error.setMaxWidth(400);
        d.getDialogPane().setContent(new VBox(10, content, error));
        ButtonType ok = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE));
        Button okButton = (Button) d.getDialogPane().lookupButton(ok);
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

    static Alert alert(Alert.AlertType type, String title, String text) {
        Alert a = new Alert(type, text);
        a.setTitle(title);
        a.setHeaderText(null);
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
