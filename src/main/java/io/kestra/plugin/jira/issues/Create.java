package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.property.Property;
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
    title = "Create a Jira issue",
    description = "Builds a JSON payload from `jira-template.peb` and posts to `/rest/api/2/issue/`. Renders project, summary, description, labels, and issue type with flow variables; template always adds a `kestra-bot` label."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a jira ticket on a failed flow execution using basic authentication.",
            full = true,
            code = """
                id: jira_flow
                namespace: company.team

                tasks:
                  - id: create_issue
                    type: io.kestra.plugin.jira.issues.Create
                    baseUrl: https://your-domain.atlassian.net
                    username: your_email@example.com
                    password: "{{ secret('JIRA_API_TOKEN') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                    issueTypeId: "10001"
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
                    baseUrl: https://your-domain.atlassian.net
                    accessToken: "{{ secret('your_jira_access_token') }}"
                    projectKey: myproject
                    summary: "Workflow failed"
                    description: "{{ execution.id }} has failed on {{ taskrun.startDate }} See the link below for more details"
                    labels:
                      - bug
                      - workflow
                    issueTypeId: "10001"
                """
        )
    }
)
public class Create extends JiraTemplate {
    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        this.templateUri = Property.ofValue("jira-template.peb");
        this.baseUrl = this.baseUrl + ISSUE_API_ROUTE;

        return super.run(runContext);
    }
}
