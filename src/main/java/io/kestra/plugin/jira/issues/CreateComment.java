package io.kestra.plugin.jira.issues;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.BROWSE_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.COMMENT_API_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Add a comment to a Jira issue",
    description = "Renders the issue key and comment body, fills `comment-jira-template.peb`, then posts to `/rest/api/2/issue/{issueIdOrKey}/comment`. Uses the same authentication fields as other Jira tasks."
)
@Plugin(
    examples = {
        @Example(
            title = "Comment on a jira ticket on a failed flow execution.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_comment_on_a_ticket
                    type: io.kestra.plugin.jira.issues.CreateComment
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: "TID-53"
                    body: "This ticket is not moving, do we need to outsource this!"
                """
        ),
        @Example(
            title = "Create a Jira issue, then comment on it in the same flow using its output key.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }}"
                    issueTypeId: "10001"

                  - id: log_issue_url
                    type: io.kestra.plugin.core.log.Log
                    message: "Created {{ outputs.create_issue.key }} — {{ outputs.create_issue.url }}"

                  - id: create_comment_on_a_ticket
                    type: io.kestra.plugin.jira.issues.CreateComment
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: "{{ outputs.create_issue.key }}"
                    body: "Linked automatically from the same flow run."
                """
        )
    }
)
public class CreateComment extends JiraTemplate implements RunnableTask<CreateComment.Output> {
    @Schema(
        title = "Project key",
        description = "Deprecated and ignored: comments are addressed to `issueIdOrKey` directly and do not require a project key. This property has no effect and will be removed in a future release."
    )
    @PluginProperty(dynamic = true, group = "destination")
    @Deprecated
    protected String projectKey;

    @Schema(
        title = "Issue key or id to comment",
        description = "Rendered value appended to `/rest/api/2/issue/` before `/comment`."
    )
    @PluginProperty(dynamic = true, group = "main")
    @NotBlank
    protected String issueIdOrKey;

    @Schema(
        title = "Comment text",
        description = "Rendered markdown or text inserted as `body` via `comment-jira-template.peb`."
    )
    @PluginProperty(dynamic = true, group = "main")
    @NotBlank
    protected String body;

    @SuppressWarnings("unchecked")
    @Override
    public Output run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("comment-jira-template.peb");

        var rIssueIdOrKey = runContext.render(this.issueIdOrKey);
        var encodedIssueIdOrKey = JiraUtil.encodePathSegment(rIssueIdOrKey);
        var rBrowseRoot = this.browseRoot(runContext);
        var uri = rBrowseRoot + ISSUE_API_ROUTE + encodedIssueIdOrKey + COMMENT_API_ROUTE;

        var template = IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(runContext.render(this.templateUri).as(String.class).orElse(null))),
            StandardCharsets.UTF_8
        );

        var render = runContext.render(template, Map.of("body", runContext.render(body)));

        var mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

        var response = this.execute(runContext, "POST", uri, JacksonMapper.ofJson().writeValueAsString(mainMap));

        var createdComment = JiraUtil.parseJsonResponse(runContext, response, CreatedComment.class, new CreatedComment(null, null));
        JiraUtil.requireField(response, createdComment.id(), "a comment id");

        return Output.builder()
            .id(createdComment.id())
            .issueIdOrKey(rIssueIdOrKey)
            .self(createdComment.self())
            .url(rBrowseRoot + BROWSE_ROUTE + encodedIssueIdOrKey + "?focusedCommentId=" + createdComment.id())
            .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedComment(String id, String self) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created comment ID", description = "Jira's internal identifier for the created comment.")
        private final String id;

        @Schema(title = "Commented issue key or id", description = "The rendered `issueIdOrKey` the comment was added to.")
        private final String issueIdOrKey;

        @Schema(
            title = "Comment browse URL", description = "Link to the issue in the Jira web UI with the new comment focused, built as `{baseUrl}/browse/{issueIdOrKey}?focusedCommentId={id}`."
        )
        private final String url;

        @Schema(title = "Comment REST link", description = "Jira's `self` REST API link for the created comment.")
        private final String self;
    }
}
