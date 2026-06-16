package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.cache.CacheService;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.CommentEntity;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.TechnicianMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TechnicianRatingService {
    private static final BigDecimal DEFAULT_SCORE = BigDecimal.valueOf(5).setScale(2, RoundingMode.HALF_UP);
    private static final String CACHE_TECHNICIANS = "list:technicians";

    private final TechnicianMapper technicianMapper;
    private final AppointmentMapper appointmentMapper;
    private final CommentMapper commentMapper;
    private final CacheService cacheService;

    public TechnicianRatingService(TechnicianMapper technicianMapper, AppointmentMapper appointmentMapper,
                                   CommentMapper commentMapper, CacheService cacheService) {
        this.technicianMapper = technicianMapper;
        this.appointmentMapper = appointmentMapper;
        this.commentMapper = commentMapper;
        this.cacheService = cacheService;
    }

    @Transactional
    public void refreshRating(Long technicianId) {
        if (technicianId == null) {
            return;
        }
        Technician technician = technicianMapper.selectById(technicianId);
        if (technician == null) {
            return;
        }

        List<Appointment> appointments = appointmentMapper.selectList(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getTechnicianId, technicianId));
        Set<Long> appointmentIds = appointments.stream().map(Appointment::getId).collect(Collectors.toSet());
        List<CommentEntity> comments = appointmentIds.isEmpty()
                ? List.of()
                : commentMapper.selectList(new LambdaQueryWrapper<CommentEntity>()
                        .in(CommentEntity::getAppointmentId, appointmentIds)
                        .eq(CommentEntity::getStatus, "APPROVED"));

        BigDecimal ratingAvg = comments.isEmpty()
                ? DEFAULT_SCORE
                : BigDecimal.valueOf(comments.stream().mapToInt(CommentEntity::getRating).average().orElse(5))
                        .setScale(2, RoundingMode.HALF_UP);
        technician.setRatingAvg(ratingAvg);
        technician.setRatingCount(comments.size());
        technician.setScore(effectiveScore(ratingAvg, technician.getScoreOverride()));
        technicianMapper.updateById(technician);
        cacheService.evict(CACHE_TECHNICIANS);
    }

    public void prepareForAdminSave(Technician body, Technician existing) {
        BigDecimal ratingAvg = existing == null || existing.getRatingAvg() == null
                ? DEFAULT_SCORE
                : scaleScore(existing.getRatingAvg());
        Integer ratingCount = existing == null || existing.getRatingCount() == null ? 0 : existing.getRatingCount();
        BigDecimal scoreOverride = body.getScoreOverride();
        if (scoreOverride == null && body.getScore() != null) {
            scoreOverride = body.getScore();
        }
        scoreOverride = scoreOverride == null ? null : scaleScore(scoreOverride);

        body.setRatingAvg(ratingAvg);
        body.setRatingCount(ratingCount);
        body.setScoreOverride(scoreOverride);
        body.setScore(effectiveScore(ratingAvg, scoreOverride));
    }

    private BigDecimal effectiveScore(BigDecimal ratingAvg, BigDecimal scoreOverride) {
        return scoreOverride == null ? scaleScore(ratingAvg) : scaleScore(scoreOverride);
    }

    private BigDecimal scaleScore(BigDecimal value) {
        BigDecimal score = value == null ? DEFAULT_SCORE : value;
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            score = BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.valueOf(5)) > 0) {
            score = BigDecimal.valueOf(5);
        }
        return score.setScale(2, RoundingMode.HALF_UP);
    }
}
