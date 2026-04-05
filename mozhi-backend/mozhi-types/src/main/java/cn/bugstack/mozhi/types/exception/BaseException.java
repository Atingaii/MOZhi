package cn.bugstack.mozhi.types.exception;

public class BaseException extends RuntimeException {

    private final String errorCode;

    public BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

