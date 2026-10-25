package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.khalilurrrahmanridoykhan.outbreaksignal.TestcontainersConfiguration;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnit;
import io.github.khalilurrrahmanridoykhan.outbreaksignal.observation.OrgUnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificationRecipientServiceIntegrationTest {

    @Autowired
    private NotificationRecipientService service;
    @Autowired
    private NotificationRecipientRepository recipients;
    @Autowired
    private OrgUnitRepository orgUnits;

    private OrgUnit kpangaKabonde;
    private OrgUnit mafindor;

    @BeforeEach
    void seedTwoOrgUnits() {
        kpangaKabonde = orgUnits.save(new OrgUnit("QwMiPiME3bA", "Kpanga Kabonde", 3, null));
        mafindor = orgUnits.save(new OrgUnit("fbfJHSPpUQD", "Mafindor", 3, null));
    }

    @AfterEach
    void cleanUp() {
        recipients.deleteAll();
        orgUnits.deleteAll();
    }

    @Test
    void createListsOnlyUnderItsOwnOrgUnit() {
        service.create(kpangaKabonde.getId(), "kpanga@example.org");
        service.create(mafindor.getId(), "mafindor@example.org");

        assertThat(service.list(kpangaKabonde.getId())).extracting("email").containsExactly("kpanga@example.org");
        assertThat(service.list(mafindor.getId())).extracting("email").containsExactly("mafindor@example.org");
        assertThat(service.list(null)).hasSize(2); // no orgUnitId filter -- every recipient, any org unit
    }

    @Test
    void creatingTheSameOrgUnitAndEmailTwiceIsRejected() {
        service.create(kpangaKabonde.getId(), "kpanga@example.org");

        assertThatThrownBy(() -> service.create(kpangaKabonde.getId(), "kpanga@example.org"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void creatingAgainstAnUnknownOrgUnitIsRejected() {
        assertThatThrownBy(() -> service.create(999_999L, "nobody@example.org"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingARealRecipientRemovesItAndDeletingAnUnknownIdFails() {
        var created = service.create(kpangaKabonde.getId(), "kpanga@example.org");

        service.delete(created.id());

        assertThat(service.list(kpangaKabonde.getId())).isEmpty();
        assertThatThrownBy(() -> service.delete(created.id())).isInstanceOf(NotificationRecipientNotFoundException.class);
    }
}
