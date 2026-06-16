package com.bfsm.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LeaveConflictPreviewVo {
    private Long leaveRecordId;
    private Boolean hasConflicts;
    private List<LeaveAppointmentConflictVo> conflicts = new ArrayList<>();
}
