package by.zoomos_v2.util;

import by.zoomos_v2.model.Product;
import by.zoomos_v2.model.RegionData;
import by.zoomos_v2.model.SiteData;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class EntityRegistryService {

    private static final List<Class<?>> ENTITY_CLASSES = Arrays.asList(Product.class, RegionData.class, SiteData.class);
    private static final String[] excludeField = new String[]{"configName"};

    // Получаем все сущности
    public List<Class<?>> getEntityClasses() {
        return ENTITY_CLASSES;
    }
}
