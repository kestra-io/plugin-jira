package io.kestra.plugin.jira.comments;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.jira.issues.AbstractJiraTest;
import io.kestra.plugin.jira.issues.JiraMockController;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GetTest extends AbstractJiraTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void fetchesAllCommentsOfAnIssueByDefault() throws Exception {
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

        assertThat(output.getSize(), is(2L));
        assertThat(output.getUri(), nullValue());
        assertThat(output.getRows(), hasSize(2));
        assertThat(output.getRows().get(0).get("id"), is("20000"));
        assertThat(output.getRows().get(0).get("body"), is("First comment"));
        assertThat(output.getRows().get(1).get("id"), is("20001"));

        assertThat(mockController.requests, hasSize(1));
        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.method(), is("GET"));
        assertThat(request.path(), is("/rest/api/2/issue/TEST-1/comment"));
        assertThat(request.authorization(), startsWith("Basic "));
    }

    @Test
    void fetchesSingleCommentById() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .commentId("20000")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getSize(), is(1L));
        assertThat(output.getRow().get("id"), is("20000"));
        assertThat(output.getRow().get("body"), is("First comment"));
        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/TEST-1/comment/20000"));
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

        assertThat(mockController.requests.getFirst().path(), is("/jira/rest/api/2/issue/TEST-1/comment"));
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
        task.run(runContext);

        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/OPS%20123/comment"));
    }

    @Test
    void failsWithClearMessageWhenCommentIdMissingFrom2xxResponse() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/missing-key")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .commentId("20000")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without a comment id"));
    }

    @Test
    void returnsNoCommentsOnEmptyOrNonJsonBody() throws Exception {
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

            // An empty or non-JSON comments payload parses into no comments rather than failing the flow.
            Get.Output output = task.run(runContext);
            assertThat(output.getSize(), is(0L));
            assertThat(output.getRows(), hasSize(0));
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
    void rejectsMissingCommentIdForFetchOne() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey("TEST-1")
            .fetchType(Property.ofValue(FetchType.FETCH_ONE))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("`commentId` is required when `fetchType` is FETCH_ONE"));
    }
}
