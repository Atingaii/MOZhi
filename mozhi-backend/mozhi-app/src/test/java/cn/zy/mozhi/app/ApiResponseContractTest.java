package cn.zy.mozhi.app;

import cn.zy.mozhi.api.dto.ApiResponse;
import cn.zy.mozhi.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseContractTest {

    @Test
    void should_create_success_response_with_shared_contract() {
        ApiResponse<String> response = ApiResponse.success("pong");

        assertAll(
                () -> assertTrue(response.isSuccess()),
                () -> assertEquals(ResponseCode.SUCCESS.getCode(), response.code()),
                () -> assertEquals(ResponseCode.SUCCESS.getMessage(), response.message()),
                () -> assertEquals("pong", response.data())
        );
    }

    @Test
    void should_create_failure_response_with_explicit_code_and_message() {
        ApiResponse<Void> response = ApiResponse.failure(ResponseCode.BAD_REQUEST.getCode(), "invalid request");

        assertAll(
                () -> assertEquals(false, response.isSuccess()),
                () -> assertEquals(ResponseCode.BAD_REQUEST.getCode(), response.code()),
                () -> assertEquals("invalid request", response.message()),
                () -> assertNull(response.data())
        );
    }
}
