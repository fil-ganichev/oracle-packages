package oracle.packages.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Только для демонстрации: маппинг LONG-колонки {@code TEXT} через {@link Lob} не работает.
 */
@Entity
@Immutable
@Table(name = "USER_VIEWS")
class UserViewTextLongEntity {

    @Id
    @Column(name = "VIEW_NAME", nullable = false, length = 128)
    private String viewName;

    @Lob
    @Column(name = "TEXT")
    private String text;

    protected UserViewTextLongEntity() {
    }

    String getViewName() {
        return viewName;
    }

    String getText() {
        return text;
    }
}
