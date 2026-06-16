package com.bfsm.service;

import com.bfsm.common.AvatarUrlUtils;
import com.bfsm.common.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public String storeAvatar(MultipartFile file) {
        return storeImage(file, "avatars", true);
    }

    public String storeAiConsultImage(MultipartFile file) {
        return storeImage(file, "ai-consult", false);
    }

    private String storeImage(MultipartFile file, String folderName, boolean normalizeAvatarUrl) {
        if (file == null || file.isEmpty()) {
            throw new BizException(4400, "请先选择图片文件");
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String normalizedExtension = extension == null ? "" : extension.trim().toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(normalizedExtension)) {
            throw new BizException(4401, "仅支持 jpg、png、webp 格式图片");
        }

        try {
            Path uploadDir = Path.of("uploads", folderName).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String fileName = UUID.randomUUID() + "." + normalizedExtension;
            Files.copy(file.getInputStream(), uploadDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            if (normalizeAvatarUrl) {
                return AvatarUrlUtils.normalize(fileName);
            }
            return "/uploads/" + folderName + "/" + fileName;
        } catch (IOException ex) {
            throw new BizException(500, "图片上传失败");
        }
    }
}
