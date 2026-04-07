package cn.zy.mozhi.domain.user.service;

import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordBlocklistPort;
import cn.zy.mozhi.types.enums.ResponseCode;
import cn.zy.mozhi.types.exception.BaseException;

public class UserPasswordPolicy {

    private final IUserPasswordBlocklistPort passwordBlocklistPort;

    public UserPasswordPolicy(IUserPasswordBlocklistPort passwordBlocklistPort) {
        this.passwordBlocklistPort = passwordBlocklistPort;
    }

    public void validate(String rawPassword, String username, String email) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must not be blank");
        }
        if (rawPassword.length() < 8) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must be at least 8 characters");
        }
        if (rawPassword.length() > 64) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password must be at most 64 characters");
        }
        if (passwordBlocklistPort.contains(rawPassword)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password is too weak");
        }

        String normalizedUsername = username == null ? "" : username.trim();
        String emailLocalPart = "";
        if (email != null) {
            String normalizedEmail = email.trim();
            int separatorIndex = normalizedEmail.indexOf('@');
            emailLocalPart = separatorIndex >= 0 ? normalizedEmail.substring(0, separatorIndex) : normalizedEmail;
        }

        if (rawPassword.equalsIgnoreCase(normalizedUsername) || rawPassword.equalsIgnoreCase(emailLocalPart)) {
            throw new BaseException(ResponseCode.BAD_REQUEST, "password is too weak");
        }
    }
}
