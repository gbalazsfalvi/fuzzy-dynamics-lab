package io.github.gbalazsfalvi.fuzzy.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects DNS rebinding hosts and cross-origin writes; no permissive CORS policy is installed. */
@Component
final class LoopbackGuard extends OncePerRequestFilter {
  private static final Set<String> HOSTS = Set.of("127.0.0.1", "localhost", "[::1]", "::1");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean allowed =
        Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(request.getRemoteAddr())
            && HOSTS.contains(request.getServerName());
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
      String origin = request.getHeader("Origin");
      String fetchSite = request.getHeader("Sec-Fetch-Site");
      allowed &= fetchSite == null || Set.of("same-origin", "none").contains(fetchSite);
      if (origin != null) {
        try {
          URI uri = URI.create(origin);
          int port =
              uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
          allowed &=
              request.getScheme().equals(uri.getScheme())
                  && request.getServerName().equals(uri.getHost())
                  && request.getServerPort() == port
                  && uri.getUserInfo() == null
                  && (uri.getPath() == null || uri.getPath().isEmpty())
                  && uri.getQuery() == null
                  && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
          allowed = false;
        }
      }
    }
    response.setHeader("Cache-Control", "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    if (!allowed) {
      response.setStatus(403);
      response.setContentType("application/json");
      response
          .getWriter()
          .write(
              "{\"code\":\"LOCAL_ORIGIN_REQUIRED\",\"message\":\"Use the loopback server and its same-origin workbench.\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}
