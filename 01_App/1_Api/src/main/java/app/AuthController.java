package app;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    record LoginRequest(String login, String password) {}

    record PasswordRequest(String oldPassword, String newPassword) {}

    final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    /** Ответ: id, fio, role, mustChangePassword и token для заголовка Authorization. */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest r) {
        Map<String, Object> user = new HashMap<>(users.login(r.login(), r.password()));
        user.put("token", AuthInterceptor.open(user));
        return user;
    }

    @PostMapping("/password")
    public void changePassword(@RequestAttribute Map<String, Object> user, @RequestBody PasswordRequest r) {
        users.changePassword((Integer) user.get("id"), r.oldPassword(), r.newPassword());
        user.put("mustChangePassword", false);
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader("Authorization") String token) {
        AuthInterceptor.close(token);
    }
}
