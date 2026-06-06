package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
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
import roomescape.dto.request.WaitingRequest;
import roomescape.dto.response.WaitingResponse;
import roomescape.dto.response.WaitingWithRankResponse;
import roomescape.exception.code.WaitingErrorCode;
import roomescape.exception.domain.WaitingException;
import roomescape.fixture.FixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class WaitingServiceTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @Autowired
    private WaitingService waitingService;

    @Test
    void 예약이_없는_상태에서_대기를_신청한_경우_예외가_발생한다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        fixtureGenerator.saveSlot(reservationDate, reservationTime, theme);

        WaitingRequest request = new WaitingRequest(reservationDate, reservationTime.getId(), theme.getId(), "대기신청자");

        assertThatThrownBy(() -> waitingService.create(request, currentDateTime))
                .isInstanceOf(WaitingException.class)
                .hasMessage(WaitingErrorCode.RESERVATION_REQUIRED_FOR_WAITING.getMessage());
    }

    @Test
    void 이미_동일한_날짜와_테마에_대기를_신청한_경우_예외가_발생한다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        fixtureGenerator.saveReservation("기존예약자", reservationDate, reservationTime, theme);

        WaitingRequest request = new WaitingRequest(reservationDate, reservationTime.getId(), theme.getId(), "대기신청자");
        waitingService.create(request, currentDateTime);

        assertThatThrownBy(() -> waitingService.create(request, currentDateTime))
                .isInstanceOf(WaitingException.class)
                .hasMessage(WaitingErrorCode.WAITING_ALREADY_EXISTS.getMessage());
    }

    @Test
    void 본인의_예약에_대기를_신청할_경우_예외가_발생한다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, Month.MAY, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        fixtureGenerator.saveReservation("기존예약자", reservationDate, reservationTime, theme);

        WaitingRequest request = new WaitingRequest(reservationDate, reservationTime.getId(), theme.getId(), "기존예약자");

        assertThatThrownBy(() -> waitingService.create(request, currentDateTime))
                .isInstanceOf(WaitingException.class)
                .hasMessage(WaitingErrorCode.CANNOT_WAIT_OWN_RESERVATION.getMessage());
    }

    @Test
    void 이름에_해당하는_대기순번_목록을_조회한다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, Month.MAY, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        fixtureGenerator.saveReservation("예약자", reservationDate, reservationTime, theme);

        String testUser = "첫번째대기신청자";
        fixtureGenerator.saveWaiting(testUser, reservationDate, reservationTime, theme, currentDateTime);
        fixtureGenerator.saveWaiting("두번째대기신청자", reservationDate, reservationTime, theme, currentDateTime.plusMinutes(1));
        fixtureGenerator.saveWaiting("세번째대기신청자", reservationDate, reservationTime, theme, currentDateTime.plusMinutes(2));

        List<WaitingWithRankResponse> response = waitingService.getWaitingsByName(testUser);

        assertThat(response)
                .singleElement()
                .satisfies(waiting -> {
                    assertThat(waiting.rank()).isEqualTo(1);
                    assertThat(waiting.name()).isEqualTo(testUser);
                });
    }

    @Test
    void 대기를_삭제할_수_있다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        fixtureGenerator.saveReservation("기존예약자", reservationDate, reservationTime, theme);

        String name = "대기신청자";
        WaitingResponse waitingResponse = waitingService.create(
                new WaitingRequest(reservationDate, reservationTime.getId(), theme.getId(), name),
                currentDateTime);

        waitingService.delete(waitingResponse.id(), name);

        assertThatThrownBy(() -> waitingService.delete(waitingResponse.id(), name))
                .isInstanceOf(WaitingException.class)
                .hasMessage(WaitingErrorCode.WAITING_NOT_FOUND.getMessage());
    }

    @Test
    void 대기_삭제_시_이름이_일치하지_않으면_예외가_발생한다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 31, 10, 0);
        LocalDate reservationDate = currentDateTime.toLocalDate();

        Theme theme = fixtureGenerator.saveTheme("테마1", "설명", "https://dsf.sdaf");
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        fixtureGenerator.saveReservation("기존예약자", reservationDate, reservationTime, theme);

        String name = "대기신청자";
        WaitingResponse waitingResponse = waitingService.create(
                new WaitingRequest(reservationDate, reservationTime.getId(), theme.getId(), name),
                currentDateTime);

        assertThatThrownBy(() -> waitingService.delete(waitingResponse.id(), "다른사람"))
                .isInstanceOf(WaitingException.class)
                .hasMessage(WaitingErrorCode.WAITING_NOT_FOUND.getMessage());
    }
}
