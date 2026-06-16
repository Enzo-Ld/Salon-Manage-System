package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AutoScheduleDayVo {
    private LocalDate workDate;
    private Integer plannedCount;
    private Integer generatedCount;
    private Integer shortageCount;
    private List<String> warnings = new ArrayList<>();
    private List<AutoScheduleItemVo> items = new ArrayList<>();
}
