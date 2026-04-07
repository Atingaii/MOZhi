package cn.zy.mozhi.infrastructure.adapter.port;

import cn.zy.mozhi.domain.user.adapter.port.IUserPasswordBlocklistPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CommonPasswordBlocklistPortImpl implements IUserPasswordBlocklistPort {

    private final Set<String> blockedPasswords;

    public CommonPasswordBlocklistPortImpl() {
        this.blockedPasswords = loadBlockedPasswords();
    }

    @Override
    public boolean contains(String rawPassword) {
        return blockedPasswords.contains(rawPassword.toLowerCase(Locale.ROOT));
    }

    private Set<String> loadBlockedPasswords() {
        ClassPathResource resource = new ClassPathResource("security/common-passwords.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (Exception exception) {
            throw new IllegalStateException("failed to load common password blocklist", exception);
        }
    }
}
