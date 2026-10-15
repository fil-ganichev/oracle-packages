package oracle.packages.object;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OracleObjectInstancesTest {

    @Test
    void createAddress_byVarArgs() {
        AddressObjType address = OracleObjectInstances.create(
                AddressObjType.class,
                NumberVar.of(1),
                Varchar2Var.of("Moscow"),
                Varchar2Var.of("Tverskaya"));

        assertThat(address.id.get()).isEqualByComparingTo("1");
        assertThat(address.city.get()).isEqualTo("Moscow");
        assertThat(address.street.get()).isEqualTo("Tverskaya");
    }

    @Test
    void createEmployee_byClassName() {
        AddressObjType address = OracleObjectInstances.create(
                AddressObjType.class,
                NumberVar.of(10),
                Varchar2Var.of("SPb"),
                Varchar2Var.of("Nevsky"));

        EmployeeObjType employee = (EmployeeObjType) OracleObjectInstances.create(
                "oracle.packages.object.EmployeeObjType",
                NumberVar.of(100),
                Varchar2Var.of("Ivan"),
                address);

        assertThat(employee.name.get()).isEqualTo("Ivan");
        assertThat(employee.address.city.get()).isEqualTo("SPb");
    }

    @Test
    void create_byFieldNameMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", NumberVar.of(5));
        values.put("city", Varchar2Var.of("Kazan"));
        values.put("street", Varchar2Var.of("Bauman"));

        AddressObjType address = OracleObjectInstances.create(AddressObjType.class, values);

        assertThat(address.street.get()).isEqualTo("Bauman");
    }

    @Test
    void fieldOrder_subclassOnly() {
        List<String> names = OracleObjectInstances.fieldNamesInConstructorOrder(EmployeeObjType.class);
        assertThat(names).containsExactly("id", "name", "address");
    }

    @Test
    void resolveImplClassName_fromObjType() {
        assertThat(OracleObjectInstances.resolveImplClassName(AddressObjType.class))
                .isEqualTo("oracle.packages.object.AddressObjImpl");
        assertThat(OracleObjectInstances.resolveImplClassName(EmployeeObjType.class))
                .isEqualTo("oracle.packages.object.EmployeeObjImpl");
    }

    @Test
    void createImpl_invokesStaticFactoryAndCopiesFields() {
        AddressObjType address = OracleObjectInstances.create(
                AddressObjType.class,
                NumberVar.of(7),
                Varchar2Var.of("Omsk"),
                Varchar2Var.of("Lenina"));

        Object raw = OracleObjectInstances.createImpl(address);
        assertThat(raw).isInstanceOf(AddressObjImpl.class);

        AddressObjImpl impl = (AddressObjImpl) raw;
        assertThat(impl.id.get()).isEqualByComparingTo("7");
        assertThat(impl.city.get()).isEqualTo("Omsk");
        assertThat(impl.street.get()).isEqualTo("Lenina");
    }

    @Test
    void createImpl_employeeNestedAddress() {
        AddressObjType address = OracleObjectInstances.create(
                AddressObjType.class,
                NumberVar.of(1),
                Varchar2Var.of("City"),
                Varchar2Var.of("Street"));
        EmployeeObjType employee = OracleObjectInstances.create(
                EmployeeObjType.class,
                NumberVar.of(42),
                Varchar2Var.of("Anna"),
                address);

        EmployeeObjImpl impl = (EmployeeObjImpl) OracleObjectInstances.createImpl(employee);

        assertThat(impl.name.get()).isEqualTo("Anna");
        assertThat(impl.address.street.get()).isEqualTo("Street");
    }

    @Test
    void staticFactoryMethodName_decapitalizesClassName() {
        assertThat(OracleObjectInstances.staticFactoryMethodName(AddressObjImpl.class))
                .isEqualTo("addressObjImpl");
    }

    @Test
    void rejectsWrongArgumentCount() {
        assertThatThrownBy(() ->
                OracleObjectInstances.create(AddressObjType.class, NumberVar.of(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field count mismatch");
    }

    @Test
    void rejectsMissingMapKey() {
        assertThatThrownBy(() ->
                OracleObjectInstances.create(
                        AddressObjType.class,
                        Map.of("id", NumberVar.of(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing value for field");
    }

    @Test
    void createImpl_rejectsClassNotEndingWithObjType() {
        assertThatThrownBy(() -> OracleObjectInstances.resolveImplClassName(ObjType.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ObjType");
    }
}
