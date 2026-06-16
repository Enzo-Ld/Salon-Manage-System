package com.bfsm.service;

import com.bfsm.entity.ServiceItem;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.vo.TechnicianRecommendationVo;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SchedulingService {
    private final TechnicianMapper technicianMapper;
    private final AppointmentMapper appointmentMapper;
    private final ScheduleConflictChecker scheduleConflictChecker;
    private final ServiceItemMapper serviceItemMapper;

    public SchedulingService(TechnicianMapper technicianMapper, AppointmentMapper appointmentMapper,
                             ScheduleConflictChecker scheduleConflictChecker, ServiceItemMapper serviceItemMapper) {
        this.technicianMapper = technicianMapper;
        this.appointmentMapper = appointmentMapper;
        this.scheduleConflictChecker = scheduleConflictChecker;
        this.serviceItemMapper = serviceItemMapper;
    }

    public TechnicianRecommendationVo recommendTechnician(Long serviceItemId, LocalDateTime appointmentTime) {
        ServiceItem item = serviceItemMapper.selectById(serviceItemId);
        if (item == null || item.getDurationMin() == null) {
            return null;
        }
        List<Technician> candidates = technicianMapper.selectActiveByServiceItemId(serviceItemId);
        return recommendFromCandidates(item, appointmentTime, candidates);
    }

    public TechnicianRecommendationVo recommendFromCandidates(ServiceItem item, LocalDateTime appointmentTime,
                                                              List<Technician> candidates) {
        if (item == null || item.getDurationMin() == null || candidates == null || candidates.isEmpty()) {
            return null;
        }

        LocalDateTime appointmentEndTime = appointmentTime.plusMinutes(item.getDurationMin());
        return candidates.stream()
                .filter(technician -> scheduleConflictChecker.isWorkingDuring(technician.getId(), appointmentTime, appointmentEndTime))
                .filter(technician -> !scheduleConflictChecker.isOnLeaveDuring(technician.getId(), appointmentTime, appointmentEndTime))
                .filter(technician -> !scheduleConflictChecker.hasConflictInRange(technician.getId(), appointmentTime, appointmentEndTime))
                .map(technician -> toRecommendation(technician, appointmentTime, appointmentEndTime, item.getDurationMin()))
                .max(Comparator.comparingDouble(TechnicianRecommendationVo::getScore))
                .orElse(null);
    }

    private TechnicianRecommendationVo toRecommendation(Technician technician, LocalDateTime appointmentTime,
                                                        LocalDateTime appointmentEndTime, Integer durationMin) {
        long dayLoad = appointmentMapper.countDayLoad(technician.getId(), appointmentTime.toLocalDate());
        double rating = technician.getScore() == null ? 5.0D : technician.getScore().doubleValue();
        double score = 60 + rating * 8 - dayLoad * 10;

        List<String> reasons = new ArrayList<>();
        reasons.add("具备当前服务项目能力");
        reasons.add("该服务时段完整落在排班内");
        reasons.add("该时间段没有请假和预约冲突");
        reasons.add("今日负载 " + dayLoad + " 单");
        reasons.add("历史评分 " + String.format("%.1f", rating));

        TechnicianRecommendationVo recommendation = new TechnicianRecommendationVo();
        recommendation.setTechnicianId(technician.getId());
        recommendation.setTechnicianName(technician.getName());
        recommendation.setScore(score);
        recommendation.setReason(String.join("，", reasons));
        recommendation.setAppointmentTime(appointmentTime);
        recommendation.setAppointmentEndTime(appointmentEndTime);
        recommendation.setDurationMinSnapshot(durationMin);
        return recommendation;
    }
}
