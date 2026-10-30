package app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Учётные записи сотрудников, только для администратора (проверяет AuthInterceptor). */
@RestController
@RequestMapping("/api/users")
public class UserController {

    record UserRequest(String lastName, String firstName, String patronymic, String login, String password, String role) {}

    final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public List<Map<String, Object>> all() {
        return users.findAll();
    }

    @PostMapping
    public void create(@RequestBody UserRequest r) {
        users.create(r.lastName(), r.firstName(), r.patronymic(), r.login(), r.password(), r.role());
    }

    @PutMapping("/{id}/unblock")
    public void unblock(@PathVariable int id) {
        users.unblock(id);
    }
}
