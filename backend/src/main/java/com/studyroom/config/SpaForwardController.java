package com.studyroom.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Lets the React router handle deep links such as /room/ABC123 when the web client is bundled. */
@Controller
public class SpaForwardController {

    @GetMapping("/room/{code:[A-Za-z0-9_-]+}")
    public String room() {
        return "forward:/index.html";
    }
}
