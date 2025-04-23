package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;
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
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String projectKey;

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
        title = "Issue type ID of the Jira ticket",
        description = "The issue type ID can be found using this URL : https://your-domain.atlassian.net/rest/api/2/issue/createmeta."
    )
    protected Property<String> issueTypeId;

    @SuppressWarnings("unchecked")
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var renderedPayload = runContext.render(this.payload).as(String.class);
        if (renderedPayload.isPresent() && !renderedPayload.get().isBlank()) {
            return super.run(runContext);
        }

            Map<String, Object> mainMap = new HashMap<>();
            Map<String, Object> renderedAttributesMap = new HashMap<>(Map.of("projectKey", runContext.render(projectKey)));
            runContext.render(this.summary).as(String.class).ifPresent(s -> renderedAttributesMap.put("summary", s));

            var renderedLabels = runContext.render(this.labels).asList(String.class);
            if (!renderedLabels.isEmpty()) { renderedAttributesMap.put("labels", renderedLabels); }
            if (runContext.render(description) != null) { renderedAttributesMap.put("description", runContext.render(description)); }
            runContext.render(this.issueTypeId).as(String.class).ifPresent(s -> renderedAttributesMap.put("issueTypeId", s));

            mainMap.put("fields", renderedAttributesMap);
            var renderedTemplateUri = runContext.render(this.templateUri).as(String.class);
            if (renderedTemplateUri.isPresent()) {
                String template = IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(renderedTemplateUri.get())),
                    StandardCharsets.UTF_8
                );
                String render = runContext.render(template, renderedAttributesMap);
                mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

            }
        this.payload = Property.of(JacksonMapper.ofJson().writeValueAsString(mainMap));
        return super.run(runContext);
    }
}
