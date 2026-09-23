package io.kestra.plugin.jira.issues;

import java.util.*;

import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class JiraTemplate extends JiraClient {

    @Schema(
        title = "Template resource path",
        hidden = true
    )
    @PluginProperty(group = "advanced")
    protected Property<String> templateUri;

    @Schema(
        title = "Project key",
        description = "Jira project key injected into the payload."
    )
    @PluginProperty(dynamic = true, group = "connection")
    @NotBlank
    protected String projectKey;

    @Schema(
        title = "Issue summary",
        description = "Rendered summary for the issue; templating supported."
    )
    @PluginProperty(group = "advanced")
    protected Property<String> summary;

    @Schema(
        title = "Issue description",
        description = "Rendered description text for the issue; templating supported."
    )
    @PluginProperty(dynamic = true, group = "advanced")
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
    protected HttpResponse<String> sendTemplated(RunContext runContext, String method, String uri) throws Exception {
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
}
