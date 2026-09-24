package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;
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
}
