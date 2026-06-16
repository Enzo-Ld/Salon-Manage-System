package com.bfsm.dto;

import lombok.Data;

import java.util.List;

@Data
public class LeaveConflictResolveReq {
    private List<Item> items;
    private String remark;

    @Data
    public static class Item {
        private Long appointmentId;
        private String action;
        private Long targetTechnicianId;
    }
}
