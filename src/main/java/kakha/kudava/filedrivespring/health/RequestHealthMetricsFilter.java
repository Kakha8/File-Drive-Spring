package kakha.kudava.filedrivespring.health;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestHealthMetricsFilter extends OncePerRequestFilter {
    private final RequestHealthMetrics metrics;

    public RequestHealthMetricsFilter(RequestHealthMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metrics.record(response.getStatus(), System.nanoTime() - started);
        }
    }
}
