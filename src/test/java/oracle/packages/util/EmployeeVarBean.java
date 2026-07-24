package oracle.packages.util;

import oracle.packages.BooleanVar;
import oracle.packages.NumberVar;
import oracle.packages.SqlTimestampVar;
import oracle.packages.TimestampVar;
import oracle.packages.Varchar2Var;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * Тестовый бин со свойствами типа {@link oracle.packages.Var}.
 */
public class EmployeeVarBean {

    private NumberVar id;
    private Varchar2Var name;
    private NumberVar age;
    private NumberVar salary;
    private TimestampVar hiredAt;
    private SqlTimestampVar updatedAt;
    private BooleanVar active;

    public EmployeeVarBean() {
    }

    public EmployeeVarBean(
            NumberVar id,
            Varchar2Var name,
            NumberVar age,
            NumberVar salary,
            TimestampVar hiredAt,
            SqlTimestampVar updatedAt,
            BooleanVar active) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.salary = salary;
        this.hiredAt = hiredAt;
        this.updatedAt = updatedAt;
        this.active = active;
    }

    public static EmployeeVarBean of(
            int id,
            String name,
            int age,
            int salary,
            OffsetDateTime hiredAt,
            Timestamp updatedAt,
            boolean active) {
        return new EmployeeVarBean(
                NumberVar.of(id),
                Varchar2Var.of(name),
                NumberVar.of(age),
                NumberVar.of(salary),
                TimestampVar.of(hiredAt),
                SqlTimestampVar.of(updatedAt),
                BooleanVar.of(active));
    }

    public NumberVar getId() {
        return id;
    }

    public void setId(NumberVar id) {
        this.id = id;
    }

    public Varchar2Var getName() {
        return name;
    }

    public void setName(Varchar2Var name) {
        this.name = name;
    }

    public NumberVar getAge() {
        return age;
    }

    public void setAge(NumberVar age) {
        this.age = age;
    }

    public NumberVar getSalary() {
        return salary;
    }

    public void setSalary(NumberVar salary) {
        this.salary = salary;
    }

    public TimestampVar getHiredAt() {
        return hiredAt;
    }

    public void setHiredAt(TimestampVar hiredAt) {
        this.hiredAt = hiredAt;
    }

    public SqlTimestampVar getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(SqlTimestampVar updatedAt) {
        this.updatedAt = updatedAt;
    }

    public BooleanVar getActive() {
        return active;
    }

    public void setActive(BooleanVar active) {
        this.active = active;
    }
}
