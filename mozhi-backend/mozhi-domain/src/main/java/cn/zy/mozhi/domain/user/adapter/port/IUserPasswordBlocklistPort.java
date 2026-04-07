package cn.zy.mozhi.domain.user.adapter.port;

public interface IUserPasswordBlocklistPort {

    boolean contains(String rawPassword);
}
