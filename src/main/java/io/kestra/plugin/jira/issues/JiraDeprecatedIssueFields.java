package io.kestra.plugin.jira.issues;

import java.util.List;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Holds the issue-content fields that {@link CreateComment} and {@link UpdateFields} used to
 * inherit from {@link JiraTemplate} before they were scoped down to {@link Create}. Kept here,
 * deprecated and ignored at runtime, so that existing flow YAML setting them on these two tasks
 * keeps deserializing and running exactly as before. {@link Create} does not extend this class:
 * it declares its own, still-active copies of these fields.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class JiraDeprecatedIssueFields extends JiraTemplate {

    @Schema(
        title = "Project key",
        description = "Deprecated and ignored: this task addresses an existing issue directly and does not require a project key. This property has no effect on this task and will be removed in a future release."
    )
    @PluginProperty(dynamic = true, group = "destination")
    @Deprecated
    protected String projectKey;

    @Schema(
        title = "Issue summary",
        description = "Deprecated and ignored: this task does not create or edit an issue summary. This property has no effect on this task and will be removed in a future release."
    )
    @PluginProperty(group = "advanced")
    @Deprecated
    protected Property<String> summary;

    @Schema(
        title = "Issue description",
        description = "Deprecated and ignored: this task does not create or edit an issue description. This property has no effect on this task and will be removed in a future release."
    )
    @PluginProperty(dynamic = true, group = "advanced")
    @Deprecated
    protected String description;

    @Schema(
        title = "Labels",
        description = "Deprecated and ignored: this task does not create or edit issue labels. This property has no effect on this task and will be removed in a future release."
    )
    @PluginProperty(group = "advanced")
    @Deprecated
    protected Property<List<String>> labels;

    @Schema(
        title = "Issue type ID",
        description = "Deprecated and ignored: this task does not create or edit an issue type. This property has no effect on this task and will be removed in a future release."
    )
    @PluginProperty(group = "advanced")
    @Deprecated
    protected Property<String> issueTypeId;
}
