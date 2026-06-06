package roomescape.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import roomescape.dao.ReservationDao;
import roomescape.domain.ReservationTime;
import roomescape.domain.Theme;
import roomescape.dto.request.ReservationRequest;
import roomescape.fixture.FixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

/*
 * 학습용 - 동시성 테스트
 */
@Disabled
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class ReservationConcurrencyTest {


    @Autowired
    private FixtureGenerator fixtureGenerator;

    @MockitoSpyBean
    private ReservationDao reservationDao;

    @Autowired
    private ReservationService reservationService;

    @Test
    void 동시에_예약을_생성하면_존재_검증와_저장_사이에_RaceCondition이_발생한다() throws InterruptedException {
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마", "설명", "https://thumbnail");
        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 6, 1, 10, 0);
        fixtureGenerator.saveSlot(date, time, theme);

        int threadCount = 2;
        CountDownLatch bothCheckedLatch = new CountDownLatch(threadCount);

        doAnswer(invocation -> {
            boolean result = (boolean) invocation.callRealMethod();
            bothCheckedLatch.countDown();
            bothCheckedLatch.await(5, TimeUnit.SECONDS);
            return result;
        }).when(reservationDao).existsByThemeAndDateAndTime(anyLong(), any(LocalDate.class), anyLong());

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> caughtExceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String name = "예약자" + i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.create(new ReservationRequest(name, date, time.getId(), theme.getId()), currentDateTime);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    caughtExceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertAll(
                () -> assertThat(successCount.get() + caughtExceptions.size()).isEqualTo(threadCount),
                () -> assertThat(reservationService.getReservations()).hasSize(1),
                () -> assertThat(caughtExceptions).hasSize(1),
                () -> assertThat(caughtExceptions.getFirst()).isInstanceOf(DuplicateKeyException.class)
        );
    }

    @Test
    void 동시에_여러_예약을_생성해도_최종적으로_예약은_1건만_저장된다() throws InterruptedException {
        ReservationTime time = fixtureGenerator.saveReservationTime(LocalTime.of(10, 0));
        Theme theme = fixtureGenerator.saveTheme("테마", "설명", "https://thumbnail");
        LocalDate date = LocalDate.of(2026, 7, 1);
        LocalDateTime currentDateTime = LocalDateTime.of(2026, 6, 1, 10, 0);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            String name = "예약자" + i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.create(new ReservationRequest(name, date, time.getId(), theme.getId()), currentDateTime);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(reservationService.getReservations()).hasSize(1);
    }
}
