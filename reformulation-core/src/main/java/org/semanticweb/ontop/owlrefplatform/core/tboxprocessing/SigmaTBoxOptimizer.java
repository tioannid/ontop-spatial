/*
 * Copyright (C) 2009-2013, Free University of Bozen Bolzano
 * This source code is available under the terms of the Affero General Public
 * License v3.
 * 
 * Please see LICENSE.txt for full license terms, including the availability of
 * proprietary exceptions.
 */
package org.semanticweb.ontop.owlrefplatform.core.tboxprocessing;

import java.util.LinkedList;
import java.util.List;

import org.semanticweb.ontop.model.OBDADataFactory;
import org.semanticweb.ontop.model.impl.OBDADataFactoryImpl;
import org.semanticweb.ontop.ontology.Axiom;
import org.semanticweb.ontop.ontology.ClassDescription;
import org.semanticweb.ontop.ontology.Ontology;
import org.semanticweb.ontop.ontology.OntologyFactory;
import org.semanticweb.ontop.ontology.Property;
import org.semanticweb.ontop.ontology.PropertySomeRestriction;
import org.semanticweb.ontop.ontology.impl.OntologyFactoryImpl;
import org.semanticweb.ontop.owlrefplatform.core.dag.DAG;
import org.semanticweb.ontop.owlrefplatform.core.dag.DAGChain;
import org.semanticweb.ontop.owlrefplatform.core.dag.DAGConstructor;
import org.semanticweb.ontop.owlrefplatform.core.dag.DAGEdgeIterator;
import org.semanticweb.ontop.owlrefplatform.core.dag.DAGNode;
import org.semanticweb.ontop.owlrefplatform.core.dag.Edge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prune Ontology for redundant assertions based on dependencies
 */
public class SigmaTBoxOptimizer {

	private static final Logger		log					= LoggerFactory.getLogger(SigmaTBoxOptimizer.class);
	private final DAG				isa;
	private final DAG				sigma;
	private final DAG				isaChain;
	private final DAG				sigmaChain;

	private static final OBDADataFactory	predicateFactory = OBDADataFactoryImpl.getInstance();
	private static final OntologyFactory	descFactory = OntologyFactoryImpl.getInstance();

	private Ontology				originalOntology	= null;

	private Ontology				originalSigma		= null;

	public SigmaTBoxOptimizer(Ontology isat, Ontology sigmat) {
		this.originalOntology = isat;

		this.originalSigma = sigmat;

		this.isa = DAGConstructor.getISADAG(isat);
		this.isa.clean();
		this.sigma = DAGConstructor.getISADAG(sigmat);
		this.sigma.clean();

		this.isaChain = DAGConstructor.getISADAG(isat);
		isaChain.clean();
		DAGChain.getChainDAG(isaChain);
		

		this.sigmaChain = DAGConstructor.getISADAG(sigmat);
		sigmaChain.clean();
		DAGChain.getChainDAG(sigmaChain);

	}

	public Ontology getReducedOntology() {
		Ontology reformulationOntology = descFactory.createOntology("http://it.unibz.krdb/obda/auxontology");
		reformulationOntology.addEntities(originalOntology.getVocabulary());

		reformulationOntology.addAssertions(reduce());
		return reformulationOntology;
	}

	public List<Axiom> reduce() {
		log.debug("Starting semantic-reduction");
		List<Axiom> rv = new LinkedList<Axiom>();

		DAGEdgeIterator edgeIterator = new DAGEdgeIterator(isa);
		while (edgeIterator.hasNext()) {
			Edge edge = edgeIterator.next();
			if (edge.getLeft().getDescription() instanceof ClassDescription) {
				if (!check_redundant(edge.getRight(), edge.getLeft())) {
					rv.add(descFactory.createSubClassAxiom((ClassDescription) edge.getLeft().getDescription(), (ClassDescription) edge
							.getRight().getDescription()));
				}
			} else {
				if (!check_redundant_role(edge.getRight(), edge.getLeft())) {
					rv.add(descFactory.createSubPropertyAxiom((Property) edge.getLeft().getDescription(), (Property) edge.getRight()
							.getDescription()));
				}

			}
		}
//		log.debug("Finished semantic-reduction.");
		return rv;
	}

	private boolean check_redundant_role(DAGNode parent, DAGNode child) {

		if (check_directly_redundant_role(parent, child))
			return true;
		else {
//			log.debug("Not directly redundant role {} {}", parent, child);
			for (DAGNode child_prime : parent.getChildren()) {
				if (!child_prime.equals(child) && check_directly_redundant_role(child_prime, child)
						&& !check_redundant(child_prime, parent)) {
					return true;
				}
			}
		}
//		log.debug("Not redundant role {} {}", parent, child);

		return false;
	}

	private boolean check_directly_redundant_role(DAGNode parent, DAGNode child) {
		Property parentDesc = (Property) parent.getDescription();
		Property childDesc = (Property) child.getDescription();

		PropertySomeRestriction existParentDesc = descFactory.getPropertySomeRestriction(parentDesc.getPredicate(), parentDesc.isInverse());
		PropertySomeRestriction existChildDesc = descFactory.getPropertySomeRestriction(childDesc.getPredicate(), childDesc.isInverse());

		DAGNode exists_parent = isa.getClassNode(existParentDesc);
		DAGNode exists_child = isa.getClassNode(existChildDesc);

		return check_directly_redundant(parent, child) && check_directly_redundant(exists_parent, exists_child);
	}

	private boolean check_redundant(DAGNode parent, DAGNode child) {
		if (check_directly_redundant(parent, child))
			return true;
		else {
			for (DAGNode child_prime : parent.getChildren()) {
				if (!child_prime.equals(child) && check_directly_redundant(child_prime, child) && !check_redundant(child_prime, parent)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean check_directly_redundant(DAGNode parent, DAGNode child) {
		DAGNode sp = sigmaChain.getNode(parent.getDescription());
		DAGNode sc = sigmaChain.getNode(child.getDescription());
		DAGNode tc = isaChain.getNode(child.getDescription());

		if (sp == null || sc == null || tc == null) {
			return false;
		}
		
		if (sigmaChain.equi_mappings.get(parent.getDescription()) != null)
			sp = sigmaChain.getNode(sigmaChain.equi_mappings.get(parent.getDescription()));
		if (sigmaChain.equi_mappings.get(child.getDescription()) != null)
			sc = sigmaChain.getNode(sigmaChain.equi_mappings.get(child.getDescription()));
		if (isaChain.equi_mappings.get(child.getDescription()) != null)
			tc = sigmaChain.getNode(isaChain.equi_mappings.get(child.getDescription()));

		boolean redundant = sp.getChildren().contains(sc) && sc.getDescendants().containsAll(tc.getDescendants());
		return (redundant);

	}

}