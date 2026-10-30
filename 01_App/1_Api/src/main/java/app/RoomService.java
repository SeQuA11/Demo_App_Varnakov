package app;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Номерной фонд. Текущий статус номера — последняя запись room_status_log,
 * смена статуса пишет запись на сегодняшнюю дату.
 */
@Service
public class RoomService {

    static final String OCCUPIED = "Занят";
    static final String CLEAN = "Чистый";
    static final String DIRTY = "Грязный";

    final JdbcTemplate db;

    public RoomService(JdbcTemplate db) {
        this.db = db;
    }

    static final String ROOMS = "SELECT r.id, r.number, f.number AS floor, c.name AS category, cc.name AS \"comfortClass\", "
            + "s.name AS status, l.status_date::text AS \"statusDate\" "
            + "FROM room r JOIN floor f ON f.id = r.floor_id "
            + "JOIN room_category c ON c.id = r.category_id JOIN comfort_class cc ON cc.id = c.comfort_class_id "
            + "LEFT JOIN LATERAL (SELECT status_id, status_date FROM room_status_log "
            + "                   WHERE room_id = r.id ORDER BY status_date DESC LIMIT 1) l ON TRUE "
            + "LEFT JOIN room_status s ON s.id = l.status_id ";

    public List<Map<String, Object>> findAll() {
        return db.queryForList(ROOMS + "ORDER BY f.number, r.number");
    }

    public List<String> statuses() {
        return db.queryForList("SELECT name FROM room_status ORDER BY id", String.class);
    }

    /** Номера без пересечения с другими бронями на период [from; to). Бронь без даты выезда занимает одни сутки. */
    public List<Map<String, Object>> free(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        return db.queryForList(ROOMS + "WHERE NOT EXISTS (SELECT 1 FROM stay st WHERE st.room_id = r.id "
                + "AND st.check_in < ? AND coalesce(st.check_out, st.check_in + 1) > ?) ORDER BY f.number, r.number",
                to, from);
    }

    public String currentStatus(int roomId) {
        List<String> s = db.queryForList("SELECT s.name FROM room_status_log l JOIN room_status s ON s.id = l.status_id "
                + "WHERE l.room_id = ? ORDER BY l.status_date DESC LIMIT 1", String.class, roomId);
        return s.isEmpty() ? null : s.get(0);
    }

    /**
     * Ручная смена статуса сотрудником: уборка (Грязный → Назначен к уборке → Чистый).
     * «Занят» ставится и снимается только заселением и выездом.
     */
    public void changeStatus(int roomId, String status) {
        if (!statuses().contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный статус: " + status);
        }
        if (OCCUPIED.equals(status) || OCCUPIED.equals(currentStatus(roomId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Статус «Занят» меняется только при заселении и выезде гостя");
        }
        setStatus(roomId, status);
    }

    void setStatus(int roomId, String status) {
        db.update("INSERT INTO room_status_log (room_id, status_id, status_date) "
                + "VALUES (?, (SELECT id FROM room_status WHERE name = ?), current_date) "
                + "ON CONFLICT (room_id, status_date) DO UPDATE SET status_id = EXCLUDED.status_id", roomId, status);
    }

    static void checkPeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата выезда должна быть позже даты заезда");
        }
    }
}
