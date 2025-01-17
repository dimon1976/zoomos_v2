package by.zoomos_v2.util;

import lombok.Data;

import java.util.List;

@Data
public class EntityFieldGroup {
    private String entityName;
    private List<EntityField> fields;
}
