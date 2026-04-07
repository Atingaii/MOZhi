package cn.zy.mozhi.domain.user.adapter.port;

public interface IUserPasswordEncoderPort {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
