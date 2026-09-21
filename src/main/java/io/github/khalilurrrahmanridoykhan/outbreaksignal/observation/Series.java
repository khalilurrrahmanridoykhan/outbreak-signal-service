package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One measured quantity (a DHIS2 data element) for one organisation unit. */
@Entity
@Table(name = "series", uniqueConstraints = @UniqueConstraint(
        name = "uq_series_org_unit_data_element", columnNames = {"org_unit_id", "dhis2_data_element_uid"}))
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_id", nullable = false)
    private OrgUnit orgUnit;

    @Column(name = "dhis2_data_element_uid", nullable = false, length = 11)
    private String dhis2DataElementUid;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Frequency frequency = Frequency.WEEKLY;

    protected Series() {
    }

    public Series(OrgUnit orgUnit, String dhis2DataElementUid, String name) {
        this.orgUnit = orgUnit;
        this.dhis2DataElementUid = dhis2DataElementUid;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public OrgUnit getOrgUnit() {
        return orgUnit;
    }

    public String getDhis2DataElementUid() {
        return dhis2DataElementUid;
    }

    public String getName() {
        return name;
    }

    public Frequency getFrequency() {
        return frequency;
    }
}
