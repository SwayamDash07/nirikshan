package com.nirikshan.service;

import com.nirikshan.dto.PanicPropagationResponse;
import com.nirikshan.dto.StampedeLikelihoodResponse;
import com.nirikshan.dto.UnusualBehaviorResponse;
import com.nirikshan.model.CitizenReport;
import com.nirikshan.model.FlowBehaviorState;
import com.nirikshan.model.RiskEvent;
import com.nirikshan.model.RiskEventSource;
import com.nirikshan.model.RiskLevel;
import com.nirikshan.model.Zone;
import com.nirikshan.repository.CitizenReportRepository;
import com.nirikshan.repository.RiskEventRepository;
import com.nirikshan.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent risk-intelligence heuristics. These are bounded decision-support
 * signals, not trained models, diagnoses, or guarantees about future events.
 */
@Service
public class RiskIntelligenceService {
    public static final long DEFAULT_PROPAGATION_WINDOW_SECONDS = 60;
    private final RiskEventRepository events;
    private final ZoneRepository zones;
    private final CitizenReportRepository reports;
    private final long propagationWindowSeconds;

    @Autowired
    public RiskIntelligenceService(RiskEventRepository events, ZoneRepository zones,
                                   CitizenReportRepository reports,
                                   @Value("${nirikshan.analysis.propagation-window-seconds:60}") long propagationWindowSeconds) {
        this.events = events;
        this.zones = zones;
        this.reports = reports;
        this.propagationWindowSeconds = Math.max(10, propagationWindowSeconds);
    }

    public RiskIntelligenceService(RiskEventRepository events, ZoneRepository zones) {
        this(events, zones, null, DEFAULT_PROPAGATION_WINDOW_SECONDS);
    }

    public StampedeLikelihoodResponse stampede(List<RiskEvent> readings, double projectedDensity,
                                               double densityTrendPerMinute, double movementSlowdown,
                                               long hotspotPersistenceSeconds, boolean bottleneck,
                                               RiskEventSource source, Long zoneId) {
        if (readings == null || readings.size() < 3 || readings.stream().anyMatch(e -> e == null || e.getTimestamp() == null)) {
            return StampedeLikelihoodResponse.insufficient("Stampede likelihood requires at least three timestamped readings in the analysis window.");
        }
        RiskEvent latest = readings.get(readings.size() - 1);
        double density = clamp(projectedDensity / 6.0);
        double acceleration = clamp(acceleration(readings) / 0.02);
        double slowdown = clamp(movementSlowdown / .60);
        double reverse = clamp(latest.getReverseMovementRatio() / .45);
        double conflicting = clamp(latest.getConflictingMovementRatio() / .30);
        double hotspot = clamp(hotspotPersistenceSeconds / 60.0);
        double bottleneckSignal = bottleneck ? 1 : 0;
        double reportSignal = reportSignal(zoneId, latest.getTimestamp());
        double score = clamp(.22 * density + .14 * acceleration + .16 * slowdown + .13 * reverse
                + .11 * conflicting + .10 * hotspot + .09 * bottleneckSignal + .05 * reportSignal);
        List<String> evidence = new ArrayList<>();
        add(evidence, density >= .35, "projected density is elevated (" + format(projectedDensity) + " people/m²)");
        add(evidence, acceleration >= .20, "density acceleration is rising");
        add(evidence, slowdown >= .20, "movement slowdown is " + percent(movementSlowdown));
        add(evidence, reverse >= 1, "reverse movement is " + percent(latest.getReverseMovementRatio()));
        add(evidence, conflicting >= 1, "conflicting movement is " + percent(latest.getConflictingMovementRatio()));
        add(evidence, hotspot >= .50, "hotspot persistence is " + hotspotPersistenceSeconds + " seconds");
        add(evidence, bottleneck, "bottleneck state is active");
        add(evidence, reportSignal > 0, "recent panic/crowd-related report signal is present");
        String level = score >= .70 ? "HIGH" : score >= .40 ? "MEDIUM" : "LOW";
        String explanation = evidence.isEmpty() ? "No strong stampede precursor signals were present in the deterministic analysis window."
                : "Heuristic score " + format(score) + " from " + String.join(", ", evidence) + ".";
        return new StampedeLikelihoodResponse(round(score), level, List.copyOf(evidence), explanation);
    }

    public UnusualBehaviorResponse unusual(List<RiskEvent> readings) {
        if (readings == null || readings.size() < 3) return UnusualBehaviorResponse.insufficient("Unusual behavior requires at least three readings; one noisy event is not classified.");
        List<String> evidence = new ArrayList<>();
        int persistent = 0;
        for (int i = 1; i < readings.size(); i++) {
            RiskEvent before = readings.get(i - 1), current = readings.get(i);
            boolean abruptDensity = accelerationPair(readings, i) >= .012;
            boolean suddenSlowdown = relativeDrop(before.getMovementSpeed(), current.getMovementSpeed()) >= .25;
            boolean reverse = current.getReverseMovementRatio() >= .45;
            boolean conflict = current.getConflictingMovementRatio() >= .30;
            boolean stopStart = (before.getMovementSpeed() <= .20 && current.getMovementSpeed() >= .60)
                    || (before.getMovementSpeed() >= .60 && current.getMovementSpeed() <= .20);
            boolean directionChange = before.getDirectionDegrees() != null && current.getDirectionDegrees() != null
                    && angularDifference(before.getDirectionDegrees(), current.getDirectionDegrees()) >= 120;
            int signals = (abruptDensity ? 1 : 0) + (suddenSlowdown ? 1 : 0) + (reverse ? 1 : 0)
                    + (conflict ? 1 : 0) + (stopStart ? 1 : 0) + (directionChange ? 1 : 0);
            if (signals > 0) {
                persistent++;
                if (abruptDensity) addUnique(evidence, "abrupt density acceleration");
                if (suddenSlowdown) addUnique(evidence, "sudden movement slowdown");
                if (reverse) addUnique(evidence, "reverse movement");
                if (conflict) addUnique(evidence, "conflicting movement");
                if (stopStart) addUnique(evidence, "unusual stop/start pattern");
                if (directionChange) addUnique(evidence, "unexpected direction change");
            } else {
                persistent = 0;
            }
        }
        boolean detected = persistent >= 2;
        if (!detected) return new UnusualBehaviorResponse(false, "NORMAL_OR_UNCONFIRMED", persistent, 0,
                List.copyOf(evidence), "Atypical movement was not persistent across two consecutive readings; no unusual behavior classification was made.");
        double confidence = clamp(.35 + .10 * Math.min(3, persistent) + .08 * Math.min(3, evidence.size()));
        return new UnusualBehaviorResponse(true, "UNUSUAL_BEHAVIOR", persistent, round(confidence), List.copyOf(evidence),
                "Unusual behavior persisted across " + persistent + " consecutive readings: " + String.join(", ", evidence) + ".");
    }

    public PanicPropagationResponse propagation(Long zoneId) {
        if (events == null || zones == null) return PanicPropagationResponse.insufficient(RiskEventSource.LIVE);
        Zone zone = zones.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));
        List<Zone> venueZones = zones.findByVenueId(zone.getVenue().getId());
        List<ZoneSnapshot> snapshots = venueZones.stream().map(this::snapshot).filter(s -> s.latest != null).toList();
        if (snapshots.isEmpty()) return PanicPropagationResponse.insufficient(RiskEventSource.LIVE);
        ZoneSnapshot source = snapshots.stream().filter(s -> elevated(s.latest)).max(Comparator.comparing(s -> s.latest.getTimestamp())).orElse(null);
        if (source == null) return new PanicPropagationResponse("NONE", null, null, List.of(), 0,
                "No connected zone is currently elevated enough to act as a propagation source.", RiskEventSource.LIVE);
        List<Long> affected = snapshots.stream().filter(s -> !s.zone.getId().equals(source.zone.getId()))
                .filter(s -> connected(source.zone, s.zone))
                .filter(s -> risingAfter(s, source.latest.getTimestamp())).map(s -> s.zone.getId()).toList();
        String state = propagationState(true, affected.size());
        double confidence = affected.isEmpty() ? .45 : Math.min(1, .55 + affected.size() * .15);
        String explanation = affected.isEmpty()
                ? source.zone.getName() + " is elevated, but connected zones have not shown a sustained rise within " + propagationWindowSeconds + " seconds."
                : source.zone.getName() + " became elevated and " + affected.size() + " connected zone(s) began rising within the configured " + propagationWindowSeconds + " second window.";
        return new PanicPropagationResponse(state, source.zone.getId(), source.zone.getName(), affected, round(confidence), explanation, source.latest.getSource());
    }

    private ZoneSnapshot snapshot(Zone zone) {
        List<RiskEvent> recent = events.findByZoneIdOrderByTimestampDesc(zone.getId(), PageRequest.of(0, 10));
        recent = recent.stream().filter(e -> e.getTimestamp() != null).sorted(Comparator.comparing(RiskEvent::getTimestamp)).toList();
        return new ZoneSnapshot(zone, recent.isEmpty() ? null : recent.get(recent.size() - 1), recent);
    }

    private boolean risingAfter(ZoneSnapshot snapshot, Instant sourceTime) {
        RiskEvent latest = snapshot.latest;
        if (latest.getTimestamp().isBefore(sourceTime) || Duration.between(sourceTime, latest.getTimestamp()).getSeconds() > propagationWindowSeconds) return false;
        if (snapshot.readings.size() < 2) return latest.getRiskLevel().ordinal() >= RiskLevel.MEDIUM.ordinal();
        RiskEvent previous = snapshot.readings.get(snapshot.readings.size() - 2);
        return latest.getDensityScore() > previous.getDensityScore() || latest.getRiskLevel().ordinal() > previous.getRiskLevel().ordinal();
    }

    private static boolean elevated(RiskEvent event) { return event.getRiskLevel().ordinal() >= RiskLevel.HIGH.ordinal() || event.getDensityScore() >= 4 || event.getMovementSlowdown() >= .45; }
    public static String propagationState(boolean sourceElevated, int affectedZoneCount) {
        if (!sourceElevated) return "NONE";
        return affectedZoneCount >= 2 ? "COORDINATED_WORSENING" : affectedZoneCount == 1 ? "PROPAGATING" : "ELEVATED";
    }
    private static boolean connected(Zone left, Zone right) {
        if (left.getLatitude() == null || left.getLongitude() == null || right.getLatitude() == null || right.getLongitude() == null) return false;
        double lat = (left.getLatitude() - right.getLatitude()) * 111_000;
        double lng = (left.getLongitude() - right.getLongitude()) * 111_000 * Math.cos(Math.toRadians(left.getLatitude()));
        return Math.hypot(lat, lng) <= 180;
    }
    private double reportSignal(Long zoneId, Instant timestamp) {
        if (reports == null || zoneId == null) return 0;
        List<CitizenReport> recent = reports.findByZone_IdOrderByTimestampDesc(zoneId).stream()
                .filter(r -> r.getTimestamp() != null && Duration.between(r.getTimestamp(), timestamp).getSeconds() <= 120)
                .filter(r -> r.getStatus() != null && r.getStatus().name().equals("OPEN"))
                .toList();
        return recent.stream().anyMatch(r -> r.getDescription() != null && r.getDescription().toLowerCase().matches(".*(panic|stampede|rush|crowd|push|trapped|emergency).*")) ? 1 : 0;
    }
    private static double acceleration(List<RiskEvent> readings) { return readings.size() < 4 ? 0 : accelerationPair(readings, readings.size() - 1); }
    private static double accelerationPair(List<RiskEvent> readings, int index) {
        if (index < 2) return 0;
        RiskEvent old = readings.get(index - 2), prior = readings.get(index - 1), current = readings.get(index);
        double first = (prior.getDensityScore() - old.getDensityScore()) / Math.max(1, Duration.between(old.getTimestamp(), prior.getTimestamp()).toSeconds());
        double second = (current.getDensityScore() - prior.getDensityScore()) / Math.max(1, Duration.between(prior.getTimestamp(), current.getTimestamp()).toSeconds());
        return Math.max(0, second - first);
    }
    private static double relativeDrop(double before, double current) { return before <= 0 ? 0 : Math.max(0, (before - current) / before); }
    private static double angularDifference(double left, double right) { return Math.abs((left - right + 180) % 360 - 180); }
    private static void add(List<String> values, boolean condition, String value) { if (condition) values.add(value); }
    private static void addUnique(List<String> values, String value) { if (!values.contains(value)) values.add(value); }
    private static double clamp(double value) { return Math.max(0, Math.min(1, Double.isFinite(value) ? value : 0)); }
    private static double round(double value) { return Math.round(clamp(value) * 100) / 100.0; }
    private static String percent(double value) { return Math.round(clamp(value) * 100) + "%"; }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private record ZoneSnapshot(Zone zone, RiskEvent latest, List<RiskEvent> readings) { }
}
