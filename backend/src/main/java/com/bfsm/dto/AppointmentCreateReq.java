package com.bfsm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentCreateReq {
    @NotNull
    private Long serviceItemId;
    private Long technicianId;
    @NotNull
    private LocalDateTime appointmentTime;
}

