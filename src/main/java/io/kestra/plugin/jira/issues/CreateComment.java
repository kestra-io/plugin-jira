package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.util.Map;
import java.util.Objects;

import static io.kestra.plugin.jira.issues.JiraUtil.COMMENT_API_ROUTE;
import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Comment on a jira ticket based on workflow execution status.",
    description = ""
)
@Plugin(
    examples = {
        @Example(
            title = "Comment on a jira ticket on a failed flow execution.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_comment_on_a_ticket
                    type: io.kestra.plugin.jira.issues.CreateComment
                    baseUrl: your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('jira_api_token') }}"
                    projectKey: project_key
                    issueIdOrKey: "TID-53"
                    body: "This ticket is not moving, do we need to outsource this!"
                """
        )
    }
)
public class CreateComment extends JiraTemplate {
    @Schema(
        title = "Jira ticket key"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String issueIdOrKey;

    @Schema(
        title = "The comment's content"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String body;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "comment-jira-template.peb";
        this.baseUrl += ISSUE_API_ROUTE + runContext.render(this.issueIdOrKey) + COMMENT_API_ROUTE;

        String template = IOUtils.toString(
            Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(this.templateUri)),
            Charsets.UTF_8
        );

        String render = runContext.render(template, Map.of("body", runContext.render(body)));

        Map<String, Object> mainMap = (Map<String, Object>) JacksonMapper.ofJson().readValue(render, Object.class);

        this.payload = JacksonMapper.ofJson().writeValueAsString(mainMap);
        return super.run(runContext);
    }
}
