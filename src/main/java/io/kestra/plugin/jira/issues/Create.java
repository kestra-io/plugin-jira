package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static io.kestra.plugin.jira.issues.JiraUtil.ISSUE_API_ROUTE;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a jira ticket based on workflow execution status."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a jira ticket on a failed flow execution using basic authentication.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: your-domain.atlassian.net
                    username: your_email@example.com
                    passwordOrToken: "{{ secret('your_jira_api_token') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                """
        ),
        @Example(
            title = "Create a jira ticket on a failed flow execution using OAUTH2 access token authentication.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.myteam

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: your-domain.atlassian.net
                    accessToken: "{{ secret('your_jira_access_token') }}"
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
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = "jira-template.peb";
        this.baseUrl += ISSUE_API_ROUTE;

        return super.run(runContext);
    }
}
