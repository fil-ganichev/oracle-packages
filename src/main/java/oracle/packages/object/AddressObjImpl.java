package oracle.packages.object;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;

/** Impl-класс для {@link AddressObjType}. */
public class AddressObjImpl {

    public NumberVar id;
    public Varchar2Var city;
    public Varchar2Var street;

    public static AddressObjImpl addressObjImpl() {
        return new AddressObjImpl();
    }
}
