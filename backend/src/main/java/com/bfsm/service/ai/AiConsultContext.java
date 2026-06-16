package com.bfsm.service.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiConsultContext {
    private byte[] imageBytes;
    private String imageMimeType;
    private String inputText;
    private List<AiServiceCatalogItem> serviceCatalog;
    private List<AiProductCatalogItem> productCatalog;
}
