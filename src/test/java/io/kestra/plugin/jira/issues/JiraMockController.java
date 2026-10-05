package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import jakarta.inject.Singleton;

/**
 * Stands in for a Jira instance during tests: any HTTP client baseUrl (with or without a trailing
 * slash or a context path) resolves to this embedded server, whose wildcard routes capture the
 * request for assertions and reply with realistic Jira REST payloads.
 */
@Controller
@Singleton
public class JiraMockController {

    public record CapturedRequest(String method, String path, String query, String authorization, String body) {
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

    private static final String GET_ISSUE_RESPONSE = """
        {"id":"10000","key":"TEST-1","self":"http://mock-jira/rest/api/2/issue/10000","fields":{"summary":"Fix the login bug","description":"Login fails on Safari","status":{"name":"Open"},"labels":["bug","backend"],"created":"2024-01-01T00:00:00.000+0000","updated":"2024-01-02T00:00:00.000+0000"}}""";

    // Two pages of three issues total, so search pagination (startAt, then nextPageToken) is exercised.
    private static final String GET_ISSUES_SEARCH_PAGE_1_RESPONSE = """
        {"startAt":0,"maxResults":2,"total":3,"issues":[{"id":"10000","key":"TEST-1","self":"http://mock-jira/rest/api/2/issue/10000"},{"id":"10001","key":"TEST-2","self":"http://mock-jira/rest/api/2/issue/10001"}]}""";

    private static final String GET_ISSUES_SEARCH_PAGE_2_RESPONSE = """
        {"startAt":2,"maxResults":1,"total":3,"issues":[{"id":"10002","key":"TEST-3","self":"http://mock-jira/rest/api/2/issue/10002"}]}""";

    private static final String SEARCH_JQL_PAGE_1_RESPONSE = """
        {"results":[{"id":"10000","key":"TEST-1","fields":{"summary":"Fix the login bug"}},{"id":"10001","key":"TEST-2","fields":{"summary":"Add pagination"}}],"nextPageToken":"page-2"}""";

    private static final String SEARCH_JQL_PAGE_2_RESPONSE = """
        {"results":[{"id":"10002","key":"TEST-3","fields":{"summary":"Polish docs"}}]}""";

    // Two pages of three comments total, so startAt pagination is exercised.
    private static final String GET_COMMENTS_PAGE_1_RESPONSE = """
        {"startAt":0,"maxResults":50,"total":3,"comments":[{"id":"20000","body":"First comment","self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20000","created":"2024-01-01T00:00:00.000+0000"},{"id":"20001","body":"Second comment","self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20001","created":"2024-01-02T00:00:00.000+0000"}]}""";

    private static final String GET_COMMENTS_PAGE_2_RESPONSE = """
        {"startAt":2,"maxResults":1,"total":3,"comments":[{"id":"20002","body":"Third comment","self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20002","created":"2024-01-03T00:00:00.000+0000"}]}""";

    private static final String GET_COMMENT_RESPONSE = """
        {"id":"20000","body":"First comment","self":"http://mock-jira/rest/api/2/issue/TEST-1/comment/20000","created":"2024-01-01T00:00:00.000+0000"}""";

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

        if (requestPath.endsWith("/search/jql")) {
            // Jira Cloud's enhanced search returns 200 (not 201): the follow-up request carries the previous page's token.
            if (requestPath.contains("/http-error/")) {
                return HttpResponse.badRequest("{\"errorMessages\":[\"" + "x".repeat(600) + "\"]}");
            }
            if (requestPath.contains("/empty-body/")) {
                return HttpResponse.ok("");
            }
            if (requestPath.contains("/bad-json/")) {
                return HttpResponse.ok("not-json");
            }
            if (requestPath.contains("/missing-key/")) {
                return HttpResponse.ok(MISSING_ISSUE_KEY_RESPONSE);
            }
            return body != null && body.contains("nextPageToken")
                ? HttpResponse.ok(SEARCH_JQL_PAGE_2_RESPONSE)
                : HttpResponse.ok(SEARCH_JQL_PAGE_1_RESPONSE);
        }

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

    @Get(uri = "/{+path}", produces = MediaType.APPLICATION_JSON)
    public HttpResponse<String> get(HttpRequest<?> request, String path) {
        capture(request, null);

        String requestPath = request.getPath();

        if (requestPath.contains("/http-error/")) {
            return HttpResponse.badRequest("{\"errorMessages\":[\"" + "x".repeat(600) + "\"]}");
        }
        if (requestPath.contains("/empty-body/")) {
            return HttpResponse.ok("");
        }
        if (requestPath.contains("/bad-json/")) {
            return HttpResponse.ok("not-json");
        }
        if (requestPath.contains("/missing-key/")) {
            if (requestPath.contains("/comment/")) {
                return HttpResponse.ok(MISSING_COMMENT_ID_RESPONSE);
            }
            return HttpResponse.ok(MISSING_ISSUE_KEY_RESPONSE);
        }
        if (requestPath.contains("/comment/")) {
            return HttpResponse.ok(GET_COMMENT_RESPONSE);
        }
        if (requestPath.endsWith("/comment")) {
            int startAt = startAtParameter(request.getUri().getQuery());
            return startAt >= 2 ? HttpResponse.ok(GET_COMMENTS_PAGE_2_RESPONSE) : HttpResponse.ok(GET_COMMENTS_PAGE_1_RESPONSE);
        }
        if (requestPath.contains("/search")) {
            int startAt = startAtParameter(request.getUri().getQuery());
            return startAt >= 2 ? HttpResponse.ok(GET_ISSUES_SEARCH_PAGE_2_RESPONSE) : HttpResponse.ok(GET_ISSUES_SEARCH_PAGE_1_RESPONSE);
        }

        return HttpResponse.ok(GET_ISSUE_RESPONSE);
    }

    /** Reads the {@code startAt} paging parameter the tasks send, defaulting to the first page. */
    private int startAtParameter(String query) {
        if (query == null) {
            return 0;
        }
        for (String parameter : query.split("&")) {
            if (parameter.startsWith("startAt=")) {
                try {
                    return Integer.parseInt(parameter.substring("startAt=".length()));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private void capture(HttpRequest<?> request, String body) {
        requests.add(
            new CapturedRequest(
                request.getMethod().name(),
                request.getPath(),
                request.getUri().getQuery(),
                request.getHeaders().get("Authorization"),
                body
            )
        );
    }
}
