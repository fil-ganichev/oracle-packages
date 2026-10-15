package oracle.packages.object;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;

/** Impl-класс для {@link EmployeeObjType}. */
public class EmployeeObjImpl {

    public NumberVar id;
    public Varchar2Var name;
    public AddressObjType address;

    public static EmployeeObjImpl employeeObjImpl() {
        return new EmployeeObjImpl();
    }
}
