package com.bfsm.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TechnicianServiceReq {
    private List<Long> serviceItemIds = new ArrayList<>();
}
