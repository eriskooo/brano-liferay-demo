package com.example.crm.web;

@Controller
class HomeController {

    @GetMapping({"/", "/blacklist"})
    String home() { return "redirect:/blacklist/search"; }
}
