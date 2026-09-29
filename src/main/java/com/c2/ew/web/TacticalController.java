package com.c2.ew.web;

import com.c2.ew.agent.dto.TacticalQueryRequest;
import com.c2.ew.agent.dto.TacticalResponseDto;
import com.c2.ew.agent.service.TacticalAgentService;
import com.c2.ew.domain.model.EmitterActivity;
import com.c2.ew.domain.model.EmitterFix;
import com.c2.ew.domain.model.EmitterLob;
import com.c2.ew.domain.model.PagedResult;
import com.c2.ew.infrastructure.repository.TacticalEmissionRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Taktik Elektronik Harp REST Denetleyicisi
 * Hem /api/v1/tactical hem de /api/tactical yollarını destekler.
 */
@RestController
@RequestMapping({"/api/v1/tactical", "/api/tactical"})
@CrossOrigin(origins = "*")
public class TacticalController {

    private final TacticalAgentService agentService;
    private final TacticalEmissionRepository repository;

    public TacticalController(TacticalAgentService agentService, TacticalEmissionRepository repository) {
        this.agentService = agentService;
        this.repository = repository;
    }

    @PostMapping("/analyze")
    public ResponseEntity<TacticalResponseDto> analyze(@RequestBody(required = false) TacticalQueryRequest request) {
        return ResponseEntity.ok(agentService.analyzeTacticalSituation(request));
    }

    @GetMapping("/geojson")
    public ResponseEntity<Map<String, Object>> getGeoJson() {
        return ResponseEntity.ok(agentService.getTacticalGeoJson());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getOperationalStatistics() {
        return ResponseEntity.ok(repository.getOperationalStatistics());
    }

    /**
     * Sayfalama parametresi (page) verilirse PagedResult, verilmezse tam envanter listesi döner.
     */
    @GetMapping("/fixes")
    public ResponseEntity<?> getFixes(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) EmitterActivity status,
        @RequestParam(required = false) String band,
        @RequestParam(required = false) Integer minThreatLevel,
        @RequestParam(required = false) String radarType,
        @RequestParam(required = false) String platformType
    ) {
        if (page != null) {
            int p = page;
            int s = (size != null && size > 0) ? size : 15;
            return ResponseEntity.ok(repository.findFixesPaged(p, s, status, band, minThreatLevel, radarType, platformType));
        }
        return ResponseEntity.ok(repository.findAllFixes());
    }

    @GetMapping("/fixes/paged")
    public ResponseEntity<PagedResult<EmitterFix>> getFixesPaged(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "15") int size,
        @RequestParam(required = false) EmitterActivity status,
        @RequestParam(required = false) String band,
        @RequestParam(required = false) Integer minThreatLevel,
        @RequestParam(required = false) String radarType,
        @RequestParam(required = false) String platformType
    ) {
        return ResponseEntity.ok(repository.findFixesPaged(page, size, status, band, minThreatLevel, radarType, platformType));
    }

    @GetMapping("/time-frequency")
    public ResponseEntity<List<Map<String, Object>>> getTimeFrequency(
        @RequestParam(required = false) Integer lastHours,
        @RequestParam(required = false) String band,
        @RequestParam(required = false) String platformType,
        @RequestParam(required = false) String radarType
    ) {
        return ResponseEntity.ok(repository.getTimeFrequencyPoints(lastHours, band, platformType, radarType));
    }

    @GetMapping("/lobs")
    public ResponseEntity<PagedResult<EmitterLob>> getLobs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String associationStatus,
        @RequestParam(required = false) Boolean unassociatedOnly,
        @RequestParam(required = false) String sensorNodeId,
        @RequestParam(required = false) String band
    ) {
        String status = associationStatus;
        if (status == null && Boolean.TRUE.equals(unassociatedOnly)) {
            status = "UNASSOCIATED";
        }
        return ResponseEntity.ok(repository.findLobsPaged(page, size, status, sensorNodeId, band));
    }
}
