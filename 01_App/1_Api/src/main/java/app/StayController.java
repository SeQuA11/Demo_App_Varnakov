package app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stays")
public class StayController {

    record BookingRequest(String lastName, String firstName, String patronymic, int roomId,
                          LocalDate checkIn, LocalDate checkOut) {}

    record PaymentRequest(BigDecimal amount) {}

    final StayService stays;

    public StayController(StayService stays) {
        this.stays = stays;
    }

    @GetMapping
    public List<Map<String, Object>> find(@RequestParam(defaultValue = "") String search) {
        return stays.find(search);
    }

    /** Ответ: {"id": номер новой брони} */
    @PostMapping
    public Map<String, Object> book(@RequestBody BookingRequest r) {
        return Map.of("id", stays.book(r.lastName(), r.firstName(), r.patronymic(), r.roomId(), r.checkIn(), r.checkOut()));
    }

    /** Ответ: {"card": номер выданной карты доступа} */
    @PostMapping("/{id}/check-in")
    public Map<String, Object> checkIn(@PathVariable int id) {
        return Map.of("card", stays.checkIn(id));
    }

    @PostMapping("/{id}/check-out")
    public void checkOut(@PathVariable int id) {
        stays.checkOut(id);
    }

    @PostMapping("/{id}/payments")
    public void pay(@PathVariable int id, @RequestBody PaymentRequest r) {
        stays.pay(id, r.amount());
    }
}
