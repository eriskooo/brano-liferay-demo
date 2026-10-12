package com.example.crm.blacklist;

import java.time.LocalDateTime;

public record Note(String text, String author, LocalDateTime createdAt) {
}
