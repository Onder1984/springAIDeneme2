package com.c2.ew;

import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TacticalEwApplication {

    public static void main(String[] args) {
        // Turkish locale bug prevention: ensures String.toUpperCase() produces ASCII "INTEGER" and "STRING"
        // for Spring AI Google GenAI Function Declarations
        Locale.setDefault(Locale.ENGLISH);
        SpringApplication.run(TacticalEwApplication.class, args);
    }
}
