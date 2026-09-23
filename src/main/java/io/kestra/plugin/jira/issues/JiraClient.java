package io.kestra.plugin.jira.issues;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class JiraClient extends Task {

    @Schema(
        title = "Jira REST base URL",
        description = "Rendered HTTPS root such as `https://your-domain.atlassian.net`; the task appends the REST route before sending the request. A trailing slash is tolerated and stripped."
    )
    @PluginProperty(dynamic = true, group = "connection")
    @NotBlank
    protected String baseUrl;

    @Schema(
        title = "Jira username or email",
        description = "Used with `password` for Basic/API token authentication; ignored when an `accessToken` is provided."
    )
    @PluginProperty(secret = true, group = "connection")
    @ToString.Exclude
    protected Property<String> username;

    @Schema(
        title = "Jira password or API token",
        description = "Used with `username` for Basic/API token authentication; ignored when an `accessToken` is provided."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> password;

    @Schema(
        title = "Jira OAuth access token",
        description = "Bearer token for OAuth; used only when `username`/`password` are not both set."
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    protected Property<String> accessToken;

    @Schema(
        title = "Prepared JSON payload",
        description = "Rendered body sent as `application/json`; usually built from a template when not explicitly provided."
    )
    @PluginProperty(group = "advanced")
    protected Property<String> payload;

    @Schema(title = "HTTP client configuration")
    HttpConfiguration options;

    /**
     * Renders {@code baseUrl} and strips a trailing slash so it can be reused both as the REST route
     * prefix and as the browse-URL root, without ever mutating the {@code baseUrl} field itself.
     */
    protected String browseRoot(RunContext runContext) throws IllegalVariableEvaluationException {
        String rBaseUrl = runContext.render(this.baseUrl);
        return rBaseUrl.endsWith("/") ? rBaseUrl.substring(0, rBaseUrl.length() - 1) : rBaseUrl;
    }

    protected HttpResponse<String> execute(RunContext runContext, String method, String uri, String payload) throws IllegalVariableEvaluationException, HttpClientException, IOException {
        try (HttpClient client = new HttpClient(runContext, this.options)) {
            HttpRequest request = authorizedRequest(runContext, method, uri, payload);

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response status: {}", response.getStatus());

            return response;
        } catch (HttpClientResponseException e) {
            HttpResponse<?> failedResponse = e.getResponse();
            if (failedResponse == null) {
                throw e;
            }

            int statusCode = failedResponse.getStatus() != null ? failedResponse.getStatus().getCode() : -1;
            throw new HttpClientResponseException(
                "Jira request failed with HTTP " + statusCode + "; response: " + JiraUtil.truncate(JiraUtil.bodyAsString(failedResponse)),
                failedResponse,
                e
            );
        }
    }

    private HttpRequest authorizedRequest(RunContext runContext, String method, String uri, String payload) throws IllegalVariableEvaluationException {
        runContext.logger().debug("Executing {} request to '{}'", method, uri);

        var renderedUsername = runContext.render(this.username).as(String.class);
        var renderedPassword = runContext.render(this.password).as(String.class);

        HttpRequest.HttpRequestBuilder request = HttpRequest.builder()
            .uri(URI.create(uri))
            .method(method)
            .body(HttpRequest.StringRequestBody.builder().content(payload).build())
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
