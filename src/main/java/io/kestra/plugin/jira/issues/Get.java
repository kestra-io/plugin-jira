package io.kestra.plugin.jira.issues;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get Jira issues",
    description = "Fetch a single issue by key or id, or several issues matching a JQL query, from Jira's REST v2 API. Uses the same authentication fields as other Jira tasks."
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
                    message: "{{ outputs.get_issue.row.fields.summary }} — {{ outputs.get_issue.row.fields.status.name }}"
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
    @PluginProperty(dynamic = true, group = "main")
    private String issueIdOrKey;

    @Schema(
        title = "JQL search query",
        description = "Required for `fetchType: FETCH` or `STORE`; a Jira Query Language expression such as `project = DEMO AND status = Open`."
    )
    @PluginProperty(dynamic = true, group = "main")
    private String jql;

    @Schema(
        title = "Maximum number of issues returned by the JQL search",
        description = "Only used for `fetchType: FETCH` or `STORE`."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> maxResults = Property.ofValue(50);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElseThrow();
        var rBrowseRoot = this.browseRoot(runContext);

        return switch (rFetchType) {
            case FETCH_ONE -> fetchOne(runContext, rBrowseRoot);
            case FETCH, STORE -> fetchMany(runContext, rBrowseRoot, rFetchType);
            case NONE -> Output.builder().size(0L).build();
        };
    }

    private Output fetchOne(RunContext runContext, String rBrowseRoot) throws Exception {
        if (this.issueIdOrKey == null || this.issueIdOrKey.isBlank()) {
            throw new IllegalArgumentException("`issueIdOrKey` is required when `fetchType` is FETCH_ONE");
        }

        var rIssueIdOrKey = runContext.render(this.issueIdOrKey);
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

    @SuppressWarnings("unchecked")
    private Output fetchMany(RunContext runContext, String rBrowseRoot, FetchType rFetchType) throws Exception {
        if (this.jql == null || this.jql.isBlank()) {
            throw new IllegalArgumentException("`jql` is required when `fetchType` is FETCH or STORE");
        }

        var rJql = runContext.render(this.jql);
        var rMaxResults = runContext.render(this.maxResults).as(Integer.class).orElse(50);
        var uri = rBrowseRoot + SEARCH_API_ROUTE
            + "?jql=" + URLEncoder.encode(rJql, StandardCharsets.UTF_8)
            + "&maxResults=" + rMaxResults;
        var response = this.execute(runContext, "GET", uri, null);

        var search = JiraUtil.parseJsonResponse(
            runContext,
            response,
            SearchResponse.class,
            new SearchResponse(null, null, null, List.of())
        );
        var issues = search.issues() == null ? List.<Map<String, Object>> of() : search.issues();

        var outputBuilder = Output.builder().size((long) issues.size());

        switch (rFetchType) {
            case STORE -> {
                var tempFile = runContext.workingDir().createTempFile(".json").toFile();
                JacksonMapper.ofJson().writeValue(tempFile, issues);
                outputBuilder.uri(runContext.storage().putFile(tempFile));
            }
            default -> outputBuilder.rows(issues);
        }

        return outputBuilder.build();
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
