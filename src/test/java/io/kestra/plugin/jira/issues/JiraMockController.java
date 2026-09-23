package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.inject.Singleton;

/**
 * Stands in for a Jira instance during tests: any HTTP client baseUrl (with or without a trailing
 * slash or a context path) resolves to this embedded server, whose wildcard routes capture the
 * request for assertions and reply with realistic Jira REST v2 payloads.
 */
@Controller
@Singleton
public class JiraMockController {

    public record CapturedRequest(String method, String path, String authorization, String body) {
    }

    public final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();

    private static final String CREATE_ISSUE_RESPONSE = """
        {"id":"10000","key":"TEST-1","self":"http://mock-jira/rest/api/2/issue/10000"}""";

    private static final String CREATE_COMMENT_RESPONSE = """
        {"id":"20000","self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20000","created":"2024-01-01T00:00:00.000+0000"}""";

    private static final String MISSING_ISSUE_KEY_RESPONSE = """
        {"self":"http://mock-jira/rest/api/2/issue/10000"}""";

    private static final String MISSING_COMMENT_ID_RESPONSE = """
        {"self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20000"}""";

    /**
     * A 2xx response body is picked based on a marker path segment the test embeds in {@code baseUrl}
     * (e.g. {@code getApiBaseUrl() + "/missing-key"}), since the real request path is otherwise fixed
     * by the task under test.
     */
    @Post(uri = "/{+path}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> post(HttpRequest<?> request, String path, @Body String body) {
        capture(request, body);

        String requestPath = request.getPath();
        boolean isComment = requestPath.endsWith("/comment");

        if (requestPath.contains("/http-error/")) {
            return HttpResponse.badRequest("{\"errorMessages\":[\"" + "x".repeat(600) + "\"]}");
        }
        if (requestPath.contains("/empty-body/")) {
            return HttpResponse.created("");
        }
        if (requestPath.contains("/bad-json/")) {
            return HttpResponse.created("not-json");
        }
        if (requestPath.contains("/missing-key/")) {
            return isComment ? HttpResponse.created(MISSING_COMMENT_ID_RESPONSE) : HttpResponse.created(MISSING_ISSUE_KEY_RESPONSE);
        }

        return isComment ? HttpResponse.created(CREATE_COMMENT_RESPONSE) : HttpResponse.created(CREATE_ISSUE_RESPONSE);
    }

    @Put(uri = "/{+path}")
    public HttpResponse<Void> put(HttpRequest<?> request, String path, @Body String body) {
        capture(request, body);

        return HttpResponse.noContent();
    }

    private void capture(HttpRequest<?> request, String body) {
        requests.add(
            new CapturedRequest(
                request.getMethod().name(),
                request.getPath(),
                request.getHeaders().get("Authorization"),
                body
            )
        );
    }
}
