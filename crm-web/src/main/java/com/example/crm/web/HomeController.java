package com.example.crm.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Vstupné body: koreň appky a „stránka“ blacklistu (cieľ presmerovania z nginx) vedú na vyhľadávanie. */
@Controller
public class HomeController {

    @GetMapping({"/", "/blacklist"})
    public String home() {
        return "redirect:/blacklist/search";
    }
}
