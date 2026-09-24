package oracle.packages.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OracleTableCollectionBindTest {

    @Test
    void toBindArray_convertsNumericAndTimestamp() {
        Object[] nums = OracleTableCollectionBind.toBindArray(
                List.of(1, 2L, new BigDecimal("3.5")),
                OracleOdciCollectionType.NUMBER,
                Integer.class);
        assertThat(nums[0]).isEqualTo(BigDecimal.valueOf(1));
        assertThat(nums[1]).isEqualTo(BigDecimal.valueOf(2));
        assertThat(nums[2]).isEqualTo(new BigDecimal("3.5"));

        Timestamp ts = Timestamp.from(Instant.parse("2024-05-01T12:00:00Z"));
        Object[] dates = OracleTableCollectionBind.toBindArray(
                List.of(ts),
                OracleOdciCollectionType.DATE,
                Timestamp.class);
        assertThat(dates[0]).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) dates[0]).getTime()).isEqualTo(ts.getTime());
    }

    @Test
    void toBindArray_emptyCollection() {
        Object[] empty = OracleTableCollectionBind.toBindArray(
                List.of(),
                OracleOdciCollectionType.NUMBER,
                Integer.class);
        assertThat(empty).isEmpty();
    }
}
