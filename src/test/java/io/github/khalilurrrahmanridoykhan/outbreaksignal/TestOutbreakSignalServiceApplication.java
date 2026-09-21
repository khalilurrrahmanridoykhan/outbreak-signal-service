package io.github.khalilurrrahmanridoykhan.outbreaksignal;

import org.springframework.boot.SpringApplication;

public class TestOutbreakSignalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(OutbreakSignalServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
