package com.fitflow.fitnessclass;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.common.error.ApiException;
import com.fitflow.fitnessclass.dto.ClassDetailResponse;
import com.fitflow.fitnessclass.dto.ClassMetadataResponse;
import com.fitflow.fitnessclass.dto.CreateClassRequest;
import com.fitflow.fitnessclass.dto.UpdateClassRequest;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassCategory;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.model.ContraindicationTag;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FitnessClassService {

    private final FitnessClassRepository fitnessClassRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ClassMetadataResponse getMetadata() {
        return new ClassMetadataResponse(
                Arrays.stream(ClassIntensity.values()).map(Enum::name).toList(),
                Arrays.stream(ClassImpactLevel.values()).map(Enum::name).toList(),
                Arrays.stream(ContraindicationTag.values()).map(ContraindicationTag::getValue).toList(),
                Arrays.stream(ClassCategory.values()).map(Enum::name).toList()
        );
    }

    @Transactional
    public ClassDetailResponse createClass(CreateClassRequest request, Long createdBy) {
        if (fitnessClassRepository.existsByName(request.name().trim())) {
            throw ApiException.conflict("Fitness class with name '" + request.name().trim() + "' already exists");
        }

        List<String> validatedTags = validateAndCleanTags(request.contraindicationTags());
        String tagsJson = serializeTags(validatedTags);

        FitnessClass fitnessClass = FitnessClass.builder()
                .name(request.name().trim())
                .description(request.description())
                .category(request.category().name())
                .durationMinutes(request.durationMinutes())
                .intensity(request.intensity())
                .impactLevel(request.impactLevel())
                .metValue(request.metValue())
                .contraindicationTags(tagsJson)
                .minAge(request.minAge() != null ? request.minAge() : 14)
                .defaultCapacity(request.defaultCapacity() != null ? request.defaultCapacity() : 20)
                .active(true)
                .metadataReviewed(true) // Admin creation sets metadata_reviewed=true
                .createdBy(createdBy)
                .build();

        FitnessClass saved = fitnessClassRepository.save(fitnessClass);
        log.info("Fitness class created id={} name='{}' by user id={}", saved.getId(), saved.getName(), createdBy);
        return toDetailResponse(saved);
    }

    @Transactional
    public ClassDetailResponse createDraftClass(CreateClassRequest request, Long createdBy) {
        if (fitnessClassRepository.existsByName(request.name().trim())) {
            throw ApiException.conflict("Fitness class with name '" + request.name().trim() + "' already exists");
        }

        List<String> validatedTags = validateAndCleanTags(request.contraindicationTags());
        String tagsJson = serializeTags(validatedTags);

        FitnessClass fitnessClass = FitnessClass.builder()
                .name(request.name().trim())
                .description(request.description())
                .category(request.category().name())
                .durationMinutes(request.durationMinutes())
                .intensity(request.intensity())
                .impactLevel(request.impactLevel())
                .metValue(request.metValue())
                .contraindicationTags(tagsJson)
                .minAge(request.minAge() != null ? request.minAge() : 14)
                .defaultCapacity(request.defaultCapacity() != null ? request.defaultCapacity() : 20)
                .active(true)
                .metadataReviewed(false)
                .createdBy(createdBy)
                .build();

        FitnessClass saved = fitnessClassRepository.save(fitnessClass);
        log.info("Draft fitness class created id={} name='{}' by user id={} with metadata_reviewed=false", saved.getId(), saved.getName(), createdBy);
        return toDetailResponse(saved);
    }

    @Transactional
    public ClassDetailResponse updateClass(Long id, UpdateClassRequest request) {
        FitnessClass existing = fitnessClassRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + id));

        if (!existing.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("Fitness class version mismatch: expected "
                    + existing.getVersion() + " but got " + request.version());
        }

        if (fitnessClassRepository.existsByNameAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Fitness class with name '" + request.name().trim() + "' already exists");
        }

        List<String> validatedTags = validateAndCleanTags(request.contraindicationTags());
        String tagsJson = serializeTags(validatedTags);

        existing.setName(request.name().trim());
        existing.setDescription(request.description());
        existing.setCategory(request.category().name());
        existing.setDurationMinutes(request.durationMinutes());
        existing.setIntensity(request.intensity());
        existing.setImpactLevel(request.impactLevel());
        existing.setMetValue(request.metValue());
        existing.setContraindicationTags(tagsJson);
        existing.setMinAge(request.minAge() != null ? request.minAge() : 14);
        existing.setDefaultCapacity(request.defaultCapacity() != null ? request.defaultCapacity() : 20);

        FitnessClass saved = fitnessClassRepository.saveAndFlush(existing);
        log.info("Fitness class updated id={} name='{}'", saved.getId(), saved.getName());
        return toDetailResponse(saved);
    }

    @Transactional
    public ClassDetailResponse deactivateClass(Long id) {
        FitnessClass existing = fitnessClassRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + id));
        existing.setActive(false);
        FitnessClass saved = fitnessClassRepository.save(existing);
        log.info("Fitness class deactivated id={}", id);
        return toDetailResponse(saved);
    }

    @Transactional
    public ClassDetailResponse activateClass(Long id) {
        FitnessClass existing = fitnessClassRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + id));
        existing.setActive(true);
        FitnessClass saved = fitnessClassRepository.save(existing);
        log.info("Fitness class activated id={}", id);
        return toDetailResponse(saved);
    }

    @Transactional
    public ClassDetailResponse confirmMetadata(Long id) {
        FitnessClass existing = fitnessClassRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + id));
        existing.setMetadataReviewed(true);
        FitnessClass saved = fitnessClassRepository.save(existing);
        log.info("Fitness class metadata confirmed id={}", id);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassDetailResponse> getClasses(
            ClassCategory category,
            ClassIntensity intensity,
            ClassImpactLevel impactLevel,
            String q,
            int page,
            int size,
            boolean isAdmin) {

        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize, Sort.by("name").ascending());

        Specification<FitnessClass> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!isAdmin) {
                predicates.add(cb.isTrue(root.get("active")));
                predicates.add(cb.isTrue(root.get("metadataReviewed")));
            }

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category.name()));
            }

            if (intensity != null) {
                predicates.add(cb.equal(root.get("intensity"), intensity));
            }

            if (impactLevel != null) {
                predicates.add(cb.equal(root.get("impactLevel"), impactLevel));
            }

            if (q != null && !q.trim().isEmpty()) {
                String term = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), term),
                        cb.like(cb.lower(root.get("description")), term)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FitnessClass> classPage = fitnessClassRepository.findAll(spec, pageable);
        return PageResponse.of(classPage, this::toDetailResponse);
    }

    @Transactional(readOnly = true)
    public ClassDetailResponse getClassById(Long id, boolean isAdmin) {
        FitnessClass fc = fitnessClassRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Fitness class not found with id: " + id));

        if (!isAdmin && (!Boolean.TRUE.equals(fc.getActive()) || !Boolean.TRUE.equals(fc.getMetadataReviewed()))) {
            throw ApiException.notFound("Fitness class not found with id: " + id);
        }

        return toDetailResponse(fc);
    }

    private List<String> validateAndCleanTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            String normalized = tag.trim().toLowerCase();
            if (!ContraindicationTag.ALLOWED_VALUES.contains(normalized)) {
                throw ApiException.badRequest("Invalid contraindication tag: '" + tag + "'. Allowed tags: " + ContraindicationTag.ALLOWED_VALUES);
            }
            if (!cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
        }
        return cleaned;
    }

    private String serializeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("Failed to serialize contraindication tags");
        }
    }

    public List<String> deserializeTags(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private ClassDetailResponse toDetailResponse(FitnessClass entity) {
        ClassCategory cat = null;
        if (entity.getCategory() != null) {
            try {
                cat = ClassCategory.valueOf(entity.getCategory().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new ClassDetailResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                cat,
                entity.getDurationMinutes(),
                entity.getIntensity(),
                entity.getImpactLevel(),
                entity.getMetValue(),
                deserializeTags(entity.getContraindicationTags()),
                entity.getMinAge(),
                entity.getDefaultCapacity(),
                entity.getActive(),
                entity.getMetadataReviewed(),
                entity.getCreatedBy(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
