package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import roomescape.dao.ReservationDao;
import roomescape.dao.WaitingDao;
import roomescape.domain.Reservation;
import roomescape.domain.ReservationTime;
import roomescape.domain.Theme;
import roomescape.domain.Waiting;
import roomescape.dto.request.UpdateReservationRequest;
import roomescape.fixture.FixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

/*
 * 학습용 - 트랜잭션 테스트
 */
@Disabled
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class ReservationTransactionTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationDao reservationDao;

    @MockitoSpyBean
    private WaitingDao waitingDao;

    @Test
    void 대기자_승격_과정에서_예외가_발생하면_예약_수정도_함께_롤백된다() {
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDate originalDate = LocalDate.of(2026, 7, 10);
        LocalDate newDate = LocalDate.of(2026, 7, 20);

        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마", "설명", "https://thumbnail");

        Reservation reservation = fixtureGenerator.saveReservation("예약자", originalDate, time, theme);
        Waiting waiting = fixtureGenerator.saveWaiting("대기자", originalDate, time, theme, currentDateTime);

        doThrow(new RuntimeException("예상치 못한 예외")).when(waitingDao).delete(anyLong());

        assertThatThrownBy(() -> reservationService.update(
                reservation.getId(),
                new UpdateReservationRequest(newDate, time.getId()),
                currentDateTime))
                .isInstanceOf(RuntimeException.class);

        Reservation foundReservation = reservationDao.findById(reservation.getId()).orElseThrow();
        assertThat(foundReservation.getDate()).isEqualTo(originalDate);

        Waiting foundWaiting = waitingDao.findFirstBySlot(reservation.getSlot().getId()).orElseThrow();
        assertThat(foundWaiting.getName()).isEqualTo(waiting.getName());
    }

    @Test
    void 대기자_승격_과정에서_예외가_발생하면_예약_삭제도_함께_롤백된다() {
        LocalDate reservationDate = LocalDate.of(2026, 7, 10);
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마", "설명", "https://thumbnail");
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 5, 1, 10, 0);

        Reservation reservation = fixtureGenerator.saveReservation("예약자", reservationDate, time, theme);
        Waiting waiting = fixtureGenerator.saveWaiting("대기자", reservationDate, time, theme, currentDateTime);

        doThrow(new RuntimeException("예상치 못한 예외")).when(waitingDao).delete(anyLong());

        assertThatThrownBy(() -> reservationService.delete(reservation.getId(), currentDateTime))
                .isInstanceOf(RuntimeException.class);

        assertAll(
                () -> assertThat(reservationDao.findById(reservation.getId())).isPresent(),
                () -> assertThat(waitingDao.findFirstBySlot(reservation.getSlot().getId()).get()).isEqualTo(waiting)
        );
    }
}
