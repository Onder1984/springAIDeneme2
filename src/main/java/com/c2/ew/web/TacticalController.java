package com.c2.ew.web;

import com.c2.ew.agent.dto.TacticalComintDetailDto;
import com.c2.ew.agent.dto.TacticalComintSummaryDto;
import com.c2.ew.agent.dto.TacticalEmissionDetailDto;
import com.c2.ew.agent.dto.TacticalEmissionSummaryDto;
import com.c2.ew.agent.dto.TacticalQueryRequest;
import com.c2.ew.agent.dto.TacticalResponseDto;
import com.c2.ew.agent.service.TacticalAgentService;
import com.c2.ew.agent.tools.ComintEmissionTools;
import com.c2.ew.agent.tools.RadarEmissionTools;
import com.c2.ew.domain.model.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/tactical", "/api/tactical"})
@CrossOrigin(origins = "*")
public class TacticalController {

    private final TacticalAgentService agentService;
    private final RadarEmissionTools emissionTools;
    private final ComintEmissionTools comintTools;

    public TacticalController(
        TacticalAgentService agentService,
        RadarEmissionTools emissionTools,
        ComintEmissionTools comintTools
    ) {
        this.agentService = agentService;
        this.emissionTools = emissionTools;
        this.comintTools = comintTools;
    }

    @PostMapping("/analyze")
    public ResponseEntity<TacticalResponseDto> analyze(@RequestBody(required = false) TacticalQueryRequest request) {
        return ResponseEntity.ok(agentService.processTacticalQuery(request));
    }

    // --- RADAR / ELINT ENDPOINTS ---

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMacroStats() {
        return ResponseEntity.ok(emissionTools.getTacticalMacroStats());
    }

    @GetMapping("/threats")
    public ResponseEntity<List<TacticalEmissionSummaryDto>> getPriorityThreats(@RequestParam(required = false, defaultValue = "15") Integer limit) {
        return ResponseEntity.ok(emissionTools.queryPriorityThreats(limit));
    }

    @GetMapping("/emissions")
    public ResponseEntity<PagedResult<TacticalEmissionSummaryDto>> getEmissionsPaged(
        @RequestParam(required = false) String teshisKimlik,
        @RequestParam(required = false) String veriKaynagi,
        @RequestParam(required = false) String radarAdi,
        @RequestParam(required = false) String hss,
        @RequestParam(required = false) Boolean taciz,
        @RequestParam(required = false) String etUygulamaDurumu,
        @RequestParam(required = false) String platformOrtami,
        @RequestParam(required = false) Double minFrekansMhz,
        @RequestParam(required = false) Double maxFrekansMhz,
        @RequestParam(required = false) String frekansTipi,
        @RequestParam(required = false) Double minPriMicroSec,
        @RequestParam(required = false) Double maxPriMicroSec,
        @RequestParam(required = false) Double minPwMicroSec,
        @RequestParam(required = false) Double maxPwMicroSec,
        @RequestParam(required = false) Double minAtpMicroSec,
        @RequestParam(required = false) Double maxAtpMicroSec,
        @RequestParam(required = false, defaultValue = "0") Integer page,
        @RequestParam(required = false, defaultValue = "15") Integer size
    ) {
        return ResponseEntity.ok(emissionTools.queryEmissionsPaged(
            teshisKimlik, veriKaynagi, radarAdi, hss, taciz, etUygulamaDurumu, platformOrtami,
            minFrekansMhz, maxFrekansMhz, frekansTipi,
            minPriMicroSec, maxPriMicroSec,
            minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec,
            page, size
        ));
    }

    @GetMapping("/emissions/{id}")
    public ResponseEntity<TacticalEmissionDetailDto> getEmissionDetails(@PathVariable String id) {
        return ResponseEntity.ok(emissionTools.getEmissionDetails(id));
    }


    @GetMapping("/et-harassment")
    public ResponseEntity<List<TacticalEmissionSummaryDto>> getEtAndHarassment() {
        return ResponseEntity.ok(emissionTools.queryElectronicAttackAndHarassment());
    }

    // --- MUHABERE / COMINT ENDPOINTS ---

    @GetMapping("/comint/stats")
    public ResponseEntity<Map<String, Object>> getComintMacroStats() {
        return ResponseEntity.ok(comintTools.getComintMacroStats());
    }

    @GetMapping("/comint/threats")
    public ResponseEntity<List<TacticalComintSummaryDto>> getComintPriorityThreats(@RequestParam(required = false, defaultValue = "15") Integer limit) {
        return ResponseEntity.ok(comintTools.queryPriorityComintThreats(limit));
    }

    @GetMapping("/comint/emissions")
    public ResponseEntity<PagedResult<TacticalComintSummaryDto>> getComintEmissionsPaged(
        @RequestParam(required = false) String teshisKimlik,
        @RequestParam(required = false) String veriKaynagi,
        @RequestParam(required = false) String lisan,
        @RequestParam(required = false) String tip,
        @RequestParam(required = false) String haberlesmeSekli,
        @RequestParam(required = false) String protokol,
        @RequestParam(required = false) String modulasyon,
        @RequestParam(required = false) String cagriAdi,
        @RequestParam(required = false) Boolean mti,
        @RequestParam(required = false) Double minFrekansMhz,
        @RequestParam(required = false) Double maxFrekansMhz,
        @RequestParam(required = false) Double minGenlikDbm,
        @RequestParam(required = false) Double maxGenlikDbm,
        @RequestParam(required = false, defaultValue = "0") Integer page,
        @RequestParam(required = false, defaultValue = "10") Integer size
    ) {
        return ResponseEntity.ok(comintTools.queryComintEmissionsPaged(
            teshisKimlik, veriKaynagi, lisan, tip, haberlesmeSekli, protokol, modulasyon, cagriAdi, mti,
            minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm,
            page, size
        ));
    }

    @GetMapping({"/comint/emissions/{id}", "/comint/{id}"})
    public ResponseEntity<TacticalComintDetailDto> getComintDetails(@PathVariable String id) {
        return ResponseEntity.ok(comintTools.getComintDetails(id));
    }

    @GetMapping("/analytics/et-assessment")
    public ResponseEntity<Map<String, Object>> getEtAssessment() {
        return ResponseEntity.ok(emissionTools.getHarassmentAndElectronicAttackAssessment());
    }

    @GetMapping("/analytics/agility")
    public ResponseEntity<Map<String, Object>> getAgilityAnalysis() {
        return ResponseEntity.ok(emissionTools.getRadarAgilityAndSignatureAnalysis());
    }

    @GetMapping("/analytics/transmission-windows")
    public ResponseEntity<Map<String, Object>> getTransmissionWindows() {
        return ResponseEntity.ok(emissionTools.getOperationalTransmissionWindows());
    }

    @GetMapping("/analytics/correlations")
    public ResponseEntity<Map<String, Object>> getCrossDomainCorrelations(
        @RequestParam(required = false, defaultValue = "5.0") Double maxDistanceKm,
        @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        return ResponseEntity.ok(emissionTools.getCrossDomainCorrelations(maxDistanceKm, limit));
    }

    @GetMapping("/comint/analytics/active-callsigns")
    public ResponseEntity<Map<String, Long>> getActiveCallsigns(
        @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(comintTools.getMostActiveCallsigns(limit));
    }

    @GetMapping("/comint/analytics/comsec")
    public ResponseEntity<Map<String, Object>> getComsecTactics() {
        return ResponseEntity.ok(comintTools.getComsecAndTransmissionTactics());
    }

    @GetMapping("/comint/network-topology")
    public ResponseEntity<Map<String, Object>> getComintNetworkTopology(
        @RequestParam(required = false, defaultValue = "50") Integer limit
    ) {
        return ResponseEntity.ok(comintTools.getComintNetworkTopologyGraph(limit));
    }

    @GetMapping("/comint/network")
    public ResponseEntity<List<TacticalComintSummaryDto>> getComintNetwork(@RequestParam String cagriAdi) {
        return ResponseEntity.ok(comintTools.queryComintCallsignNetwork(cagriAdi));
    }
}
