package roomescape.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import roomescape.fixture.ApiFixtureGenerator;
import roomescape.fixture.FixtureGeneratorConfig;

@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Import(FixtureGeneratorConfig.class)
class ThemeControllerTest {

    @LocalServerPort
    private int port;


    @Autowired
    private ApiFixtureGenerator apiFixtureGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    void 테마_목록을_조회한다() {
        apiFixtureGenerator.createTheme("방탈출1", "다함께 탈출해요 방탈출.", "https://asdfsdf.sdfs");

        RestAssured.given().log().all()
                .when().get("/themes")
                .then().log().all()
                .statusCode(200)
                .body("name", hasItem("방탈출1"));
    }

    @Test
    void 최근_7일간_인기_테마_상위_10개를_조회한다() {
        createRankingData();

        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .when().get("/themes/rankings")
                .then().log().all()
                .statusCode(200)
                .body("name", contains(
                        "공포의 저택", "사라진 연구소", "시간 여행자", "감옥 탈출", "마법사의 방",
                        "좀비 바이러스", "해적의 보물", "스파이 미션", "우주 정거장", "고대 유적"
                ))
                .body("name", not(hasItem("지하 벙커")));
    }

    private void createRankingData() {
        LocalDate baseDate = LocalDate.now();

        long time10 = apiFixtureGenerator.createTime("10:00");
        long time11 = apiFixtureGenerator.createTime("11:00");
        long time12 = apiFixtureGenerator.createTime("12:00");
        long time13 = apiFixtureGenerator.createTime("13:00");
        long time14 = apiFixtureGenerator.createTime("14:00");
        long time15 = apiFixtureGenerator.createTime("15:00");
        long time16 = apiFixtureGenerator.createTime("16:00");
        long time17 = apiFixtureGenerator.createTime("17:00");
        long time18 = apiFixtureGenerator.createTime("18:00");

        long horror    = apiFixtureGenerator.createTheme("공포의 저택", "오래된 저택에서 탈출하세요", "https://example.com/theme1.jpg");
        long lab       = apiFixtureGenerator.createTheme("사라진 연구소", "비밀 연구소의 진실을 밝혀내세요", "https://example.com/theme2.jpg");
        long timeTravel = apiFixtureGenerator.createTheme("시간 여행자", "시간의 틈에서 탈출하세요", "https://example.com/theme3.jpg");
        long prison    = apiFixtureGenerator.createTheme("감옥 탈출", "제한 시간 안에 감옥을 탈출하세요", "https://example.com/theme4.jpg");
        long wizard    = apiFixtureGenerator.createTheme("마법사의 방", "마법사의 숨겨진 방을 탐험하세요", "https://example.com/theme5.jpg");
        long zombie    = apiFixtureGenerator.createTheme("좀비 바이러스", "바이러스가 퍼진 도시에서 살아남으세요", "https://example.com/theme6.jpg");
        long pirate    = apiFixtureGenerator.createTheme("해적의 보물", "해적선에 숨겨진 보물을 찾으세요", "https://example.com/theme7.jpg");
        long spy       = apiFixtureGenerator.createTheme("스파이 미션", "비밀 요원이 되어 임무를 완수하세요", "https://example.com/theme8.jpg");
        long space     = apiFixtureGenerator.createTheme("우주 정거장", "고장난 우주 정거장에서 탈출하세요", "https://example.com/theme9.jpg");
        long ancient   = apiFixtureGenerator.createTheme("고대 유적", "고대 유적의 수수께끼를 풀어보세요", "https://example.com/theme10.jpg");
        long bunker    = apiFixtureGenerator.createTheme("지하 벙커", "폐쇄된 지하 벙커에서 탈출하세요", "https://example.com/theme12.jpg");

        // 공포의 저택: 최근 7일 12건
        for (long[] pair : new long[][]{{time10,horror},{time11,horror},{time12,horror},{time13,horror},{time14,horror},
                {time15,horror},{time16,horror},{time17,horror},{time18,horror}}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), pair[0], pair[1]);
        }
        saveReservationFixture("예약자십", baseDate.minusDays(2), time10, horror);
        saveReservationFixture("예약자십일", baseDate.minusDays(2), time11, horror);
        saveReservationFixture("예약자십이", baseDate.minusDays(2), time12, horror);

        // 사라진 연구소: 10건
        for (long t : new long[]{time10,time11,time12,time13,time14,time15,time16,time17,time18}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, lab);
        }
        saveReservationFixture("예약자십", baseDate.minusDays(2), time10, lab);

        // 시간 여행자: 9건
        for (long t : new long[]{time10,time11,time12,time13,time14,time15,time16,time17,time18}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, timeTravel);
        }

        // 감옥 탈출: 8건
        for (long t : new long[]{time10,time11,time12,time13,time14,time15,time16,time17}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, prison);
        }

        // 마법사의 방: 7건
        for (long t : new long[]{time10,time11,time12,time13,time14,time15,time16}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, wizard);
        }

        // 좀비 바이러스: 6건
        for (long t : new long[]{time10,time11,time12,time13,time14,time15}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, zombie);
        }

        // 해적의 보물: 5건
        for (long t : new long[]{time10,time11,time12,time13,time14}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, pirate);
        }

        // 스파이 미션: 4건
        for (long t : new long[]{time10,time11,time12,time13}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, spy);
        }

        // 우주 정거장: 3건
        for (long t : new long[]{time10,time11,time12}) {
            saveReservationFixture("예약자", baseDate.minusDays(1), t, space);
        }

        // 고대 유적: 2건
        saveReservationFixture("예약자일", baseDate.minusDays(1), time10, ancient);
        saveReservationFixture("예약자이", baseDate.minusDays(1), time11, ancient);

        // 지하 벙커: 예약 많지만 7일 밖 → 랭킹 미반영
        for (long t : new long[]{time10,time11,time12,time13,time14,time15,time16,time17,time18}) {
            saveReservationFixture("오래된예약자", baseDate.minusDays(8), t, bunker);
        }
        saveReservationFixture("미래예약자일", baseDate.plusDays(1), time10, bunker);
        saveReservationFixture("미래예약자이", baseDate.plusDays(2), time11, bunker);
        saveReservationFixture("미래예약자삼", baseDate.plusDays(2), time12, bunker);
    }

    private void saveReservationFixture(String name, LocalDate date, long timeId, long themeId) {
        String slotSql = "INSERT INTO slot (date, time_id, theme_id) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(slotSql, new String[]{"id"});
            ps.setObject(1, date);
            ps.setLong(2, timeId);
            ps.setLong(3, themeId);
            return ps;
        }, keyHolder);
        long slotId = keyHolder.getKey().longValue();
        jdbcTemplate.update("INSERT INTO reservation (name, slot_id) VALUES (?, ?)", name, slotId);
    }
}
