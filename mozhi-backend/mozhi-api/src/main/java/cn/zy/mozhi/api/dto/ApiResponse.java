package cn.zy.mozhi.api.dto;

import cn.zy.mozhi.types.enums.ResponseCode;

public final class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), data);
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static ApiResponse<Void> failure(ResponseCode responseCode) {
        return failure(responseCode.getCode(), responseCode.getMessage());
    }

    public static ApiResponse<Void> failure(ResponseCode responseCode, String message) {
        return failure(responseCode.getCode(), message);
    }

    public static ApiResponse<Void> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getCode() {
        return code;
    }

    public String code() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String message() {
        return message;
    }

    public T getData() {
        return data;
    }

    public T data() {
        return data;
    }
}
