package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * REST API гостиницы поверх БД booking_41_varnakov.
 * Все адреса, кроме /api/auth/login, требуют заголовок Authorization с токеном, выданным при входе.
 */
@SpringBootApplication
@EnableScheduling
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
