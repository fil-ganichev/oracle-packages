package oracle.packages.object;

import oracle.packages.NumberVar;
import oracle.packages.Varchar2Var;

/** Пример OBJECT TYPE: адрес. */
public class AddressObjType extends ObjType {

    public NumberVar id;
    public Varchar2Var city;
    public Varchar2Var street;

    public AddressObjType(NumberVar id, Varchar2Var city, Varchar2Var street) {
        this.id = id;
        this.city = city;
        this.street = street;
    }
}
