package app;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    record StatusRequest(String status) {}

    final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        return rooms.findAll();
    }

    @GetMapping("/statuses")
    public List<String> statuses() {
        return rooms.statuses();
    }

    /** /api/rooms/free?from=2026-11-01&to=2026-11-03 */
    @GetMapping("/free")
    public List<Map<String, Object>> free(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return rooms.free(from, to);
    }

    @PutMapping("/{id}/status")
    public void changeStatus(@PathVariable int id, @RequestBody StatusRequest r) {
        rooms.changeStatus(id, r.status());
    }
}
