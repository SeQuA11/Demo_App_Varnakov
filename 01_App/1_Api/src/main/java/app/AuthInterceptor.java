package app;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Проверка токена входа на каждом запросе. Токены хранятся в памяти и пропадают при перезапуске API.
 * Раздел /api/users доступен только роли «Администратор».
 */
@Component
public class AuthInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String ADMIN = "Администратор";

    /** токен → данные пользователя из UserService.login */
    static final Map<String, Map<String, Object>> sessions = new ConcurrentHashMap<>();

    static String open(Map<String, Object> user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        return token;
    }

    static void close(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.equals("/api/auth/login")) {
            return true;
        }
        String token = request.getHeader("Authorization");
        Map<String, Object> user = token == null ? null : sessions.get(token);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Выполните вход в систему");
        }
        if (path.startsWith("/api/users") && !ADMIN.equals(user.get("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }
        request.setAttribute("user", user);
        return true;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }
}
