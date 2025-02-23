package nextstep.jdbc;

import java.util.List;

public class JdbcTemplateUtils {

    private JdbcTemplateUtils() {
        throw new RuntimeException("생성할 수 없는 클래스입니다.");
    }

    public static <T> T singleResult(final List<T> results) {
        if (results.isEmpty()) {
            throw new DataAccessException("일치하는 데이터가 없습니다.");
        }
        if (results.size() > 1) {
            throw new DataAccessException(String.format("조회 데이터 갯수가 %d 입니다.", results.size()));
        }
        return results.get(0);
    }
}
