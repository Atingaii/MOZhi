package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordEncoderPort;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserPasswordEncoderPortImpl implements IUserPasswordEncoderPort {

    private final PasswordEncoder passwordEncoder;
    private final BCryptPasswordEncoder legacyBcryptPasswordEncoder = new BCryptPasswordEncoder();

    public UserPasswordEncoderPortImpl() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", legacyBcryptPasswordEncoder);
        this.passwordEncoder = new DelegatingPasswordEncoder("argon2", encoders);
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        if (encodedPassword != null && !encodedPassword.startsWith("{")) {
            return legacyBcryptPasswordEncoder.matches(rawPassword, encodedPassword);
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
