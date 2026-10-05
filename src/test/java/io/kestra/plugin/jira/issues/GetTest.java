package io.kestra.plugin.jira.issues;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractJiraTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    @SuppressWarnings("unchecked")
    void fetchesSingleIssueByDefault() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getUri(), nullValue());
        assertThat(output.getRow().get("id"), is("10000"));
        assertThat(output.getRow().get("key"), is("TEST-1"));

        var fields = (Map<String, Object>) output.getRow().get("fields");
        assertThat(fields.get("summary"), is("Fix the login bug"));
        assertThat(((Map<String, Object>) fields.get("status")).get("name"), is("Open"));

        assertThat(mockController.requests, hasSize(1));
        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.method(), is("GET"));
        assertThat(request.path(), is("/rest/api/2/issue/TEST-1"));
        assertThat(request.authorization(), startsWith("Basic "));
    }

    @Test
    void fetchesIssuesMatchingAJqlQuery() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .jql("project = TEST")
            .maxResults(Property.ofValue(10))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getRows().get(0).get("key"), is("TEST-1"));
        assertThat(output.getRows().get(1).get("key"), is("TEST-2"));

        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.path(), is("/rest/api/2/search"));
        // The mock receives the query as Micronaut re-encodes it: spaces become '+', '=' stays literal.
        assertThat(request.query(), is("jql=project+=+TEST&maxResults=10"));
    }

    @Test
    void storesIssuesMatchingAJqlQueryInKestraStorage() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.STORE))
            .jql("project = TEST")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), notNullValue());
        assertThat(output.getRows(), nullValue());
    }

    @Test
    void supportsBaseUrlWithContextPathAndTrailingSlash() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/jira/")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        assertThat(mockController.requests.getFirst().path(), is("/jira/rest/api/2/issue/TEST-1"));
    }

    @Test
    void encodesIssueIdOrKeyPathSegment() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("OPS 123")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getRow().get("key"), is("TEST-1"));
        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/OPS%20123"));
    }

    @Test
    void usesBearerTokenWhenOnlyAccessTokenIsSet() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .accessToken(Property.ofValue("oauth-token"))
            .issueIdOrKey("TEST-1")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        assertThat(mockController.requests.getFirst().authorization(), is("Bearer oauth-token"));
    }

    @Test
    void failsWithClearMessageWhenIssueKeyMissingFrom2xxResponse() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/missing-key")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without an issue key"));
    }

    @Test
    void failsWithClearMessageOnEmptyOrNonJsonBody() {
        for (String marker : new String[] { "/empty-body", "/bad-json" }) {
            Get task = Get.builder()
                .id(IdUtils.create())
                .type(Get.class.getName())
                .baseUrl(getApiBaseUrl() + marker)
                .username(Property.ofValue("user@example.com"))
                .password(Property.ofValue("token"))
                .issueIdOrKey("TEST-1")
                .build();

            RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
            assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without an issue key"));
        }
    }

    @Test
    void failsWithClearMessageOnHttpError() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/http-error")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira request failed with HTTP 400"));
    }

    @Test
    void rejectsMissingIssueIdOrKeyForFetchOne() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("`issueIdOrKey` is required when `fetchType` is FETCH_ONE"));
    }

    @Test
    void rejectsMissingJqlForFetch() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("`jql` is required when `fetchType` is FETCH or STORE"));
    }
}
