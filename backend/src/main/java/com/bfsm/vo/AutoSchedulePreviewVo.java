package com.bfsm.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AutoSchedulePreviewVo {
    private Integer totalPlanned;
    private Integer totalGenerated;
    private Integer totalShortage;
    private List<AutoScheduleDayVo> days = new ArrayList<>();
}
