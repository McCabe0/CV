package com.skill2career.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello() = mapOf(
        "message" to "Hello from Skill2Career backend 🚀"
    )
}
