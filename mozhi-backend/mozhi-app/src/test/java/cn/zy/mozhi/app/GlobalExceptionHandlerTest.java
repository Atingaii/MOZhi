package cn.zy.mozhi.app;

import cn.zy.mozhi.trigger.http.GlobalExceptionHandler;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .setValidator(validator)
                .build();
    }

    @Test
    void should_wrap_domain_exception_as_bad_request_response() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ResponseCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void should_wrap_validation_exception_as_bad_request_response() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ResponseCode.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.message").value("name must not be blank"));
    }

    @Test
    void should_wrap_unexpected_exception_as_internal_error_response() throws Exception {
        mockMvc.perform(get("/test/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ResponseCode.SYSTEM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(ResponseCode.SYSTEM_ERROR.getMessage()));
    }

    @RestController
    static class FailureController {

        @GetMapping("/test/business")
        String businessFailure() {
            throw new BaseException(ResponseCode.BAD_REQUEST.getCode(), "invalid request");
        }

        @PostMapping("/test/validation")
        String validationFailure(@Valid @RequestBody ValidationRequest request) {
            return request.name();
        }

        @GetMapping("/test/system")
        String systemFailure() {
            throw new IllegalStateException("boom");
        }
    }

    record ValidationRequest(@NotBlank(message = "name must not be blank") String name) {
    }
}
