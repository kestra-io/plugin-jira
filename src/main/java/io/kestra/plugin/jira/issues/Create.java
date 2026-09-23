package io.kestra.plugin.jira.issues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.BROWSE_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a Jira issue",
    description = "Builds a JSON payload from the rendered project, summary, description, labels, and issue type, then posts it to `/rest/api/2/issue/`. `summary` and `description` are optional and omitted from the payload when not set; a `kestra-bot` label is always included."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a jira ticket on a failed flow execution using basic authentication.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.team

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                    issueTypeId: "10001"
                """
        ),
        @Example(
            title = "Create a jira ticket on a failed flow execution using OAUTH2 access token authentication.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: https://your-domain.atlassian.net
                    accessToken: "{{ secret('your_jira_access_token') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                    issueTypeId: "10001"
                """
        )
    }
)
public class Create extends JiraTemplate implements RunnableTask<Create.Output> {
    @Override
    public Output run(RunContext runContext) throws Exception {
        String rBrowseRoot = this.browseRoot(runContext);
        HttpResponse<String> response = this.sendTemplated(runContext, "POST", rBrowseRoot + ISSUE_API_ROUTE);

        CreatedIssue createdIssue = JiraUtil.parseJsonResponse(runContext, response, CreatedIssue.class, new CreatedIssue(null, null, null));
        JiraUtil.requireField(response, createdIssue.key(), "an issue key");

        return Output.builder()
            .id(createdIssue.id())
            .key(createdIssue.key())
            .self(createdIssue.self())
            .url(rBrowseRoot + BROWSE_ROUTE + createdIssue.key())
            .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CreatedIssue(String id, String key, String self) {
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Created issue ID", description = "Jira's internal numeric identifier for the issue.")
        private final String id;

        @Schema(title = "Created issue key", description = "Human-readable issue key such as `PROJ-123`; use it to reference the issue in downstream tasks.")
        private final String key;

        @Schema(title = "Issue browse URL", description = "Link to the issue in the Jira web UI, built as `{baseUrl}/browse/{key}`.")
        private final String url;

        @Schema(title = "Issue REST link", description = "Jira's `self` REST API link for the created issue.")
        private final String self;
    }
}
