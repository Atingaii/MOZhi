package cn.zy.mozhi.types.exception;

import cn.zy.mozhi.types.enums.ResponseCode;

public class BaseException extends RuntimeException {

    private final String errorCode;

    public BaseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BaseException(ResponseCode responseCode) {
        this(responseCode.getCode(), responseCode.getMessage());
    }

    public BaseException(ResponseCode responseCode, String message) {
        this(responseCode.getCode(), message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
