package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.jira.issues.annotations.NotBlankProperty;
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
    protected Property<String> templateUri;

    @Schema(
        title = "Atlassian project's key"
    )
    @NotBlankProperty
    protected Property<String> projectKey;

    @Schema(
        title = "Summary of the ticket"
    )
    protected Property<String> summary;

    @Schema(
        title = "Description of the ticket to be created"
    )
    @PluginProperty(dynamic = true)
    protected String description;

    @Schema(
        title = "Labels associated with opened ticket"
    )
    protected Property<List<String>> labels;

    @Schema(
        title = "Issue type of the Jira ticket",
        description = "Examples: Story, Task, Bug (default value is Task)"
    )
    @Builder.Default
    protected Property<String> issuetype = Property.of("Task");

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        final String payloadRendered = runContext.render(this.payload, String.class);
        if (payloadRendered != null && !payloadRendered.isBlank()) {
            return super.run(runContext);
        }

            Map<String, Object> mainMap = new HashMap<>();
            Map<String, Object> renderedAttributesMap = Map.of(
                "projectKey", runContext.render(projectKey, String.class),
                "summary", runContext.render(summary, String.class),
                "labels", this.labels.asList(runContext, String.class),
                "description", runContext.render(description),
                "issuetype", runContext.render(issuetype, String.class)
            );

            mainMap.put("fields", renderedAttributesMap);
            if (this.templateUri != null) {
                String template = IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(runContext.render(this.templateUri, String.class))),
                    Charsets.UTF_8
                );
                String render = runContext.render(template, renderedAttributesMap);
                mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

            }
        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(mainMap));
        return super.run(runContext);
    }
}
