package io.kestra.plugin.jira.issues;

public class JiraUtil {
    public static final String ISSUE_API_ROUTE = "/rest/api/2/issue/";

    public static final String COMMENT_API_ROUTE = "/comment";

    public static final String BROWSE_ROUTE = "/browse/";

    private static final int MAX_ERROR_BODY_LENGTH = 500;

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
