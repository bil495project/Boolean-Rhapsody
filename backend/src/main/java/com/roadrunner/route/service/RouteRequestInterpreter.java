package com.roadrunner.route.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.roadrunner.route.dto.request.GenerateRoutesRequest;
import com.roadrunner.route.dto.request.RouteConstraintsRequest;

@Service
public class RouteRequestInterpreter {

    private final RoutePreferenceVectorMapper vectorMapper;
    private final RouteConstraintResolver constraintResolver;

    public RouteRequestInterpreter(RoutePreferenceVectorMapper vectorMapper,
                                   RouteConstraintResolver constraintResolver) {
        this.vectorMapper = vectorMapper;
        this.constraintResolver = constraintResolver;
    }

    public ResolvedRouteGenerationRequest interpret(GenerateRoutesRequest req) {
        Map<String, String> userVector = vectorMapper.buildGenerationUserVector(req);
        RouteConstraintsRequest constraints = req.getConstraints();

        if (constraintResolver.shouldUseLegacyFallback(constraints)) {
            boolean stayAtHotel = constraints == null
                    || constraints.getStayAtHotel() == null
                    || constraints.getStayAtHotel();
            return new ResolvedRouteGenerationRequest(userVector, true, stayAtHotel, null);
        }

        RouteConstraintSpec spec = constraintResolver.resolve(req, userVector);
        return new ResolvedRouteGenerationRequest(userVector, false, false, spec);
    }
}
