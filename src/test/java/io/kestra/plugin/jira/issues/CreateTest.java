package io.kestra.plugin.jira.issues;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
@WireMockTest(httpPort = 5083)
class CreateTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run(WireMockRuntimeInfo wireMockRuntimeInfo) throws Exception {
        stubFor(any(urlPathEqualTo("/rest/api/2/issue"))
            .willReturn(okJson("{\"key\":\"TEST-123\",\"id\":\"12345\"}")));

        RunContext runContext = runContextFactory.of(Map.of());

        Create task = Create.builder()
            .baseUrl(wireMockRuntimeInfo.getHttpBaseUrl())
            .username(Property.ofValue("test@test.com"))
            .password(Property.ofValue("api-token"))
            .projectKey("TEST")
            .issueTypeId(Property.ofValue("10003"))
            .summary(Property.ofValue("Test issue summary"))
            .description("Test issue description")
            .labels(Property.ofValue(List.of("bug", "test")))
            .build();

        task.run(runContext);

        verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Authorization", matching("Basic .+")));
    }
}