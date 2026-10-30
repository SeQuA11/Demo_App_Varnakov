package app;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Бронирование, заселение, выезд и оплата.
 * Состояние брони: «Бронь» — гость ещё не заехал, «Проживает» — карта доступа активна, «Выехал» — карта заблокирована.
 */
@Service
public class StayService {

    final JdbcTemplate db;
    final RoomService rooms;

    public StayService(JdbcTemplate db, RoomService rooms) {
        this.db = db;
        this.rooms = rooms;
    }

    /** search — начало фамилии гостя, пустая строка — все брони. */
    public List<Map<String, Object>> find(String search) {
        String s = search == null ? "" : search.trim();
        return db.queryForList("SELECT st.id, concat_ws(' ', g.last_name, g.first_name, g.patronymic) AS guest, "
                + "r.id AS \"roomId\", r.number AS room, st.check_in::text AS \"checkIn\", st.check_out::text AS \"checkOut\", "
                + "CASE WHEN NOT st.is_checked_in THEN 'Бронь' WHEN st.card_active THEN 'Проживает' ELSE 'Выехал' END AS state, "
                + "st.card_number AS card, coalesce((SELECT sum(p.amount) FROM payment p WHERE p.stay_id = st.id), 0) AS paid "
                + "FROM stay st JOIN guest g ON g.id = st.guest_id JOIN room r ON r.id = st.room_id "
                + "WHERE ? = '' OR g.last_name ILIKE ? || '%' ORDER BY st.check_in DESC, st.id DESC", s, s);
    }

    /** Новая бронь: гость заводится в справочнике, номер должен быть свободен на весь период. Возвращает id брони. */
    @Transactional
    public int book(String lastName, String firstName, String patronymic, int roomId, LocalDate checkIn, LocalDate checkOut) {
        if (UserService.blank(lastName) || UserService.blank(firstName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите фамилию и имя гостя");
        }
        RoomService.checkPeriod(checkIn, checkOut);
        boolean free = rooms.free(checkIn, checkOut).stream().anyMatch(r -> r.get("id").equals(roomId));
        if (!free) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер занят на выбранные даты");
        }
        Integer guestId = db.queryForObject("INSERT INTO guest (last_name, first_name, patronymic) VALUES (?, ?, ?) RETURNING id",
                Integer.class, lastName.trim(), firstName.trim(), UserService.blank(patronymic) ? null : patronymic.trim());
        return db.queryForObject("INSERT INTO stay (room_id, guest_id, check_in, check_out, is_checked_in) "
                + "VALUES (?, ?, ?, ?, FALSE) RETURNING id", Integer.class, roomId, guestId, checkIn, checkOut);
    }

    /** Заселить можно только в чистый номер. Номер становится «Занят», карта доступа активируется. */
    @Transactional
    public String checkIn(int stayId) {
        Map<String, Object> st = get(stayId);
        if ((Boolean) st.get("is_checked_in")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Гость уже заселён");
        }
        int roomId = (Integer) st.get("room_id");
        if (!RoomService.CLEAN.equals(rooms.currentStatus(roomId))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заселить гостя можно только в чистый номер");
        }
        String card = String.format("%s-%05d", st.get("number"), stayId);
        db.update("UPDATE stay SET is_checked_in = TRUE, card_number = ?, card_active = TRUE WHERE id = ?", card, stayId);
        rooms.setStatus(roomId, RoomService.OCCUPIED);
        return card;
    }

    /**
     * Выезд из занятого номера (только последнее заселение в этом номере). Ранний выезд ставит дату выезда на сегодня,
     * плановая дата, которая уже прошла, сохраняется. Карта блокируется, номер становится «Грязный».
     */
    @Transactional
    public void checkOut(int stayId) {
        Map<String, Object> st = get(stayId);
        int roomId = (Integer) st.get("room_id");
        if (!(Boolean) st.get("is_checked_in") || !RoomService.OCCUPIED.equals(rooms.currentStatus(roomId))
                || Boolean.TRUE.equals(st.get("newer_guest"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Гость не проживает в номере");
        }
        db.update("UPDATE stay SET card_active = FALSE, check_out = CASE WHEN check_out IS NULL OR check_out > current_date "
                + "THEN greatest(current_date, check_in) ELSE check_out END WHERE id = ?", stayId);
        rooms.setStatus(roomId, RoomService.DIRTY);
    }

    public void pay(int stayId, BigDecimal amount) {
        get(stayId);
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма оплаты должна быть больше нуля");
        }
        db.update("INSERT INTO payment (stay_id, amount) VALUES (?, ?)", stayId, amount);
    }

    /** Каждую минуту: в день выезда с 12:00 карта доступа к номеру блокируется. */
    @Scheduled(cron = "0 * * * * *")
    public void blockCards() {
        db.update("UPDATE stay SET card_active = FALSE WHERE card_active "
                + "AND (check_out < current_date OR (check_out = current_date AND localtime >= '12:00'))");
    }

    Map<String, Object> get(int stayId) {
        List<Map<String, Object>> st = db.queryForList("SELECT st.room_id, st.is_checked_in, r.number, "
                + "EXISTS (SELECT 1 FROM stay n WHERE n.room_id = st.room_id AND n.is_checked_in AND n.check_in > st.check_in) "
                + "AS newer_guest FROM stay st JOIN room r ON r.id = st.room_id WHERE st.id = ?", stayId);
        if (st.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Бронь не найдена");
        }
        return st.get(0);
    }
}
