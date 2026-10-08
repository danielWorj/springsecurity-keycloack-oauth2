package com.example.springsecuritykeycloack.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HomeController {
    @GetMapping("/home")
    public String home() {
        return "Welcome to the Home Page!";
    }
    @GetMapping("/admin")
    public String admin() {
        return "Welcome to the Admin Page!";
    }
    @GetMapping("/user")
    public String user() {
        return "Welcome to the User Page!";
    }
}
