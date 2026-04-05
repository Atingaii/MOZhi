package cn.zy.mozhi.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.zy.mozhi")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
