package com.bfsm.vo;

import lombok.Data;

@Data
public class AiConsultTurnVo {
    private AiConsultSessionVo session;
    private AiConsultMessageVo assistantMessage;
}
