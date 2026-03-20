package com.roadrunner.place.service;

import com.roadrunner.place.dto.response.PlaceResponse;
import com.roadrunner.place.exception.PlaceNotFoundException;
import com.roadrunner.place.mapper.PlaceMapper;
import com.roadrunner.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link PlaceService}.
 * Delegates persistence to {@link PlaceRepository} and mapping to
 * {@link PlaceMapper}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceServiceImpl implements PlaceService {

    private final PlaceRepository placeRepository;
    private final PlaceMapper placeMapper;

    /** {@inheritDoc} */
    @Override
    public Page<PlaceResponse> getAllPlaces(Pageable pageable) {
        return placeRepository.findAll(pageable)
                .map(placeMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    public Page<PlaceResponse> getPlacesByType(String type, Pageable pageable) {
        return placeRepository.findByTypesContaining(type, pageable)
                .map(placeMapper::toResponse);
    }

    /**
     * {@inheritDoc}
     * 
     * @throws IllegalArgumentException if minScore is outside [0.0, 5.0]
     */
    @Override
    public Page<PlaceResponse> getPlacesByRatingScore(double minScore, Pageable pageable) {
        if (minScore < 0.0 || minScore > 5.0) {
            throw new IllegalArgumentException("minScore must be between 0.0 and 5.0, got: " + minScore);
        }
        return placeRepository.findByRatingScoreGreaterThanEqual(minScore, pageable)
                .map(placeMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    public PlaceResponse getPlaceByName(String name) {
        return placeRepository.findByNameIgnoreCase(name)
                .map(placeMapper::toResponse)
                .orElseThrow(() -> PlaceNotFoundException.forName(name));
    }

    /** {@inheritDoc} */
    @Override
    public Page<PlaceResponse> searchPlacesByName(String namePart, Pageable pageable) {
        return placeRepository.findByNameContainingIgnoreCase(namePart, pageable)
                .map(placeMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    public Page<PlaceResponse> getPlacesByPriceLevel(String priceLevel, Pageable pageable) {
        return placeRepository.findByPriceLevel(priceLevel, pageable)
                .map(placeMapper::toResponse);
    }

    /** {@inheritDoc} */
    @Override
    public PlaceResponse getPlaceById(String id) {
        return placeRepository.findById(id)
                .map(placeMapper::toResponse)
                .orElseThrow(() -> PlaceNotFoundException.forId(id));
    }

    /** {@inheritDoc} */
    @Override
    public List<PlaceResponse> getPlacesByIds(List<String> ids) {
        return placeRepository.findAllByIdIn(ids)
                .stream()
                .map(placeMapper::toResponse)
                .toList();
    }
}
