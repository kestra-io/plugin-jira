package io.kestra.plugin.jira.issues;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

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
    description = "Builds a JSON payload from `jira-template.peb` and posts to `/rest/api/2/issue/`. Renders project, summary, description, labels, and issue type with flow variables; template always adds a `kestra-bot` label."
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
        this.templateUri = Property.ofValue("jira-template.peb");

        String rBrowseRoot = this.browseRoot(runContext);
        HttpResponse<String> response = this.sendTemplated(runContext, "POST", rBrowseRoot + ISSUE_API_ROUTE);

        CreatedIssue createdIssue = parseResponse(runContext, response);
        if (createdIssue.key() == null) {
            throw new IllegalStateException(
                "Jira returned HTTP " + response.getStatus().getCode() + " without an issue key; response: " + JiraUtil.truncate(response.getBody())
            );
        }

        return Output.builder()
            .id(createdIssue.id())
            .key(createdIssue.key())
            .self(createdIssue.self())
            .url(rBrowseRoot + BROWSE_ROUTE + createdIssue.key())
            .build();
    }

    private static CreatedIssue parseResponse(RunContext runContext, HttpResponse<String> response) {
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            runContext.logger().debug("Jira returned an empty body for the create-issue response (status {})", response.getStatus());
            return new CreatedIssue(null, null, null);
        }

        try {
            return JacksonMapper.ofJson().readValue(body, CreatedIssue.class);
        } catch (JsonProcessingException e) {
            runContext.logger().debug("Could not parse the Jira create-issue response body: {}", e.getMessage());
            return new CreatedIssue(null, null, null);
        }
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
