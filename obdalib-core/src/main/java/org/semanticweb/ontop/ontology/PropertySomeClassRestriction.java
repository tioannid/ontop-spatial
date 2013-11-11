/*
 * Copyright (C) 2009-2013, Free University of Bozen Bolzano
 * This source code is available under the terms of the Affero General Public
 * License v3.
 * 
 * Please see LICENSE.txt for full license terms, including the availability of
 * proprietary exceptions.
 */
package org.semanticweb.ontop.ontology;

import org.semanticweb.ontop.model.Predicate;

/**
 * An OWL2 property restriction on a named class. Corresponds to owl 2 QL
 * qualified property restrictions or DL existential roles.
 */
public interface PropertySomeClassRestriction extends ClassDescription {
	
	public boolean isInverse();

	public Predicate getPredicate();

	public OClass getFiller();
}