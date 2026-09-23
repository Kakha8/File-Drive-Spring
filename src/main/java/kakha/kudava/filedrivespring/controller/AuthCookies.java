package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.time.Duration;

final class AuthCookies {
    private AuthCookies() {}
    static void setRefresh(HttpServletResponse response, String token, int days) {
        boolean secure = Boolean.parseBoolean(System.getenv().getOrDefault("AUTH_COOKIE_SECURE", "true"));
        response.addHeader("Set-Cookie", ResponseCookie.from("refresh_token", token)
                .secure(secure).httpOnly(true).path("/").sameSite(secure ? "None" : "Lax")
                .maxAge(Duration.ofDays(days)).build().toString());
    }
    static void clearRefresh(HttpServletResponse response) { setRefresh(response, "", 0); }
}
