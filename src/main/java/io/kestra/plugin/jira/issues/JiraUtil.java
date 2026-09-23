package io.kestra.plugin.jira.issues;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

public class JiraUtil {
    public static final String ISSUE_API_ROUTE = "/rest/api/2/issue/";

    public static final String COMMENT_API_ROUTE = "/comment";

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
     * Truncates a Jira response body before it's embedded in an exception message, so a large error
     * payload never balloons the flow's execution log.
     */
    public static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_ERROR_BODY_LENGTH ? value.substring(0, MAX_ERROR_BODY_LENGTH) + "..." : value;
    }
}
