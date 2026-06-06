package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import roomescape.domain.ReservationTime;
import roomescape.domain.Slot;
import roomescape.domain.Theme;
import roomescape.fixture.FixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class SlotServiceTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @Autowired
    private SlotService slotService;

    @Test
    void 슬롯이_존재하지_않으면_새로_생성한다() {
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출", "설명", "https://thumbnail.url");
        LocalDate date = LocalDate.of(2026, 5, 10);

        Slot slot = slotService.findOrCreate(date, time, theme);

        assertThat(slot)
                .extracting(Slot::getDate, s -> s.getTime().getId(), s -> s.getTheme().getId())
                .containsExactly(date, time.getId(), theme.getId());
    }

    @Test
    void 슬롯이_이미_존재하면_기존_슬롯을_반환한다() {
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출", "설명", "https://thumbnail.url");
        LocalDate date = LocalDate.of(2026, 5, 10);

        Slot first = slotService.findOrCreate(date, time, theme);
        Slot second = slotService.findOrCreate(date, time, theme);

        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    void 시간만_다르면_별개의_슬롯을_생성한다() {
        ReservationTime time1 = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        ReservationTime time2 = fixtureGenerator.saveReservationTime(LocalTime.of(11, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출", "설명", "https://thumbnail.url");
        LocalDate date = LocalDate.of(2026, 5, 10);

        Slot first = slotService.findOrCreate(date, time1, theme);
        Slot second = slotService.findOrCreate(date, time2, theme);

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void 테마만_다르면_별개의_슬롯을_생성한다() {
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme1 = fixtureGenerator.saveTheme("방탈출1", "설명", "https://thumbnail1.url");
        Theme theme2 = fixtureGenerator.saveTheme("방탈출2", "설명", "https://thumbnail2.url");
        LocalDate date = LocalDate.of(2026, 5, 10);

        Slot first = slotService.findOrCreate(date, time, theme1);
        Slot second = slotService.findOrCreate(date, time, theme2);

        assertThat(second.getId()).isNotEqualTo(first.getId());
    }
}
