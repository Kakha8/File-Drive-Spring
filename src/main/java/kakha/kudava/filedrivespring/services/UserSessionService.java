package kakha.kudava.filedrivespring.services;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

@Service
public class UserSessionService {
    public boolean checkUserSession(HttpSession session) {
        Object username = session.getAttribute("USERNAME");
        if (username != null)
            return true;
        return false;
    }

    public String getUsernameFromSession(HttpSession session) {
        Object username = session.getAttribute("USERNAME");
        return username != null ? username.toString() : null;
    }
}
