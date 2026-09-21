package io.github.khalilurrrahmanridoykhan.outbreaksignal.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = ApiExceptionHandlerTest.Probe.class)
@Import({ApiExceptionHandler.class, ApiExceptionHandlerTest.Probe.class})
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void validationFailureIsAProblemDocumentWithStatus400() throws Exception {
        mvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedMethodIsAProblemDocumentWithStatus405() throws Exception {
        mvc.perform(post("/probe/conflict"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void dataIntegrityViolationIsAConflictThatDoesNotLeakTheSql() throws Exception {
        mvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with data that already exists."));
    }

    @Test
    void unexpectedFailureIsAGenericServerErrorThatDoesNotLeakTheMessage() throws Exception {
        mvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    record Payload(@NotBlank String name) {
    }

    @RestController
    static class Probe {

        @PostMapping("/probe/validate")
        void validate(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/probe/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint uq_secret");
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("internal detail that must not be exposed");
        }
    }
}
