package com.bigbangcraft.regions.flag;

import java.util.Objects;

public class RegionFlag {
    private final String id;
    private final String dataType;
    private final FlagPolicy defaultValue;
    private final boolean editableByPlayer;
    private final boolean editableByAdmin;
    private final String description;
    private final String category;
    private final boolean supported;

    public RegionFlag(String id, String dataType, FlagPolicy defaultValue, boolean editableByPlayer,
                      boolean editableByAdmin, String description, String category, boolean supported) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.dataType = Objects.requireNonNull(dataType, "DataType cannot be null");
        this.defaultValue = Objects.requireNonNull(defaultValue, "DefaultValue cannot be null");
        this.editableByPlayer = editableByPlayer;
        this.editableByAdmin = editableByAdmin;
        this.description = description;
        this.category = category;
        this.supported = supported;
    }

    public String getId() {
        return id;
    }

    public String getDataType() {
        return dataType;
    }

    public FlagPolicy getDefaultValue() {
        return defaultValue;
    }

    public boolean isEditableByPlayer() {
        return editableByPlayer;
    }

    public boolean isEditableByAdmin() {
        return editableByAdmin;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public boolean isSupported() {
        return supported;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionFlag that = (RegionFlag) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
