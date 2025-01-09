package by.zoomos_v2.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Data;

@Data
@Entity
@DiscriminatorValue("simple.ru") // Указываем тип для отличия сущности
public class SimpleFileEntity extends BaseFileEntity{

    private String competitorStatus;
    private String parseDate;
    private String webcacheUrl;
    private String competitorUrl;
}
