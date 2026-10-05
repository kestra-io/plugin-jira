package io.kestra.plugin.jira.issues;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

public class JiraUtil {
    public static final String ISSUE_API_ROUTE = "/rest/api/2/issue/";

    public static final String SEARCH_API_ROUTE = "/rest/api/2/search";

    /**
     * Jira Cloud's enhanced search endpoint ({@code POST}, token pagination). Replaces the classic
     * {@link #SEARCH_API_ROUTE}, which Atlassian removed from Jira Cloud (see CHANGE-2046); the
     * classic endpoint remains available on Jira Server and Data Center.
     */
    public static final String SEARCH_JQL_API_ROUTE = "/rest/api/3/search/jql";

    public static final String COMMENT_API_ROUTE = "/comment";

    /** Upper bound accepted for the tasks' {@code maxResults} page size, mirroring Jira's own limits. */
    public static final int MAX_RESULTS_LIMIT = 5000;

    public static final String BROWSE_ROUTE = "/browse/";

    private static final int MAX_ERROR_BODY_LENGTH = 500;

    /**
     * Parses a Jira JSON response body into {@code type}, falling back to {@code emptyValue} (with a
     * debug log, not a thrown exception) on an empty or non-JSON body — Jira's response shape isn't
     * guaranteed stable across API versions.
     */
    public static <T> T parseJsonResponse(RunContext runContext, HttpResponse<String> response, Class<T> type, T emptyValue) {
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            runContext.logger().debug("Jira returned an empty {} response body (status {})", type.getSimpleName(), response.getStatus());
            return emptyValue;
        }

        try {
            return JacksonMapper.ofJson().readValue(body, type);
        } catch (JsonProcessingException e) {
            runContext.logger().debug("Could not parse the Jira {} response body: {}", type.getSimpleName(), e.getMessage());
            return emptyValue;
        }
    }

    /**
     * Parses a Jira JSON response body into {@code type}, failing with an actionable message —
     * including the (truncated) response body — when a non-empty body cannot be parsed. Only a truly
     * empty body falls back to {@code emptyValue}: a flow should never look successful while Jira
     * actually returned data the task could not understand.
     */
    public static <T> T parseJsonResponseStrict(RunContext runContext, HttpResponse<String> response, Class<T> type, T emptyValue) {
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            runContext.logger().debug("Jira returned an empty {} response body (status {})", type.getSimpleName(), response.getStatus());
            return emptyValue;
        }

        try {
            return JacksonMapper.ofJson().readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                "Jira returned HTTP " + response.getStatus().getCode() + " with an unparsable response body; response: " + truncate(body),
                e
            );
        }
    }

    /**
     * Validates a rendered {@code maxResults} page size against {@link #MAX_RESULTS_LIMIT} so a bad
     * value fails fast with an actionable message instead of producing a confusing Jira 400.
     */
    public static int validateMaxResults(Integer maxResults) {
        if (maxResults == null || maxResults < 1) {
            throw new IllegalArgumentException("`maxResults` must be at least 1");
        }
        if (maxResults > MAX_RESULTS_LIMIT) {
            throw new IllegalArgumentException("`maxResults` must be at most " + MAX_RESULTS_LIMIT);
        }
        return maxResults;
    }

    /**
     * Fails with an actionable message — including the (truncated) Jira response body — when a
     * required field is missing from an otherwise successful response.
     */
    public static void requireField(HttpResponse<String> response, Object fieldValue, String fieldDescription) {
        if (fieldValue == null) {
            throw new IllegalStateException(
                "Jira returned HTTP " + response.getStatus().getCode() + " without " + fieldDescription + "; response: " + truncate(response.getBody())
            );
        }
    }

    /**
     * Reads a failed response's body as a {@code String} regardless of the raw type Kestra's HTTP
     * client captured it as (typically {@code byte[]} for a non-2xx response).
     */
    public static String bodyAsString(HttpResponse<?> response) {
        if (response == null || response.getBody() == null) {
            return null;
        }

        Object body = response.getBody();
        return body instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : body.toString();
    }

    /**
     * Truncates a Jira response body before it's embedded in an exception message, so a large error
     * payload never balloons the flow's execution log.
     */
    public static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_ERROR_BODY_LENGTH ? value.substring(0, MAX_ERROR_BODY_LENGTH) + "..." : value;
    }

    /**
     * Encodes a rendered value (an issue key, id, or comment id) for safe use as a single URL path
     * segment. Plain keys such as {@code PROJ-123} are returned unchanged; spaces become {@code %20}
     * rather than form-encoding's {@code +}, which would be invalid in a path.
     */
    public static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
