package io.github.khalilurrrahmanridoykhan.outbreaksignal.observation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A DHIS2 organisation unit (for example a district), arranged in a hierarchy. */
@Entity
@Table(name = "org_unit")
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dhis2_uid", nullable = false, unique = true, length = 11)
    private String dhis2Uid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int level;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private OrgUnit parent;

    protected OrgUnit() {
    }

    public OrgUnit(String dhis2Uid, String name, int level, OrgUnit parent) {
        this.dhis2Uid = dhis2Uid;
        this.name = name;
        this.level = level;
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public String getDhis2Uid() {
        return dhis2Uid;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public OrgUnit getParent() {
        return parent;
    }
}
