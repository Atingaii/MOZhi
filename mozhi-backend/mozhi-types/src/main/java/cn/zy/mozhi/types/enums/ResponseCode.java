package cn.zy.mozhi.types.enums;

public enum ResponseCode {

    SUCCESS("0000", "success"),
    BAD_REQUEST("A0400", "bad request"),
    UNAUTHORIZED("A0401", "unauthorized"),
    FORBIDDEN("A0403", "forbidden"),
    NOT_FOUND("A0404", "not found"),
    CONFLICT("A0409", "conflict"),
    AUTH_CHALLENGE_REQUIRED("A0410", "auth challenge required"),
    TOO_MANY_REQUESTS("A0429", "too many requests"),
    SYSTEM_ERROR("B0001", "system error");

    private final String code;
    private final String message;

    ResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
