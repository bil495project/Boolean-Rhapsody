package com.roadrunner.route.service;

import java.util.List;
import java.util.Map;

import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RouteAnchorRequest;
import com.roadrunner.route.dto.request.RouteBoundarySelectionRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;
import com.roadrunner.route.dto.request.RoutePoiSlotRequest;
import com.roadrunner.route.service.RouteConstraintSpec.BoundaryKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Unit Tests - RouteConstraintResolver")
class RouteConstraintResolverTest {

    private final RouteConstraintResolver resolver = new RouteConstraintResolver();

    private GenerateRoutesRequest requestWithConstraints(RouteConstraintsRequest constraints) {
        GenerateRoutesRequest req = new GenerateRoutesRequest();
        req.setConstraints(constraints);
        return req;
    }

    private Map<String, String> userVector() {
        return Map.of("weight_toplamPoiYogunlugu", "0.5");
    }

    @Test
    @DisplayName("stayAtHotel true iken explicit override yoksa iki taraf da hotel olur")
    void shouldMapStayAtHotelToHotelLoopBoundaries() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStayAtHotel(true);

        RouteConstraintSpec spec = resolver.resolve(requestWithConstraints(constraints), userVector());

        assertThat(spec.startBoundary().kind()).isEqualTo(BoundaryKind.HOTEL);
        assertThat(spec.endBoundary().kind()).isEqualTo(BoundaryKind.HOTEL);
        assertThat(spec.sameHotelLoop()).isTrue();
    }

    @Test
    @DisplayName("stayAtHotel true ve startWithPoi true ise start override edilir end hotel kalir")
    void shouldLetStartPoiOverrideStayAtHotelPerSide() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStayAtHotel(true);
        constraints.setStartWithPoi(true);
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", "l1", null, null));

        RouteConstraintSpec spec = resolver.resolve(requestWithConstraints(constraints), userVector());

        assertThat(spec.startBoundary().kind()).isEqualTo(BoundaryKind.PLACE);
        assertThat(spec.startBoundary().placeId()).isEqualTo("l1");
        assertThat(spec.endBoundary().kind()).isEqualTo(BoundaryKind.HOTEL);
    }

    @Test
    @DisplayName("stayAtHotel true ve endWithPoi true ise end override edilir start hotel kalir")
    void shouldLetEndPoiOverrideStayAtHotelPerSide() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStayAtHotel(true);
        constraints.setEndWithPoi(true);
        constraints.setEndAnchor(new RouteAnchorRequest("TYPE", null, "PARK", null));

        RouteConstraintSpec spec = resolver.resolve(requestWithConstraints(constraints), userVector());

        assertThat(spec.startBoundary().kind()).isEqualTo(BoundaryKind.HOTEL);
        assertThat(spec.endBoundary().kind()).isEqualTo(BoundaryKind.TYPE);
        assertThat(spec.endBoundary().poiType()).isEqualTo("PARK");
    }

    @Test
    @DisplayName("Anchor tek basina boundary olusturmaz")
    void shouldIgnoreStandaloneAnchorsWithoutBoundaryFlags() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", "l1", null, null));
        constraints.setEndAnchor(new RouteAnchorRequest("TYPE", null, "HOTEL", null));

        RouteConstraintSpec spec = resolver.resolve(requestWithConstraints(constraints), userVector());

        assertThat(spec.startBoundary().kind()).isEqualTo(BoundaryKind.NONE);
        assertThat(spec.endBoundary().kind()).isEqualTo(BoundaryKind.NONE);
    }

    @Test
    @DisplayName("Explicit ve legacy boundary alanlari ayni tarafta mix edilirse hata verir")
    void shouldRejectMixedExplicitAndLegacyFieldsPerSide() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStartPoint(new RouteBoundarySelectionRequest("HOTEL", null, null, null));
        constraints.setStartWithHotel(true);

        assertThatThrownBy(() -> resolver.resolve(requestWithConstraints(constraints), userVector()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("start boundary");
    }

    @Test
    @DisplayName("PLACE anchor placeId olmadan hata verir")
    void shouldValidatePlaceAnchorRequiresPlaceId() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setStartWithPoi(true);
        constraints.setStartAnchor(new RouteAnchorRequest("PLACE", null, null, null));

        assertThatThrownBy(() -> resolver.resolve(requestWithConstraints(constraints), userVector()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("startAnchor PLACE requires placeId");
    }

    @Test
    @DisplayName("TYPE poi slot poiType olmadan hata verir")
    void shouldValidateTypeSlotRequiresPoiType() {
        RouteConstraintsRequest constraints = new RouteConstraintsRequest();
        constraints.setPoiSlots(List.of(new RoutePoiSlotRequest("TYPE", null, null, null)));

        assertThatThrownBy(() -> resolver.resolve(requestWithConstraints(constraints), userVector()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("TYPE requires poiType");
    }
}
