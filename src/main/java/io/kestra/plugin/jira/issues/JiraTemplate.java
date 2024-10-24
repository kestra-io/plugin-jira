package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class JiraTemplate extends JiraClient {

    @Schema(
        title = "Template to use",
        hidden = true
    )
    @PluginProperty(dynamic = true)
    protected String templateUri;

    @Schema(
        title = "Atlassian project's key"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String projectKey;

    @Schema(
        title = "Summary of the ticket"
    )
    @PluginProperty(dynamic = true)
    protected String summary;

    @Schema(
        title = "Description of the ticket to be created"
    )
    @PluginProperty(dynamic = true)
    protected String description;

    @Schema(
        title = "Labels associated with opened ticket"
    )
    @PluginProperty(dynamic = true)
    protected List<String> labels;

    @Schema(
        title = "Issue type of the Jira ticket"
    )
    @PluginProperty(dynamic = true)
    protected String issuetype;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        if (payload != null && !payload.isBlank()) {
            return super.run(runContext);
        }

            issuetype = runContext.render(issuetype) == null ? "Task" : runContext.render(issuetype);

            Map<String, Object> mainMap = new HashMap<>();
            Map<String, Object> renderedAttributesMap = Map.of(
                "projectKey", runContext.render(projectKey),
                "summary", runContext.render(summary),
                "labels", runContext.render(labels),
                "description", runContext.render(description),
                "issuetype", runContext.render(issuetype)
            );

            mainMap.put("fields", renderedAttributesMap);
            if (this.templateUri != null) {
                String template = IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
                    Charsets.UTF_8
                );
                String render = runContext.render(template, renderedAttributesMap);
                mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

            }
        this.payload = JacksonMapper.ofJson().writeValueAsString(mainMap);
        return super.run(runContext);
    }
}
