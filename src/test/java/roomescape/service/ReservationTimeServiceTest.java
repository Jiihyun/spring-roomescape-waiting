package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import roomescape.domain.ReservationTime;
import roomescape.domain.Theme;
import roomescape.dto.request.ReservationTimeRequest;
import roomescape.dto.response.AvailableReservationTimeResponse;
import roomescape.dto.response.ReservationTimeResponse;
import roomescape.exception.code.ReservationErrorCode;
import roomescape.exception.code.ReservationTimeErrorCode;
import roomescape.exception.code.ThemeErrorCode;
import roomescape.exception.domain.ReservationException;
import roomescape.exception.domain.ReservationTimeException;
import roomescape.exception.domain.ThemeException;
import roomescape.fixture.FixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class ReservationTimeServiceTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @Autowired
    private ReservationTimeService reservationTimeService;

    @Test
    void 예약_시간을_생성할_수_있다() {
        LocalTime startAt = LocalTime.of(10, 0);
        ReservationTimeResponse response = reservationTimeService.create(new ReservationTimeRequest(startAt));
        assertThat(response.startAt()).isEqualTo(startAt);
    }

    @Test
    void 이미_존재하는_예약_시간을_저장_시_예외를_반환한다() {
        LocalTime startAt = LocalTime.of(10, 0);
        fixtureGenerator.saveReservationTime(startAt);

        assertThatThrownBy(() -> reservationTimeService.create(new ReservationTimeRequest(startAt)))
                .isInstanceOf(ReservationTimeException.class)
                .hasMessage(ReservationTimeErrorCode.RESERVATION_TIME_ALREADY_EXISTS.getMessage());
    }

    @Test
    void 테마_및_날짜에_따른_예약시간을_조회할_수_있다() {
        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        LocalDate baseDate = LocalDate.of(2026, 5, 31);
        ReservationTime reservedTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        ReservationTime notReservedTime = fixtureGenerator.saveReservationTime(LocalTime.of(11, 0));
        fixtureGenerator.saveReservation("예약1", baseDate, reservedTime, theme);

        List<AvailableReservationTimeResponse> responses =
                reservationTimeService.getReservationTimes(theme.getId(), baseDate, LocalDate.of(2026, 5, 30));

        assertThat(responses)
                .extracting(AvailableReservationTimeResponse::id, AvailableReservationTimeResponse::startAt, AvailableReservationTimeResponse::reserved)
                .containsExactlyInAnyOrder(
                        tuple(reservedTime.getId(), reservedTime.getStartAt(), true),
                        tuple(notReservedTime.getId(), notReservedTime.getStartAt(), false)
                );
    }

    @Test
    void 예약시간_조회시_날짜가_오늘_이전이면_예외가_발생한다() {
        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        LocalDate today = LocalDate.of(2026, 5, 31);
        LocalDate invalidDate = today.minusDays(1);

        assertThatThrownBy(() -> reservationTimeService.getReservationTimes(theme.getId(), invalidDate, today))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.PAST_DATE_NOT_ALLOWED.getMessage());
    }

    @Test
    void 예약시간_조회시_테마가_존재하지_않으면_예외가_발생한다() {
        assertThatThrownBy(() -> reservationTimeService.getReservationTimes(0L, LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 31)))
                .isInstanceOf(ThemeException.class)
                .hasMessage(ThemeErrorCode.THEME_NOT_FOUND.getMessage());
    }

    @Test
    void 예약시간을_삭제할_수_있다() {
        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        LocalDate today = LocalDate.of(2026, 5, 31);
        ReservationTimeResponse response = reservationTimeService.create(new ReservationTimeRequest(LocalTime.of(10, 0)));

        int beforeSize = reservationTimeService.getReservationTimes(theme.getId(), today, today).size();
        reservationTimeService.delete(response.id());

        List<AvailableReservationTimeResponse> reservations = reservationTimeService.getReservationTimes(theme.getId(), today, today);
        assertAll(
                () -> assertThat(reservations).hasSize(beforeSize - 1),
                () -> assertThat(reservations).extracting(AvailableReservationTimeResponse::id).doesNotContain(response.id())
        );
    }

    @Test
    void 예약시간_삭제시_관련_예약이_존재하면_예외를_반환한다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        fixtureGenerator.saveReservation("예약1", LocalDate.of(2026, 5, 8), reservationTime, theme);

        assertThatThrownBy(() -> reservationTimeService.delete(reservationTime.getId()))
                .isInstanceOf(ReservationTimeException.class)
                .hasMessage(ReservationTimeErrorCode.RESERVATION_TIME_HAS_RESERVATION.getMessage());
    }

    @Test
    void 삭제할_예약시간이_존재하지_않으면_예외를_반환한다() {
        assertThatThrownBy(() -> reservationTimeService.delete(0L))
                .isInstanceOf(ReservationTimeException.class)
                .hasMessage(ReservationTimeErrorCode.RESERVATION_TIME_NOT_FOUND.getMessage());
    }
}
