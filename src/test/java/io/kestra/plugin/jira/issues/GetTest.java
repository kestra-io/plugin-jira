package io.kestra.plugin.jira.issues;

import java.util.List;
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
import static org.hamcrest.Matchers.not;
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
            .issueIdOrKey(Property.ofValue("TEST-1"))
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
        var request = mockController.requests.getFirst();
        assertThat(request.method(), is("GET"));
        assertThat(request.path(), is("/rest/api/2/issue/TEST-1"));
        assertThat(request.authorization(), startsWith("Basic "));
    }

    @Test
    void fetchesIssuesMatchingAJqlQueryWithTheEnhancedSearchApi() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .jql(Property.ofValue("project = TEST"))
            .maxResults(Property.ofValue(10))
            .fields(Property.ofValue(List.of("summary", "status")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        // The mock spreads three matches over two token-paginated pages; both must be fetched.
        assertThat(output.getSize(), is(3L));
        assertThat(output.getRows().getFirst().get("key"), is("TEST-1"));
        assertThat(output.getRows().get(1).get("key"), is("TEST-2"));
        assertThat(output.getRows().get(2).get("key"), is("TEST-3"));

        assertThat(mockController.requests, hasSize(2));
        var firstRequest = mockController.requests.getFirst();
        assertThat(firstRequest.method(), is("POST"));
        assertThat(firstRequest.path(), is("/rest/api/3/search/jql"));
        // The enhanced search endpoint returns only ids by default, so the requested fields are sent explicitly.
        assertThat(firstRequest.body(), containsString("\"jql\":\"project = TEST\""));
        assertThat(firstRequest.body(), containsString("\"maxResults\":10"));
        assertThat(firstRequest.body(), containsString("\"fields\":[\"summary\",\"status\"]"));
        assertThat(firstRequest.body(), not(containsString("nextPageToken")));

        var secondRequest = mockController.requests.get(1);
        assertThat(secondRequest.body(), containsString("\"nextPageToken\":\"page-2\""));
    }

    @Test
    void fetchesIssuesMatchingAJqlQueryWithTheServerSearchApi() throws Exception {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .searchApi(Property.ofValue(Get.SearchApi.SERVER))
            .jql(Property.ofValue("project = TEST"))
            .maxResults(Property.ofValue(10))
            .fields(Property.ofValue(List.of("summary", "status")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        // The mock spreads three matches over two startAt-paginated pages; both must be fetched.
        assertThat(output.getSize(), is(3L));
        assertThat(output.getRows().get(2).get("key"), is("TEST-3"));

        assertThat(mockController.requests, hasSize(2));
        var firstRequest = mockController.requests.getFirst();
        assertThat(firstRequest.method(), is("GET"));
        assertThat(firstRequest.path(), is("/rest/api/2/search"));
        // The mock receives the query as Micronaut re-encodes it: spaces become '+', '=' stays literal.
        assertThat(firstRequest.query(), is("jql=project+=+TEST&maxResults=10&startAt=0&fields=summary,status"));
        assertThat(mockController.requests.get(1).query(), is("jql=project+=+TEST&maxResults=10&startAt=2&fields=summary,status"));
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
            .jql(Property.ofValue("project = TEST"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Get.Output output = task.run(runContext);

        assertThat(output.getSize(), is(3L));
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
            .issueIdOrKey(Property.ofValue("TEST-1"))
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
            .issueIdOrKey(Property.ofValue("OPS 123"))
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
            .issueIdOrKey(Property.ofValue("TEST-1"))
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
            .issueIdOrKey(Property.ofValue("TEST-1"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without an issue key"));
    }

    @Test
    void failsWithClearMessageOnEmptyBodyForFetchOne() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/empty-body")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey(Property.ofValue("TEST-1"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without an issue key"));
    }

    @Test
    void failsWhenSearchResponseBodyIsUnparsable() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/bad-json")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .jql(Property.ofValue("project = TEST"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        // A non-empty body the task cannot parse must fail the flow, not look like zero results.
        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 with an unparsable response body"));
    }

    @Test
    void failsWithClearMessageWhenSearchResponseLacksIssues() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/missing-key")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .fetchType(Property.ofValue(FetchType.FETCH))
            .jql(Property.ofValue("project = TEST"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira returned HTTP 200 without a list of issues"));
    }

    @Test
    void failsWithClearMessageOnHttpError() {
        Get task = Get.builder()
            .id(IdUtils.create())
            .type(Get.class.getName())
            .baseUrl(getApiBaseUrl() + "/http-error")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .issueIdOrKey(Property.ofValue("TEST-1"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

        var exception = assertThrows(HttpClientResponseException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("Jira request failed with HTTP 400"));
    }

    @Test
    void rejectsMaxResultsOutsideTheDocumentedBounds() {
        for (int invalidMaxResults : new int[] { 0, JiraUtil.MAX_RESULTS_LIMIT + 1 }) {
            Get task = Get.builder()
                .id(IdUtils.create())
                .type(Get.class.getName())
                .baseUrl(getApiBaseUrl())
                .username(Property.ofValue("user@example.com"))
                .password(Property.ofValue("token"))
                .fetchType(Property.ofValue(FetchType.FETCH))
                .jql(Property.ofValue("project = TEST"))
                .maxResults(Property.ofValue(invalidMaxResults))
                .build();

            RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());

            var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
            assertThat(exception.getMessage(), containsString("`maxResults` must be"));
        }
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

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
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

        var exception = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(exception.getMessage(), containsString("`jql` is required when `fetchType` is FETCH or STORE"));
    }
}
