package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

class CreateTest extends AbstractJiraTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void createsIssueAndExposesOutputs() throws Exception {
        Create task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Test summary"))
            .description("Test description")
            .issueTypeId(Property.ofValue("10001"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Create.Output output = task.run(runContext);

        assertThat(output.getId(), is("10000"));
        assertThat(output.getKey(), is("TEST-1"));
        assertThat(output.getSelf(), is("http://mock-jira/rest/api/2/issue/10000"));
        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/TEST-1"));

        assertThat(mockController.requests, hasSize(1));
        JiraMockController.CapturedRequest request = mockController.requests.getFirst();
        assertThat(request.method(), is("POST"));
        assertThat(request.path(), is("/rest/api/2/issue/"));
        assertThat(request.authorization(), startsWith("Basic "));
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() throws Exception {
        Create task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .baseUrl(getApiBaseUrl() + "/")
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Test summary"))
            .description("Test description")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Create.Output output = task.run(runContext);

        assertThat(output.getUrl(), is(getApiBaseUrl() + "/browse/TEST-1"));
        assertThat(mockController.requests.getFirst().path(), is("/rest/api/2/issue/"));
    }

    @Test
    void supportsBaseUrlWithContextPath() throws Exception {
        Create task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .baseUrl(getApiBaseUrl() + "/jira")
            .accessToken(Property.ofValue("oauth-token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Test summary"))
            .description("Test description")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Create.Output output = task.run(runContext);

        assertThat(output.getUrl(), is(getApiBaseUrl() + "/jira/browse/TEST-1"));
        assertThat(mockController.requests.getFirst().path(), is("/jira/rest/api/2/issue/"));
        assertThat(mockController.requests.getFirst().authorization(), is("Bearer oauth-token"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsIssueWithoutOptionalFields() throws Exception {
        Create task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Test summary"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Create.Output output = task.run(runContext);

        assertThat(output.getKey(), is("TEST-1"));

        Map<String, Object> fields = fieldsOf(mockController.requests.getFirst().body());
        assertThat(fields.keySet(), not(hasItem("description")));
        assertThat((List<String>) fields.get("labels"), hasItem("kestra-bot"));
    }

    @Test
    void producesValidJsonWhenSummaryAndDescriptionContainSpecialCharacters() throws Exception {
        Create task = Create.builder()
            .id(IdUtils.create())
            .type(Create.class.getName())
            .baseUrl(getApiBaseUrl())
            .username(Property.ofValue("user@example.com"))
            .password(Property.ofValue("token"))
            .projectKey("PROJ")
            .summary(Property.ofValue("Summary with \"quotes\""))
            .description("Line one\nLine two")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        task.run(runContext);

        Map<String, Object> fields = fieldsOf(mockController.requests.getFirst().body());
        assertThat(fields.get("summary"), is("Summary with \"quotes\""));
        assertThat(fields.get("description"), is("Line one\nLine two"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fieldsOf(String requestBody) throws Exception {
        Map<String, Object> body = JacksonMapper.ofJson().readValue(requestBody, new TypeReference<>() {
        });
        return (Map<String, Object>) body.get("fields");
    }
}
