package it.unibz.krdb.obda.model.impl;

import it.unibz.krdb.obda.model.Variable;

import com.sun.msv.datatype.xsd.XSDatatype;

public class UndistinguishedVariable implements Variable {

	private String name= "_";
	private final int identifier = -4000;
	private final XSDatatype type = null;

	protected UndistinguishedVariable() {

	}

	@Override
	public boolean equals(Object obj){
		 if (obj == null || !(obj instanceof UndistinguishedVariable)) {
			 return false;
		 }

		 UndistinguishedVariable var2 = (UndistinguishedVariable) obj;
		 return this.identifier == var2.hashCode();
	 }

	@Override
	public int hashCode(){
		return identifier;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Variable clone() {
		return new UndistinguishedVariable();
	}

	public void setName(String n){
		name = n;
	}

	@Override
	public String toString() {
		return getName();
	}
}
