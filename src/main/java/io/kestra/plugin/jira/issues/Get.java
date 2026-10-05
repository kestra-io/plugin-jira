package io.kestra.plugin.jira.issues;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.SEARCH_API_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.SEARCH_JQL_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get Jira issues",
    description = "Fetch a single issue by key or id, or several issues matching a JQL query, from Jira's REST API. Uses the same authentication fields as other Jira tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch a single Jira issue by key and log its summary and status.",
            full = true,
            code = """
                id: jira_get_issue
                namespace: company.myteam

                tasks:
                  - id: get_issue
                    type: io.kestra.plugin.jira.issues.Get
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: "TID-53"
                    fetchType: FETCH_ONE

                  - id: log_issue
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ outputs.get_issue.row.fields.summary }}: {{ outputs.get_issue.row.fields.status.name }}"
                """
        ),
        @Example(
            title = "Search Jira issues with a JQL query.",
            full = true,
            code = """
                id: jira_search_issues
                namespace: company.myteam

                tasks:
                  - id: search_issues
                    type: io.kestra.plugin.jira.issues.Get
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    jql: "project = DEMO AND status = Open ORDER BY created DESC"
                    fetchType: FETCH
                    maxResults: 10

                  - id: log_issues
                    type: io.kestra.plugin.core.log.Log
                    message: "Found {{ outputs.search_issues.size }} issues"
                """
        )
    }
)
public class Get extends JiraClient implements RunnableTask<Get.Output> {

    @Schema(
        title = "How fetched issues are exposed",
        description = "`FETCH_ONE` returns the issue addressed by `issueIdOrKey` as `row`; `FETCH` returns the issues matching `jql` as `rows`; `STORE` stores the matching issues in Kestra storage and exposes the storage URI."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH_ONE);

    @Schema(
        title = "Issue key or id",
        description = "Required for `fetchType: FETCH_ONE`; rendered value appended to `/rest/api/2/issue/`."
    )
    @PluginProperty(group = "main")
    private Property<String> issueIdOrKey;

    @Schema(
        title = "JQL search query",
        description = "Required for `fetchType: FETCH` or `STORE`; a Jira Query Language expression such as `project = DEMO AND status = Open`."
    )
    @PluginProperty(group = "main")
    private Property<String> jql;

    public enum SearchApi {
        /**
         * Jira Cloud's enhanced search endpoint (`POST /rest/api/3/search/jql`), paginated with
         * `nextPageToken`. The classic `/rest/api/2/search` endpoint was removed from Jira Cloud.
         */
        CLOUD,
        /** The classic search endpoint (`GET /rest/api/2/search`), paginated with `startAt`, as still served by Jira Server and Data Center. */
        SERVER
    }

    @Schema(
        title = "Jira search API used for `FETCH` and `STORE`",
        description = "`CLOUD` (default) sends `POST /rest/api/3/search/jql` and pages through results with `nextPageToken`; use it for Jira Cloud, where the classic `/rest/api/2/search` endpoint was removed. `SERVER` sends `GET /rest/api/2/search` and pages through results with `startAt`; use it for Jira Server and Data Center."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<SearchApi> searchApi = Property.ofValue(SearchApi.CLOUD);

    @Schema(
        title = "Issue fields requested from Jira",
        description = "The Jira Cloud enhanced search endpoint returns only issue ids and keys unless explicit fields are requested, so this list is sent as the `fields` parameter (in the request body for `CLOUD`, as a query parameter for `SERVER`). Defaults to `summary`, `status`, `description`, `labels`, `created` and `updated`."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<List<String>> fields = Property.ofValue(List.of("summary", "status", "description", "labels", "created", "updated"));

    @Schema(
        title = "Page size for the JQL search",
        description = "Only used for `fetchType: FETCH` or `STORE`; every page is fetched until Jira reports no more results, so `size` reflects the full match count. Must be between 1 and 5000."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> maxResults = Property.ofValue(50);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH_ONE);
        var rBrowseRoot = this.browseRoot(runContext);

        return switch (rFetchType) {
            case FETCH_ONE -> fetchOne(runContext, rBrowseRoot);
            case FETCH, STORE -> fetchMany(runContext, rBrowseRoot, rFetchType);
            case NONE -> Output.builder().size(0L).build();
        };
    }

    private Output fetchOne(RunContext runContext, String rBrowseRoot) throws Exception {
        var rIssueIdOrKey = optionalRendered(runContext, this.issueIdOrKey)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("`issueIdOrKey` is required when `fetchType` is FETCH_ONE"));
        var encodedIssueIdOrKey = JiraUtil.encodePathSegment(rIssueIdOrKey);
        var uri = rBrowseRoot + ISSUE_API_ROUTE + encodedIssueIdOrKey;
        var response = this.execute(runContext, "GET", uri, null);

        Map<String, Object> issue = JiraUtil.parseJsonResponse(runContext, response, Map.class, null);
        JiraUtil.requireField(response, issue == null ? null : issue.get("key"), "an issue key");

        return Output.builder()
            .row(issue)
            .size(1L)
            .build();
    }

    private Output fetchMany(RunContext runContext, String rBrowseRoot, FetchType rFetchType) throws Exception {
        var rJql = optionalRendered(runContext, this.jql)
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("`jql` is required when `fetchType` is FETCH or STORE"));
        var rSearchApi = runContext.render(this.searchApi).as(SearchApi.class).orElse(SearchApi.CLOUD);
        var rFields = runContext.render(this.fields).asList(String.class);
        var rMaxResults = JiraUtil.validateMaxResults(runContext.render(this.maxResults).as(Integer.class).orElse(50));

        var outputBuilder = Output.builder();

        // STORE streams each page straight to the storage file so a large match count never has to sit in memory.
        switch (rFetchType) {
            case STORE -> {
                var tempFile = runContext.workingDir().createTempFile(".json").toFile();
                long size;
                try (var generator = JacksonMapper.ofJson().getFactory().createGenerator(tempFile, JsonEncoding.UTF8)) {
                    generator.writeStartArray();
                    PageHandler writePage = page ->
                    {
                        for (var issue : page) {
                            generator.writeObject(issue);
                        }
                    };
                    size = rSearchApi == SearchApi.CLOUD
                        ? searchWithEnhancedApi(runContext, rBrowseRoot, rJql, rMaxResults, rFields, writePage)
                        : searchWithServerApi(runContext, rBrowseRoot, rJql, rMaxResults, rFields, writePage);
                    generator.writeEndArray();
                }
                outputBuilder.size(size).uri(runContext.storage().putFile(tempFile));
            }
            default -> {
                var issues = new ArrayList<Map<String, Object>>();
                long size = rSearchApi == SearchApi.CLOUD
                    ? searchWithEnhancedApi(runContext, rBrowseRoot, rJql, rMaxResults, rFields, issues::addAll)
                    : searchWithServerApi(runContext, rBrowseRoot, rJql, rMaxResults, rFields, issues::addAll);
                outputBuilder.size(size).rows(issues);
            }
        }

        return outputBuilder.build();
    }

    /** Pages through Jira Cloud's enhanced search endpoint, handing each page to {@code pageHandler}. */
    private long searchWithEnhancedApi(RunContext runContext, String rBrowseRoot, String rJql, int rMaxResults, List<String> rFields, PageHandler pageHandler) throws Exception {
        var uri = rBrowseRoot + SEARCH_JQL_API_ROUTE;
        // The endpoint omits the parameter when null, so an empty list does not go out as `"fields": []`.
        var requestFields = rFields.isEmpty() ? null : rFields;
        String nextPageToken = null;
        long total = 0;

        do {
            var requestBody = JacksonMapper.ofJson().writeValueAsString(
                new EnhancedSearchRequest(rJql, rMaxResults, requestFields, nextPageToken)
            );
            var response = this.execute(runContext, "POST", uri, requestBody);

            var search = JiraUtil.parseJsonResponseStrict(
                runContext,
                response,
                EnhancedSearchResponse.class,
                new EnhancedSearchResponse(null, null, List.of())
            );
            JiraUtil.requireField(response, search.issues(), "a list of issues");

            var page = search.issues();
            if (page.isEmpty()) {
                break;
            }
            pageHandler.handle(page);
            total += page.size();
            if (Boolean.TRUE.equals(search.isLast())) {
                break;
            }
            nextPageToken = search.nextPageToken();
        } while (nextPageToken != null && !nextPageToken.isBlank());

        return total;
    }

    /** Pages through the classic search endpoint, handing each page to {@code pageHandler}. */
    private long searchWithServerApi(RunContext runContext, String rBrowseRoot, String rJql, int rMaxResults, List<String> rFields, PageHandler pageHandler) throws Exception {
        var fieldsParameter = rFields.isEmpty()
            ? ""
            : "&fields=" + URLEncoder.encode(String.join(",", rFields), StandardCharsets.UTF_8);
        Integer total = null;
        var startAt = 0;
        long fetched = 0;
        int lastPageSize;

        do {
            var uri = rBrowseRoot + SEARCH_API_ROUTE
                + "?jql=" + URLEncoder.encode(rJql, StandardCharsets.UTF_8)
                + "&maxResults=" + rMaxResults
                + "&startAt=" + startAt
                + fieldsParameter;
            var response = this.execute(runContext, "GET", uri, null);

            var search = JiraUtil.parseJsonResponseStrict(
                runContext,
                response,
                SearchResponse.class,
                new SearchResponse(null, null, null, List.of())
            );
            JiraUtil.requireField(response, search.issues(), "a list of issues");

            var page = search.issues();
            if (page.isEmpty()) {
                break;
            }
            pageHandler.handle(page);
            lastPageSize = page.size();
            fetched += lastPageSize;
            startAt += lastPageSize;
            total = search.total();
        } while (total != null && startAt < total && lastPageSize > 0);

        return fetched;
    }

    /** Renders an optional `Property<String>` input, treating an unset property as absent. */
    private Optional<String> optionalRendered(RunContext runContext, Property<String> value) throws IllegalVariableEvaluationException {
        return value == null ? Optional.empty() : runContext.render(value).as(String.class);
    }

    /** Handles one page of fetched issues, either collecting it or streaming it to storage. */
    private interface PageHandler {
        void handle(List<Map<String, Object>> page) throws Exception;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnhancedSearchRequest(String jql, Integer maxResults, List<String> fields, String nextPageToken) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EnhancedSearchResponse(String nextPageToken, Boolean isLast, List<Map<String, Object>> issues) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchResponse(Integer startAt, Integer maxResults, Integer total, List<Map<String, Object>> issues) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The fetched issue", description = "Only populated when `fetchType` is `FETCH_ONE`.")
        private final Map<String, Object> row;

        @Schema(title = "The list of fetched issues", description = "Only populated when `fetchType` is `FETCH`.")
        private final List<Map<String, Object>> rows;

        @Schema(
            title = "Kestra's internal storage URI of the stored issues",
            description = "Only populated when `fetchType` is `STORE`."
        )
        private final URI uri;

        @Schema(title = "The number of fetched issues")
        private final Long size;
    }
}
