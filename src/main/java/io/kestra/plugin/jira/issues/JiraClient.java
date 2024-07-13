package io.kestra.plugin.jira.issues;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import java.net.URI;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a jira ticket on a task failure.",
    description = "Add this task to a list of `errors` tasks to implement custom flow-level failure notifications."
)
@Plugin(
    examples = {
        @Example(
            title = "Create a jira ticket on a failed flow execution",
            full = true,
            code = """
                id: unreliable_flow
                namespace: company.team

                tasks:
                  - id: fail
                    type: io.kestra.plugin.scripts.shell.Commands
                    runner: PROCESS
                    commands:
                      - exit 1

                errors:
                  - id: alert_on_failure
                    type: io.kestra.plugin.jira.issues.JiraClient
                    baseUrl: your-domain.atlassian.net
                    username: your_email@example.com
                    password: your_jira_api_token
                    payload: |
                        {
                             'fields': {
                                 'project': {
                                     'key': PROJECT_KEY
                                 },
                                 'summary': ISSUE_SUMMARY,
                                 'description': ISSUE_DESCRIPTION,
                                 'issuetype': {
                                     'name': 'Story'  # Or 'Bug', 'Story', etc.
                                 },
                                 'labels': ISSUE_LABELS
                             }
                         }
                    username: your_email@example.com
                    password: your_jira_api_token
                """
        ),
        @Example(
            title = "Create a jira ticket",
            full = true,
            code = """
                id: myflow
                namespace: company.myteam

                tasks:
                  - id: hello
                    type: io.kestra.plugin.jira.issues.JiraClient
                    baseUrl: your-domain.atlassian.net
                    payload: |
                        {
                             'fields': {
                                 'project': {
                                     'key': PROJECT_KEY
                                 },
                                 'summary': ISSUE_SUMMARY,
                                 'description': ISSUE_DESCRIPTION,
                                 'issuetype': {
                                     'name': 'Story'  # Or 'Bug', 'Story', etc.
                                 },
                                 'labels': ISSUE_LABELS
                             }
                         }
                    username: your_email@example.com
                    password: "{{ secret('your_jira_api_token') }}"
                 """
        ),
    }
)
public class JiraClient extends Task implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Atlassian URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String baseUrl;

    @Schema(
        title = "Atlassian Username"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String username;

    @Schema(
        title = "Atlassian password"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String password;

    @Schema(
        title = ""
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String baseUrl = runContext.render(this.baseUrl);
        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(baseUrl))) {
            String payload = runContext.render(this.payload);

            client
                .toBlocking()
                .exchange(
                    HttpRequest
                        .POST(baseUrl, payload)
                        .basicAuth(runContext.render(this.username), runContext.render(this.password))
                );
        }
        return null;
    }
}