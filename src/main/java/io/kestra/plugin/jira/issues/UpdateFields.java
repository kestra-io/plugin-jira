package io.kestra.plugin.jira.issues;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.BROWSE_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Update fields on a Jira issue",
    description = "Sends a PUT to Jira's issue API to change selected fields, per the Jira REST v2 `PUT /rest/api/2/issue/{issueIdOrKey}` edit-issue endpoint. Renders `issueIdOrKey` and `fields` with flow variables, then serializes values via `update-field-template.peb` (nested objects are not supported). Requires Jira authentication (Basic or OAuth) configured on the task."
)
@Plugin(
    examples = {
        @Example(
            title = "Update a Jira ticket fields",
            full = true,
            code = """
                id: jira_update_field
                namespace: company.myteam

                tasks:
                  - id: update_ticket_field
                    type: io.kestra.plugin.jira.issues.UpdateFields
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    issueIdOrKey: YOUR_ISSUE_KEY
                    fields:
                      description: "Updated description of: {{ execution.id }}"
                      customfield_10005: "Updated value"
                """
        )
    }
)
public class UpdateFields extends JiraTemplate implements RunnableTask<UpdateFields.Output> {

    private final static ObjectMapper mapper = JacksonMapper.ofJson();

    @Schema(
        title = "Issue key or id to update",
        description = "Rendered value appended to `/rest/api/2/issue/` before sending the request."
    )
    @PluginProperty(dynamic = true, group = "main")
    @NotBlank
    private String issueIdOrKey;

    @Schema(
        title = "Field names and new values",
        description = "Rendered map of field keys to updated values; entries are stringified by the template, so use simple scalar values."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> fields;

    @Override
    public Output run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("update-field-template.peb");

        var rIssueIdOrKey = runContext.render(this.issueIdOrKey);
        var encodedIssueIdOrKey = JiraUtil.encodePathSegment(rIssueIdOrKey);
        var rBrowseRoot = this.browseRoot(runContext);
        var uri = rBrowseRoot + ISSUE_API_ROUTE + encodedIssueIdOrKey;

        var templateUri = runContext.render(this.templateUri)
            .as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid templateUri: " + this.templateUri));

        var template = IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(templateUri)),
            StandardCharsets.UTF_8
        );

        var render = runContext.render(
            template, Map.of("fields", runContext.render(this.fields).asMap(String.class, Object.class))
        );

        Map<String, Object> body = mapper.readValue(render, new TypeReference<>() {
        });

        // Jira REST v2 only accepts PUT on /rest/api/2/issue/{issueIdOrKey} for editing an issue; POST is rejected.
        this.execute(runContext, "PUT", uri, mapper.writeValueAsString(body));

        return Output.builder()
            .issueIdOrKey(rIssueIdOrKey)
            .url(rBrowseRoot + BROWSE_ROUTE + encodedIssueIdOrKey)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Updated issue key or id", description = "The rendered `issueIdOrKey` that was updated.")
        private final String issueIdOrKey;

        @Schema(title = "Issue browse URL", description = "Link to the updated issue in the Jira web UI, built as `{baseUrl}/browse/{issueIdOrKey}`.")
        private final String url;
    }
}
