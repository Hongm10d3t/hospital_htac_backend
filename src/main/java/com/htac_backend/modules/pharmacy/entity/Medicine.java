package com.htac_backend.modules.pharmacy.entity;

import com.htac_backend.shared.audit.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "medicine")
public class Medicine extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 30)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "active_ingredient", nullable = false, length = 200)
    private String activeIngredient;
    @Column(nullable = false, length = 100)
    private String strength;
    @Column(name = "dosage_form", nullable = false, length = 50)
    private String dosageForm;
    @Column(name = "base_unit", nullable = false, length = 20)
    private String baseUnit;
    @Column(name = "minimum_stock", nullable = false, precision = 14, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;
    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Medicine() { }

    public Medicine(String code, String name, String activeIngredient, String strength,
            String dosageForm, String baseUnit, BigDecimal minimumStock, int leadTimeDays) {
        this.code = code;
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.strength = strength;
        this.dosageForm = dosageForm;
        this.baseUnit = baseUnit;
        this.minimumStock = minimumStock;
        this.leadTimeDays = leadTimeDays;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getActiveIngredient() { return activeIngredient; }
    public String getStrength() { return strength; }
    public String getDosageForm() { return dosageForm; }
    public String getBaseUnit() { return baseUnit; }
    public BigDecimal getMinimumStock() { return minimumStock; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public boolean isActive() { return active; }

    public void update(String code, String name, String activeIngredient, String strength,
            String dosageForm, String baseUnit, BigDecimal minimumStock, int leadTimeDays, boolean active) {
        this.code = code;
        this.name = name;
        this.activeIngredient = activeIngredient;
        this.strength = strength;
        this.dosageForm = dosageForm;
        this.baseUnit = baseUnit;
        this.minimumStock = minimumStock;
        this.leadTimeDays = leadTimeDays;
        this.active = active;
    }
}
