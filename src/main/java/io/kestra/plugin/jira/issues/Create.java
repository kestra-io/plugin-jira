package io.kestra.plugin.jira.issues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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

    @Schema(
        title = "Project key",
        description = "Jira project key injected into the payload."
    )
    @PluginProperty(dynamic = true, group = "destination")
    @NotBlank
    protected String projectKey;

    @Schema(
        title = "Issue summary",
        description = "Rendered summary for the issue; templating supported."
    )
    @PluginProperty(group = "main")
    protected Property<String> summary;

    @Schema(
        title = "Issue description",
        description = "Rendered description text for the issue; templating supported."
    )
    @PluginProperty(dynamic = true, group = "main")
    protected String description;

    @Schema(
        title = "Labels",
        description = "Rendered list of labels added to the issue; `kestra-bot` is always included."
    )
    @PluginProperty(group = "advanced")
    protected Property<List<String>> labels;

    @Schema(
        title = "Issue type ID",
        description = "Optional Jira issue type ID; discover available values at `https://your-domain.atlassian.net/rest/api/2/issue/createmeta.`"
    )
    @PluginProperty(group = "advanced")
    protected Property<String> issueTypeId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rBrowseRoot = this.browseRoot(runContext);
        var response = this.sendTemplated(runContext, "POST", rBrowseRoot + ISSUE_API_ROUTE);

        var createdIssue = JiraUtil.parseJsonResponse(runContext, response, CreatedIssue.class, new CreatedIssue(null, null, null));
        JiraUtil.requireField(response, createdIssue.key(), "an issue key");

        return Output.builder()
            .id(createdIssue.id())
            .key(createdIssue.key())
            .self(createdIssue.self())
            .url(rBrowseRoot + BROWSE_ROUTE + JiraUtil.encodePathSegment(createdIssue.key()))
            .build();
    }

    /**
     * Builds the request body — either the explicit {@code payload} override, or a JSON "fields" map
     * assembled directly from the rendered project/summary/description/labels/issue-type properties —
     * and sends it to {@code uri}.
     *
     * <p>
     * The map is built in Java rather than interpolated into a Pebble-rendered JSON template: optional
     * fields (summary, description) can then simply be omitted instead of resolving to an undefined
     * Pebble variable, and Jackson correctly escapes values containing quotes or newlines.
     */
    private HttpResponse<String> sendTemplated(RunContext runContext, String method, String uri) throws Exception {
        var renderedPayload = runContext.render(this.payload).as(String.class);
        if (renderedPayload.isPresent() && !renderedPayload.get().isBlank()) {
            return this.execute(runContext, method, uri, renderedPayload.get());
        }

        var fields = new LinkedHashMap<String, Object>();
        fields.put("project", Map.of("key", runContext.render(this.projectKey)));

        runContext.render(this.summary).as(String.class).ifPresent(s -> fields.put("summary", s));

        var rDescription = runContext.render(this.description);
        if (rDescription != null && !rDescription.isBlank()) {
            fields.put("description", rDescription);
        }

        runContext.render(this.issueTypeId).as(String.class).ifPresent(id -> fields.put("issuetype", Map.of("id", id)));

        var labels = new ArrayList<>(List.of("kestra-bot"));
        labels.addAll(runContext.render(this.labels).asList(String.class));
        fields.put("labels", labels);

        var payloadRendered = JacksonMapper.ofJson().writeValueAsString(Map.of("fields", fields));
        return this.execute(runContext, method, uri, payloadRendered);
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
