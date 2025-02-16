-- Удаляем старое ограничение
ALTER TABLE export_configs DROP CONSTRAINT export_configs_strategy_type_check;

-- Добавляем новое ограничение с точно таким же форматом
ALTER TABLE export_configs
    ADD CONSTRAINT export_configs_strategy_type_check
        CHECK ((strategy_type::text = ANY ((ARRAY[
            'DEFAULT'::character varying,
            'CLEAN_URLS'::character varying,
            'FILTER_PRICES'::character varying,
            'MERGE_PRODUCTS'::character varying,
            'TASK_BASED_FILTER'::character varying
            ])::text[])));