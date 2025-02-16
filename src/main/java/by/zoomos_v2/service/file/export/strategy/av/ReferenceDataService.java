package by.zoomos_v2.service.file.export.strategy.av;

import by.zoomos_v2.model.entity.ReferenceData;
import by.zoomos_v2.repository.ReferenceDataRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReferenceDataService {
    private final ReferenceDataRepository referenceDataRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void uploadReferenceData(Long clientId, String taskNumber, List<Map<String, Object>> referenceData) {
        log.debug("Загрузка справочных данных для клиента {} и задачи {}", clientId, taskNumber);

        // Деактивируем существующие справочные данные
        referenceDataRepository.deactivateExisting(clientId, taskNumber);

        // Создаем новую запись
        ReferenceData newReferenceData = new ReferenceData();
        newReferenceData.setClientId(clientId);
        newReferenceData.setTaskNumber(taskNumber);
        newReferenceData.setUploadedAt(LocalDateTime.now());
        newReferenceData.setActive(true);

        try {
            newReferenceData.setReferenceDataJson(objectMapper.writeValueAsString(referenceData));
            referenceDataRepository.save(newReferenceData);
            log.info("Справочные данные успешно загружены");
        } catch (JsonProcessingException e) {
            log.error("Ошибка сериализации справочных данных: {}", e.getMessage());
            throw new IllegalStateException("Ошибка сохранения справочных данных", e);
        }
    }
}
