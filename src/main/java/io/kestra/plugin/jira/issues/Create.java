package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a jira ticket based on workflow execution status",
    description = ""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a jira ticket on a failed flow execution",
            full = true,
            code = """
                id: myflow
                namespace: company.myteam

                tasks:
                  - id: hello
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('your_jira_api_token') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                """
        )
    }
)
public class Create extends JiraTemplate {
    @Builder.Default
    private final String executionId = "{{ execution.id }}";
    private Map<String, Object> customFields;
    private String customMessage;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "jira-template.peb";

        return super.run(runContext);
    }
}
