package oracle.packages.object;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;

/** Пример OBJECT TYPE: сотрудник с вложенным объектом. */
public class EmployeeObjType extends ObjType {

    public NumberVar id;
    public Varchar2Var name;
    public AddressObjType address;

    public EmployeeObjType(NumberVar id, Varchar2Var name, AddressObjType address) {
        this.id = id;
        this.name = name;
        this.address = address;
    }
}
