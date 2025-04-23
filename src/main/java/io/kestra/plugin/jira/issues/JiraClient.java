package io.kestra.plugin.jira.issues;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    @Schema(title = "The HTTP client configuration.")
    HttpConfiguration options;

    public VoidOutput run(RunContext runContext) throws Exception {

        try (HttpClient client = new HttpClient(runContext, options)) {

            HttpRequest request = getAuthorizedRequest(runContext);

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            return null;
        }
    }

    private HttpRequest getAuthorizedRequest(
        RunContext runContext
    ) throws IllegalVariableEvaluationException {

        String baseUrlRendered = runContext.render(this.baseUrl);
        String payloadRendered = runContext.render(this.payload).as(String.class).orElse(null);

        runContext.logger().debug("Executing request with payload: {}", payloadRendered);

        var renderedUsername = runContext.render(this.username).as(String.class);
        var renderedPassword = runContext.render(this.password).as(String.class);

        HttpRequest.HttpRequestBuilder request = HttpRequest.builder()
            .uri(URI.create(baseUrlRendered))
            .method("POST")
            .body(HttpRequest.StringRequestBody.builder().content(payloadRendered).build())
            .addHeader("Content-Type", "application/json");

        if (renderedUsername.isPresent() && renderedPassword.isPresent()) {
            String authHeader = Base64.getEncoder().encodeToString(
                (renderedUsername.get() + ":" + renderedPassword.get()).getBytes(StandardCharsets.UTF_8)
            );
            return request.addHeader("Authorization", "Basic " + authHeader).build();
        }

        var accessTokenRendered = runContext.render(this.accessToken).as(String.class);

        if (accessTokenRendered.isPresent()) {
            return request.addHeader("Authorization", "Bearer " + accessTokenRendered.get()).build();
        }

        throw new IllegalArgumentException("Missing required authentication fields");
    }
}