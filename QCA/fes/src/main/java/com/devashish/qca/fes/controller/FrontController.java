package com.devashish.qca.fes.controller;

import com.devashish.qca.fes.dto.ScanFindingsResponse;
import com.devashish.qca.fes.dto.ScanRequest;
import com.devashish.qca.fes.dto.ScanListResponse;
import com.devashish.qca.fes.dto.ScanResponse;
import com.devashish.qca.fes.dto.ScanStatusResponse;
import com.devashish.qca.fes.dto.StartScanRequest;
import com.devashish.qca.fes.dto.StartScanResponse;
import com.devashish.qca.fes.service.FeatureFlagGuard;
import com.devashish.qca.fes.service.ScanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FrontController {

    private final FeatureFlagGuard featureFlagGuard;
    private final ScanService scanService;

    public FrontController(FeatureFlagGuard featureFlagGuard, ScanService scanService) {
        this.featureFlagGuard = featureFlagGuard;
        this.scanService = scanService;
    }

    @PostMapping("/create-scan")
    public ResponseEntity<ScanResponse> createScan(@RequestBody ScanRequest request) {
        featureFlagGuard.requireCreateScanEnabled();
        ScanResponse response = scanService.createScan(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/start-scan")
    public ResponseEntity<StartScanResponse> startScan(@RequestBody StartScanRequest request) {
        featureFlagGuard.requireStartScanEnabled();
        StartScanResponse response = scanService.startScan(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/accounts/{accountId}/scans")
    public ResponseEntity<List<ScanListResponse>> listScansByAccountId(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(scanService.listScansByAccountId(accountId, limit));
    }

    @GetMapping("/scans/{scanId}/findings")
    public ResponseEntity<ScanFindingsResponse> getScanFindings(@PathVariable String scanId) {
        return ResponseEntity.ok(scanService.getScanFindings(scanId));
    }

    @GetMapping("/scans/{scanId}/status")
    public ResponseEntity<ScanStatusResponse> getScanStatus(@PathVariable String scanId) {
        return ResponseEntity.ok(scanService.getScanStatus(scanId));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
