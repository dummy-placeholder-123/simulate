package com.devashish.qca.fes.controller;

import com.devashish.qca.fes.dto.ScanRequest;
import com.devashish.qca.fes.dto.ScanResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class FrontController {

    @PostMapping("/start-scan")
    public ResponseEntity<ScanResponse> startScan(@RequestBody ScanRequest request) {
        ScanResponse response = new ScanResponse(request.scanId(), request.useCase(), request.service(), "ACCEPTED");
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
