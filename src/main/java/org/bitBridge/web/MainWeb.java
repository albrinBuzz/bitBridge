package org.bitBridge.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
//@ComponentScan(basePackages = "com.filetalk;")
//@ComponentScan(basePackages = "org.filetalk.filetalk")
//@ComponentScan(basePackages = "org.filetalk.filetalk.web")
@ComponentScan(basePackages = "org.bitBridge")

public class MainWeb {
    public static void main(String[] args) {

      SpringApplication.run(MainWeb.class, args);


    }


}
