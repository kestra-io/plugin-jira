package io.kestra.plugin.jira.issues;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
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
public abstract class JiraClient extends Task implements RunnableTask<VoidOutput> {

    @Schema(
        title = "Atlassian URL"
    )
    @PluginProperty(dynamic = true)
    @NotBlank
    protected String baseUrl;

    @Schema(
        title = "Atlassian Username",
        description = "(Required for basic & API token authorization)"
    )
    @PluginProperty(dynamic = true)
    protected String username;

    @Schema(
        title = "Atlassian password or API token",
        description = "(Required for basic & API token authorization)"
    )
    @PluginProperty(dynamic = true)
    protected String password;

    @Schema(
        title = "Atlassian OAuth access token",
        description = "(Required for OAuth authorization)"
    )
    @PluginProperty(dynamic = true)
    protected String accessToken;

    @Schema(
        title = ""
    )
    @PluginProperty(dynamic = true)
    protected String payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String baseUrl = runContext.render(this.baseUrl);
        String payload = runContext.render(this.payload);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(baseUrl))) {
            MutableHttpRequest<String> request = getAuthorizedRequest(runContext, baseUrl, payload);

            client.toBlocking().exchange(request);
        }

        return null;
    }

    private MutableHttpRequest<String> getAuthorizedRequest(
        RunContext runContext,
        String baseUrl,
        String payload
    ) throws IllegalVariableEvaluationException {
        MutableHttpRequest<String> request = HttpRequest.POST(baseUrl, payload);

        if (this.username != null && password != null) {
            return request.basicAuth(
                runContext.render(this.username),
                runContext.render(this.password)
            );
        }

        if (this.accessToken != null) {
            return request.bearerAuth(
                runContext.render(this.accessToken)
            );
        }

        throw new IllegalArgumentException("Missing required authentication fields");
    }

}