package oracle.packages.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Строка каталога {@code USER_VIEWS}. Колонку {@code TEXT} (LONG) в сущность не маппим —
 * Hibernate читает её как CLOB и падает ({@code ORA-17004}).
 */
@Entity
@Immutable
@Table(name = "USER_VIEWS")
public class UserView {

    @Id
    @Column(name = "VIEW_NAME", nullable = false, length = 128)
    private String viewName;

    protected UserView() {
    }

    public String getViewName() {
        return viewName;
    }
}
