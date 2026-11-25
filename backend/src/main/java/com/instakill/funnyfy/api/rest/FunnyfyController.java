package com.instakill.funnyfy.api.rest;

import com.instakill.funnyfy.application.FunnyfyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FunnyfyController {

    private final FunnyfyService funnyfyService;

    public FunnyfyController(FunnyfyService funnyfyService) {
        this.funnyfyService = funnyfyService;
    }

    @PostMapping("/funnyfy")
    public FunnyfyResponse funnyfy(@Valid @RequestBody FunnyfyRequest request) {
        return new FunnyfyResponse(funnyfyService.rewrite(request.text()));
    }
}
