package io.kestra.plugin.jira.issues;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    protected Property<String> username;

    @Schema(
        title = "Atlassian password or API token",
        description = "(Required for basic & API token authorization)"
    )
    protected Property<String> password;

    @Schema(
        title = "Atlassian OAuth access token",
        description = "(Required for OAuth authorization)"
    )
    protected Property<String> accessToken;

    @Schema(
        title = "Payload"
    )
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String baseUrlRendered = runContext.render(this.baseUrl);
        String payloadRendered = runContext.render(this.payload, String.class);

        try (DefaultHttpClient client = new DefaultHttpClient(URI.create(baseUrlRendered))) {
            MutableHttpRequest<String> request = getAuthorizedRequest(runContext, baseUrlRendered, payloadRendered);

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
                runContext.render(this.username, String.class),
                runContext.render(this.password, String.class)
            );
        }

        if (this.accessToken != null) {
            return request.bearerAuth(
                runContext.render(this.accessToken, String.class)
            );
        }

        throw new IllegalArgumentException("Missing required authentication fields");
    }

}