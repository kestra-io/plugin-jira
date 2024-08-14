package io.kestra.plugin.jira.issues;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.IOUtils;

import java.util.Map;
import java.util.Objects;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Update a Jira field",
    description = "Updates a specific field in a Jira ticket."
)
@Plugin(
    examples = {
        @Example(
            title = "Update a Jira ticket field",
            full = true,
            code = """
                id: jira_update_field
                namespace: company.myteam

                tasks:
                  - id: update_ticket_field
                    type: io.kestra.plugin.jira.issues.UpdateFields
                    baseUrl: your-domain.atlassian.net
                    username: your_email@example.com
                    passwordOrToken: "{{ secret('your_jira_api_token') }}"
                    issueIdOrKey: YOUR_ISSUE_KEY
                    fields:
                      description: "Updated description of: {{ execution.id }}"
                      customfield_10005: "Updated value"
              """
        )
    }
)
public class UpdateFields extends JiraTemplate {

    private final static ObjectMapper mapper = JacksonMapper.ofJson();

    @Schema(
        title = "Jira ticket key."
    )
    @NotBlank
    @PluginProperty(dynamic = true)
    private String issueIdOrKey;

    @Schema(
        title = "Fields map of names and new values."
    )
    @NotNull
    @PluginProperty(dynamic = true)
    private Map<String, Object> fields;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "update-field-template.peb";
        this.baseUrl += JiraUtil.ISSUE_API_ROUTE + runContext.render(this.issueIdOrKey);

        String template = IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
            Charsets.UTF_8
        );

        String render = runContext.render(
            template, Map.of("fields", runContext.render(this.fields))
        );

        Map<String, Object> body = mapper.readValue(render, new TypeReference<>() {});

        this.payload = mapper.writeValueAsString(body);
        return super.run(runContext);
    }

}