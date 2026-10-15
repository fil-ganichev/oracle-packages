package oracle.packages.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserViewRepository extends JpaRepository<UserView, String> {

    /**
     * Прямой SELECT LONG-колонки в native query: ojdbc может вернуть {@link String},
     * но маппинг той же колонки на поле сущности с {@code @Lob} ломается ({@code ORA-17004}).
     */
    @Query(value = """
            SELECT TEXT
            FROM USER_VIEWS
            WHERE VIEW_NAME = :viewName
            """, nativeQuery = true)
    Optional<String> findViewTextLongColumn(@Param("viewName") String viewName);

    /**
     * {@code TO_LOB(TEXT)} в чистом SQL для {@code USER_VIEWS} не компилируется ({@code ORA-00932}).
     * Обёртка в PL/SQL переводит LONG в CLOB, который JPA мапит в {@link String}.
     */
    @Query(value = """
            SELECT DBMS_LOB.SUBSTR(jpa_read_user_view_text(:viewName), 32767, 1)
            FROM DUAL
            """, nativeQuery = true)
    Optional<String> findViewTextViaPlSqlWrapper(@Param("viewName") String viewName);

    /**
     * Альтернатива без чтения {@code TEXT}: DDL представления (фрагмент до 32 КБ как {@link String}).
     */
    @Query(value = """
            SELECT DBMS_LOB.SUBSTR(DBMS_METADATA.GET_DDL('VIEW', :viewName, USER), 32767, 1)
            FROM DUAL
            """, nativeQuery = true)
    Optional<String> findViewDdl(@Param("viewName") String viewName);
}
