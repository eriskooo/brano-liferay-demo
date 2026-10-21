package com.example.crm.web;

@Controller
@RequestMapping("/blacklist")
@SessionAttributes("searchForm")
public class WithPrefixController {

    @GetMapping("/search")
    String search() { return "search"; }

    @PostMapping("/searchAction")
    String searchAction() { return "redirect:/blacklist/search"; }
}
