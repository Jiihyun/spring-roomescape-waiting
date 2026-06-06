package roomescape.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.restassured.RestAssured;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import roomescape.dto.response.AvailableReservationTimeResponse;
import roomescape.fixture.ApiFixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
public class ReservationTimeControllerTest {

    @LocalServerPort
    private int port;


    @Autowired
    private ApiFixtureGenerator apiFixtureGenerator;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 시간을_조회한다() {
        long reservationTimeId = apiFixtureGenerator.createTime("22:00");
        long themeId = apiFixtureGenerator.createTheme("방탈출1", "다함께 탈출해요 방탈출.", "https://asdfsdf.sdfs");
        LocalDate reservationDate = LocalDate.of(2099, 5, 31);

        apiFixtureGenerator.createReservation("러키", reservationDate, reservationTimeId, themeId);

        List<AvailableReservationTimeResponse> responses = RestAssured.given().log().all()
                .when().get("/times?themeId=" + themeId + "&baseDate=" + reservationDate)
                .then().log().all()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", AvailableReservationTimeResponse.class);

        assertThat(responses)
                .extracting("startAt", "reserved")
                .contains(tuple(LocalTime.of(22, 0), true));
    }
}
