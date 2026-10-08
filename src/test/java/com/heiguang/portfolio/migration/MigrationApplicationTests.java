package com.heiguang.portfolio.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.demo.customers=25", "app.demo.orders=500"})
@AutoConfigureMockMvc
class MigrationApplicationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("targetJdbcTemplate") JdbcTemplate target;

    @BeforeEach
    void resetMigrationState() {
        target.execute("DELETE FROM migration_failures");
        target.execute("DELETE FROM migration_runs");
        target.execute("DELETE FROM customers");
    }

    @Test void migratesAndValidatesAllRows() throws Exception {
        mvc.perform(post("/api/migrations/customers").contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":7}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.sourceCount").value(25)).andExpect(jsonPath("$.targetCount").value(25))
                .andExpect(jsonPath("$.dataMatches").value(true));
    }

    @Test void cleansNamesEmailsAndStatuses() throws Exception {
        migrate("{\"batchSize\":10}");
        var row = target.queryForMap("SELECT full_name,email,status FROM customers WHERE source_id=7");
        assertThat(row.get("full_name")).isEqualTo("Customer 7");
        assertThat(row.get("email")).isEqualTo("user7@example.com");
        assertThat(row.get("status")).isEqualTo("INACTIVE");
    }

    @Test void rerunIsIdempotent() throws Exception {
        migrate("{\"batchSize\":10}"); migrate("{\"batchSize\":6}");
        assertThat(target.queryForObject("SELECT COUNT(*) FROM customers", Long.class)).isEqualTo(25);
    }

    @Test void recordsCheckpointOnSimulatedFailure() throws Exception {
        JsonNode result = migrate("{\"batchSize\":5,\"failAfterRows\":10}");
        assertThat(result.get("status").asText()).isEqualTo("FAILED");
        assertThat(result.get("lastSourceId").asLong()).isEqualTo(10);
        assertThat(target.queryForObject("SELECT COUNT(*) FROM migration_failures", Long.class)).isEqualTo(1);
    }

    @Test void resumesFailedRunFromCheckpoint() throws Exception {
        JsonNode failed = migrate("{\"batchSize\":5,\"failAfterRows\":10}");
        String body = "{\"batchSize\":4,\"resumeRunId\":\"" + failed.get("runId").asText() + "\"}";
        JsonNode resumed = migrate(body);
        assertThat(resumed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(resumed.get("processedRows").asLong()).isEqualTo(25);
    }

    @Test void rejectsInvalidBatchSize() throws Exception {
        mvc.perform(post("/api/migrations/customers").contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":1001}"))
                .andExpect(status().isBadRequest());
    }

    @Test void returns404ForUnknownRun() throws Exception {
        mvc.perform(get("/api/migrations/missing")).andExpect(status().isNotFound());
    }

    @Test void explainPlanUsesCompositeIndex() throws Exception {
        mvc.perform(post("/api/performance/orders/benchmark?customerId=7&repetitions=3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.indexDetected").value(true))
                .andExpect(jsonPath("$.beforePlan").isString()).andExpect(jsonPath("$.afterPlan").isString());
    }

    private JsonNode migrate(String body) throws Exception {
        String response = mvc.perform(post("/api/migrations/customers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
