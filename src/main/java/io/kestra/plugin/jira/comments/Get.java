package io.kestra.plugin.jira.comments;

import java.net.URI;
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
import io.kestra.plugin.jira.issues.JiraClient;
import io.kestra.plugin.jira.issues.JiraUtil;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.COMMENT_API_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get Jira comments",
    description = "Fetch a single comment by id, or all comments of a Jira issue, from Jira's REST v2 API. Uses the same authentication fields as other Jira tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Fetch all comments of a Jira issue and log the latest one.",
            full = true,
            code = """
                id: jira_get_comments
                namespace: company.myteam

                tasks:
                  - id: get_comments
                    type: io.kestra.plugin.jira.comments.Get
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: "TID-53"
                    fetchType: FETCH

                  - id: log_latest_comment
                    type: io.kestra.plugin.core.log.Log
                    message: "{{ outputs.get_comments.rows | last }}"
                """
        ),
        @Example(
            title = "Fetch a single Jira comment by id.",
            full = true,
            code = """
                id: jira_get_comment
                namespace: company.myteam

                tasks:
                  - id: get_comment
                    type: io.kestra.plugin.jira.comments.Get
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: "TID-53"
                    commentId: "10100"
                    fetchType: FETCH_ONE
                """
        )
    }
)
public class Get extends JiraClient implements RunnableTask<Get.Output> {

    @Schema(
        title = "How fetched comments are exposed",
        description = "`FETCH_ONE` returns the comment addressed by `commentId` as `row`; `FETCH` returns the comments of the issue addressed by `issueIdOrKey` as `rows`; `STORE` stores those comments in Kestra storage and exposes the storage URI."
    )
    @Builder.Default
    @PluginProperty(group = "processing")
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Schema(
        title = "Issue key or id owning the comments",
        description = "Rendered value appended to `/rest/api/2/issue/` before the comment route."
    )
    @PluginProperty(dynamic = true, group = "main")
    @NotBlank
    protected String issueIdOrKey;

    @Schema(
        title = "Comment id",
        description = "Required for `fetchType: FETCH_ONE`; rendered value appended after the issue's `/comment` route."
    )
    @PluginProperty(dynamic = true, group = "main")
    private String commentId;

    @Schema(
        title = "Maximum number of comments returned by the issue comments route",
        description = "Only used for `fetchType: FETCH` or `STORE`."
    )
    @Builder.Default
    @PluginProperty(group = "advanced")
    private Property<Integer> maxResults = Property.ofValue(50);

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElseThrow();
        var rBrowseRoot = this.browseRoot(runContext);
        var rIssueIdOrKey = runContext.render(this.issueIdOrKey);
        var encodedIssueIdOrKey = JiraUtil.encodePathSegment(rIssueIdOrKey);
        var issueRoute = rBrowseRoot + ISSUE_API_ROUTE + encodedIssueIdOrKey + COMMENT_API_ROUTE;

        return switch (rFetchType) {
            case FETCH_ONE -> fetchOne(runContext, issueRoute);
            case FETCH, STORE -> fetchMany(runContext, issueRoute, rFetchType);
            case NONE -> Output.builder().size(0L).build();
        };
    }

    private Output fetchOne(RunContext runContext, String issueRoute) throws Exception {
        if (this.commentId == null || this.commentId.isBlank()) {
            throw new IllegalArgumentException("`commentId` is required when `fetchType` is FETCH_ONE");
        }

        var rCommentId = runContext.render(this.commentId);
        var uri = issueRoute + "/" + JiraUtil.encodePathSegment(rCommentId);
        var response = this.execute(runContext, "GET", uri, null);

        Map<String, Object> comment = JiraUtil.parseJsonResponse(runContext, response, Map.class, null);
        JiraUtil.requireField(response, comment == null ? null : comment.get("id"), "a comment id");

        return Output.builder()
            .row(comment)
            .size(1L)
            .build();
    }

    private Output fetchMany(RunContext runContext, String issueRoute, FetchType rFetchType) throws Exception {
        var rMaxResults = runContext.render(this.maxResults).as(Integer.class).orElse(50);
        var uri = issueRoute + "?maxResults=" + rMaxResults;
        var response = this.execute(runContext, "GET", uri, null);

        var commentsResponse = JiraUtil.parseJsonResponse(
            runContext,
            response,
            CommentsResponse.class,
            new CommentsResponse(null, null, null, List.of())
        );
        var comments = commentsResponse.comments() == null ? List.<Map<String, Object>> of() : commentsResponse.comments();

        var outputBuilder = Output.builder().size((long) comments.size());

        switch (rFetchType) {
            case STORE -> {
                var tempFile = runContext.workingDir().createTempFile(".json").toFile();
                JacksonMapper.ofJson().writeValue(tempFile, comments);
                outputBuilder.uri(runContext.storage().putFile(tempFile));
            }
            default -> outputBuilder.rows(comments);
        }

        return outputBuilder.build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentsResponse(Integer startAt, Integer maxResults, Integer total, List<Map<String, Object>> comments) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The fetched comment", description = "Only populated when `fetchType` is `FETCH_ONE`.")
        private final Map<String, Object> row;

        @Schema(title = "The list of fetched comments", description = "Only populated when `fetchType` is `FETCH`.")
        private final List<Map<String, Object>> rows;

        @Schema(
            title = "Kestra's internal storage URI of the stored comments",
            description = "Only populated when `fetchType` is `STORE`."
        )
        private final URI uri;

        @Schema(title = "The number of fetched comments")
        private final Long size;
    }
}
