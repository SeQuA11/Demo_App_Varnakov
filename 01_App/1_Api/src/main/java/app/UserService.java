package app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Пользователи ИС: вход, блокировка после 3 неверных паролей, смена пароля, учётные записи.
 * При пустой таблице app_user создаётся администратор admin / admin.
 */
@Service
public class UserService implements CommandLineRunner {

    static final int MAX_ATTEMPTS = 3;
    static final String WRONG = "Вы ввели неверный логин или пароль. Пожалуйста проверьте ещё раз введенные данные";
    static final String BLOCKED = "Вы заблокированы. Обратитесь к администратору";

    final JdbcTemplate db;

    public UserService(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public void run(String... args) {
        if (db.queryForObject("SELECT count(*) FROM app_user", Integer.class) == 0) {
            db.update("INSERT INTO app_user (last_name, first_name, login, password_hash, role_id) "
                    + "VALUES ('Администратор', 'Системы', 'admin', ?, (SELECT id FROM role WHERE name = 'Администратор'))",
                    hash("admin"));
        }
    }

    /** SHA-256 в виде hex-строки, в БД пароль открытым текстом не хранится. */
    static String hash(String password) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Проверяет логин и пароль. Неверный пароль увеличивает счётчик попыток, на третьей учётная запись блокируется.
     * Вход больше месяца назад тоже блокирует учётную запись.
     */
    public Map<String, Object> login(String login, String password) {
        List<Map<String, Object>> found = db.queryForList(
                "SELECT u.id, u.password_hash, u.is_blocked, u.failed_attempts, u.must_change_password, r.name AS role, "
                        + "u.last_login_at < now() - interval '1 month' AS expired, "
                        + "concat_ws(' ', u.last_name, u.first_name, u.patronymic) AS fio "
                        + "FROM app_user u JOIN role r ON r.id = u.role_id WHERE u.login = ?", login);
        if (found.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, WRONG);
        }
        Map<String, Object> u = found.get(0);
        int id = (Integer) u.get("id");
        if ((Boolean) u.get("is_blocked")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, BLOCKED);
        }
        if (Boolean.TRUE.equals(u.get("expired"))) {
            db.update("UPDATE app_user SET is_blocked = TRUE WHERE id = ?", id);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, BLOCKED);
        }
        if (!hash(password).equals(u.get("password_hash"))) {
            int attempts = ((Number) u.get("failed_attempts")).intValue() + 1;
            db.update("UPDATE app_user SET failed_attempts = ?, is_blocked = ? WHERE id = ?",
                    attempts, attempts >= MAX_ATTEMPTS, id);
            throw new ResponseStatusException(attempts >= MAX_ATTEMPTS ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED,
                    attempts >= MAX_ATTEMPTS ? BLOCKED : WRONG);
        }
        db.update("UPDATE app_user SET failed_attempts = 0, last_login_at = now() WHERE id = ?", id);
        return Map.of("id", id, "fio", u.get("fio"), "role", u.get("role"),
                "mustChangePassword", u.get("must_change_password"));
    }

    /** Новый пароль: не короче 4 символов и не равен текущему. После смены флаг первого входа снимается. */
    public void changePassword(int userId, String oldPassword, String newPassword) {
        String current = db.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, userId);
        if (!hash(oldPassword).equals(current)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Текущий пароль введён неверно");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль должен быть не короче 4 символов");
        }
        if (Objects.equals(oldPassword, newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль совпадает с текущим");
        }
        db.update("UPDATE app_user SET password_hash = ?, must_change_password = FALSE WHERE id = ?",
                hash(newPassword), userId);
    }

    public List<Map<String, Object>> findAll() {
        return db.queryForList("SELECT u.id, u.last_name AS \"lastName\", u.first_name AS \"firstName\", "
                + "u.patronymic, u.login, r.name AS role, u.is_blocked AS blocked, "
                + "to_char(u.last_login_at, 'DD.MM.YYYY HH24:MI') AS \"lastLogin\" "
                + "FROM app_user u JOIN role r ON r.id = u.role_id ORDER BY u.last_name, u.first_name");
    }

    /** Добавляет сотрудника. Логин должен быть уникальным, при первом входе пароль нужно будет сменить. */
    public void create(String lastName, String firstName, String patronymic, String login, String password, String role) {
        if (blank(lastName) || blank(firstName) || blank(login) || blank(password) || blank(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните фамилию, имя, логин, пароль и роль");
        }
        if (db.queryForObject("SELECT count(*) FROM app_user WHERE login = ?", Integer.class, login) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь с таким логином уже существует");
        }
        db.update("INSERT INTO app_user (last_name, first_name, patronymic, login, password_hash, role_id) "
                        + "VALUES (?, ?, ?, ?, ?, (SELECT id FROM role WHERE name = ?))",
                lastName.trim(), firstName.trim(), blank(patronymic) ? null : patronymic.trim(), login.trim(),
                hash(password), role);
    }

    /** Снимает блокировку и обнуляет счётчик попыток; дата входа сбрасывается, чтобы правило месяца не сработало сразу. */
    public void unblock(int id) {
        db.update("UPDATE app_user SET is_blocked = FALSE, failed_attempts = 0, last_login_at = NULL WHERE id = ?", id);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
