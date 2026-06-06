package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import roomescape.dto.request.ReservationRequest;
import roomescape.dto.request.UpdateReservationRequest;
import roomescape.dto.response.ReservationResponse;
import roomescape.dto.response.ReservationTimeResponse;
import roomescape.dto.response.ThemeResponse;
import roomescape.dto.response.WaitingWithRankResponse;
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
class ReservationServiceTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private WaitingService waitingService;

    @Test
    void 예약을_생성할_수_있다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "로지와 러키의 방탈출", "https:fsof/ommff");
        LocalDate reservationDate = LocalDate.of(2026, 5, 8);
        ReservationRequest request = new ReservationRequest("예약1", reservationDate, reservationTime.getId(), theme.getId());

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse response = reservationService.create(request, currentDateTime);

        assertThat(response)
                .extracting(ReservationResponse::name, ReservationResponse::date, r -> r.time().id())
                .containsExactly(request.name(), request.date(), request.timeId());
    }

    @Test
    void 예약_생성시_예약시간이_존재하지_않으면_예외가_발생한다() {
        Theme theme = fixtureGenerator.saveTheme("테마1", "로지와 러키의 방탈출", "https:fsof/ommff");
        ReservationRequest request = new ReservationRequest("예약1", LocalDate.of(2026, 5, 8), 0L, theme.getId());

        assertThatThrownBy(() -> reservationService.create(request, LocalDateTime.of(2026, 5, 7, 10, 0)))
                .isInstanceOf(ReservationTimeException.class)
                .hasMessage(ReservationTimeErrorCode.RESERVATION_TIME_NOT_FOUND.getMessage());
    }

    @Test
    void 예약_생성시_테마가_존재하지_않으면_예외가_발생한다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        ReservationRequest request = new ReservationRequest("예약1", LocalDate.of(2026, 5, 8), reservationTime.getId(), 0L);

        assertThatThrownBy(() -> reservationService.create(request, LocalDateTime.of(2026, 5, 7, 10, 0)))
                .isInstanceOf(ThemeException.class)
                .hasMessage(ThemeErrorCode.THEME_NOT_FOUND.getMessage());
    }

    @Test
    void 예약_생성시_동일한_조건의_예약이_이미_존재하면_예외가_발생한다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "로지와 러키의 방탈출", "https:fsof/ommff");
        ReservationRequest request = new ReservationRequest("예약1", LocalDate.of(2026, 5, 8), reservationTime.getId(), theme.getId());

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        reservationService.create(request, currentDateTime);

        assertThatThrownBy(() -> reservationService.create(request, currentDateTime))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.RESERVATION_ALREADY_EXISTS.getMessage());
    }

    @Test
    void 예약을_수정할_수_있다() {
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");
        ReservationTime originalTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        ReservationTime changedTime = fixtureGenerator.saveReservationTime(LocalTime.of(20, 0));
        LocalDate reservationDate = LocalDate.of(2026, 5, 10);
        LocalDate changedDate = LocalDate.of(2026, 5, 12);

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("예약1", reservationDate, originalTime.getId(), theme.getId()),
                currentDateTime);

        ReservationResponse response = reservationService.update(
                savedReservation.id(),
                new UpdateReservationRequest(changedDate, changedTime.getId()),
                currentDateTime);

        assertAll(
                () -> assertThat(response)
                        .extracting(ReservationResponse::id, ReservationResponse::name, ReservationResponse::date)
                        .containsExactly(savedReservation.id(), "예약1", changedDate),
                () -> assertThat(response.time())
                        .extracting(ReservationTimeResponse::id, ReservationTimeResponse::startAt)
                        .containsExactly(changedTime.getId(), changedTime.getStartAt()),
                () -> assertThat(response.theme())
                        .extracting(ThemeResponse::id, ThemeResponse::name)
                        .containsExactly(theme.getId(), theme.getName())
        );
    }

    @Test
    void 예약_수정_후_변경_전_예약에_대기가_존재하면_승격시킨다() {
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");
        ReservationTime originalTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        ReservationTime changedTime = fixtureGenerator.saveReservationTime(LocalTime.of(20, 0));
        LocalDate reservationDate = LocalDate.of(2026, 5, 10);
        LocalDate changedDate = LocalDate.of(2026, 5, 12);

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("예약1", reservationDate, originalTime.getId(), theme.getId()),
                currentDateTime);

        fixtureGenerator.saveWaiting("대기자", reservationDate, originalTime, theme, LocalDateTime.now());

        reservationService.update(savedReservation.id(), new UpdateReservationRequest(changedDate, changedTime.getId()), currentDateTime);

        List<ReservationResponse> promotedReservations = reservationService.getReservationsByName("대기자");
        assertThat(promotedReservations)
                .hasSize(1)
                .extracting(ReservationResponse::date, r -> r.time().id())
                .containsExactly(tuple(reservationDate, originalTime.getId()));

        List<WaitingWithRankResponse> remainingWaitings = waitingService.getWaitingsByName("대기자");
        assertThat(remainingWaitings).isEmpty();
    }

    @Test
    void 이미_예약된_날짜_시간_테마로는_예약을_수정할_수_없다() {
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");
        LocalDate alreadyReservedDate = LocalDate.of(2026, 5, 12);
        ReservationTime alreadyReservedTime = fixtureGenerator.saveReservationTime(LocalTime.of(20, 0));

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        reservationService.create(new ReservationRequest("로지", alreadyReservedDate, alreadyReservedTime.getId(), theme.getId()), currentDateTime);

        ReservationTime originalTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("러키", LocalDate.of(2026, 5, 10), originalTime.getId(), theme.getId()),
                currentDateTime);

        assertThatThrownBy(() -> reservationService.update(savedReservation.id(), new UpdateReservationRequest(alreadyReservedDate, alreadyReservedTime.getId()), currentDateTime))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.RESERVATION_ALREADY_EXISTS.getMessage());
    }

    @Test
    void 현재_시간보다_이전_날짜로_예약을_수정하면_예외가_발생한다() {
        ReservationTime originalTime = fixtureGenerator.saveReservationTime(LocalTime.of(11, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("러키", LocalDate.of(2026, 5, 25), originalTime.getId(), theme.getId()),
                currentDateTime);

        ReservationTime pastTime = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        assertThatThrownBy(() -> reservationService.update(savedReservation.id(),
                new UpdateReservationRequest(currentDateTime.minusDays(1).toLocalDate(), pastTime.getId()),
                currentDateTime))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.PAST_DATE_NOT_ALLOWED.getMessage());
    }

    @Test
    void 예약_시간_또는_날짜_변경하지_않으면_예외가_발생한다() {
        ReservationTime originalTime = fixtureGenerator.saveReservationTime(LocalTime.of(11, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");

        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("러키", LocalDate.of(2026, 5, 25), originalTime.getId(), theme.getId()),
                currentDateTime);

        assertThatThrownBy(() -> reservationService.update(savedReservation.id(),
                new UpdateReservationRequest(savedReservation.date(), savedReservation.time().id()),
                currentDateTime))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.RESERVATION_NOT_CHANGED.getMessage());
    }

    @Test
    void 예약을_삭제할_수_있다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "로지와 러키의 방탈출", "https:fsof/ommff");
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);
        ReservationResponse response = reservationService.create(
                new ReservationRequest("예약1", LocalDate.of(2026, 5, 8), reservationTime.getId(), theme.getId()),
                currentDateTime);

        int beforeSize = reservationService.getReservations().size();
        reservationService.delete(response.id(), currentDateTime);

        List<ReservationResponse> reservations = reservationService.getReservations();
        assertAll(
                () -> assertThat(reservations).hasSize(beforeSize - 1),
                () -> assertThat(reservations).extracting(ReservationResponse::id).doesNotContain(response.id())
        );
    }

    @Test
    void 예약_삭제_후_삭제_전_예약에_대기가_존재하면_승격시킨다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        Theme theme = fixtureGenerator.saveTheme("방탈출1", "로지와 러키의 방탈출", "https:fsof/ommff");
        LocalDate reservationDate = LocalDate.of(2026, 5, 10);
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 7, 10, 0);

        ReservationResponse savedReservation = reservationService.create(
                new ReservationRequest("예약1", reservationDate, reservationTime.getId(), theme.getId()),
                currentDateTime);
        fixtureGenerator.saveWaiting("대기자", reservationDate, reservationTime, theme, LocalDateTime.now());

        reservationService.delete(savedReservation.id(), currentDateTime.plusHours(1));

        List<ReservationResponse> promotedReservations = reservationService.getReservationsByName("대기자");
        assertThat(promotedReservations)
                .hasSize(1)
                .extracting(ReservationResponse::date, r -> r.time().id())
                .containsExactly(tuple(reservationDate, reservationTime.getId()));

        assertThat(waitingService.getWaitingsByName("대기자")).isEmpty();
    }

    @Test
    void 삭제할_예약이_존재하지_않으면_예외를_반환한다() {
        assertThatThrownBy(() -> reservationService.delete(0L, LocalDateTime.of(2026, 5, 7, 10, 0)))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.RESERVATION_NOT_FOUND.getMessage());
    }

    @Test
    void 예약_취소_마감_기한이_지난_예약은_삭제할_수_없다() {
        ReservationTime reservationTime = fixtureGenerator.saveReservationTime(LocalTime.of(13, 0));
        Theme theme = fixtureGenerator.saveTheme("테마1", "로지와 러키의 방탈출", "https:fsof/ommff");
        LocalDate reservationDate = LocalDate.of(2026, 5, 5);

        ReservationResponse response = reservationService.create(
                new ReservationRequest("예약1", reservationDate, reservationTime.getId(), theme.getId()),
                LocalDateTime.of(2026, 5, 1, 10, 0));

        int beforeSize = reservationService.getReservations().size();

        assertThatThrownBy(() -> reservationService.delete(response.id(), LocalDateTime.of(2026, 5, 4, 14, 0)))
                .isInstanceOf(ReservationException.class)
                .hasMessage(ReservationErrorCode.RESERVATION_CANCEL_DEADLINE_PASSED.getMessage());

        List<ReservationResponse> reservations = reservationService.getReservations();
        assertAll(
                () -> assertThat(reservations).hasSize(beforeSize),
                () -> assertThat(reservations).extracting(ReservationResponse::id).contains(response.id())
        );
    }
}
