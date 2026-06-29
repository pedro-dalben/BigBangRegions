package com.bigbangcraft.regions.flag;

import java.util.Objects;

public class RegionFlagDefinition {
    private final String id;
    private final String category;
    private final RegionFlagValueType valueType;
    private final String defaultValue;
    private final String description;
    private final String editableBy;

    public RegionFlagDefinition(String id, String category, RegionFlagValueType valueType,
                                String defaultValue, String description, String editableBy) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.category = Objects.requireNonNull(category, "Category cannot be null");
        this.valueType = Objects.requireNonNull(valueType, "ValueType cannot be null");
        this.defaultValue = Objects.requireNonNull(defaultValue, "DefaultValue cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.editableBy = Objects.requireNonNull(editableBy, "EditableBy cannot be null");
    }

    public String getId() { return id; }
    public String getCategory() { return category; }
    public RegionFlagValueType getValueType() { return valueType; }
    public String getDefaultValue() { return defaultValue; }
    public String getDescription() { return description; }
    public String getEditableBy() { return editableBy; }
}
