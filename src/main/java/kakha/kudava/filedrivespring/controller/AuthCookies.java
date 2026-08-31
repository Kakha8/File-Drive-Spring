package kakha.kudava.filedrivespring.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import java.time.Duration;

final class AuthCookies {
    private AuthCookies() {}
    static void setRefresh(HttpServletResponse response, String token, int days) {
        response.addHeader("Set-Cookie", ResponseCookie.from("refresh_token", token)
                .secure(true).httpOnly(true).path("/").sameSite("None")
                .maxAge(Duration.ofDays(days)).build().toString());
    }
    static void clearRefresh(HttpServletResponse response) { setRefresh(response, "", 0); }
}
